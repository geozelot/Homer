package com.geozelot.homer.data.metadata

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort extractor for embedded chapters in MP4/M4B/M4A files — the case ExoPlayer's track
 * metadata doesn't surface (only ID3 CHAP comes free from the duration probe). Reads Nero-style
 * `moov/udta/chpl` chapter markers via small ranged reads over the same authenticated data source
 * that streams audio, so it works for both streamed and downloaded books without pulling the whole
 * file.
 *
 * Scope + safety: it targets the common `chpl` box (written by ffmpeg, mp4v2, and many audiobook
 * tools). QuickTime chapter *text tracks* are not parsed. Anything it can't confidently read yields
 * an empty list, so it can only ever *add* chapters — never break playback or duration probing.
 */
@OptIn(UnstableApi::class)
@Singleton
class Mp4ChapterParser @Inject constructor(
    dataSourceFactory: DataSource.Factory,
) {
    private val boxes = Mp4Boxes(dataSourceFactory)

    /** Chapter marks in start order, or empty if none / unreadable. */
    suspend fun parse(mediaUri: String): List<DurationExtractor.ChapterMark> = withContext(Dispatchers.IO) {
        runCatching { parseInternal(Uri.parse(mediaUri)) }
            .getOrElse {
                Log.d(TAG, "mp4 chapter parse failed for $mediaUri", it)
                emptyList()
            }
    }

    private fun parseInternal(uri: Uri): List<DurationExtractor.ChapterMark> {
        val moov = boxes.findBox(uri, 0L, Long.MAX_VALUE, "moov") ?: return emptyList()
        val udta = boxes.findBox(uri, moov.contentStart, moov.end, "udta") ?: return emptyList()
        val chpl = boxes.findBox(uri, udta.contentStart, udta.end, "chpl") ?: return emptyList()
        if (chpl.contentSize <= 0 || chpl.contentSize > MAX_CHPL_BYTES) return emptyList()
        val payload = boxes.readAt(uri, chpl.contentStart, chpl.contentSize) ?: return emptyList()
        return parseChpl(payload)
    }

    private fun parseChpl(p: ByteArray): List<DurationExtractor.ChapterMark> {
        // Fullbox header (version + flags) = 4 bytes, then a reserved byte and a 1-byte count
        // (the ffmpeg/Nero layout). 100ns timestamps → milliseconds.
        if (p.size < 6) return emptyList()
        var i = 4          // skip version + flags
        i += 1             // reserved
        val count = p[i].toInt() and 0xFF
        i += 1
        if (count == 0) return emptyList()
        val marks = ArrayList<DurationExtractor.ChapterMark>(count)
        repeat(count) {
            if (i + 9 > p.size) return marks.sortedBy { it.startMs } // truncated → keep what parsed
            val start100ns = u64(p, i); i += 8
            val titleLen = p[i].toInt() and 0xFF; i += 1
            if (i + titleLen > p.size) return marks.sortedBy { it.startMs }
            val title = String(p, i, titleLen, Charsets.UTF_8).trim().ifBlank { null }
            i += titleLen
            marks += DurationExtractor.ChapterMark(startMs = start100ns / 10_000L, title = title)
        }
        return marks.distinctBy { it.startMs }.sortedBy { it.startMs }
    }

    private fun u64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = (v shl 8) or (b[o + k].toLong() and 0xFF)
        return v
    }

    private companion object {
        const val TAG = "HomerMeta"
        const val MAX_CHPL_BYTES = 1L shl 20 // 1 MB is plenty for a chapter list
    }
}
