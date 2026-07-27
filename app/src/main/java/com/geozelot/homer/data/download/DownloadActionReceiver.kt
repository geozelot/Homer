package com.geozelot.homer.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the Pause / Cancel actions on the download notification. [DownloadManager] does the work
 * on its own scope, so [onReceive] returns immediately.
 */
@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {

    @Inject lateinit var downloadManager: DownloadManager

    override fun onReceive(context: Context, intent: Intent) {
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return
        when (intent.action) {
            ACTION_PAUSE -> downloadManager.pause(bookId)
            ACTION_CANCEL -> downloadManager.delete(bookId)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.geozelot.homer.action.DOWNLOAD_PAUSE"
        const val ACTION_CANCEL = "com.geozelot.homer.action.DOWNLOAD_CANCEL"
        const val EXTRA_BOOK_ID = "bookId"
    }
}
