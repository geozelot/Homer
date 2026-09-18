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
import androidx.media3.exoplayer.analytics.AnalyticsListener
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

    /** The volume override in force, kept so it can be re-applied when the audio session changes. */
    private var volumeMode: String = VolumeMode.NORMAL

    /** The audio session [loudnessEnhancer] is bound to; an effect cannot be moved between them. */
    private var enhancerSession: Int = C.AUDIO_SESSION_ID_UNSET

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

        // The boost has to follow the audio session, and the session does not exist yet.
        //
        // An AudioEffect is bound to one session id for its whole life, and a player has no session
        // until it prepares its first track — so the enhancer built in onCreate would attach to
        // AUDIO_SESSION_ID_UNSET, which is the GLOBAL output mix rather than this app's audio, and
        // then stay there for every book that followed. This rebinds it the moment a real session
        // arrives, and again whenever it changes.
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int,
            ) {
                applyBoost(audioSessionId)
            }
        })

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

    /**
     * Applies a volume override: player volume for reduced and normal, plus a [LoudnessEnhancer]
     * for the "increased" boost.
     *
     * The mode is remembered because the boost cannot always be applied when it is chosen — see
     * [applyBoost].
     */
    private fun applyVolumeMode(mode: String) {
        volumeMode = mode
        val exo = player ?: return
        exo.volume = VolumeMode.playerVolume(mode)
        applyBoost(exo.audioSessionId)
    }

    /**
     * Binds the boost to the session that is actually playing, and puts it in the state the current
     * mode asks for.
     *
     * Two things had to be true for this to work and neither was. The effect needs
     * `MODIFY_AUDIO_SETTINGS`, which was not declared — the constructor threw on every attempt and
     * a `runCatching` swallowed it, so the boost had never once been applied. And it has to be built
     * against a real session id: called from `onCreate` there is none yet, and an effect built on
     * the unset id attaches to the global output mix and then stays bound to it.
     *
     * Failures still degrade silently, because audio effects genuinely are per-device flaky — but
     * they are logged now, rather than being indistinguishable from a boost that simply did nothing.
     */
    private fun applyBoost(sessionId: Int) {
        if (sessionId != enhancerSession) {
            runCatching { loudnessEnhancer?.release() }
            loudnessEnhancer = null
            enhancerSession = sessionId
        }
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            val enhancer = loudnessEnhancer ?: LoudnessEnhancer(sessionId).also { loudnessEnhancer = it }
            if (volumeMode == VolumeMode.INCREASED) {
                enhancer.setTargetGain(BOOST_MILLIBELS)
                enhancer.enabled = true
            } else {
                enhancer.enabled = false
            }
        }.onFailure { Log.w(TAG, "loudness boost unavailable on this device", it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    private companion object {
        const val TAG = "HomerPlay"

        /** "Increased" as a gain, in millibels: +7 dB. */
        const val BOOST_MILLIBELS = 700
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
            // The standard transport commands go to everyone — that is how the notification,
            // Bluetooth, a watch and the system media controls drive playback, and refusing them
            // would break all of it. Homer's OWN commands do not: skip-silence and the volume
            // boost are this app's settings, and the service is exported (a MediaSessionService
            // has to be), so without this check any installed app could bind and change them.
            val ours = controller.packageName == packageName
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .apply {
                        if (ours) {
                            add(PlaybackCommands.SET_SKIP_SILENCE)
                            add(PlaybackCommands.SET_VOLUME_MODE)
                        }
                    }
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
