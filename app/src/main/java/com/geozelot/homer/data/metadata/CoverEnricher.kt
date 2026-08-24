package com.geozelot.homer.data.metadata

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Gives books an artwork cover. Embedded audiobook art usually lives only on the first file,
 * so we extract it once from that file and cache it as the book-level cover (or, with the shared index on,
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
        val credentials = credentialStore.awaitCredentials() ?: return
        val libraryRoot = librarySettings.libraryRoot.first()
        val sharedCatalog = librarySettings.sharedCatalogEnabled.first()
        val onlineLookup = librarySettings.onlineCoverLookup.first()

        // With the shared cache on we want to retry books a local extraction gave up on, because
        // another device may have published their art since. But re-probing every art-less book on
        // every pass costs one request per book — 330 round trips on a large library, every time.
        // The shared cover folder's collection ETag changes when anything is added to it, so one
        // PROPFIND tells us whether a full sweep could possibly find anything new. No folder (or an
        // unchanged one) means there is nothing to gain, and we fall back to fresh books only.
        val sweepShared = if (sharedCatalog) {
            val etag = orNullUnlessCancelled {
                webDavClient.propfind(sharedCoverDir(libraryRoot), depth = 0).firstOrNull()?.etag
            }
            val changed = etag != null && etag != librarySettings.lastCoverSweepEtag.first()
            if (changed) librarySettings.setLastCoverSweepEtag(etag!!)
            changed
        } else {
            false
        }
        val books = if (sweepShared) bookDao.booksWithoutArt() else bookDao.booksNeedingCover()
        val total = books.size
        if (total == 0) return
        Log.i(TAG, "enriching covers for $total books")
        var found = 0
        for ((index, book) in books.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(index, total)
            // Shared catalog: prefer the shared cover cache (a small download) over re-extracting
            // the art by streaming the first file.
            if (sharedCatalog) {
                val cached = orNullUnlessCancelled {
                    webDavClient.getBytes("${sharedCoverDir(libraryRoot)}/${coverCache.coverName(book.id)}")
                }
                if (cached != null) {
                    bookDao.updateLocalCover(book.id, coverCache.write(book.id, cached))
                    found++
                    continue
                }
                // Shared cache missed and we already tried extracting this one before — don't
                // re-stream it every pass; leave it for a future publish to the shared cache.
                if (book.coverAttempted) continue
            }
            // A cover image sitting in the book's folder on the server: fetch that one small file
            // and cache it. This is both the cheapest source (no audio streaming, unlike embedded
            // extraction) and the fix for it otherwise being re-downloaded on every display and
            // missing entirely offline.
            if (book.coverFilePath != null) {
                // Three different answers, and they must not be conflated. The crawl LISTED this
                // file, so a failed request says nothing about whether the art exists — marking the
                // book "tried" on a dropped connection blanked its cover permanently, recoverable
                // only by re-fetching every cover in the library.
                var requestFailed = false
                val folderArt = try {
                    webDavClient.getBytes(joinPath(libraryRoot, book.coverFilePath))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "folder cover fetch failed for ${book.id}", e)
                    requestFailed = true
                    null
                }
                when {
                    folderArt != null && folderArt.isNotEmpty() -> {
                        bookDao.updateLocalCover(book.id, coverCache.write(book.id, folderArt))
                        found++
                    }
                    // Ask again next pass. A file the crawl saw is far likelier to be behind a
                    // hiccup than to be permanently unreadable, and if it really has gone the next
                    // crawl clears `coverFilePath` and this branch stops being reachable.
                    requestFailed -> Unit
                    // The server answered, and there is nothing usable there. Stop asking; the
                    // remote URL still renders as a fallback via BookCover.
                    else -> bookDao.markCoverAttempted(book.id)
                }
                continue
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

    /** Joins the (possibly empty) library root with a library-relative path for a files-root call. */
    private fun joinPath(libraryRoot: String, relativePath: String): String =
        if (libraryRoot.isBlank()) relativePath else "${libraryRoot.trimEnd('/')}/$relativePath"

    /** The shared cover cache folder at the library root. */
    private fun sharedCoverDir(libraryRoot: String): String = joinPath(libraryRoot, ".homer/covers")

    /**
     * [block]'s result, or null if it failed — but never swallowing cancellation.
     *
     * This used to be `runCatching`, which catches Throwable, and `CancellationException` is one:
     * pressing Stop mid-pass was absorbed here and the loop carried on into the next book. The
     * `ensureActive()` at the top of the loop bounds that to a single wasted iteration today —
     * which is exactly the kind of guarantee that quietly stops holding when a line moves, and the
     * damage if it does is a book marked "no art available" because someone pressed Stop.
     */
    private suspend fun <T> orNullUnlessCancelled(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "cover fetch failed", e)
            null
        }

    private companion object {
        const val TAG = "HomerMeta"
    }
}
