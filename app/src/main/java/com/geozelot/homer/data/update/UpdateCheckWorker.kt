package com.geozelot.homer.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.geozelot.homer.MainActivity
import com.geozelot.homer.R
import com.geozelot.homer.data.settings.UpdateSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The daily "is there a new Homer?" check.
 *
 * Reads the opt-in preference itself and does nothing when it is off, rather than being enqueued
 * and cancelled as the setting changes: one scheduled job whose first act is to check consent has
 * no window in which a stale schedule can fire against a withdrawn one.
 *
 * A found release is notified at most once. Without [UpdateSettings.notifiedVersion] the same
 * release would produce a notification every day until it was installed.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val updateManager: UpdateManager,
    private val settings: UpdateSettings,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!settings.autoCheck.first()) return Result.success()

        return try {
            val release = updateManager.findUpdate(settings.channel.first())
            settings.setLastCheckedAtMs(System.currentTimeMillis())
            if (release == null) return Result.success()

            updateManager.publishFound(release)
            if (settings.notifiedVersion.first() != release.version.raw) {
                settings.setNotifiedVersion(release.version.raw)
                notify(release)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateCheckException) {
            // Nothing was learned; the next run tries again. Retrying now would burn the hourly
            // API allowance on a network that is probably still down.
            Log.w(TAG, "scheduled update check failed: ${e.message}")
            Result.success()
        }
    }

    private fun notify(release: UpdateRelease) {
        // From Android 13 posting is a runtime permission. Homer asks for it for playback and
        // downloads; if it was refused there is nothing to post to, so leave quietly — the About
        // screen still shows the update when the user next opens it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(appContext.getString(R.string.update_notif_title, release.version.raw))
            .setContentText(appContext.getString(R.string.update_notif_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(NOTIF_ID, notification) }
            .onFailure { Log.w(TAG, "could not post the update notification", it) }
    }

    companion object {
        private const val TAG = "HomerUpdate"
        private const val CHANNEL_ID = "updates"
        private const val NOTIF_ID = 44
        private const val WORK_NAME = "update-check"

        /**
         * Enqueues the daily check. Called unconditionally at startup with KEEP: the worker itself
         * honours the preference, so this never needs to be cancelled and re-enqueued.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                // A check is worth nothing in the first minutes after a boot or an install, and
                // spreading the start avoids every device asking GitHub at the same moment.
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
