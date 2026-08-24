package com.geozelot.homer.data.metadata

/**
 * Reads genre and embedded chapters out of an ID3v2 tag, walking its frames rather than streaming
 * the file.
 *
 * The tag is a list of length-prefixed frames, so the interesting ones can be reached by arithmetic
 * and the enormous one — the cover art, routinely hundreds of kilobytes — can be stepped over
 * without ever being fetched. That is the whole saving: the reader this replaces opened a stream
 * per book and cost seconds, most often to discover the book had no genre at all.
 *
 * **Reads only what it needs.** [read] is given the bytes already in hand and a way to fetch more;
 * it asks for another window only when a frame header lands outside the current one, and gives up
 * after [MAX_HOPS] of them so a pathological tag cannot turn into a download.
 *
 * **Answers null unless the whole tag was understood.** A partial parse cannot tell "this book has
 * no chapters" from "the chapters are further in", and the caller records that answer permanently.
 * So anything unexpected — an unsynchronised tag, ID3v2.2's different frame layout, an extended
 * header, a frame that runs past the tag — declines, and the slow path decides instead.
 */
internal object Id3Tags {

    /** What the tag says. An empty [chapters] means the tag genuinely carries none. */
    data class Tags(val genre: String?, val chapters: List<DurationExtractor.ChapterMark>)

    /**
     * Parses the tag at the start of the file.
     *
     * @param head bytes from offset 0 — the tag header and as many frames as came with it.
     * @param fetch supplies a window of the file at an absolute offset, or null if it cannot.
     * @return the tag's contents, or null if it could not be read in full.
     */
    fun read(head: ByteArray, fetch: (Long, Int) -> ByteArray?): Tags? {
        if (head.size < HEADER_BYTES) return null
        if (head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) {
            return null
        }
        val major = head[3].toInt() and 0xFF
        // v2.2 uses three-character ids and three-byte sizes — a different walk, and vanishingly
        // rare in audiobooks. Anything newer than v2.4 is unknown by definition.
        if (major != 3 && major != 4) return null

        val flags = head[5].toInt() and 0xFF
        // Unsynchronisation rewrites the byte stream, so frame offsets cannot be trusted until it
        // is undone. An extended header shifts where the frames begin. Both are rare; decline.
        if (flags and 0x80 != 0 || flags and 0x40 != 0) return null

        var size = 0L
        for (i in 6..9) {
            val b = head[i].toInt()
            if (b and 0x80 != 0) return null // not syncsafe → not a tag we understand
            size = (size shl 7) or (b and 0x7F).toLong()
        }
        if (size <= 0) return null
        val tagEnd = HEADER_BYTES + size

        val window = Window(head, fetch)
        var cursor = HEADER_BYTES.toLong()
        var genre: String? = null
        val chapters = mutableListOf<DurationExtractor.ChapterMark>()
        var guard = 0

        while (cursor + FRAME_HEADER_BYTES <= tagEnd) {
            if (guard++ >= MAX_FRAMES) return null
            val header = window.at(cursor, FRAME_HEADER_BYTES) ?: return null
            // Padding: the tag is over, and everything in it was seen.
            if (header.all { it == 0.toByte() }) break

            val id = String(header, 0, 4, Charsets.US_ASCII)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) return null
            val frameSize = if (major == 4) syncsafe(header, 4) else be32(header, 4)
            if (frameSize < 0 || cursor + FRAME_HEADER_BYTES + frameSize > tagEnd) return null

            if (!isAwkward(header, major) && frameSize in 1..MAX_FRAME_BYTES) {
                when (id) {
                    "TCON" -> {
                        val body = window.at(cursor + FRAME_HEADER_BYTES, frameSize.toInt()) ?: return null
                        genre = Id3Genres.resolve(decodeText(body))
                    }
                    "CHAP" -> {
                        val body = window.at(cursor + FRAME_HEADER_BYTES, frameSize.toInt()) ?: return null
                        parseChapter(body, major)?.let(chapters::add)
                    }
                }
            }
            cursor += FRAME_HEADER_BYTES + frameSize
        }

