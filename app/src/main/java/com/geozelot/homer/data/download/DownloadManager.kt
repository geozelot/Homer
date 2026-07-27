package com.geozelot.homer.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.settings.PlaybackSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates offline downloads via WorkManager: enqueues a [DownloadWorker] per book
 * (unique work, survives process death, retries with backoff) and cancels + cleans up on
 * removal. The Wi‑Fi-only preference becomes a network constraint on the request.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadDao: DownloadDao,
    private val audioFileDao: AudioFileDao,
    private val storage: DownloadStorage,
    private val settings: PlaybackSettings,
) {
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Enqueues a download for [bookId]; a no-op if one is already pending/running. */
    fun download(bookId: String) {
        scope.launch {
            // Write a QUEUED row up front so the UI shows a spinner immediately, even before the
            // worker starts (or while it waits on its Wi‑Fi constraint). Preserve prior progress
            // so a resume doesn't look like it restarted.
            val existing = downloadDao.findByBookId(bookId)
            val total = existing?.totalFiles?.takeIf { it > 0 } ?: audioFileDao.findForBook(bookId).size
            downloadDao.upsert(
                DownloadEntity(
                    bookId = bookId,
                    status = DownloadStatus.QUEUED,
                    downloadedFiles = existing?.downloadedFiles ?: 0,
                    totalFiles = total,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val networkType = if (settings.wifiOnlyDownloads.first()) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_BOOK_ID to bookId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(workName(bookId), ExistingWorkPolicy.KEEP, request)
        }
    }

    /** Cancels any in-flight download and removes the book's files + record. */
    fun delete(bookId: String) {
        scope.launch {
            workManager.cancelUniqueWork(workName(bookId))
            storage.deleteBook(bookId)
            downloadDao.delete(bookId)
        }
    }

    /** Stops the worker but keeps the partial files + progress, marking the download paused. */
    fun pause(bookId: String) {
        scope.launch {
            workManager.cancelUniqueWork(workName(bookId))
            downloadDao.findByBookId(bookId)?.let {
                downloadDao.upsert(it.copy(status = DownloadStatus.PAUSED, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    /** Re-enqueues a paused/failed download; the worker resumes from the last completed file. */
    fun resume(bookId: String) = download(bookId)

    private fun workName(bookId: String) = "$WORK_PREFIX$bookId"

    private companion object {
        const val WORK_PREFIX = "download:"
    }
}
