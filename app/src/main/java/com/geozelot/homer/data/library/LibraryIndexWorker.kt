package com.geozelot.homer.data.library

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
import com.geozelot.homer.data.metadata.CoverEnricher
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.sync.HomerCatalogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Foreground (data-sync) worker that indexes the library — an optional scan followed by cover
 * enrichment — so the work survives the app being backgrounded or killed and shows a progress
 * notification. Modelled on the download worker; it reuses WorkManager's SystemForegroundService
 * (data-sync type already declared in the manifest).
 *
 * At Tier 3 it also publishes the shared catalog after a scan (owner-gated creation, open updates).
 */
@HiltWorker
class LibraryIndexWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val coverEnricher: CoverEnricher,
    private val catalog: HomerCatalogRepository,
    private val librarySettings: LibrarySettings,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val doScan = inputData.getBoolean(KEY_SCAN, false)
        val resetCovers = inputData.getBoolean(KEY_RESET_COVERS, false)
        ensureChannel()

        try {
            // Full re-scan / refresh: clear cached art + every attempted flag, so covers re-fetch
            // and durations/tags that failed to probe once get another chance.
            if (resetCovers) libraryRepository.resetEnrichment()

            if (doScan) {
                setForegroundSafely(foregroundInfo("Scanning library…", 0, 0))
                libraryRepository.scan(incremental = inputData.getBoolean(KEY_INCREMENTAL, false))
            } else if (coverEnricher.pendingCount() == 0) {
                // Nothing to fetch — finish without ever showing a notification.
                return Result.success()
            }

            // Throttle the notification: one update per book overran Android's ~5/s notify budget on
            // a large library, so the framework shed the updates and flooded the log with
            // "rate limit exceeded" instead of showing progress. Once a second is plenty for a
            // progress bar, and the final call always lands so it never ends mid-way.
            var lastNotifyMs = 0L
            coverEnricher.enrich { done, total ->
                val now = System.currentTimeMillis()
                if (done >= total || now - lastNotifyMs >= PROGRESS_NOTIFY_INTERVAL_MS) {
                    lastNotifyMs = now
                    setForegroundSafely(foregroundInfo("Fetching covers…", done, total))
                }
            }

            // Shared catalog: publish the freshly-scanned catalog (owner-gated creation, open updates).
            if (doScan && librarySettings.sharedCatalogEnabled.first()) {
                setForegroundSafely(foregroundInfo("Updating shared library…", 0, 0))
                catalog.publishIfAllowed()
            }
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "library index failed", e)
            return Result.failure()
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
                NotificationChannel(CHANNEL_ID, "Library", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun foregroundInfo(text: String, done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Homer")
            .setContentText(if (total > 0) "$text ${done}/$total" else text)
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
        const val KEY_SCAN = "scan"
        const val KEY_INCREMENTAL = "incremental"
        const val KEY_RESET_COVERS = "reset_covers"
        const val WORK_NAME = "library-index"
        private const val TAG = "HomerScan"
        private const val CHANNEL_ID = "library"
        private const val NOTIF_ID = 42

        /** Minimum gap between progress notifications (the platform sheds updates above ~5/s). */
        private const val PROGRESS_NOTIFY_INTERVAL_MS = 1_000L
    }
}
