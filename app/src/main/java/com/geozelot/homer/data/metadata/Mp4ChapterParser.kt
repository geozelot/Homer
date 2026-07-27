package com.geozelot.homer.data.metadata

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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
    @ApplicationContext private val context: Context,
    private val dataSourceFactory: DataSource.Factory,
) {
    private data class Box(val contentStart: Long, val contentSize: Long) {
        val end get() = contentStart + contentSize
    }

    /** Chapter marks in start order, or empty if none / unreadable. */
    suspend fun parse(mediaUri: String): List<DurationExtractor.ChapterMark> = withContext(Dispatchers.IO) {
        runCatching { parseInternal(Uri.parse(mediaUri)) }
            .getOrElse {
                Log.w(TAG, "mp4 chapter parse failed for $mediaUri", it)
                emptyList()
            }
    }

    private fun parseInternal(uri: Uri): List<DurationExtractor.ChapterMark> {
        val moov = findBox(uri, 0L, Long.MAX_VALUE, "moov") ?: return emptyList()
        val udta = findBox(uri, moov.contentStart, moov.end, "udta") ?: return emptyList()
        val chpl = findBox(uri, udta.contentStart, udta.end, "chpl") ?: return emptyList()
        if (chpl.contentSize <= 0 || chpl.contentSize > MAX_CHPL_BYTES) return emptyList()
        val payload = readAt(uri, chpl.contentStart, chpl.contentSize) ?: return emptyList()
        return parseChpl(payload)
    }

    /** Scans the boxes in [start, end) for [type], returning its content range (past the header). */
    private fun findBox(uri: Uri, start: Long, end: Long, type: String): Box? {
        var cursor = start
        var guard = 0
        while (cursor < end && guard++ < MAX_BOXES) {
            val header = readAt(uri, cursor, 16L) ?: return null
            if (header.size < 8) return null
            var size = u32(header, 0)
            var headerLen = 8L
            if (size == 1L) {
                if (header.size < 16) return null
                size = u64(header, 8)
                headerLen = 16L
            } else if (size == 0L) {
                // Extends to the end of the container.
                size = end - cursor
            }
            if (size < headerLen) return null
            val boxType = String(header, 4, 4, Charsets.US_ASCII)
            if (boxType == type) return Box(cursor + headerLen, size - headerLen)
            cursor += size
        }
        return null
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

    /** Reads [length] bytes at [position] via a fresh ranged data-source open (Range request). */
    private fun readAt(uri: Uri, position: Long, length: Long): ByteArray? {
        if (length <= 0) return null
        val ds = dataSourceFactory.createDataSource()
        return try {
            ds.open(DataSpec.Builder().setUri(uri).setPosition(position).setLength(length).build())
            val out = ByteArray(length.toInt())
            var off = 0
            while (off < out.size) {
                val n = ds.read(out, off, out.size - off)
                if (n == C.RESULT_END_OF_INPUT) break
                off += n
            }
            if (off == 0) null else if (off < out.size) out.copyOf(off) else out
        } catch (e: Exception) {
            null
        } finally {
            runCatching { ds.close() }
        }
    }

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = (v shl 8) or (b[o + k].toLong() and 0xFF)
        return v
    }

    private companion object {
        const val TAG = "HomerMeta"
        const val MAX_BOXES = 4096
        const val MAX_CHPL_BYTES = 1L shl 20 // 1 MB is plenty for a chapter list
    }
}
