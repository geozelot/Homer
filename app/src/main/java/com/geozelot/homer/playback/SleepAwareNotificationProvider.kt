package com.geozelot.homer.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * The media notification, with the sleep timer's remaining time beside the author.
 *
 * ## Why it is on the notification at all
 *
 * A sleep timer is the one thing Homer does that a reader wants to check WITHOUT opening Homer —
 * they are in bed with the screen face down, and the question is "how long before it stops". The
 * player shows the exact seconds on the cover; this shows the minutes wherever they already are.
 *
 * ## Why minutes
 *
 * A notification rebuilt every second is a notification rebuilt three thousand six hundred times an
 * hour, and the system posts every one of them. Minutes change sixty times less often, which is why
 * [PlaybackService] refreshes on the minute rather than on the tick. The seconds are on the cover,
 * where redrawing is free.
 *
 * Only [getNotificationContentText] is overridden — the buttons, the channel, the media style and
 * every other decision stay the platform's, which is the point of extending the default provider
 * rather than writing one.
 */
@OptIn(UnstableApi::class)
class SleepAwareNotificationProvider(
    context: Context,
    private val remainingMs: () -> Long?,
    private val format: (Long) -> String,
) : DefaultMediaNotificationProvider(context) {

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
        val base = super.getNotificationContentText(metadata)
        val left = remainingMs() ?: return base
        val timer = format(left)
        return if (base.isNullOrBlank()) timer else "$base · $timer"
    }
}
