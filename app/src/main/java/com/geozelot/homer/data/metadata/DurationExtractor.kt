package com.geozelot.homer.data.metadata

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Reads a single file's playback duration by preparing a headless [ExoPlayer] over the
 * authenticated OkHttp data source — the same transport that streams audio. For most
 * container formats ExoPlayer reports the duration once it reaches [Player.STATE_READY]
 * without downloading the whole file (moov atom for M4A/M4B, Xing/VBRI header for MP3).
 *
 * The platform MediaMetadataRetriever/MediaExtractor can't be used here: their HTTP path
 * doesn't carry our Basic-auth headers, so it fails against authenticated WebDAV (same
 * reason [MetadataExtractor] drives cover extraction through Media3).
 */
@OptIn(UnstableApi::class)
@Singleton
class DurationExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSourceFactory: DataSource.Factory,
) {
    // ExoPlayer must only be touched from the thread it was built on; keep one dedicated
    // looper thread so probing never blocks Main and callbacks arrive here in order.
    private val thread = HandlerThread("HomerDurationProbe").apply { start() }
    private val handler = Handler(thread.looper)

    /** Duration in ms, or null if unknown / on failure / on timeout. */
    suspend fun probe(mediaUri: String): Long? = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            handler.post {
                val settled = AtomicBoolean(false)
                var player: ExoPlayer? = null
                try {
                    val exo = ExoPlayer.Builder(context)
                        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                        .build()
                    player = exo

                    fun finish(duration: Long?) {
                        if (!settled.compareAndSet(false, true)) return
                        exo.release()
                        if (cont.isActive) cont.resume(duration)
                    }

                    exo.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_READY -> {
                                    val d = exo.duration
                                    finish(if (d > 0 && d != C.TIME_UNSET) d else null)
                                }
                                Player.STATE_ENDED -> finish(null)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.w(TAG, "duration probe failed for $mediaUri", error)
                            finish(null)
                        }
                    })

                    exo.setMediaItem(MediaItem.fromUri(mediaUri))
                    exo.playWhenReady = false
                    exo.prepare()

                    // Timeout / caller cancellation: tear the player down on its own thread.
                    cont.invokeOnCancellation {
                        handler.post { if (settled.compareAndSet(false, true)) exo.release() }
                    }
                } catch (e: Throwable) {
                    // A synchronous failure (player build / prepare) would otherwise leave
                    // the coroutine hanging until the 30s timeout — resolve it now.
                    Log.w(TAG, "duration probe setup failed for $mediaUri", e)
                    player?.release()
                    if (settled.compareAndSet(false, true) && cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private companion object {
        const val TAG = "HomerMeta"
        const val PROBE_TIMEOUT_MS = 30_000L
    }
}
