package com.geozelot.homer.data.storage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Foreground (data-sync) worker that moves Homer's local data to a newly chosen storage folder via
 * [StorageMigrator], so a large move survives the app being backgrounded and shows progress.
 * Reuses WorkManager's SystemForegroundService (data-sync type declared in the manifest).
 */
@HiltWorker
class StorageMigrationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val migrator: StorageMigrator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val source = inputData.getString(KEY_SOURCE)
        val target = inputData.getString(KEY_TARGET)
        val overwrite = inputData.getBoolean(KEY_OVERWRITE, false)
        ensureChannel()
        return try {
            setForegroundSafely(foregroundInfo("Moving your library…", 0, 0))
            migrator.migrate(source, target, overwrite) { p ->
                setForegroundSafely(foregroundInfo(p.label, p.done, p.total))
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "storage migration worker failed", e)
            Result.failure()
        }
    }

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
                NotificationChannel(CHANNEL_ID, "Storage", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun foregroundInfo(text: String, done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Homer")
            .setContentText(if (total > 0) "$text $done/$total" else text)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    companion object {
        const val KEY_SOURCE = "source"
        const val KEY_TARGET = "target"
        const val KEY_OVERWRITE = "overwrite"
        const val WORK_NAME = "storage-migration"
        private const val TAG = "HomerStore"
        private const val CHANNEL_ID = "storage"
        private const val NOTIF_ID = 43
    }
}
