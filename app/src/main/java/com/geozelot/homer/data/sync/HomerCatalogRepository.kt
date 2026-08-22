package com.geozelot.homer.data.sync

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.ChapterTier
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.metadata.CoverCache
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.DavRead
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
    /** ETag of the catalog this device last applied, so an unchanged one costs a 304 (no body). */
    private var cachedCatalogEtag: String? = null

    /**
     * True if a shared catalog exists at the library root (⇒ Tier 3 is available here).
     * Best-effort like the rest of the repo: offline or on any network error it returns false
     * rather than throwing (this is called from a fire-and-forget init coroutine — an escaped
     * exception there crashes the app).
     */
    suspend fun exists(): Boolean {
        if (credentialStore.awaitCredentials() == null || !networkMonitor.isOnline()) return false
        return try {
            // PROPFIND Depth 0 — a few hundred bytes. This used to GET the whole catalog (megabytes
            // for a large library) just to answer a boolean, on every app start and every publish.
            webDavClient.exists(catalogPath())
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
        val me = credentialStore.awaitCredentials()?.loginName ?: return false
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

    /**
     * Publishes only if allowed. For a share library the gate is the write-probe result (a
     * read-only link can't publish); for an account it's the existing rule — the catalog already
     * exists (open updates) or we're the folder owner.
     */
    suspend fun publishIfAllowed() {
        val library = credentialStore.awaitCredentials() ?: return
        val allowed = if (library.kind == com.geozelot.homer.data.auth.WebDavKind.SHARE) {
            librarySettings.libraryWritable.first()
        } else {
            exists() || isOwner()
        }
        if (allowed) publish()
    }

    /** Merges the local library into the shared catalog and pushes it. */
    suspend fun publish() {
        if (credentialStore.awaitCredentials() == null || !networkMonitor.isOnline()) return
        try {
            val local = buildLocalCatalog()
            val path = catalogPath()
            var coversUploaded = false
            for (attempt in 0 until MAX_ATTEMPTS) {
                val remoteFile = webDavClient.getText(path)
                val remote = remoteFile?.content?.takeIf { it.isNotBlank() }?.let { body ->
                    runCatching { json.decodeFromString<HomerCatalog>(body) }.getOrElse {
                        Log.w(TAG, "remote catalog unparseable; skipping publish")
                        return
                    }
                }
                // Upload extracted (embedded) covers that aren't in the shared cache yet, so
                // other devices download them instead of re-extracting from the audio. ONCE per
                // publish: this sat inside the retry loop, and since a 412 means the remote catalog
                // did NOT change, its `hasCachedCover` flags still read false — so every conflicted
                // publish re-uploaded the entire pending cover set (hundreds of MB).
                if (!coversUploaded) {
                    uploadNewCovers(local, remote)
                    coversUploaded = true
                }
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

    /**
     * Pulls the shared catalog into Room (books + files), newest-wins per book. Returns whether a
     * shared catalog exists at all (so callers don't need a second existence probe).
     *
     * Conditional: once this device has applied a catalog, an unchanged one answers 304 with no
     * body. Previously every app open downloaded the whole catalog — and then downloaded it a
     * second time for the existence check.
     */
    suspend fun consume(): Boolean {
        if (credentialStore.awaitCredentials() == null || !networkMonitor.isOnline()) return false
        return try {
            val body = when (val read = webDavClient.readText(catalogPath(), cachedCatalogEtag)) {
                DavRead.NotModified -> return true // already applied exactly this catalog
                DavRead.Absent -> return false
                is DavRead.Body -> {
                    cachedCatalogEtag = read.etag
                    read.content.takeIf { it.isNotBlank() } ?: return false
                }
            }
            val catalog = json.decodeFromString<HomerCatalog>(body)
            var applied = 0
            for ((id, cb) in catalog.books) {
                val local = bookDao.findById(id)
                if (local != null && local.updatedAt >= cb.updatedAt) continue
                bookDao.upsert(listOf(cb.toBook(id, local)))
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
                contentHash = book.contentHash,
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

    /**
     * The shared catalog carries the *library's* view of a book, not this device's. Everything
     * device-local — where the cover bytes are cached, the user's chosen cover, and what has already
     * been probed — is therefore carried over from [local] rather than written as null: this row is
     * upserted straight over the existing one, so blanking those fields drops the cached cover
     * pointers and re-arms probes that already ran. [contentHash] falls back to the local value for
     * catalogs written by an older build that didn't publish it; losing it would permanently break
     * move/rename re-linking for this book.
     */
    private fun CatalogBook.toBook(id: String, local: BookEntity?): BookEntity {
        val now = System.currentTimeMillis()
        return BookEntity(
            id = id,
            contentHash = contentHash ?: local?.contentHash,
            title = title,
            author = author,
            series = series,
            seriesIndex = seriesIndex,
            genre = genre,
            relativePath = id,
            coverFilePath = coverFilePath,
            localCoverPath = local?.localCoverPath,
            customCoverPath = local?.customCoverPath,
            coverAttempted = local?.coverAttempted ?: false,
            metadataAttempted = local?.metadataAttempted ?: false,
            // The consuming device doesn't re-derive chapters, so keep whatever it worked out itself.
            chapterTier = local?.chapterTier ?: ChapterTier.UNDETERMINED,
            isMultiFile = isMultiFile,
            fileCount = files.size,
            totalDurationMs = totalDurationMs,
            addedAt = local?.addedAt ?: now,
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
