package com.geozelot.homer.playback

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
import com.geozelot.homer.MainActivity
import com.geozelot.homer.R
import com.geozelot.homer.data.settings.PlaybackSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * A notification of Homer's own, saying how long the sleep timer has left.
 *
 * ## Why this and not the media notification
 *
 * The countdown was first put beside the author on the media notification, by overriding Media3's
 * notification provider. It never appeared. Two mechanisms were tried — the service rebuilding on a
 * timer, then the provider posting through the callback Media3 hands it for exactly that purpose —
 * and neither put the minutes on screen. Somewhere between our provider and the posted notification
 * the text was being dropped, and finding out where meant device time spent on a convenience.
 *
 * A notification we post ourselves has none of that in the way: it is built here, posted here and
 * cancelled here, and the only thing that can go wrong is something this file does. It is also
 * better behaved — dismissible, on a channel the reader can mute on its own, and it says the one
 * thing it is for rather than sharing a line with the author.
 *
 * ## What it costs
 *
 * Updated on the MINUTE, not the tick: a notification reposted every second is three and a half
 * thousand system posts an hour for a number nobody reads that closely. The exact seconds are on
 * the player's cover, where redrawing is free.
 *
 * Minutes are rounded UP, so a timer with 30 seconds left says "1 min" rather than "0" — a
 * countdown that sits on zero while audio still plays reads as broken.
 */
@Singleton
class SleepTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackSettings: PlaybackSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var enabled = true

    /** The minute last posted, so an unchanged one is not reposted sixty times. */
    private var shownMinutes: Long? = null

    init {
        scope.launch {
            playbackSettings.sleepTimerNotification.collect { on ->
                enabled = on
                // Switching it off takes down whatever is already up, rather than leaving one
                // notification stranded until the timer happens to end.
                if (!on) cancel()
            }
        }
    }

    /**
     * Reports the countdown. Null means there is no timer, which takes the notification down.
     *
     * Called on every tick; it is this method's job to be cheap about it, not the caller's.
     */
    fun update(remainingMs: Long?) {
        if (!enabled || remainingMs == null) {
            cancel()
            return
        }
        val minutes = ceil(remainingMs / 60_000.0).toLong().coerceAtLeast(1L)
        if (minutes == shownMinutes) return
        shownMinutes = minutes
        post(minutes)
    }

    private fun post(minutes: Long) {
        // Inline rather than behind a helper, and not by preference: lint's flow analysis does not
        // follow a call, so a checked-elsewhere permission reads to it as an unchecked one. Below
        // Android 13 there is no permission to hold.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.sleep_channel_name),
                    // LOW: it must not make a sound at the moment somebody is falling asleep, which
                    // is the entire situation this exists for.
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.sleep_notif_title))
            .setContentText(context.getString(R.string.player_sleep_minutes_left, minutes))
            .setContentIntent(open)
            // Dismissible: it is a readout, not a task in progress, and somebody who does not want
            // it on the lock screen should be able to swipe it away without turning the timer off.
            .setOngoing(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
            .onFailure { Log.w(TAG, "could not post the sleep timer notification", it) }
    }

    private fun cancel() {
        if (shownMinutes == null) return
        shownMinutes = null
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIF_ID) }
    }

    private companion object {
        const val TAG = "HomerPlay"
        const val CHANNEL_ID = "sleep"

        /** Its own id, so it replaces itself on every update and never stacks. Homer's others are
         *  42, 43 and 44; this is the next one. */
        const val NOTIF_ID = 45
    }
}
