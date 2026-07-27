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
 * Keeps a local `.homer/index.json` progress mirror inside the active [StorageLocation] area, so
 * resume positions survive an app reinstall (they otherwise live only in the internal Room DB) —
 * especially for on-device-only (Tier 1) users, whose progress never reaches the server manifest.
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
    /** Writes the current resume positions to `.homer/index.json` in the active storage area. */
    suspend fun export() {
        val states = playbackStateDao.getAll()
        if (states.isEmpty()) return
        val books = states.associate { s ->
            s.bookId to HomerBookState(mediaId = s.currentMediaId, positionMs = s.positionMs, updatedAt = s.updatedAt)
        }
        runCatching {
            storageLocation.area().write(MIRROR_PATH, json.encodeToString(HomerIndex(books = books)).toByteArray())
        }.onFailure { Log.w(TAG, "local mirror export failed", it) }
    }

    /** Merges positions from the area's `.homer/index.json` into Room (last-write-wins). */
    suspend fun import() {
        val bytes = runCatching { storageLocation.area().readBytes(MIRROR_PATH) }.getOrNull() ?: return
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
     * Recomputes each book's download status against the active storage area: books whose files are
     * present are marked done (so a reconnected folder's downloads aren't re-fetched); books whose
     * files are absent are cleared. Existence is probed by the first file (a cheap heuristic).
     */
    suspend fun adoptDownloads() {
        val now = System.currentTimeMillis()
        var adopted = 0
        for (book in bookDao.getAll()) {
            val files = audioFileDao.findForBook(book.id)
            val present = files.isNotEmpty() && downloadStorage.uri(files.first().relativePath) != null
            if (present) {
                downloadDao.upsert(DownloadEntity(book.id, DownloadStatus.DONE, files.size, files.size, now))
                adopted++
            } else {
                downloadDao.delete(book.id)
            }
        }
        Log.i(TAG, "adopted $adopted downloaded book(s) from the storage folder")
    }

    private companion object {
        const val TAG = "HomerStore"
        const val MIRROR_PATH = ".homer/index.json"
    }
}
