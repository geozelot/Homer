package com.geozelot.homer.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.webdav.WebDavClient
import com.geozelot.homer.di.Authed
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Downloads a book's audio files to app-private storage for offline playback. Runs under
 * WorkManager so it survives process death and retries with backoff; a Wi‑Fi-only network
 * constraint (when set) is applied by the enqueuer. Progress is written to Room (drives the
 * UI) and mirrored to a lightweight notification.
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val credentials = credentialStore.credentials.value ?: return Result.failure()
        val files = audioFileDao.findForBook(bookId)
        if (files.isEmpty()) return Result.success()

        val title = bookDao.findById(bookId)?.title ?: bookId.substringAfterLast('/')
        val notifId = bookId.hashCode()
        ensureChannel()

        downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DOWNLOADING, 0, files.size, now()))
        try {
            files.forEachIndexed { index, file ->
                notifyProgress(notifId, title, index, files.size)
                val dest = storage.fileFor(file.relativePath)
                dest.parentFile?.mkdirs()
                val request = Request.Builder().url(webDavClient.urlFor(credentials, file.relativePath)).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${file.relativePath}")
                    val body = response.body ?: throw IOException("empty body for ${file.relativePath}")
                    body.byteStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
                }
                downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DOWNLOADING, index + 1, files.size, now()))
            }
            downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.DONE, files.size, files.size, now()))
            cancelNotification(notifId)
            Log.i(TAG, "downloaded $bookId (${files.size} files)")
            return Result.success()
        } catch (e: CancellationException) {
            throw e // stopped/cancelled — DownloadManager.delete does the cleanup
        } catch (e: Exception) {
            Log.w(TAG, "download failed for $bookId (attempt ${runAttemptCount + 1})", e)
            if (runAttemptCount + 1 < MAX_ATTEMPTS) return Result.retry()
            storage.deleteBook(bookId)
            downloadDao.upsert(DownloadEntity(bookId, DownloadStatus.FAILED, 0, files.size, now()))
            cancelNotification(notifId)
            return Result.failure()
        }
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notifyProgress(id: Int, title: String, done: Int, total: Int) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Downloading $done/$total…")
            .setOngoing(true)
            .setProgress(total, done, false)
            .build()
        // No-op if POST_NOTIFICATIONS isn't granted; the download proceeds regardless.
        runCatching { NotificationManagerCompat.from(appContext).notify(id, notification) }
    }

    private fun cancelNotification(id: Int) {
        runCatching { NotificationManagerCompat.from(appContext).cancel(id) }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val KEY_BOOK_ID = "bookId"
        private const val TAG = "HomerDownload"
        private const val CHANNEL_ID = "downloads"
        private const val MAX_ATTEMPTS = 3
    }
}
