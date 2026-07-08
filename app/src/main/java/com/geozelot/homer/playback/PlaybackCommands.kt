package com.geozelot.homer.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Custom session commands for player features that aren't standard [androidx.media3.common.Player]
 * commands and so can't cross the MediaController boundary on their own. Skip-silence lives on
 * the ExoPlayer instance, so the app asks the service to toggle it via this command.
 */
object PlaybackCommands {
    const val ACTION_SET_SKIP_SILENCE = "com.geozelot.homer.command.SET_SKIP_SILENCE"
    const val KEY_ENABLED = "enabled"

    val SET_SKIP_SILENCE = SessionCommand(ACTION_SET_SKIP_SILENCE, Bundle.EMPTY)
}
