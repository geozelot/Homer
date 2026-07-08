package com.geozelot.homer.data.sync

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.PlaybackStateDao
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
 * Reconciles resume positions with the central `.homer` manifest for cross-device resume.
 * One [sync] pass merges the remote manifest and local Room state by last-write-wins on
 * `updatedAt` (SCOPE D3): remote-newer entries flow into Room, and the merged result is
 * written back under ETag optimistic concurrency (retry once on a conflicting write).
 *
 * Best-effort: any failure (offline, no account, server hiccup) is logged and swallowed so
 * sync never blocks or breaks playback.
 */
@Singleton
class HomerSyncRepository @Inject constructor(
    private val webDavClient: WebDavClient,
    private val playbackStateDao: PlaybackStateDao,
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

            val local = playbackStateDao.getAll().associateBy { it.bookId }
            Log.i(TAG, "remote=${remoteIndex.books.size} books (etag=${remoteEtag ?: "none"}), local=${local.size} books")
            val merged = LinkedHashMap<String, HomerBookState>()
            val remoteWins = mutableListOf<PlaybackStateEntity>()
            for (id in remoteIndex.books.keys + local.keys) {
                val remote = remoteIndex.books[id]
                val localState = local[id]
                when {
                    remote == null -> merged[id] = localState!!.toBookState()
                    localState == null -> {
                        merged[id] = remote
                        remoteWins += remote.toEntity(id)
                    }
                    localState.updatedAt >= remote.updatedAt -> merged[id] = localState.toBookState()
                    else -> {
                        merged[id] = remote
                        remoteWins += remote.toEntity(id)
                    }
                }
            }

            // Bring Room forward for books the manifest knows more recently.
            remoteWins.forEach { playbackStateDao.upsert(it) }
            if (remoteWins.isNotEmpty()) Log.i(TAG, "pulled ${remoteWins.size} newer positions")

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

private fun PlaybackStateEntity.toBookState() =
    HomerBookState(mediaId = currentMediaId, positionMs = positionMs, updatedAt = updatedAt)

private fun HomerBookState.toEntity(bookId: String) =
    PlaybackStateEntity(
        bookId = bookId,
        currentMediaId = mediaId,
        positionMs = positionMs,
        updatedAt = updatedAt,
    )
