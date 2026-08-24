package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.ChapterDao
import com.geozelot.homer.data.db.entity.ChapterEntity
import com.geozelot.homer.data.db.entity.ChapterTier
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Fills in per-file playback durations for a book the first time it is opened, then sums
 * them into the book's total. ExoPlayer already reports the *current* chapter's length,
 * but the whole-book total needs each file probed once (see [DurationExtractor]); results
 * cache in Room so this only pays the network cost once per book.
 *
 * Runs per-book (unlike the library-wide [CoverEnricher]) because probing every file of
 * all ~300 books up front would be far too much streaming — durations are only wanted for
 * books the user actually opens.
 */
@Singleton
class DurationEnricher @Inject constructor(
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val chapterDao: ChapterDao,
    private val credentialStore: CredentialStore,
    private val webDavClient: WebDavClient,
    private val durationExtractor: DurationExtractor,
    private val mp4ChapterParser: Mp4ChapterParser,
    private val librarySettings: LibrarySettings,
    private val networkMonitor: NetworkMonitor,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "unhandled duration-enrich error", e) },
    )
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    /** Process-wide: once we learn this device can't do decoder-free probes, stop paying for it. */
    private val fastProbes = FastProbeGate()

    /** Fire-and-forget; no-op if this book is already fully measured or being measured. */
    fun enrich(bookId: String) {
        scope.launch { measure(bookId) }
    }

    /**
     * Measures every book in [bookIds], one at a time, reporting `(done, total)` as it goes.
     *
     * Sequential on purpose. [enrich] returns immediately and does its work on an internal scope,
     * which is right for the one book being opened — but calling it in a loop over a whole library
     * would put hundreds of probe jobs on the network at once. Each book here is awaited before the
     * next begins.
     *
     * Stops early when the connection drops: [measure] declines to record anything as unmeasurable
     * while offline, so continuing would just burn a 30-second timeout per file for nothing.
     */
    suspend fun measureAll(bookIds: List<String>, onProgress: suspend (Int, Int) -> Unit) {
        for ((index, bookId) in bookIds.withIndex()) {
            coroutineContext.ensureActive()
            if (!networkMonitor.isOnline()) {
                Log.i(TAG, "measureAll: offline after $index/${bookIds.size}, stopping")
                return
            }
            // index + 1: the label reads "Measuring N of M", so N is the book being worked on,
            // not the number already behind us — reporting the raw index showed "0 of 41" for the
            // whole of the first book.
            onProgress(index + 1, bookIds.size)
            measure(bookId)
        }
        onProgress(bookIds.size, bookIds.size)
    }

    /** The measurement itself. Suspends until this book is done; no-op if it is already in hand. */
    private suspend fun measure(bookId: String) {
        if (!inFlight.add(bookId)) return
        try {
            val credentials = credentialStore.awaitCredentials() ?: return
            // Offline, nothing can be measured — and crucially nothing may be RECORDED as
            // unmeasurable: probe() returns null both for "this file has no duration" and for
            // "I couldn't reach it", so a single offline open would otherwise mark every file
            // and the book permanently attempted. A book that never becomes fully measured
            // loses its time-left, progress ring and auto-finish until a full re-scan.
            if (!networkMonitor.isOnline()) return
            val libraryRoot = librarySettings.libraryRoot.first()
            val files = audioFileDao.findForBook(bookId)
            val book = bookDao.findById(bookId)
            // A probe that came back empty is recorded, because nothing else ever settles these
            // questions: a book whose tags simply carry no genre, and a file whose duration
            // can't be read, would otherwise be re-streamed on every single open of the book,
            // forever. A full library refresh clears the flags to allow a retry.
            val metadataTried = book?.metadataAttempted ?: false
            val needsGenre = book?.genre == null && !metadataTried
            // Embedded chapters only apply to single-file books (a multi-file book's file list
            // already is its chapter list). Extract once, when the tier is still undetermined.
            val needsChapters = files.size == 1 && !metadataTried &&
                (book?.chapterTier ?: ChapterTier.UNDETERMINED) == ChapterTier.UNDETERMINED
            val missing = files.filter { it.durationMs == null && !it.durationAttempted }
            if (missing.isEmpty() && !needsGenre && !needsChapters) return
            if (missing.isNotEmpty()) Log.i(TAG, "measuring ${missing.size}/${files.size} files for book $bookId")

            // Genre + embedded chapters live on the (first) file; capture them from its probe.
            var genre: String? = null
            var firstProbe: DurationExtractor.Probe? = null
            for (file in missing) {
                coroutineContext.ensureActive()
                // Connectivity can drop part-way through. Every further probe would only wait
                // out its 30s timeout and then be discarded, so a 40-file book would spend
                // twenty minutes achieving nothing. Stop and let a later open resume.
                if (!networkMonitor.isOnline()) break
                val url = webDavClient.urlFor(credentials, libraryRoot, file.relativePath).toString()

                // Decoder-free first. A duration lives in the container header, but the full probe
                // reaches it by building a player with renderers and waiting for STATE_READY —
                // which instantiates a hardware audio decoder per file, seconds each, and floods
                // logcat with codec chatter that evicts Homer's own lines from the log window.
                var measured = if (fastProbes.shouldTryFast()) durationExtractor.probeDuration(url) else null
                if (measured != null) {
                    fastProbes.onFastSuccess()
                } else {
                    // The FULL probe is the authority. Nothing is ever recorded as unmeasurable on
                    // the fast probe's word, so a device where it silently returns nothing loses
                    // time and never correctness — and the gate stops that loss being unbounded.
                    val full = durationExtractor.probe(url)
                    measured = full.durationMs
                    if (genre == null) genre = full.genre
                    if (firstProbe == null) firstProbe = full
                    when {
                        measured == null -> fastProbes.onBothFailed()
                        fastProbes.onFullProbeRescue() ->
                            Log.i(TAG, "decoder-free probes aren't working here; using the full probe from now on")
                    }
                }

                if (measured != null) {
                    audioFileDao.updateDuration(file.relativePath, measured)
                } else if (networkMonitor.isOnline()) {
                    // Only a negative answer we can trust: still online, so "no duration" is
                    // about the file, not the connection.
                    audioFileDao.markDurationAttempted(file.relativePath)
                }
            }

            // Nothing was probed above but genre/chapters are still wanted — probe the first
            // file once just for its tags.
            if ((needsGenre && genre == null) || (needsChapters && firstProbe == null)) {
                files.firstOrNull()?.let { first ->
                    coroutineContext.ensureActive()
                    val url = webDavClient.urlFor(credentials, libraryRoot, first.relativePath).toString()
                    val probe = durationExtractor.probe(url)
                    if (genre == null) genre = probe.genre
                    if (firstProbe == null) firstProbe = probe
                }
            }
            if (needsGenre && genre != null) bookDao.updateGenre(bookId, genre!!)
            // No genre in the tags, or the probe itself failed: settle it so the next open
            // doesn't stream this book's first file all over again for the same answer.
            if (((needsGenre && genre == null) || (needsChapters && firstProbe == null)) &&
                networkMonitor.isOnline()
            ) {
                bookDao.markMetadataAttempted(bookId)
            }

            // Persist embedded chapters (empty = none found) and settle the tier so we don't
            // re-probe on every open.
            if (needsChapters && firstProbe != null) {
                var marks = firstProbe!!.chapters
                // ID3 CHAP covers MP3; for MP4/M4B (no ID3 chapters) fall back to the Nero
                // `chpl` parser over the same authed source.
                val first = files.firstOrNull()
                if (marks.isEmpty() && first != null && first.relativePath.isMp4Family()) {
                    val url = webDavClient.urlFor(credentials, libraryRoot, first.relativePath).toString()
                    marks = mp4ChapterParser.parse(url)
                    if (marks.isNotEmpty()) Log.i(TAG, "book $bookId: ${marks.size} chapters from mp4 chpl")
                }
                chapterDao.replaceForBook(
                    bookId,
                    marks.mapIndexed { i, m -> ChapterEntity(bookId = bookId, sortIndex = i, title = m.title, startMs = m.startMs) },
                )
                bookDao.updateChapterTier(bookId, if (marks.isNotEmpty()) ChapterTier.EMBEDDED else ChapterTier.NONE)
                if (marks.isNotEmpty()) Log.i(TAG, "book $bookId: ${marks.size} embedded chapters")
            }

            // All-or-nothing, matching LibraryScanner: a PARTIAL sum under-reports the book
            // length, so whole-book elapsed exceeds it and the book reads as "finished" —
            // which is what silently emptied the Currently-listening shelf. A later open measures the
            // rest and the total lands then.
            val all = audioFileDao.findForBook(bookId)
            val durations = all.mapNotNull { it.durationMs }
            if (all.isNotEmpty() && durations.size == all.size) {
                bookDao.updateTotalDuration(bookId, durations.sum())
            }
            Log.i(TAG, "book $bookId: ${durations.size}/${all.size} files measured, genre=${genre ?: "—"}")
        } finally {
            inFlight.remove(bookId)
        }
    }

    private companion object {
        const val TAG = "HomerMeta"
    }
}

/** MP4-family containers whose chapters live in a `chpl`/track rather than ID3 CHAP. */
private fun String.isMp4Family(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("m4b", "m4a", "mp4", "aac")
