package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Background pass that gives books an artwork cover. Embedded audiobook art usually
 * lives only on the first file, so we extract it once from that file and cache it as
 * the book-level cover. Runs only for books that have no cover yet (no folder image,
 * no cached art), sequentially to avoid hammering the server.
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    /** Fire-and-forget; no-op if already running. */
    fun enrichMissingCovers() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                val credentials = credentialStore.credentials.value ?: return@launch
                val libraryRoot = librarySettings.libraryRoot.first()
                val tier = librarySettings.syncTier.first()
                val books = bookDao.booksNeedingCover()
                Log.i(TAG, "enriching covers for ${books.size} books")
                var found = 0
                for (book in books) {
                    coroutineContext.ensureActive()
                    // Tier 3: prefer the shared cover cache (a small download) over re-extracting
                    // the art by streaming the first file.
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
                    val firstFile = audioFileDao.findForBook(book.id).firstOrNull() ?: continue
                    val url = webDavClient.urlFor(credentials, libraryRoot, firstFile.relativePath).toString()
                    val bytes = metadataExtractor.extractEmbeddedPicture(url) ?: continue
                    val path = coverCache.write(book.id, bytes)
                    bookDao.updateLocalCover(book.id, path)
                    found++
                }
                Log.i(TAG, "cover enrichment done: $found/${books.size} got art")
            } finally {
                running.set(false)
            }
        }
    }

    private companion object {
        const val TAG = "HomerMeta"
    }
}
