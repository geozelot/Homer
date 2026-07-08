package com.geozelot.homer.data.sync

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkMetaEntity
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import com.geozelot.homer.data.settings.LibrarySettings
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
 * Reconciles resume positions and bookmarks with the central `.homer` manifest for
 * cross-device sync. One [sync] pass merges the remote manifest and local Room state:
 * the position and the bookmark list are each reconciled by last-write-wins on their own
 * timestamp (SCOPE D3), remote-newer data flows into Room, and the merged result is written
 * back under ETag optimistic concurrency (retry on conflict; final attempt unconditional).
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
    private val credentialStore: CredentialStore,
    private val librarySettings: LibrarySettings,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var ensuredDir: String? = null

    /** Pull-merge-push in one pass. No-op if no account is configured. */
    suspend fun sync() {
        if (credentialStore.credentials.value == null) {
            Log.i(TAG, "sync skipped: no account")
            return
        }
        mutex.withLock {
            try {
                reconcile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
            }
        }
    }

    private suspend fun reconcile() {
        val dir = manifestDir()
        val path = "$dir/$FILE"
        Log.i(TAG, "sync start: path=$path")

        // Re-pull, re-merge and retry on each conflict; the last attempt writes
        // unconditionally so a mangled/weak ETag can never block sync forever
        // (single-user last-write-wins is safe — SCOPE D3).
        for (attempt in 0 until MAX_ATTEMPTS) {
            val remoteFile = webDavClient.getText(path)
            val remoteEtag = remoteFile?.etag
            val remoteIndex = remoteFile?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString<HomerIndex>(it) }.getOrNull() }
                ?: HomerIndex()

            val localPos = playbackStateDao.getAll().associateBy { it.bookId }
            val localBookmarks = bookmarkDao.getAll().groupBy { it.bookId }
            val localBmTs = bookmarkMetaDao.getAll().associate { it.bookId to it.updatedAt }
            Log.i(TAG, "remote=${remoteIndex.books.size} books (etag=${remoteEtag ?: "none"}), local=${localPos.size} positions")

            val merged = LinkedHashMap<String, HomerBookState>()
            val positionPulls = mutableListOf<PlaybackStateEntity>()
            val bookmarkPulls = mutableListOf<Triple<String, Long, List<HomerBookmark>>>()

            for (id in remoteIndex.books.keys + localPos.keys + localBookmarks.keys) {
                val remote = remoteIndex.books[id]

                // --- position: LWW on updatedAt ---
                val lp = localPos[id]
                var posMediaId: String? = null
                var posMs = 0L
                var posTs = 0L
                when {
                    remote?.hasPosition == true && (lp == null || remote.updatedAt > lp.updatedAt) -> {
                        posMediaId = remote.mediaId; posMs = remote.positionMs; posTs = remote.updatedAt
                        positionPulls += PlaybackStateEntity(id, remote.mediaId!!, remote.positionMs, remote.updatedAt)
                    }
                    lp != null -> { posMediaId = lp.currentMediaId; posMs = lp.positionMs; posTs = lp.updatedAt }
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
                        val union = unionBookmarks(localList, remoteList)
                        if (union.keySet() == localList.keySet()) {
                            winnerList = localList; winnerTs = localTs
                        } else {
                            // Local was missing some — adopt the union with a fresh ts so it propagates.
                            winnerList = union; winnerTs = System.currentTimeMillis()
                            bookmarkPulls += Triple(id, winnerTs, union)
                        }
                    }
                }

                merged[id] = HomerBookState(posMediaId, posMs, posTs, winnerList, winnerTs)
            }

            // Bring Room forward where the manifest knows more.
            positionPulls.forEach { playbackStateDao.upsert(it) }
            for ((bookId, ts, list) in bookmarkPulls) {
                bookmarkDao.deleteForBook(bookId)
                bookmarkDao.insertAll(list.map { it.toEntity(bookId) })
                bookmarkMetaDao.upsert(BookmarkMetaEntity(bookId, ts))
            }
            if (positionPulls.isNotEmpty() || bookmarkPulls.isNotEmpty()) {
                Log.i(TAG, "pulled ${positionPulls.size} positions, ${bookmarkPulls.size} bookmark sets")
            }

            // Push only when we'd actually change what the server has.
            val mergedIndex = HomerIndex(books = merged)
            if (mergedIndex == remoteIndex) {
                Log.i(TAG, "already in sync, nothing to push")
                return
            }

            ensureDir(dir)
            val ifMatch = remoteEtag.takeUnless { attempt == MAX_ATTEMPTS - 1 }
            try {
                webDavClient.putText(path, json.encodeToString(mergedIndex), ifMatch)
                val how = if (ifMatch == null) " [unconditional]" else ""
                Log.i(TAG, "pushed manifest (${merged.size} books)$how")
                return
            } catch (e: PreconditionFailedException) {
                Log.i(TAG, "manifest changed under us; retry ${attempt + 1}/$MAX_ATTEMPTS")
            }
        }
    }

    private suspend fun manifestDir(): String {
        val root = librarySettings.libraryRoot.first()
        return listOf(root, DIR).filter { it.isNotBlank() }.joinToString("/")
    }

    private suspend fun ensureDir(dir: String) {
        if (ensuredDir == dir) return
        webDavClient.mkcol(dir)
        ensuredDir = dir
    }

    private companion object {
        const val TAG = "HomerSync"
        const val DIR = ".homer"
        const val FILE = "index.json"
        const val MAX_ATTEMPTS = 3
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
