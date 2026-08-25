package com.geozelot.homer.data.metadata

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Decides whether [DurationExtractor.probeDuration]'s decoder-free path is still worth trying.
 *
 * Whether a renderer-less player reports a duration depends on the device's extractors, and there
 * is no way to establish that up front. So the fast path is used optimistically and withdrawn if
 * the full probe keeps having to rescue it: [FALLBACK_LIMIT] consecutive rescues and the rest of
 * the process goes straight to the full probe. A fast path that does not work on some device
 * therefore costs a bounded amount of time and never a wrong answer.
 *
 * Only a rescue counts — the fast probe finding nothing where the FULL probe then finds a duration.
 * Both failing says nothing about the fast path (the file may genuinely have no readable duration,
 * or the link may have dropped), so it does not count against it. Without that distinction a
 * library of unmeasurable files would disable a perfectly good fast path.
 *
 * Synchronised because it is one gate for the whole process: a measure sweep and a book being
 * opened can be probing at the same time, so the count is not owned by a single caller.
 */
internal class FastProbeGate(private val fallbackLimit: Int = FALLBACK_LIMIT) {

    private var consecutiveRescues = 0
    private var withdrawn = false

    @Synchronized
    fun shouldTryFast(): Boolean = !withdrawn

    /** The fast probe answered. */
    @Synchronized
    fun onFastSuccess() {
        consecutiveRescues = 0
    }

    /**
     * The fast probe found nothing and the full probe found a duration.
     * Returns true on the transition to withdrawn, so the caller can log it once.
     */
    @Synchronized
    fun onFullProbeRescue(): Boolean {
        if (withdrawn) return false
        consecutiveRescues++
        if (consecutiveRescues < fallbackLimit) return false
        withdrawn = true
        return true
    }

    /** Neither probe found a duration — not the fast path's fault, so it is not held against it. */
    fun onBothFailed() = Unit

    companion object {
        const val FALLBACK_LIMIT = 3
    }
}

/**
 * A renderer that claims audio tracks and decodes nothing.
 *
 * The probe needs the player to load a file's header without instantiating a hardware decoder.
 * Building it with NO renderers looks like the way to do that, and it is why the first attempt
 * silently returned nothing on every file: with no renderers, track selection enables no tracks,
 * and `ProgressiveMediaPeriod.getBufferedPositionUs()` answers `TIME_END_OF_SOURCE` whenever its
 * enabled-track count is zero. The player reads that as "buffered to the end", `renderersEnded` is
 * vacuously true because there are no renderers to end — and it goes straight to `STATE_ENDED`,
 * before the timeline refresh that carries the duration ever arrives.
 *
 * One renderer that accepts audio fixes that: a track is enabled, the period reports a real
 * buffered position, and nothing ends prematurely. [render] does nothing and [isEnded] is always
 * false, so no sample is ever decoded and no `MediaCodec` is created — the duration still comes
 * off the timeline, which is populated by the extractor, not by playback.
 */
@OptIn(UnstableApi::class)
private class HeaderOnlyAudioRenderer : BaseRenderer(C.TRACK_TYPE_AUDIO) {

    override fun getName(): String = "HomerHeaderOnlyAudio"

