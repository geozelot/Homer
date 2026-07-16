package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Gives books an artwork cover. Embedded audiobook art usually lives only on the first file,
 * so we extract it once from that file and cache it as the book-level cover (or, at Tier 3,
 * download it from the shared cover cache instead). Runs only for books with no cover yet.
 *
 * Driven by [com.geozelot.homer.data.library.LibraryIndexWorker] so it survives the app being
 * backgrounded and shows progress; [enrich] is a plain suspend pass, cancellable per book.
 */
@Singleton
class CoverEnricher @Inject constructor(
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val credentialStore: CredentialStore,
    private val webDavClient: WebDavClient,
    private val metadataExtractor: MetadataExtractor,
    private val coverCache: CoverCache,
    private val librarySettings: LibrarySettings,
    private val onlineCoverClient: OnlineCoverClient,
) {
    /** How many books still need a cover — lets the worker skip foregrounding when there's none. */
    suspend fun pendingCount(): Int = bookDao.booksNeedingCover().size

    /** Fetches covers for all books missing one, reporting progress. Suspends until done. */
    suspend fun enrich(onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val credentials = credentialStore.credentials.value ?: return
        val libraryRoot = librarySettings.libraryRoot.first()
        val tier = librarySettings.syncTier.first()
        val onlineLookup = librarySettings.onlineCoverLookup.first()
        val books = bookDao.booksNeedingCover()
        val total = books.size
        if (total == 0) return
        Log.i(TAG, "enriching covers for $total books")
        var found = 0
        for ((index, book) in books.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(index, total)
            // Tier 3: prefer the shared cover cache (a small download) over re-extracting the
            // art by streaming the first file.
            if (tier >= 3) {
                val cached = runCatching {
                    webDavClient.getBytes("$libraryRoot/.homer/covers/${coverCache.coverName(book.id)}")
                }.getOrNull()
                if (cached != null) {
                    bookDao.updateLocalCover(book.id, coverCache.write(book.id, cached))
                    found++
                    continue
                }
            }
            val firstFile = audioFileDao.findForBook(book.id).firstOrNull()
            val bytes = firstFile?.let {
                metadataExtractor.extractEmbeddedPicture(
                    webDavClient.urlFor(credentials, libraryRoot, it.relativePath).toString(),
                )
            }
            if (bytes == null) {
                // No embedded/folder art. If the user opted in, try Open Library by title/author
                // before giving up; either way remember we tried so we don't re-probe every run.
                val online = if (onlineLookup) onlineCoverClient.fetchCover(book.title, book.author) else null
                if (online != null) {
                    bookDao.updateLocalCover(book.id, coverCache.write(book.id, online))
                    found++
                } else {
                    bookDao.markCoverAttempted(book.id)
                }
                continue
            }
            bookDao.updateLocalCover(book.id, coverCache.write(book.id, bytes))
            found++
        }
        onProgress(total, total)
        Log.i(TAG, "cover enrichment done: $found/$total got art")
    }

    private companion object {
        const val TAG = "HomerMeta"
    }
}
