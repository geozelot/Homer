package com.geozelot.homer.data.storage

import android.util.Log
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import com.geozelot.homer.data.download.DownloadStorage
import com.geozelot.homer.data.sync.HomerBookState
import com.geozelot.homer.data.sync.HomerIndex
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a local `progress.json` mirror inside the active [StorageLocation] area, so
 * resume positions survive an app reinstall (they otherwise live only in the internal Room DB) —
 * especially for users with progress sync off, whose positions never reach the server manifest.
 * Uses the same [HomerIndex] format as the server manifest (positions only here).
 *
 * On reconnecting a custom folder that already holds Homer data, [import] merges its progress into
 * Room (last-write-wins) and [adoptDownloads] recognises files already present so they aren't
 * re-downloaded.
 */
@Singleton
class LocalMirror @Inject constructor(
    private val storageLocation: StorageLocation,
    private val playbackStateDao: PlaybackStateDao,
    private val audioFileDao: AudioFileDao,
    private val bookDao: BookDao,
    private val downloadDao: DownloadDao,
    private val downloadStorage: DownloadStorage,
    private val json: Json,
) {
    /** Writes the current resume positions to the visible `progress.json` in the active area. */
    suspend fun export() {
        val states = playbackStateDao.getAll()
        if (states.isEmpty()) return
        val books = states.associate { s ->
            s.bookId to HomerBookState(mediaId = s.currentMediaId, positionMs = s.positionMs, updatedAt = s.updatedAt)
        }
        runCatching {
            val area = storageLocation.area()
            area.write(MIRROR_PATH, json.encodeToString(HomerIndex(books = books)).toByteArray())
        }.onFailure { Log.w(TAG, "local mirror export failed", it) }
    }

    /** Merges positions from the area's `progress.json` into Room (last write wins). */
    suspend fun import() {
        val area = storageLocation.area()
        val bytes = runCatching { area.readBytes(MIRROR_PATH) }.getOrNull() ?: return
        val index = runCatching { json.decodeFromString<HomerIndex>(String(bytes)) }.getOrElse {
            Log.w(TAG, "local mirror unparseable; skipping import")
            return
        }
        var applied = 0
        for ((bookId, state) in index.books) {
            if (!state.hasPosition) continue
            val local = playbackStateDao.findByBookId(bookId)
            if (local != null && local.updatedAt >= state.updatedAt) continue
            playbackStateDao.upsert(
                PlaybackStateEntity(bookId, state.mediaId!!, state.positionMs, state.updatedAt),
            )
            applied++
        }
        Log.i(TAG, "local mirror import: ${index.books.size} entries, $applied applied")
    }

    /**
     * Recomputes each book's download status against the active storage area: a book with ALL files
     * present is marked done (so a reconnected folder's downloads aren't re-fetched), a book with a
     * partial (contiguous) prefix present is marked paused with the real count so the worker resumes
     * the rest, and a book with nothing present is cleared. Verifying the whole prefix — not just
     * the first file — stops an interrupted download from masquerading as complete.
     */
    suspend fun adoptDownloads() {
        val now = System.currentTimeMillis()
        var adopted = 0
        for (book in bookDao.getAll()) {
            val files = audioFileDao.findForBook(book.id)
            // The worker downloads sequentially and resumes from downloadedFiles, so "downloaded"
            // means a contiguous leading run of present files.
            var prefix = 0
            while (prefix < files.size && downloadStorage.uri(files[prefix].relativePath) != null) prefix++
            when {
                files.isNotEmpty() && prefix == files.size -> {
                    downloadDao.upsert(DownloadEntity(book.id, DownloadStatus.DONE, files.size, files.size, now))
                    adopted++
                }
                prefix > 0 -> {
                    downloadDao.upsert(DownloadEntity(book.id, DownloadStatus.PAUSED, prefix, files.size, now))
                }
                else -> downloadDao.delete(book.id)
            }
        }
        Log.i(TAG, "adopted $adopted downloaded book(s) from the storage folder")
    }

    private companion object {
        const val TAG = "HomerStore"
        const val MIRROR_PATH = "progress.json"
    }
}