        return Tags(genre, chapters.distinctBy { it.startMs }.sortedBy { it.startMs })
    }

    /**
     * Compressed or encrypted frames cannot be read as they lie — the very case Media3 logs as
     * "Skipping unsupported compressed or encrypted frame". Skipped rather than refused: the frame
     * is unreadable, the tag around it is not.
     */
    private fun isAwkward(header: ByteArray, major: Int): Boolean {
        val formatFlags = header[9].toInt() and 0xFF
        return if (major == 4) formatFlags and 0x0C != 0 else formatFlags and 0xC0 != 0
    }

    /** A text frame: one encoding byte, then the string, terminated by a null or the frame end. */
    private fun decodeText(body: ByteArray): String? {
        if (body.size < 2) return null
        val charset = when (body[0].toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16 // byte-order mark leads
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        return String(body, 1, body.size - 1, charset)
            // The terminator, not whitespace: a genre like "Hörspiel für Kinder" is one value.
            .substringBefore('\u0000')
            .trim()
            .ifBlank { null }
    }

    /**
     * A CHAP frame: a null-terminated element id, four 32-bit times, then nested frames. Only the
     * start time and the TIT2 title are wanted; ID3 has no flat title field, hence the nesting.
     */
    private fun parseChapter(body: ByteArray, major: Int): DurationExtractor.ChapterMark? {
        val idEnd = body.indexOfFirst { it == 0.toByte() }
        if (idEnd < 0) return null
        var i = idEnd + 1
        if (i + 16 > body.size) return null
        val startMs = be32(body, i)
        i += 16

        var title: String? = null
        var guard = 0
        while (i + FRAME_HEADER_BYTES <= body.size && guard++ < MAX_SUBFRAMES) {
            val id = String(body, i, 4, Charsets.US_ASCII)
            val size = if (major == 4) syncsafe(body, i + 4) else be32(body, i + 4)
            if (size <= 0 || i + FRAME_HEADER_BYTES + size > body.size) break
            if (id == "TIT2") {
                val from = i + FRAME_HEADER_BYTES
                title = decodeText(body.copyOfRange(from, from + size.toInt()))
                break
            }
            i += FRAME_HEADER_BYTES + size.toInt()
        }
        return DurationExtractor.ChapterMark(startMs = startMs, title = title)
    }

    /**
     * A sliding view of the file. Frames are walked in order, so one window plus the occasional
     * jump covers the whole tag; [MAX_HOPS] bounds what a tag full of huge frames can cost.
     */
    private class Window(head: ByteArray, private val fetch: (Long, Int) -> ByteArray?) {
        private var bytes = head
        private var start = 0L
        private var hops = 0

        fun at(position: Long, length: Int): ByteArray? {
            if (length <= 0) return null
            if (position >= start && position + length <= start + bytes.size) {
                val offset = (position - start).toInt()
                return bytes.copyOfRange(offset, offset + length)
            }
            if (hops++ >= MAX_HOPS) return null
            val fetched = fetch(position, maxOf(length, CHUNK_BYTES)) ?: return null
            if (fetched.size < length) return null
            bytes = fetched
            start = position
            return fetched.copyOfRange(0, length)
        }
    }

    private fun be32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    /** Four 7-bit groups: the high bit is always clear so a size can never look like a sync word. */
    private fun syncsafe(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 4) {
            val x = b[o + i].toInt()
            if (x and 0x80 != 0) return -1
            v = (v shl 7) or (x and 0x7F).toLong()
        }
        return v
    }

    const val HEADER_BYTES = 10
    private const val FRAME_HEADER_BYTES = 10
    private const val MAX_FRAMES = 512
    private const val MAX_SUBFRAMES = 16

    /** Big enough for any text or chapter frame, small enough that cover art is never fetched. */
    private const val MAX_FRAME_BYTES = 64L * 1024

    private const val CHUNK_BYTES = 8 * 1024
    private const val MAX_HOPS = 8
}
