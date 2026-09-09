package com.geozelot.homer.playback

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
 *
 * ## How the update gets posted, and why it is not the service's job
 *
 * A notification is built once and then sits there. Nothing about a sleep timer changes the PLAYER,
 * so none of the events Media3 already watches fires while it counts down, and the line went stale
 * the moment it was drawn.
 *
 * The first attempt asked the service to rebuild — `onUpdateNotification(session, false)` on the
 * minute. Media3 offers a mechanism for exactly this case and that was not it: a provider is handed
 * an [MediaNotification.Provider.Callback] precisely so it can say "what I built has changed, post
 * it again", which is how the default provider posts artwork that finished loading after the fact.
 * Using the intended path also puts the rebuild on the thread the API promises it on.
 *
 * So the provider watches its own number: while a notification is live, a coroutine follows the
 * remaining MINUTES and hands a freshly built notification back through the callback whenever they
 * change.
 */
@OptIn(UnstableApi::class)
class SleepAwareNotificationProvider(
    context: Context,
    private val remainingMs: StateFlow<Long?>,
    private val format: (Long) -> String,
    /** Runs on the player's application thread, which is where the callback must be called. */
    private val scope: CoroutineScope,
) : MediaNotification.Provider {

    /**
     * Composition rather than inheritance, and not by preference.
     *
     * `DefaultMediaNotificationProvider.createNotification` is FINAL, so a subclass cannot get at
     * the callback that posts an update — which is the one thing this needs. Holding an instance
     * and implementing the interface around it gets both: the default's whole notification, and a
     * place to stand between it and Media3. The one method that IS open, [getNotificationContentText],
     * is still overridden, on the instance.
     */
    private val delegate = object : DefaultMediaNotificationProvider(context) {
        override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
            val base = super.getNotificationContentText(metadata)
            val left = remainingMs.value ?: return base
            val timer = format(left)
            return if (base.isNullOrBlank()) timer else "$base · $timer"
        }
    }

    /** Watches the countdown for the notification most recently built. One at a time. */
    private var watcher: Job? = null

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        // Re-armed against THESE arguments: a rebuild triggered later has to be for the same
        // session with the same buttons, or it would post a notification for a book that is no
        // longer playing. The previous watcher is cancelled because its arguments are now stale.
        watcher?.cancel()
        watcher = scope.launch {
            remainingMs
                .map { it?.let { ms -> ms / 60_000L } }
                .distinctUntilChanged()
                // The first value is already in the notification being returned below; posting it
                // again would be a duplicate before the reader has seen the first one.
                .drop(1)
                .collect { minutes ->
                    Log.i(TAG, "sleep timer: reposting notification (${minutes ?: "none"} min)")
                    onNotificationChangedCallback.onNotificationChanged(
                        delegate.createNotification(
                            mediaSession,
                            mediaButtonPreferences,
                            actionFactory,
                            onNotificationChangedCallback,
                        ),
                    )
                }
        }
        return delegate.createNotification(
            mediaSession,
            mediaButtonPreferences,
            actionFactory,
            onNotificationChangedCallback,
        )
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(session, action, extras)

    private companion object {
        const val TAG = "HomerPlay"
    }
}
