package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.webdav.WebDavClient
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
    private val credentialStore: CredentialStore,
    private val webDavClient: WebDavClient,
    private val durationExtractor: DurationExtractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    /** Fire-and-forget; no-op if this book is already fully measured or being measured. */
    fun enrich(bookId: String) {
        if (!inFlight.add(bookId)) return
        scope.launch {
            try {
                val credentials = credentialStore.credentials.value ?: return@launch
                val files = audioFileDao.findForBook(bookId)
                val missing = files.filter { it.durationMs == null }
                if (missing.isEmpty()) return@launch
                Log.i(TAG, "measuring ${missing.size}/${files.size} files for book $bookId")

                for (file in missing) {
                    coroutineContext.ensureActive()
                    val url = webDavClient.urlFor(credentials, file.relativePath).toString()
                    val duration = durationExtractor.probe(url) ?: continue
                    audioFileDao.updateDuration(file.relativePath, duration)
                }

                // Best-effort total from whatever is now known; improves on a later open
                // if some files couldn't be measured this time.
                val durations = audioFileDao.findForBook(bookId).mapNotNull { it.durationMs }
                if (durations.isNotEmpty()) {
                    bookDao.updateTotalDuration(bookId, durations.sum())
                }
                Log.i(TAG, "book $bookId: ${durations.size}/${files.size} files measured")
            } finally {
                inFlight.remove(bookId)
            }
        }
    }

    private companion object {
        const val TAG = "HomerMeta"
    }
}
