package com.geozelot.homer.data.metadata

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a file's duration out of its container header with one or two small ranged requests.
 *
 * This is the cheapest of the three ways Homer can learn a duration, and the reason it exists is
 * arithmetic: measuring a library was ~2.7s per file even with no decoder in the path, because
 * every probe was a whole WebDAV `GET` that ExoPlayer streamed until the duration happened to fall
 * out. Nextcloud's per-request overhead dominates, so the way to go faster is fewer, smaller
 * requests — not fewer bytes within one.
 *
 * Request budget per file, which is the number that matters:
 *  - MP3 with no ID3 tag, or an MP4 with `moov` at the front: **one**.
 *  - MP3 behind an ID3 tag (cover art, so usually): **two**.
 *  - MP3 that is constant-bitrate with no Xing header: two more, to prove the bitrate holds.
 *  - MP4 with `moov` at the end: a handful of 16-byte box-header reads.
 *
 * **It answers null freely.** Anything unrecognised — Ogg, Opus, raw AAC, a damaged header, a
 * variable-bitrate MP3 that never declared itself — falls through to the probes in
 * [DurationExtractor], which stay the authority. A null costs time; a wrong number silently
 * corrupts a book's length, its time-left and its finished state.
 */
@OptIn(UnstableApi::class)
@Singleton
class AudioHeaderDuration @Inject constructor(
    dataSourceFactory: DataSource.Factory,
) {
    private val boxes = Mp4Boxes(RangedReader(dataSourceFactory))

    /**
     * Duration of [mediaUri], or null if it cannot be read from the header alone.
     *
     * [sizeBytes] comes from the scan, which already records it — so a constant-bitrate MP3 needs
     * no extra request to learn how long the file is.
     */
    suspend fun durationMs(mediaUri: String, sizeBytes: Long): Long? = withContext(Dispatchers.IO) {
        try {
            read(Uri.parse(mediaUri), sizeBytes)
        } catch (e: CancellationException) {
            // runCatching would swallow this: the sweep is cancellable and must stay that way.
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "header duration failed for $mediaUri", e)
            null
        }
    }

    private fun read(uri: Uri, sizeBytes: Long): Long? {
        val head = boxes.readAt(uri, 0, HEAD_BYTES) ?: return null
        return when {
            // Sniffed, not guessed from the extension: a mislabelled file should fall through to
            // the probes rather than be parsed as something it is not.
            startsWith(head, 0, "fLaC") -> HeaderDurations.flacDurationMs(head)
            startsWith(head, 0, "RIFF") -> HeaderDurations.wavDurationMs(head)
            startsWith(head, 4, "ftyp") -> mp4(uri, head)
            else -> mp3(uri, head, sizeBytes)
        }
    }

    private fun mp4(uri: Uri, head: ByteArray): Long? {
        // A "faststart" file keeps moov at the front, so the duration is already in hand.
        HeaderDurations.mvhdInBuffer(head)?.let { return it }
        // Otherwise moov is at the end; walk to it by box size rather than reading through.
        val moov = boxes.findBox(uri, 0L, Long.MAX_VALUE, "moov") ?: return null
        val mvhd = boxes.findBox(uri, moov.contentStart, moov.end, "mvhd") ?: return null
        if (mvhd.contentSize !in 1..MAX_MVHD_BYTES) return null
        val content = boxes.readAt(uri, mvhd.contentStart, mvhd.contentSize) ?: return null
        return HeaderDurations.mvhdDurationMs(content)
    }

    private fun mp3(uri: Uri, head: ByteArray, sizeBytes: Long): Long? {
        val audioStart = HeaderDurations.id3v2Length(head)
        if (audioStart >= sizeBytes) return null
        // Skipping the tag rather than scanning past it is the whole trick: an audiobook's ID3v2
        // routinely carries cover art, so the first audio frame can be hundreds of KB in.
        val window = if (audioStart == 0L) head else boxes.readAt(uri, audioStart, AUDIO_WINDOW_BYTES)
        if (window == null) return null

        val frame = HeaderDurations.findFrame(window) ?: return null
        // A Xing/Info/VBRI header is an exact frame count — the only trustworthy answer for a
        // variable-bitrate file, and encoders write it for constant-bitrate ones too.
        HeaderDurations.vbrDurationMs(window, frame)?.let { return it }

        // No declaration, so the constant-bitrate formula is the only option — and it is wrong by
        // however much the bitrate actually varies. Prove it holds before trusting it.
        val audioBytes = sizeBytes - audioStart - frame.offset
        if (!bitrateHolds(uri, audioStart + frame.offset, audioBytes, frame)) return null
        return HeaderDurations.cbrDurationMs(audioBytes, frame)
    }

    /**
     * Samples two points further into the stream and checks the bitrate is the same there.
     *
     * Two rather than one because a variable-bitrate file passes a single check whenever that one
     * point happens to match the opening frame — which for a file that spends most of its time at
     * its nominal rate is not unlikely.
     */
    private fun bitrateHolds(
        uri: Uri,
        audioStart: Long,
        audioBytes: Long,
        first: HeaderDurations.MpegFrame,
    ): Boolean {
        // Too short to sample meaningfully; a file this small is not worth a second request and
        // could not be wrong by much anyway.
        if (audioBytes < MIN_SAMPLED_BYTES) return true
        for (fraction in SAMPLE_POINTS) {
            val at = audioStart + (audioBytes * fraction).toLong()
            val window = boxes.readAt(uri, at, SAMPLE_WINDOW_BYTES) ?: return false
            val frame = HeaderDurations.findFrame(window) ?: return false
            if (frame.bitrateBps != first.bitrateBps || frame.sampleRateHz != first.sampleRateHz) {
                return false
            }
        }
        return true
    }

    private fun startsWith(b: ByteArray, offset: Int, tag: String): Boolean {
        if (offset + tag.length > b.size) return false
        return String(b, offset, tag.length, Charsets.US_ASCII) == tag
    }

    private companion object {
        const val TAG = "HomerMeta"

        /**
         * Enough for a RIFF chunk list, a FLAC STREAMINFO, a faststart `moov/mvhd`, and an MP3's
         * first frames including its Xing header — while staying one small request.
         */
        const val HEAD_BYTES = 8L * 1024

        /** Room for two full frames at any legal bitrate, plus slack to find the first one. */
        const val AUDIO_WINDOW_BYTES = 8L * 1024

        const val SAMPLE_WINDOW_BYTES = 4L * 1024
        const val MIN_SAMPLED_BYTES = 256L * 1024
        val SAMPLE_POINTS = doubleArrayOf(0.4, 0.8)

        /** An `mvhd` is ~100 bytes; anything claiming more is not one. */
        const val MAX_MVHD_BYTES = 4096L
    }
}
