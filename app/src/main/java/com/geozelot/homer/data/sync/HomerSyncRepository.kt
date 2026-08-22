package com.geozelot.homer.data.sync

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkMetaEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.DavRead
import com.geozelot.homer.data.webdav.PreconditionFailedException
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles resume positions, bookmarks and metadata overrides with the central `.homer`
 * manifest for cross-device sync. One [sync] pass merges the remote manifest and local Room
 * state: the position, the bookmark list and the override are each reconciled by
 * last-write-wins on their own timestamp (SCOPE D3), remote-newer data flows into Room, and
 * the merged result is written back under ETag optimistic concurrency (retry on conflict;
 * final attempt unconditional).
 *
 * Bookmark ties (equal timestamps — notably pre-sync bookmarks at ts 0) union both sides so
 * nothing is dropped; a genuine concurrent edit still resolves last-write-wins.
 *
 * Best-effort: any failure (offline, no account, server hiccup) is logged and swallowed so
 * sync never blocks or breaks playback.
 */
@Singleton
class HomerSyncRepository @Inject constructor(
    private val webDavClient: WebDavClient,
    private val playbackStateDao: PlaybackStateDao,
    private val bookmarkDao: BookmarkDao,
    private val bookmarkMetaDao: BookmarkMetaDao,
    private val bookOverrideDao: BookOverrideDao,
    private val credentialStore: CredentialStore,
    private val librarySettings: LibrarySettings,
    private val networkMonitor: NetworkMonitor,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var ensuredDir: String? = null

    /**
     * The manifest we last saw or wrote, with its ETag. Holding it lets the next sync validate with
     * a conditional GET (304, no body) and still merge — so a steady-state sync transfers one
     * document instead of two, and an unchanged one transfers none.
     */
    private var cachedBody: String? = null
    private var cachedEtag: String? = null
    private var lastSyncAtMs = 0L

    /**
     * Pull-merge-push in one pass. Private progress is always keyed to the **sync account**, never
     * the library backend — so a share-link library with no personal account stays device-local
     * (this is a no-op). Also a no-op if progress sync is off or the device is offline.
     *
     * [force] bypasses the quiet-period throttle. Use it for the moments that must not be lost —
     * pausing, backgrounding, and explicit user actions — and leave it off for opportunistic
     * triggers like opening a book, which would otherwise sync several times a minute.
     */
    suspend fun sync(force: Boolean = false) {
        val account = credentialStore.awaitSyncAccount()
        if (account == null) {
            Log.i(TAG, "sync skipped: no sync account (device-local only)")
            return
        }
        // Progress sync off = on-device only: never touch the .homer manifest.
        if (!librarySettings.progressSyncEnabled.first()) {
            Log.i(TAG, "sync skipped: progress sync disabled (device-only)")
            return
        }
        // Offline: don't burn the connect timeout on a doomed round-trip. Local Room writes have
        // already happened; the next trigger (open, pause, chapter, background) syncs once online.
        if (!networkMonitor.isOnline()) {
            Log.i(TAG, "sync skipped: offline")
            return
        }
        mutex.withLock {
            val now = System.currentTimeMillis()
            val localRevision = localRevision()
            val pushed = librarySettings.lastPushedRevision.first()
            val dirty = localRevision > pushed
            // Nothing new locally and we looked recently → don't touch the network at all. This is
            // the difference between "a sync costs a round trip" and "a sync costs nothing".
            if (!force && !dirty && now - lastSyncAtMs < QUIET_INTERVAL_MS) {
                return
            }
            try {
                reconcile(account, localRevision)
                lastSyncAtMs = now
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
                // The .homer dir may have been removed server-side; re-MKCOL next time. Drop the
                // cached copy too — after a failure we can't assume we know the server's state.
                ensuredDir = null
                cachedBody = null
                cachedEtag = null
            }
        }
    }

    /**
     * Newest local change across everything the manifest carries. Compared against the last pushed
     * value this answers "is there anything to send?" with three indexed MAX queries instead of a
     * full document round trip — and needs no dirty flag threaded through every writer.
     */
    private suspend fun localRevision(): Long = maxOf(
        playbackStateDao.maxUpdatedAt() ?: 0L,
        bookmarkMetaDao.maxUpdatedAt() ?: 0L,
        bookOverrideDao.maxUpdatedAt() ?: 0L,
    )

    private suspend fun reconcile(account: NextcloudCredentials, localRevision: Long) {
        val dir = DIR
        val path = "$dir/$FILE"

        // One-time migration: earlier builds kept the manifest under the (configurable)
        // library folder, so changing that folder moved the manifest and silently broke
        // sync. It's now pinned to the files-root. If the pinned copy is absent but a
        // legacy per-root copy exists, seed the new location from it so nothing is lost.
        migrateLegacyManifest(dir, path, account)

        // Read the remote state ONCE, conditionally when we still hold the copy we last saw or
        // wrote: an unchanged manifest answers 304 with no body. The old loop re-downloaded the
        // whole document on every retry attempt, so a server that rewrites ETags turned one sync
        // into three full GETs plus three full PUTs.
        var remoteBody: String
        var remoteEtag: String?
        when (val read = webDavClient.readText(path, cachedEtag.takeIf { cachedBody != null }, account)) {
            is DavRead.Body -> { remoteBody = read.content; remoteEtag = read.etag }
            DavRead.NotModified -> { remoteBody = cachedBody.orEmpty(); remoteEtag = cachedEtag }
            DavRead.Absent -> { remoteBody = ""; remoteEtag = null } // safe to create
        }

        for (attempt in 0 until MAX_ATTEMPTS) {
            val remoteIndex = when {
                remoteBody.isBlank() -> HomerIndex()
                else -> runCatching { json.decodeFromString<HomerIndex>(remoteBody) }.getOrElse {
                    // Present but unparseable (proxy/HTML error page, corruption): do NOT treat
                    // it as empty and overwrite it — abort this pass to avoid clobbering.
                    Log.w(TAG, "manifest present but unparseable; skipping sync")
                    cachedBody = null
                    cachedEtag = null
                    return
                }
            }

            val localPos = playbackStateDao.getAll().associateBy { it.bookId }
            val localBookmarks = bookmarkDao.getAll().groupBy { it.bookId }
            val localBmTs = bookmarkMetaDao.getAll().associate { it.bookId to it.updatedAt }
            val localOverrides = bookOverrideDao.getAll().associateBy { it.bookId }
            Log.i(TAG, "remote=${remoteIndex.books.size} books (etag=${remoteEtag ?: "none"}), local=${localPos.size} positions")

            val merged = LinkedHashMap<String, HomerBookState>()
            val positionPulls = mutableListOf<PlaybackStateEntity>()
            val bookmarkPulls = mutableListOf<Triple<String, Long, List<HomerBookmark>>>()
            val overridePulls = mutableListOf<BookOverrideEntity>()

            for (id in remoteIndex.books.keys + localPos.keys + localBookmarks.keys + localOverrides.keys) {
                val remote = remoteIndex.books[id]

                // --- position: LWW on updatedAt, deterministic tiebreak on an exact tie so
                //     two same-ms writes converge instead of diverging forever ---
                val lp = localPos[id]
                val rMediaId = if (remote != null && remote.hasPosition) remote.mediaId else null
                var posMediaId: String? = null
                var posMs = 0L
                var posTs = 0L
                if (remote != null && rMediaId != null) {
                    val takeRemote = when {
                        lp == null -> true
                        remote.updatedAt > lp.updatedAt -> true
                        remote.updatedAt == lp.updatedAt -> {
                            // Tie: pick greater (mediaId, then positionMs) — same on both devices.
                            val cmp = rMediaId.compareTo(lp.currentMediaId)
                            if (cmp != 0) cmp > 0 else remote.positionMs > lp.positionMs
                        }
                        else -> false
                    }
                    if (takeRemote) {
                        posMediaId = rMediaId; posMs = remote.positionMs; posTs = remote.updatedAt
                        positionPulls += PlaybackStateEntity(id, rMediaId, remote.positionMs, remote.updatedAt)
                    }
                }
                if (posMediaId == null && lp != null) {
                    posMediaId = lp.currentMediaId; posMs = lp.positionMs; posTs = lp.updatedAt
                }

                // --- bookmarks: LWW on bookmarksUpdatedAt, union on tie ---
                val localList = localBookmarks[id].orEmpty().map { it.toHomer() }
                val localTs = localBmTs[id] ?: 0L
                val remoteList = remote?.bookmarks.orEmpty()
                val remoteTs = remote?.bookmarksUpdatedAt ?: 0L
                val winnerList: List<HomerBookmark>
                val winnerTs: Long
                when {
                    remoteTs > localTs -> {
                        winnerList = remoteList; winnerTs = remoteTs
                        bookmarkPulls += Triple(id, winnerTs, remoteList)
                    }
                    localTs > remoteTs -> {
                        winnerList = localList; winnerTs = localTs
                    }
                    else -> {
                        // Equal timestamps: union both sides so nothing is lost, keeping the
                        // tied ts (NOT a fresh one) so the merge is idempotent — both devices
                        // compute the same union@ts and converge instead of ping-ponging.
                        val union = unionBookmarks(localList, remoteList)
                        winnerList = union
                        winnerTs = localTs
                        if (union.keySet() != localList.keySet()) {
                            bookmarkPulls += Triple(id, winnerTs, union)
                        }
                    }
                }

                // --- override: LWW on updatedAt ---
                val localOv = localOverrides[id]
                val remoteOv = remote?.override
                val winnerOv: HomerOverride? = when {
                    remoteOv != null && (localOv == null || remoteOv.updatedAt > localOv.updatedAt) -> {
                        overridePulls += remoteOv.toEntity(id)
                        remoteOv
                    }
                    localOv != null -> localOv.toHomer()
                    else -> null
                }

                merged[id] = HomerBookState(posMediaId, posMs, posTs, winnerList, winnerTs, winnerOv)
            }

            // Bring Room forward where the manifest knows more.
            positionPulls.forEach { playbackStateDao.upsert(it) }
            for ((bookId, ts, list) in bookmarkPulls) {
                bookmarkDao.deleteForBook(bookId)
                bookmarkDao.insertAll(list.map { it.toEntity(bookId) })
                bookmarkMetaDao.upsert(BookmarkMetaEntity(bookId, ts))
            }
            overridePulls.forEach { bookOverrideDao.upsert(it) }
            if (positionPulls.isNotEmpty() || bookmarkPulls.isNotEmpty() || overridePulls.isNotEmpty()) {
                Log.i(TAG, "pulled ${positionPulls.size} positions, ${bookmarkPulls.size} bookmark sets, ${overridePulls.size} overrides")
            }

            // Drop entries that carry no information. Without this the manifest only ever grows:
            // every book ever opened keeps a dead record that costs bytes on every transfer.
            val pruned = merged.filterValues {
                it.mediaId != null || it.bookmarks.isNotEmpty() || it.override != null
            }

            // Push only when we'd actually change what the server has.
            val mergedIndex = HomerIndex(books = pruned)
            if (mergedIndex == remoteIndex) {
                Log.i(TAG, "already in sync, nothing to push")
                cachedBody = remoteBody
                cachedEtag = remoteEtag
                librarySettings.setLastPushedRevision(localRevision)
                return
            }

            ensureDir(dir, account)
            val payload = json.encodeToString(mergedIndex)
            val ifMatch = remoteEtag.takeUnless { attempt == MAX_ATTEMPTS - 1 }
            try {
                val newEtag = webDavClient.putText(path, payload, ifMatch, account)
                val how = if (ifMatch == null) " [unconditional]" else ""
                Log.i(TAG, "pushed manifest (${pruned.size} books)$how")
                // Remember exactly what we wrote: the next sync validates it with a conditional
                // GET (304) instead of downloading the document all over again.
                cachedBody = payload
                cachedEtag = newEtag
                librarySettings.setLastPushedRevision(localRevision)
                return
            } catch (e: PreconditionFailedException) {
                Log.i(TAG, "manifest changed under us; retry ${attempt + 1}/$MAX_ATTEMPTS")
                // A real conflict, or a server that rewrites ETags. Re-read once and let the final
                // attempt write unconditionally — bounded at 2 reads + 2 writes, never 3+3.
                cachedBody = null
                cachedEtag = null
                when (val re = webDavClient.readText(path, null, account)) {
                    is DavRead.Body -> { remoteBody = re.content; remoteEtag = re.etag }
                    else -> { remoteBody = ""; remoteEtag = null }
                }
            }
        }
    }

    /**
     * Seeds the pinned files-root manifest from a legacy per-library-root copy, once per
     * process. Cheap after the first run: as soon as the pinned copy exists (or there's no
     * legacy to migrate) the flag short-circuits all further checks.
     */
    private suspend fun migrateLegacyManifest(
        dir: String,
        path: String,
        account: NextcloudCredentials,
    ) {
        // Persisted, not per-process: this used to re-probe on every app launch, paying a full
        // manifest GET (two, when the pinned copy was absent) purely as an existence test.
        if (librarySettings.legacyManifestMigrated.first()) return
        val root = librarySettings.libraryRoot.first().trim('/')
        // No configurable root, or the root already IS the files-root → nothing to migrate.
        if (root.isEmpty()) { librarySettings.setLegacyManifestMigrated(true); return }
        try {
            // Existence via PROPFIND Depth 0, not a full download.
            if (webDavClient.exists(path, account)) {
                librarySettings.setLegacyManifestMigrated(true)
                return
            }
            val legacy = webDavClient.getText("$root/$DIR/$FILE", account)?.content
            if (legacy != null && legacy.isNotBlank()) {
                ensureDir(dir, account)
                webDavClient.putText(path, legacy, null, account)
                Log.i(TAG, "migrated legacy manifest ($root/$DIR/$FILE) to pinned $path")
            }
            librarySettings.setLegacyManifestMigrated(true)
        } catch (e: Exception) {
            // Leave the flag unset so a transient failure retries on a later sync.
            Log.w(TAG, "legacy manifest migration skipped", e)
        }
    }

    private suspend fun ensureDir(dir: String, account: NextcloudCredentials) {
        if (ensuredDir == dir) return
        webDavClient.mkcol(dir, account)
        ensuredDir = dir
    }

    private companion object {
        const val TAG = "HomerSync"
        const val DIR = ".homer"
        const val FILE = "index.json"

        /** One read + one write, then one bounded retry. Never the old 3×(GET+PUT). */
        const val MAX_ATTEMPTS = 2

        /** Skip an opportunistic sync when nothing changed locally and we looked this recently. */
        const val QUIET_INTERVAL_MS = 60_000L
    }
}

