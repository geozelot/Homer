package com.geozelot.homer.playback

import android.content.pm.PackageManager
import android.media.audiofx.LoudnessEnhancer
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.geozelot.homer.data.settings.PlaybackSettings
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Background audio service. Hosts a single [ExoPlayer] behind a [MediaLibrarySession],
 * which gives us the system media notification, lockscreen controls, and (later) the
 * Android Auto browse tree for free. Streaming uses the authenticated, Range-capable
 * OkHttp data source.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var dataSourceFactory: DataSource.Factory

    @Inject
    lateinit var playbackSettings: PlaybackSettings

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(audiobookLoadControl())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Hold a partial wake lock + a WifiLock while playing. ExoPlayer defaults to
            // WAKE_MODE_NONE, which takes neither — a foreground service keeps the SERVICE alive
            // without keeping the DEVICE awake, so with the screen off the CPU can suspend and the
            // radio power down mid-stream. NETWORK rather than LOCAL because a book is streamed
            // unless it has been downloaded; both locks are taken on play and dropped on
            // pause/stop, so an idle app holds nothing.
            //
            // This is correct on its own merits, but for the record it was NOT the cause of the
            // "stops when backgrounded" reports — that turned out to be the OS battery saver
            // freezing the app, which no wake lock overrides. See the Playback settings row.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = exoPlayer

        // Apply persisted skip-silence + volume override to this session's player.
        serviceScope.launch {
            exoPlayer.skipSilenceEnabled = playbackSettings.skipSilence.first()
            applyVolumeMode(playbackSettings.volumeMode.first())
        }

        session = MediaLibrarySession.Builder(this, exoPlayer, LibraryCallback()).build()

        // The two things that decide whether audio survives backgrounding, and neither is visible
        // from inside the app once it goes wrong. A wake lock the system declines to honour and a
        // battery-optimised app the OS freezes outright look identical from here: the audio simply
        // stops and no player callback fires. Logged once per service start so the next report of
        // "it stopped again" arrives with half the answer already attached.
        val exempt = getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName)
        val wakeLockGranted =
            checkSelfPermission(android.Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "player ready: wakeMode=NETWORK wakeLock=$wakeLockGranted batteryExempt=$exempt")
    }

    /**
     * A load control sized for a spoken-word book streamed over WebDAV, rather than for video.
     *
     * ExoPlayer's defaults buffer at most 50 seconds ahead, and cap an audio renderer at ~832 KB.
     * At an audiobook's ~64 kbps that is about a minute and a half of audio, so any network stall
     * longer than that drains the buffer and the audio underruns. Ten minutes of a low-bitrate
     * mono stream is a few megabytes, so the trade the video defaults make — keep memory down,
     * accept re-buffering — is the wrong one here.
     *
     * The byte target has to be raised as well: it binds first otherwise and the duration target
     * never takes effect.
     *
     * Note this only helps a STREAMED book. It was added while chasing the background-playback
     * stalls and did not fix them — those were the OS battery saver freezing the app, with a
     * downloaded book that never touched the network.
     */
    private fun audiobookLoadControl(): LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 2 * 60_000,
            /* maxBufferMs = */ 10 * 60_000,
            // Unchanged: how little is needed to START, which should stay snappy.
            /* bufferForPlaybackMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            /* bufferForPlaybackAfterRebufferMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setTargetBufferBytes(8 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    /** Applies a volume override: player volume for reduced/normal, plus a LoudnessEnhancer for
     *  the "increased" boost (audio effects are flaky per-device, so failures degrade silently). */
    private fun applyVolumeMode(mode: String) {
        val exo = player ?: return
        exo.volume = if (mode == VolumeMode.REDUCED) 0.45f else 1.0f
        runCatching {
            val enhancer = loudnessEnhancer ?: LoudnessEnhancer(exo.audioSessionId).also { loudnessEnhancer = it }
            if (mode == VolumeMode.INCREASED) {
                enhancer.setTargetGain(700) // +7 dB loudness boost
                enhancer.enabled = true
            } else {
                enhancer.enabled = false
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    private companion object {
        const val TAG = "HomerPlay"
    }

    override fun onDestroy() {
        serviceScope.cancel()
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Advertise our custom commands alongside the standard media/library ones.
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .add(PlaybackCommands.SET_SKIP_SILENCE)
                    .add(PlaybackCommands.SET_VOLUME_MODE)
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.ACTION_SET_SKIP_SILENCE -> {
                    player?.skipSilenceEnabled = args.getBoolean(PlaybackCommands.KEY_ENABLED)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlaybackCommands.ACTION_SET_VOLUME_MODE -> {
                    applyVolumeMode(args.getString(PlaybackCommands.KEY_VOLUME_MODE) ?: VolumeMode.NORMAL)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}