    override fun supportsFormat(format: Format): Int = RendererCapabilities.create(
        if (MimeTypes.isAudio(format.sampleMimeType)) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE,
    )

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) = Unit

    // Never ready and never ended. Ready would invite the player to start making playback
    // progress; ended is the premature-stop this class exists to prevent. The probe resolves off
    // the timeline, so neither is needed.
    override fun isReady(): Boolean = false

    override fun isEnded(): Boolean = false
}

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

    /**
     * What one probe learned: duration, (best-effort) genre and language, and any embedded ID3
     * chapters.
     */
    data class Probe(
        val durationMs: Long?,
        val genre: String?,
        /** ISO 639-1 code, already normalised; null when nothing in the file said. */
        val language: String? = null,
        val chapters: List<ChapterMark> = emptyList(),
    )

    // ExoPlayer must only be touched from the thread it was built on; keep one dedicated
    // looper thread so probing never blocks Main and callbacks arrive here in order.
    private val thread = HandlerThread("HomerDurationProbe").apply { start() }
    private val handler = Handler(thread.looper)

    /**
     * Duration only, with no decoder anywhere in the path.
     *
     * [probe] builds a player WITH renderers and waits for STATE_READY, which instantiates a
     * hardware audio decoder for every file — a Codec2Client/CCodec cluster per probe in logcat,
     * a couple of seconds each, and hours across a library. A duration lives in the container
     * header, so this builds the player with NO renderers and takes the value off the timeline the
     * moment the source reports it, which does not depend on renderer state.
     *
     * Returns null when the duration never arrives. Callers MUST read that as "ask [probe]", never
     * as "this file has no duration": whether a renderer-less player reports a duration depends on
     * the device's extractors, and that cannot be established from here. [FastProbeGate] bounds
     * how long a device where this doesn't work goes on paying for it.
     */
    suspend fun probeDuration(mediaUri: String): Long? {
        val outcome = withTimeoutOrNull(FAST_PROBE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                handler.post {
                    val settled = AtomicBoolean(false)
                    var player: ExoPlayer? = null

                    fun settle(result: Pair<Long?, String>, exo: ExoPlayer?) {
                        if (!settled.compareAndSet(false, true)) return
                        exo?.release()
                        if (cont.isActive) cont.resume(result)
                    }

                    try {
                        val exo = ExoPlayer.Builder(
                            context,
                            RenderersFactory { _, _, _, _, _ -> arrayOf(HeaderOnlyAudioRenderer()) },
                        )
                            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                            // The header is all this needs. Without a tight budget the player
                            // would happily pull the default ~50s of audio for a number that
                            // arrives in the first few KB.
                            .setLoadControl(
                                DefaultLoadControl.Builder()
                                    .setBufferDurationsMs(1_000, 2_000, 1_000, 1_000)
                                    .setTargetBufferBytes(256 * 1024)
                                    .setPrioritizeTimeOverSizeThresholds(false)
                                    .build(),
                            )
                            .build()
                        player = exo

                        fun resolveIfKnown() {
                            val d = exo.duration
                            if (d > 0 && d != C.TIME_UNSET) settle(d to "ok", exo)
                        }

                        exo.addListener(object : Player.Listener {
                            // The timeline carries the duration as soon as the extractor has read
                            // enough of the container to know it — which is the whole point: it
                            // does not depend on any renderer becoming ready.
                            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                                resolveIfKnown()
                            }

                            override fun onPlaybackStateChanged(state: Int) {
                                when (state) {
                                    Player.STATE_READY -> resolveIfKnown()
                                    // Should no longer be reachable before the duration arrives
                                    // (see HeaderOnlyAudioRenderer); named so the log says which
                                    // way it went if it ever is.
                                    Player.STATE_ENDED -> settle(null to "ended before a duration", exo)
                                    else -> Unit
                                }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                settle(null to "error ${error.errorCodeName}", exo)
                            }
                        })

                        exo.setMediaItem(MediaItem.fromUri(mediaUri))
                        exo.playWhenReady = false
                        exo.prepare()

                        cont.invokeOnCancellation {
                            handler.post { if (settled.compareAndSet(false, true)) exo.release() }
                        }
                    } catch (e: Throwable) {
                        settle(null to "setup failed: ${e.message}", player)
                    }
                }
            }
        }

        // Logged at I, not D: R8 strips Log.d from release builds, and release is the only build
        // that ever runs against a real library. A silent fast path is what made the first version
        // of this cost three betas to diagnose.
        if (outcome == null) {
            Log.i(TAG, "fast duration probe gave up: timed out after ${FAST_PROBE_TIMEOUT_MS}ms")
            return null
        }
        val (duration, reason) = outcome
        if (duration == null) Log.i(TAG, "fast duration probe gave up: $reason")
        return duration
    }

    /**
     * Genre + embedded chapters, with no decoder anywhere in the path.
     *
     * [probe] reaches the tags by building a player WITH renderers and waiting for STATE_READY,
     * which instantiates a hardware audio decoder. Tags live in the container header, so this uses
     * [MetadataRetriever] — the same renderer-less reader that already drives cover extraction —
     * and folds the track metadata exactly the way ExoPlayer does.
     *
     * Returns null ONLY when the container could not be read at all (no track groups, a failure,
     * or a timeout); the caller must rescue that with [probe]. A parsed container with no TCON and
     * no CHAP frames is a real answer and comes back as an empty [Probe], because that is a
     * question nothing else will ever settle. `durationMs` is always null here — this path never
     * speaks about duration, so it can never mark a file unmeasurable.
     */
    suspend fun probeTags(mediaUri: String): Probe? = withContext(Dispatchers.IO) {
        try {
            val future = MetadataRetriever.retrieveMetadata(
                DefaultMediaSourceFactory(dataSourceFactory),
                MediaItem.fromUri(mediaUri),
            )
            try {
                val groups = future.get(TAG_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // No track groups means the container never parsed — a structural failure, not an
                // untagged file. Say nothing rather than settle the question wrongly.
                if (groups.length == 0) return@withContext null

                val folded = MediaMetadata.Builder()
                val marks = mutableListOf<ChapterMark>()
                // Walked by hand: MediaMetadata folds the standard fields and has no language
                // among them, so populateFromMetadata cannot carry this one.
                var language: String? = null
                for (i in 0 until groups.length) {
                    val group = groups.get(i)
                    for (j in 0 until group.length) {
                        val metadata = group.getFormat(j).metadata ?: continue
                        folded.populateFromMetadata(metadata)
                        if (language == null) language = languageTag(metadata)
                        marks += chapterMarks(metadata)
                    }
                }
                Probe(
                    durationMs = null,
                    genre = Id3Genres.resolve(folded.build().genre?.toString()),
                    language = language,
                    chapters = marks.distinctBy { it.startMs }.sortedBy { it.startMs },
                )
            } finally {
                // Release the retriever's internal player, looper and open data source instead of
                // letting an abandoned read linger until the OkHttp timeout.
                future.cancel(true)
            }
        } catch (e: Exception) {
            // Log.d (stripped from release by R8): the URL carries the account + book path.
            Log.d(TAG, "decoder-free tag probe failed for $mediaUri", e)
            null
        }
    }

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
                            // Named, not positional: this path has no language to offer (the
                            // full probe reads MediaMetadata, which folds no language field), and
                            // a positional call silently put the chapters in its place.
                            if (cont.isActive) cont.resume(Probe(duration, genre, chapters = chapters))
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
     * order. Returns empty for formats without ID3 chapters (e.g. M4B — that path is the "hole"
     * noted in the chapter-parsing research and stays behind this best-effort probe).
     */
    private fun readChapters(exo: ExoPlayer): List<ChapterMark> {
        val marks = mutableListOf<ChapterMark>()
        val groups = exo.currentTracks.groups
        for (g in groups.indices) {
            val group = groups[g]
            for (t in 0 until group.length) {
                marks += chapterMarks(group.getTrackFormat(t).metadata ?: continue)
            }
        }
        return marks.distinctBy { it.startMs }.sortedBy { it.startMs }
    }

    /**
     * The CHAP frames in one track's metadata. Each chapter's title is a nested TIT2 text subframe
     * (ID3 has no flat title field). Shared by the player path and [probeTags] so both read
     * chapters identically — the retriever hands back the same [Metadata] the player exposes.
     */
    private fun chapterMarks(metadata: Metadata): List<ChapterMark> =
        (0 until metadata.length())
            .map { metadata.get(it) }
            .filterIsInstance<ChapterFrame>()
            .map { entry ->
                val title = (0 until entry.subFrameCount)
                    .map { entry.getSubFrame(it) }
                    .filterIsInstance<TextInformationFrame>()
                    .firstOrNull { it.id == "TIT2" }
                    ?.values?.firstOrNull()
                    ?.trim()
                    ?.ifBlank { null }
                ChapterMark(startMs = entry.startTimeMs.toLong(), title = title)
            }

    /**
     * The TLAN frame, if the container carries one.
     *
     * Normalised on the way out for the same reason [Id3Tags] does it: the frame holds ISO 639-2,
     * and a file is as likely to say "ger" as "deu".
     */
    private fun languageTag(metadata: Metadata): String? =
        (0 until metadata.length())
            .map { metadata.get(it) }
            .filterIsInstance<TextInformationFrame>()
            .firstOrNull { it.id == "TLAN" }
            ?.values?.firstOrNull()
            ?.let(BookLanguage::normalise)

    private companion object {
        const val TAG = "HomerMeta"
        const val PROBE_TIMEOUT_MS = 30_000L

        /**
         * Shorter than the full probe's on purpose. A working decoder-free probe resolves in well
         * under a second — it reads a header — so this is not the discriminator for a slow link;
         * it is what makes a device where the fast path does NOT work cheap to find out about.
         *
         * Note what [HeaderOnlyAudioRenderer] changed about the failure profile: the probe no
         * longer ends early, so a file whose container parses but never yields a duration now
         * waits this out instead of returning at once. Network and parse failures still settle
         * immediately through `onPlayerError`, and the gate caps the cost of a device where the
         * path is broken at three files — but a genuinely unreadable file is dearer than it was.
         */
        const val FAST_PROBE_TIMEOUT_MS = 12_000L

        /** Matches the cover extractor's — both are the same renderer-less header read. */
        const val TAG_PROBE_TIMEOUT_SECONDS = 30L
    }
}
