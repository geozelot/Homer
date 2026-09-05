package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.ChapterDao
import com.geozelot.homer.data.db.entity.ChapterEntity
import com.geozelot.homer.data.db.entity.ChapterTier
import com.geozelot.homer.data.download.DownloadStorage
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
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
    private val audioHeaderDuration: AudioHeaderDuration,
    private val audioHeaderTags: AudioHeaderTags,
    private val mp4ChapterParser: Mp4ChapterParser,
    private val librarySettings: LibrarySettings,
    private val networkMonitor: NetworkMonitor,
    private val downloadStorage: DownloadStorage,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "unhandled duration-enrich error", e) },
    )
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    /** Process-wide: once we learn this device can't do decoder-free probes, stop paying for it. */
    private val fastProbes = FastProbeGate()

    /** How far a whole-library measure pass has got, in both units the user cares about. */
    data class MeasureProgress(
        val files: Int,
        val fileTotal: Int,
        val books: Int,
        val bookTotal: Int,
    )

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
    suspend fun measureAll(bookIds: List<String>, onProgress: suspend (MeasureProgress) -> Unit) {
        // Files as well as books. A "book" here can be a 1020-file complete edition, so book-level
        // progress alone sat on "1 of 226" for the whole of it — which reads as a hang, and was
        // reported as one. Books alone say how much is left; files say that something is moving.
        val fileTotal = audioFileDao.countUnmeasured()
        // Atomic: measure() runs a book's files concurrently, so this is incremented from several
        // coroutines and a plain var would quietly lose counts.
        val filesDone = AtomicInteger(0)
        // Serialised for the same reason. This callback ends in WorkManager's setForeground and
        // setProgress, which must not be entered from several coroutines at once: doing so failed
        // the foreground promotion, and because that failure is caught and shrugged off, the
        // notification silently vanished for the rest of the pass.
        val publishLock = Mutex()

        suspend fun publish(books: Int) = publishLock.withLock {
            onProgress(MeasureProgress(filesDone.get(), fileTotal, books, bookIds.size))
        }

        publish(0)
        for ((index, bookId) in bookIds.withIndex()) {
            coroutineContext.ensureActive()
            if (!networkMonitor.isOnline()) {
                Log.i(TAG, "measureAll: offline after $index/${bookIds.size} books, stopping")
                return
            }
            measure(bookId) {
                filesDone.incrementAndGet()
                publish(index + 1)
            }
        }
        filesDone.set(fileTotal)
        publish(bookIds.size)
    }

    /** The measurement itself. Suspends until this book is done; no-op if it is already in hand. */
    private suspend fun measure(bookId: String, onFileDone: suspend () -> Unit = {}) {
        if (!inFlight.add(bookId)) return
        try {
            val credentials = credentialStore.awaitCredentials() ?: return
            val libraryRoot = librarySettings.libraryRoot.first()
            val files = audioFileDao.findForBook(bookId)
            // Where each file can be read WITHOUT the network, when it has been downloaded.
            //
            // The reader underneath is a DefaultDataSource, which dispatches on the scheme — so a
            // content:// or file:// URI is read by exactly the same header parser that reads an
            // http one, only locally and with no round trip. Which makes a downloaded book the
            // cheapest thing there is to measure, and it is the case where measuring matters
            // most: offline is when a chapter list cannot fall back to asking the server.
            val local = files.associate { it.relativePath to downloadStorage.uri(it.relativePath) }
            // Offline, nothing that is not already here can be measured — and crucially nothing may
            // be RECORDED as unmeasurable: probe() returns null both for "this file has no
            // duration" and for "I couldn't reach it", so a single offline open would otherwise
            // mark every file and the book permanently attempted. A book that never becomes fully
            // measured loses its time-left, progress ring and auto-finish until a full re-scan.
            val offline = !networkMonitor.isOnline()
            if (offline && files.none { local[it.relativePath] != null }) return
            val book = bookDao.findById(bookId)
            // A probe that came back empty is recorded, because nothing else ever settles these
            // questions: a book whose tags simply carry no genre, and a file whose duration
            // can't be read, would otherwise be re-streamed on every single open of the book,
            // forever. A full library refresh clears the flags to allow a retry.
            val metadataTried = book?.metadataAttempted ?: false
            val needsGenre = book?.genre == null && !metadataTried
            // The crawl may already have read a language out of the file names. That is a real
            // answer, so a tag read is only wanted where it found nothing.
            val needsLanguage = book?.language == null && !metadataTried
            // Embedded chapters only apply to single-file books (a multi-file book's file list
            // already is its chapter list). Extract once, when the tier is still undetermined.
            val needsChapters = files.size == 1 && !metadataTried &&
                (book?.chapterTier ?: ChapterTier.UNDETERMINED) == ChapterTier.UNDETERMINED
            val missing = files.filter { it.durationMs == null && !it.durationAttempted }
            if (missing.isEmpty() && !needsGenre && !needsLanguage && !needsChapters) return
            if (missing.isNotEmpty()) Log.i(TAG, "measuring ${missing.size}/${files.size} files for book $bookId")

            // Genre + embedded chapters live on the (first) file; captured from a probe if one
            // runs. Guarded by [state] below, because the files are measured concurrently.
            val state = BookMeasureState(total = missing.size, startedAtMs = System.currentTimeMillis())

            // Files are independent, and what is left after the header reader landed is almost
            // entirely round-trip latency on small ranged reads — so the way to go faster is to
            // have several in flight. Book-at-a-time is preserved; only the files inside one book
            // overlap, which keeps the progress denominator and the genre/chapter logic simple.
            coroutineScope {
                val slots = Semaphore(MEASURE_CONCURRENCY)
                // The ExoPlayer tiers stay SERIAL. A device has a small, finite number of hardware
                // decoders, and running several full probes at once would surface as probe
                // failures — which this code records as "unmeasurable". Concurrency must never be
                // able to turn a readable file into a permanently unmeasured one, so the fallback
                // path keeps exactly the behaviour it has today.
                val probeSlot = Semaphore(1)
                missing.map { file ->
                    async {
                        slots.withPermit {
                            ensureActive()
                            val localUri = local[file.relativePath]
                            // Connectivity can drop part-way through. Every further probe would
                            // only wait out its timeout and be discarded, so stop claiming files
                            // and let a later pass resume — unless this one is on the device, in
                            // which case the network was never in the path at all.
                            if (localUri == null && !networkMonitor.isOnline()) return@withPermit
                            val url = localUri?.toString()
                                ?: webDavClient.urlFor(credentials, libraryRoot, file.relativePath).toString()
                            val measured = measureFile(url, file.sizeBytes, probeSlot, state)

                            if (measured != null) {
                                audioFileDao.updateDuration(file.relativePath, measured)
                            } else if (localUri != null || networkMonitor.isOnline()) {
                                // Only a negative answer we can trust: the file was here on the
                                // device, or we are still online — so "no duration" is about the
                                // file rather than about the connection.
                                audioFileDao.markDurationAttempted(file.relativePath)
                            }
                            state.fileDone(bookId)
                            onFileDone()
                        }
                    }
                }.awaitAll()
            }

            var genre: String? = state.genre
            var language: String? = state.language
            var firstProbe: DurationExtractor.Probe? = state.firstProbe
            // null when the book already had its tags and no read was needed at all.
            var tagsSource: String? = null
            val fromHeader = state.fromHeader
            val fromFastProbe = state.fromFastProbe
            val fromFullProbe = state.fromFullProbe

            // Nothing was probed above but genre/chapters are still wanted — read the first
            // file's tags once.
            //
            // This is the path a WORKING duration reader takes: it answers every file, so no probe
            // ever runs and neither genre nor chapters get picked up on the way. That made this
            // block the dominant cost of a sweep — one stream opened per book, one to five
            // seconds, most often to learn the book has no genre. Both answers are in the ID3 tag
            // at the front of the file, so it is read the same way the durations were.
            if ((needsGenre && genre == null) || (needsLanguage && language == null) ||
                (needsChapters && firstProbe == null)
            ) {
                files.firstOrNull()?.let { first ->
                    coroutineContext.ensureActive()
                    val url = local[first.relativePath]?.toString()
                        ?: webDavClient.urlFor(credentials, libraryRoot, first.relativePath).toString()
                    val fromHeaderTag = audioHeaderTags.read(url)
                    if (fromHeaderTag != null) {
                        tagsSource = "header"
                        // An empty result is an answer: the tag was walked in full and holds no
                        // genre and no chapters. Id3Tags returns null rather than empty whenever
                        // it could not see the whole tag, so this can never invent an absence.
                        if (genre == null) genre = fromHeaderTag.genre
                        if (language == null) language = fromHeaderTag.language
                        if (firstProbe == null) firstProbe = fromHeaderTag
                    } else {
                        // Not ID3, or a tag this cannot walk — MP4-family books land here, and
                        // their chapters still come from Mp4ChapterParser below.
                        tagsSource = "probe"
                        val probe = durationExtractor.probeTags(url)
                            ?: durationExtractor.probe(url).also {
                                Log.i(TAG, "book $bookId: tags needed the full probe")
                            }
                        if (genre == null) genre = probe.genre
                        if (language == null) language = probe.language
                        if (firstProbe == null) firstProbe = probe
                    }
                }
            }
            if (needsGenre && genre != null) bookDao.updateGenre(bookId, genre!!)
            if (needsLanguage && language != null) bookDao.updateLanguage(bookId, language!!)
            // No genre in the tags, or the probe itself failed: settle it so the next open
            // doesn't stream this book's first file all over again for the same answer.
            if (((needsGenre && genre == null) || (needsLanguage && language == null) ||
                    (needsChapters && firstProbe == null)) &&
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
            Log.i(
                TAG,
                "book $bookId: ${durations.size}/${all.size} files measured " +
                    "(header=$fromHeader fast=$fromFastProbe full=$fromFullProbe), " +
                    "tags=${tagsSource ?: "—"}, genre=${genre ?: "—"}",
            )
        } finally {
            inFlight.remove(bookId)
        }
    }

    /**
     * The shared tallies for one book's concurrent measure pass.
     *
     * Every field is written from several coroutines at once, so all of it sits behind one lock.
     * Nothing here suspends, which is why a plain monitor is enough and a Mutex would be noise.
     */
    private inner class BookMeasureState(private val total: Int, private val startedAtMs: Long) {
        private val lock = Any()
        private var header = 0
        private var fast = 0
        private var full = 0
        private var processed = 0
        private var lastHeartbeatMs = startedAtMs
        private var genreValue: String? = null
        private var languageValue: String? = null
        private var firstProbeValue: DurationExtractor.Probe? = null

        val fromHeader: Int get() = synchronized(lock) { header }
        val fromFastProbe: Int get() = synchronized(lock) { fast }
        val fromFullProbe: Int get() = synchronized(lock) { full }
        val genre: String? get() = synchronized(lock) { genreValue }
        val language: String? get() = synchronized(lock) { languageValue }
        val firstProbe: DurationExtractor.Probe? get() = synchronized(lock) { firstProbeValue }

        fun recordHeader() = synchronized(lock) { header++ }

        fun recordFastProbe() = synchronized(lock) { fast++ }

        /**
         * A full probe also carries the book's genre and any embedded chapters, so whichever file
         * needed one supplies them. Which file that is no longer has a defined order, and it does
         * not matter: genre is a property of the book, and chapters are only ever read for
         * single-file books, where there is exactly one candidate.
         */
        fun recordFullProbe(probe: DurationExtractor.Probe) = synchronized(lock) {
            if (probe.durationMs != null) full++
            if (genreValue == null) genreValue = probe.genre
            if (languageValue == null) languageValue = probe.language
            if (firstProbeValue == null) firstProbeValue = probe
        }

        /** Counts a finished file and, on a beat, says where the book is and which tier is working. */
        fun fileDone(bookId: String) {
            val line = synchronized(lock) {
                processed++
                val now = System.currentTimeMillis()
                // An early one so a rate is known within seconds, then a steady beat.
                if (processed != HEARTBEAT_FIRST_AT && now - lastHeartbeatMs < HEARTBEAT_INTERVAL_MS) return
                lastHeartbeatMs = now
                "book $bookId: $processed/$total at ${(now - startedAtMs) / processed}ms/file " +
                    "(header=$header fast=$fast full=$full)"
            }
            Log.i(TAG, line)
        }
    }

    /**
     * One file, cheapest tier first. Returns its duration, or null if no tier could read it.
     *
     * [probeSlot] serialises the two ExoPlayer tiers; see the comment at its construction.
     */
    private suspend fun measureFile(
        url: String,
        sizeBytes: Long,
        probeSlot: Semaphore,
        state: BookMeasureState,
    ): Long? {
        // Cheapest first, and concurrent: the duration is a few bytes of container header, so
        // read those rather than streaming the file until a player works it out.
        audioHeaderDuration.durationMs(url, sizeBytes)?.let {
            state.recordHeader()
            return it
        }

        return probeSlot.withPermit {
            if (fastProbes.shouldTryFast()) {
                val fast = durationExtractor.probeDuration(url)
                // Only a fast-probe answer resets the gate. Crediting it with a header read would
                // mean the gate never withdraws on a device where the probe is broken, because the
                // header answers most files and would keep clearing the count.
                if (fast != null) {
                    fastProbes.onFastSuccess()
                    state.recordFastProbe()
                    return@withPermit fast
                }
            }
            // The FULL probe is the authority. Nothing is ever recorded as unmeasurable on the
            // header reader's or the fast probe's word, so a file either of them declines to
            // answer for loses time and never correctness — and the gate stops that loss being
            // unbounded on a device where the fast probe cannot work.
            val full = durationExtractor.probe(url)
            state.recordFullProbe(full)
            when {
                full.durationMs == null -> fastProbes.onBothFailed()
                fastProbes.onFullProbeRescue() ->
                    Log.i(TAG, "decoder-free probes aren't working here; using the full probe from now on")
            }
            full.durationMs
        }
    }

    private companion object {
        const val TAG = "HomerMeta"
        const val HEARTBEAT_FIRST_AT = 25
        const val HEARTBEAT_INTERVAL_MS = 30_000L

        /**
         * Files measured at once within one book. What remains after the header reader is round
         * trips, so this is the multiplier; kept modest because the other end is one Nextcloud
         * being asked for thousands of ranges.
         */
        const val MEASURE_CONCURRENCY = 6
    }
}

/** MP4-family containers whose chapters live in a `chpl`/track rather than ID3 CHAP. */
private fun String.isMp4Family(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("m4b", "m4a", "mp4", "aac")
