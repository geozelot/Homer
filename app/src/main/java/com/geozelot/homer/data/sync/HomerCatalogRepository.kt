package com.geozelot.homer.data.sync

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.metadata.CoverCache
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.PreconditionFailedException
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes and consumes the Tier-3 shared library catalog ([HomerCatalog]) at
 * `‹libraryRoot›/.homer/catalog.json`. [publish] merges the local scan into the shared copy
 * (last-write-wins per book, ETag optimistic concurrency); [consume] pulls the shared catalog
 * into Room so a device has the whole library without crawling or probing. Best-effort: all
 * failures are logged and swallowed so nothing blocks the UI or playback.
 */
@Singleton
class HomerCatalogRepository @Inject constructor(
    private val webDavClient: WebDavClient,
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val bookOverrideDao: BookOverrideDao,
    private val credentialStore: CredentialStore,
    private val librarySettings: LibrarySettings,
    private val coverCache: CoverCache,
    private val networkMonitor: NetworkMonitor,
    private val json: Json,
) {
    /**
     * True if a shared catalog exists at the library root (⇒ Tier 3 is available here).
     * Best-effort like the rest of the repo: offline or on any network error it returns false
     * rather than throwing (this is called from a fire-and-forget init coroutine — an escaped
     * exception there crashes the app).
     */
    suspend fun exists(): Boolean {
        if (credentialStore.credentials.value == null || !networkMonitor.isOnline()) return false
        return try {
            webDavClient.getText(catalogPath()) != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "catalog exists() check failed", e)
            false
        }
    }

    /**
     * Whether this user may CREATE the shared catalog — the Nextcloud folder owner, or
     * (when ownership can't be determined) anyone, claim-based. Not consulted once it exists.
     * Best-effort: any failure resolves to false.
     */
    suspend fun isOwner(): Boolean {
        val me = credentialStore.credentials.value?.loginName ?: return false
        if (!networkMonitor.isOnline()) return false
        return try {
            val owner = webDavClient.fetchOwnerId(librarySettings.libraryRoot.first())
            owner == null || owner.equals(me, ignoreCase = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "catalog isOwner() check failed", e)
            false
        }
    }

    /** Publishes only if allowed: the catalog already exists (open updates) or we're the owner. */
    suspend fun publishIfAllowed() {
        if (exists() || isOwner()) publish()
    }

    /** Merges the local library into the shared catalog and pushes it. */
    suspend fun publish() {
        if (credentialStore.credentials.value == null || !networkMonitor.isOnline()) return
        try {
            val local = buildLocalCatalog()
            val path = catalogPath()
            for (attempt in 0 until MAX_ATTEMPTS) {
                val remoteFile = webDavClient.getText(path)
                val remote = remoteFile?.content?.takeIf { it.isNotBlank() }?.let { body ->
                    runCatching { json.decodeFromString<HomerCatalog>(body) }.getOrElse {
                        Log.w(TAG, "remote catalog unparseable; skipping publish")
                        return
                    }
                }
                // Upload extracted (embedded) covers that aren't in the shared cache yet, so
                // other devices download them instead of re-extracting from the audio.
                uploadNewCovers(local, remote)
                val merged = mergeCatalogs(remote, local)
                if (merged == remote) {
                    Log.i(TAG, "catalog already up to date")
                    return
                }
                ensureDir()
                val ifMatch = remoteFile?.etag.takeUnless { attempt == MAX_ATTEMPTS - 1 }
                try {
                    webDavClient.putText(path, json.encodeToString(merged), ifMatch)
                    Log.i(TAG, "published catalog (${merged.books.size} books)")
                    return
                } catch (e: PreconditionFailedException) {
                    Log.i(TAG, "catalog changed under us; retry ${attempt + 1}/$MAX_ATTEMPTS")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "catalog publish failed", e)
        }
    }

    /** Pulls the shared catalog into Room (books + files), newest-wins per book. */
    suspend fun consume(): Boolean {
        if (credentialStore.credentials.value == null || !networkMonitor.isOnline()) return false
        return try {
            val body = webDavClient.getText(catalogPath())?.content?.takeIf { it.isNotBlank() } ?: return false
            val catalog = json.decodeFromString<HomerCatalog>(body)
            var applied = 0
            for ((id, cb) in catalog.books) {
                val local = bookDao.findById(id)
                if (local != null && local.updatedAt >= cb.updatedAt) continue
                bookDao.upsert(listOf(cb.toBook(id)))
                audioFileDao.deleteForBook(id)
                audioFileDao.upsert(cb.files.map { it.toEntity(id) })
                applied++
            }
            Log.i(TAG, "consumed catalog: ${catalog.books.size} books, $applied applied")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "catalog consume failed", e)
            false
        }
    }

    private suspend fun buildLocalCatalog(): HomerCatalog {
        val overrides = bookOverrideDao.getAll().associateBy { it.bookId }
        val books = LinkedHashMap<String, CatalogBook>()
        for (book in bookDao.getAll()) {
            val eff = book.applyOverride(overrides[book.id])
            val files = audioFileDao.findForBook(book.id)
            books[book.id] = CatalogBook(
                title = eff.title,
                author = eff.author,
                series = eff.series,
                seriesIndex = eff.seriesIndex,
                genre = eff.genre,
                coverFilePath = eff.coverFilePath,
                hasCachedCover = book.localCoverPath != null,
                totalDurationMs = eff.totalDurationMs,
                isMultiFile = eff.isMultiFile,
                updatedAt = book.updatedAt,
                files = files.map {
                    CatalogFile(
                        relativePath = it.relativePath,
                        fileName = it.fileName,
                        sortIndex = it.sortIndex,
                        sizeBytes = it.sizeBytes,
                        durationMs = it.durationMs,
                        etag = it.etag,
                        lastModifiedMs = it.lastModified,
                        contentType = it.contentType,
                    )
                },
            )
        }
        return HomerCatalog(books = books)
    }

    private fun mergeCatalogs(remote: HomerCatalog?, local: HomerCatalog): HomerCatalog {
        if (remote == null) return local
        val merged = LinkedHashMap<String, CatalogBook>()
        for (id in remote.books.keys + local.books.keys) {
            val r = remote.books[id]
            val l = local.books[id]
            merged[id] = when {
                r == null -> l!!
                l == null -> r
                l.updatedAt >= r.updatedAt -> l
                else -> r
            }
        }
        return HomerCatalog(books = merged)
    }

    /** Uploads each book's cached embedded cover to `.homer/covers/` once (skips already-shared). */
    private suspend fun uploadNewCovers(local: HomerCatalog, remote: HomerCatalog?) {
        val coversDir = "${catalogDir()}/covers"
        var dirEnsured = false
        for ((id, cb) in local.books) {
            if (!cb.hasCachedCover) continue
            if (remote?.books?.get(id)?.hasCachedCover == true) continue // already in the shared cache
            val bytes = coverCache.readBytes(id) ?: continue
            try {
                if (!dirEnsured) { webDavClient.mkcol(coversDir); dirEnsured = true }
                webDavClient.putBytes("$coversDir/${coverCache.coverName(id)}", bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "cover upload failed for $id", e)
            }
        }
    }

    private suspend fun catalogDir(): String {
        val root = librarySettings.libraryRoot.first().trim('/')
        return listOf(root, DIR).filter { it.isNotBlank() }.joinToString("/")
    }

    private suspend fun catalogPath(): String = "${catalogDir()}/$FILE"

    private suspend fun ensureDir() {
        webDavClient.mkcol(catalogDir())
    }

    private fun CatalogBook.toBook(id: String): BookEntity {
        val now = System.currentTimeMillis()
        return BookEntity(
            id = id,
            title = title,
            author = author,
            series = series,
            seriesIndex = seriesIndex,
            genre = genre,
            relativePath = id,
            coverFilePath = coverFilePath,
            localCoverPath = null,
            chapterTier = 0, // undetermined; the consuming device doesn't re-derive chapters
            isMultiFile = isMultiFile,
            fileCount = files.size,
            totalDurationMs = totalDurationMs,
            addedAt = now,
            updatedAt = updatedAt.takeIf { it > 0 } ?: now,
        )
    }

    private fun CatalogFile.toEntity(bookId: String) = AudioFileEntity(
        relativePath = relativePath,
        bookId = bookId,
        fileName = fileName,
        sortIndex = sortIndex,
        sizeBytes = sizeBytes,
        etag = etag,
        lastModified = lastModifiedMs,
        contentType = contentType,
        durationMs = durationMs,
    )

    private companion object {
        const val TAG = "HomerCatalog"
        const val DIR = ".homer"
        const val FILE = "catalog.json"
        const val MAX_ATTEMPTS = 3
    }
}