/** Bookmark identity for dedup: same chapter + offset + creation time. */
private fun HomerBookmark.key() = "$mediaId|$positionMs|$createdAt"

private fun List<HomerBookmark>.keySet() = mapTo(HashSet()) { it.key() }

private fun unionBookmarks(a: List<HomerBookmark>, b: List<HomerBookmark>): List<HomerBookmark> {
    val seen = HashSet<String>()
    return (a + b).filter { seen.add(it.key()) }.sortedBy { it.createdAt }
}

private fun BookmarkEntity.toHomer() =
    HomerBookmark(mediaId = mediaId, positionMs = positionMs, chapterTitle = chapterTitle, label = label, createdAt = createdAt)

private fun HomerBookmark.toEntity(bookId: String) =
    BookmarkEntity(
        bookId = bookId,
        mediaId = mediaId,
        chapterTitle = chapterTitle,
        positionMs = positionMs,
        label = label,
        createdAt = createdAt,
    )

private fun BookOverrideEntity.toHomer() =
    HomerOverride(
        title = title,
        author = author,
        series = series,
        seriesIndex = seriesIndex,
        genre = genre,
        tags = tags?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
        finished = finished,
        downloadOnPlay = downloadOnPlay,
        hidden = hidden,
        updatedAt = updatedAt,
    )

private fun HomerOverride.toEntity(bookId: String) =
    BookOverrideEntity(
        bookId = bookId,
        title = title,
        author = author,
        series = series,
        seriesIndex = seriesIndex,
        genre = genre,
        tags = tags.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        finished = finished,
        downloadOnPlay = downloadOnPlay,
        hidden = hidden,
        updatedAt = updatedAt,
    )
