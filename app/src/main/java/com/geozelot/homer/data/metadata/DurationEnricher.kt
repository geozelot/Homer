package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.ChapterDao
import com.geozelot.homer.data.db.entity.ChapterEntity
import com.geozelot.homer.data.db.entity.ChapterTier
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.flow.first
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
    private val librarySettings: LibrarySettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    /** Fire-and-forget; no-op if this book is already fully measured or being measured. */
    fun enrich(bookId: String) {
        if (!inFlight.add(bookId)) return
        scope.launch {
            try {
                val credentials = credentialStore.credentials.value ?: return@launch
                val libraryRoot = librarySettings.libraryRoot.first()
                val files = audioFileDao.findForBook(bookId)
                val book = bookDao.findById(bookId)
                val needsGenre = book?.genre == null
                // Embedded chapters only apply to single-file books (a multi-file book's file list
                // already is its chapter list). Extract once, when the tier is still undetermined.
                val needsChapters = files.size == 1 &&
                    (book?.chapterTier ?: ChapterTier.UNDETERMINED) == ChapterTier.UNDETERMINED
                val missing = files.filter { it.durationMs == null }
                if (missing.isEmpty() && !needsGenre && !needsChapters) return@launch
                if (missing.isNotEmpty()) Log.i(TAG, "measuring ${missing.size}/${files.size} files for book $bookId")

                // Genre + embedded chapters live on the (first) file; capture them from its probe.
                var genre: String? = null
                var firstProbe: DurationExtractor.Probe? = null
                for (file in missing) {
                    coroutineContext.ensureActive()
                    val url = webDavClient.urlFor(credentials, libraryRoot, file.relativePath).toString()
                    val probe = durationExtractor.probe(url)
                    probe.durationMs?.let { audioFileDao.updateDuration(file.relativePath, it) }
                    if (genre == null) genre = probe.genre
                    if (firstProbe == null) firstProbe = probe
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

                // Persist embedded chapters (empty = none found) and settle the tier so we don't
                // re-probe on every open.
                if (needsChapters && firstProbe != null) {
                    val marks = firstProbe!!.chapters
                    chapterDao.replaceForBook(
                        bookId,
                        marks.mapIndexed { i, m -> ChapterEntity(bookId = bookId, sortIndex = i, title = m.title, startMs = m.startMs) },
                    )
                    bookDao.updateChapterTier(bookId, if (marks.isNotEmpty()) ChapterTier.EMBEDDED else ChapterTier.NONE)
                    if (marks.isNotEmpty()) Log.i(TAG, "book $bookId: ${marks.size} embedded chapters")
                }

                // Best-effort total from whatever is now known; improves on a later open
                // if some files couldn't be measured this time.
                val durations = audioFileDao.findForBook(bookId).mapNotNull { it.durationMs }
                if (durations.isNotEmpty()) {
                    bookDao.updateTotalDuration(bookId, durations.sum())
                }
                Log.i(TAG, "book $bookId: ${durations.size}/${files.size} files measured, genre=${genre ?: "—"}")
            } finally {
                inFlight.remove(bookId)
            }
        }
    }

    private companion object {
        const val TAG = "HomerMeta"
    }
}
