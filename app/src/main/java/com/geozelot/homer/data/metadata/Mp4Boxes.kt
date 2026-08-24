package com.geozelot.homer.data.metadata

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Small ranged reads over the authenticated data source, and an MP4 box walk built on them.
 *
 * Shared by [Mp4ChapterParser] and [AudioHeaderDuration] so the box arithmetic exists once. The
 * walk skips a box by its recorded size rather than reading through it, so reaching a `moov` at
 * the end of a large file costs a handful of 16-byte requests instead of a download.
 */
@OptIn(UnstableApi::class)
internal class Mp4Boxes(private val dataSourceFactory: DataSource.Factory) {

    data class Box(val contentStart: Long, val contentSize: Long) {
        val end get() = contentStart + contentSize
    }

    /** Scans the boxes in [start, end) for [type], returning its content range (past the header). */
    fun findBox(uri: Uri, start: Long, end: Long, type: String): Box? {
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

    /** Reads [length] bytes at [position] via a fresh ranged data-source open (Range request). */
    fun readAt(uri: Uri, position: Long, length: Long): ByteArray? {
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
        const val MAX_BOXES = 4096
    }
}
