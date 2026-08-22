package com.geozelot.homer.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.geozelot.homer.R
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import com.geozelot.homer.di.Authed
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads a book's audio files to app-private storage for offline playback. Runs under
 * WorkManager as a long-running foreground (data-sync) service, so it keeps going when the
 * app is closed and survives memory pressure; it retries with backoff, and a Wi‑Fi-only
 * network constraint (when set) is applied by the enqueuer. Progress is written to Room
 * (drives the UI) and shown in the foreground notification.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    @Authed private val client: OkHttpClient,
    private val webDavClient: WebDavClient,
    private val credentialStore: CredentialStore,
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val downloadDao: DownloadDao,
    private val storage: DownloadStorage,
    private val librarySettings: LibrarySettings,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        // Wait for the credential store to finish its async load; reading eagerly could see null
        // in a fresh worker process and abort a perfectly valid download.
        val credentials = credentialStore.awaitCredentials() ?: return Result.failure()
        val files = audioFileDao.findForBook(bookId)
        if (files.isEmpty()) return Result.success()
        val libraryRoot = librarySettings.libraryRoot.first()

        val title = bookDao.findById(bookId)?.title ?: bookId.substringAfterLast('/')
        val notifId = bookId.hashCode()
        ensureChannel()
        // Promote to a foreground service so the download continues if the app is closed.
        setForegroundSafely(foregroundInfo(notifId, bookId, title, 0, files.size))

        // Resume from the last completed file: a paused (or retried) download keeps its progress
        // count, so already-finished files are skipped rather than re-fetched.
        val startIndex = (downloadDao.findByBookId(bookId)?.downloadedFiles ?: 0).coerceIn(0, files.size)
        downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DOWNLOADING, startIndex, files.size, now()))
        // Notification updates are throttled because Android's notify rate limit (~5/s) is
        // PER PACKAGE, not per notification. A book of many small files posted an update per file
        // and burned the whole budget — and since download-on-play starts a download the moment
        // playback begins, the shed notification was the media one, leaving PlaybackService
        // improperly foregrounded and killed once the app went to the background.
        try {
            for ((index, file) in files.withIndex()) {
                if (index < startIndex) continue // completed before a pause/retry
                // Stop promptly on cancellation (user removed the download / constraint lost)
                // WITHOUT resurrecting the row DownloadManager.delete just cleaned up.
                if (isStopped) return Result.failure()
                if (claimNotifySlot(now())) {
                    setForegroundSafely(foregroundInfo(notifId, bookId, title, index, files.size))
                }
                // The storage area streams into place (atomically where the backend supports it),
                // so a truncated write is never mistaken for a finished download.
                val request = Request.Builder().url(webDavClient.urlFor(credentials, libraryRoot, file.relativePath)).build()
                // CoroutineWorker runs on Dispatchers.Default (CPU pool); the blocking OkHttp call
                // and byte copy belong on the IO dispatcher so a long transfer can't starve it.
                withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${file.relativePath}")
                        val body = response.body ?: throw IOException("empty body for ${file.relativePath}")
                        storage.writeStream(file.relativePath) { output -> body.byteStream().use { it.copyTo(output) } }
                    }
                }
                if (isStopped) return Result.failure()
                downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DOWNLOADING, index + 1, files.size, now()))
            }
            if (isStopped) return Result.failure()
            downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DONE, files.size, files.size, now()))
            Log.i(TAG, "downloaded $bookId (${files.size} files)")
            return Result.success()
        } catch (e: CancellationException) {
            throw e // stopped/cancelled — DownloadManager.delete does the cleanup
        } catch (e: Exception) {
            Log.w(TAG, "download failed for $bookId (attempt ${runAttemptCount + 1})", e)
            if (runAttemptCount + 1 < MAX_ATTEMPTS) return Result.retry()
            // KEEP whatever already landed. This used to delete the whole book on final failure,
            // so losing the connection near the end of a long download threw away hundreds of MB
            // and re-fetched all of it on the next play tap. The recorded count is exactly where
            // the worker resumes from, so a retry continues instead of restarting.
            val completed = downloadDao.findByBookId(bookId)?.downloadedFiles ?: 0
            downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.FAILED, completed, files.size, now()))
            return Result.failure()
        }
    }

    /** Promotes to foreground; a background-start restriction just degrades to a normal
     *  worker, but cancellation must still unwind. */
    private suspend fun setForegroundSafely(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "setForeground failed; continuing as background worker", e)
        }
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, appContext.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    /** Foreground notification for the ongoing download, with Pause + Cancel actions; the WM
     *  service is removed on finish. */
    private fun foregroundInfo(id: Int, bookId: String, title: String, done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(appContext.getString(R.string.download_notification_progress, done, total))
            .setOngoing(true)
            .setProgress(total, done, done == 0)
            .addAction(0, appContext.getString(R.string.download_action_pause), actionIntent(DownloadActionReceiver.ACTION_PAUSE, bookId))
            .addAction(0, appContext.getString(R.string.download_action_cancel), actionIntent(DownloadActionReceiver.ACTION_CANCEL, bookId))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private fun actionIntent(action: String, bookId: String): PendingIntent {
        val intent = Intent(appContext, DownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(DownloadActionReceiver.EXTRA_BOOK_ID, bookId)
        return PendingIntent.getBroadcast(
            appContext,
            (action + bookId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val KEY_BOOK_ID = "bookId"
        private const val TAG = "HomerDownload"
        private const val CHANNEL_ID = "downloads"
        private const val MAX_ATTEMPTS = 3

        /** Minimum gap between progress notifications (the platform's ~5/s budget is per package). */
        private const val PROGRESS_NOTIFY_INTERVAL_MS = 1_000L

        /**
         * When any DownloadWorker last posted a progress update, shared across every instance.
         *
         * The budget this protects is PER PACKAGE, so the throttle has to be too. It used to be a
         * local `var` in [doWork] — correct while only one book could be downloading, which was
         * true until the series menu grew a switch that enqueues one worker per episode. WorkManager
         * runs several of those at once, each with its own notification id, and N workers each
         * honouring their own 1/s budget still post N per second between them. Blowing the budget
         * is not a cosmetic problem: the shed notification can be the media one, which leaves
         * PlaybackService improperly foregrounded and gets it killed the moment the app goes to
         * the background.
         */
        private val lastProgressNotifyMs = AtomicLong(0L)

        /**
         * Reserves the package's next notification slot, returning false if one was taken too
         * recently. Compare-and-set rather than a plain read/write: the callers are concurrent
         * workers, and two of them reading the same stale timestamp would both post.
         */
        private fun claimNotifySlot(nowMs: Long): Boolean {
            while (true) {
                val last = lastProgressNotifyMs.get()
                if (nowMs - last < PROGRESS_NOTIFY_INTERVAL_MS) return false
                if (lastProgressNotifyMs.compareAndSet(last, nowMs)) return true
            }
        }
    }
}
