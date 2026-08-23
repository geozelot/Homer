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
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
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
    /** One embedded chapter mark: where it starts in the file and its title (if tagged). */
    data class ChapterMark(val startMs: Long, val title: String?)

    /** What one probe learned: duration, (best-effort) genre, and any embedded ID3 chapters. */
    data class Probe(
        val durationMs: Long?,
        val genre: String?,
        val chapters: List<ChapterMark> = emptyList(),
    )

    // ExoPlayer must only be touched from the thread it was built on; keep one dedicated
    // looper thread so probing never blocks Main and callbacks arrive here in order.
    private val thread = HandlerThread("HomerDurationProbe").apply { start() }
    private val handler = Handler(thread.looper)

    /** Duration + genre, each null if unknown / on failure / on timeout. */
    suspend fun probe(mediaUri: String): Probe =
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
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
                            // ExoPlayer folds the container tags into a unified MediaMetadata by
                            // STATE_READY, so genre + embedded ID3 chapters come free from the
                            // same probe (empty if none / not yet READY).
                            val genre = Id3Genres.resolve(exo.mediaMetadata.genre?.toString())
                            val chapters = readChapters(exo)
                            exo.release()
                            if (cont.isActive) cont.resume(Probe(duration, genre, chapters))
                        }

                        exo.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                when (state) {
                                    Player.STATE_READY -> {
                                        val d = exo.duration
                                        finish(if (d > 0 && d != C.TIME_UNSET) d else null)
                                    }
                                    Player.STATE_ENDED -> finish(null)
                                    // IDLE and BUFFERING are the states on the way there; the
                                    // probe resolves on READY, on ENDED, or on the timeout.
                                    else -> Unit
                                }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                Log.d(TAG, "duration probe failed for $mediaUri", error)
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
                        Log.d(TAG, "duration probe setup failed for $mediaUri", e)
                        player?.release()
                        if (settled.compareAndSet(false, true) && cont.isActive) cont.resume(Probe(null, null))
                    }
                }
            }
        } ?: Probe(null, null)

    /**
     * Pulls embedded ID3v2 CHAP frames out of the prepared player's track metadata, in start-time
     * order. Each chapter's title is a nested TIT2 text subframe (ID3 has no flat title field).
     * Returns empty for formats without ID3 chapters (e.g. M4B — that path is the "hole" noted in
     * the chapter-parsing research and stays behind this best-effort probe).
     */
    private fun readChapters(exo: ExoPlayer): List<ChapterMark> {
        val marks = mutableListOf<ChapterMark>()
        val groups = exo.currentTracks.groups
        for (g in groups.indices) {
            val group = groups[g]
            for (t in 0 until group.length) {
                val metadata = group.getTrackFormat(t).metadata ?: continue
                for (e in 0 until metadata.length()) {
                    val entry = metadata.get(e)
                    if (entry !is ChapterFrame) continue
                    val title = (0 until entry.subFrameCount)
                        .map { entry.getSubFrame(it) }
                        .filterIsInstance<TextInformationFrame>()
                        .firstOrNull { it.id == "TIT2" }
                        ?.values?.firstOrNull()
                        ?.trim()
                        ?.ifBlank { null }
                    marks += ChapterMark(startMs = entry.startTimeMs.toLong(), title = title)
                }
            }
        }
        return marks.distinctBy { it.startMs }.sortedBy { it.startMs }
    }

    private companion object {
        const val TAG = "HomerMeta"
        const val PROBE_TIMEOUT_MS = 30_000L
    }
}
