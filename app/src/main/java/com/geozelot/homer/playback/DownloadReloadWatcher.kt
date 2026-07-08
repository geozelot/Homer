package com.geozelot.homer.playback

import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.entity.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Watches a book's offline-download status and invokes [onSourceFlip] when it crosses the
 * downloaded/not-downloaded boundary, so the host can re-resolve the playlist (stream ↔ local)
 * without interrupting the current position.
 */
class DownloadReloadWatcher(
    private val scope: CoroutineScope,
    private val downloadDao: DownloadDao,
) {
    private var job: Job? = null

    /** (Re)starts watching [bookId]; [isOffline] reports the currently-loaded source. */
    fun watch(bookId: String, isOffline: () -> Boolean, onSourceFlip: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            downloadDao.observeByBookId(bookId).collect { download ->
                val nowOffline = download?.status == DownloadStatus.DONE
                if (nowOffline != isOffline()) onSourceFlip()
            }
        }
    }
}
