package com.geozelot.homer.data.metadata

import android.net.Uri

/**
 * An MP4 box walk over [RangedReader].
 *
 * Shared by [Mp4ChapterParser] and [AudioHeaderDuration] so the box arithmetic exists once. The
 * walk skips a box by its recorded size rather than reading through it, so reaching a `moov` at
 * the end of a large file costs a handful of 16-byte requests instead of a download.
 */
internal class Mp4Boxes(private val reader: RangedReader) {

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

    /** Delegates to [RangedReader]; kept so the box walk reads like one thing. */
    fun readAt(uri: Uri, position: Long, length: Long): ByteArray? = reader.readAt(uri, position, length)

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
