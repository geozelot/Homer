package com.geozelot.homer.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tag walking, over bytes built here.
 *
 * The refusals matter as much as the reads: the caller records "no chapters" permanently, so a
 * parse that half-understood a tag must decline rather than report an absence it cannot see.
 */
class Id3TagsTest {

    // ── builders ─────────────────────────────────────────────────────────────────────────────

    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    private fun be32(v: Long) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun syncsafe(v: Int) = byteArrayOf(
        ((v ushr 21) and 0x7F).toByte(),
        ((v ushr 14) and 0x7F).toByte(),
        ((v ushr 7) and 0x7F).toByte(),
        (v and 0x7F).toByte(),
    )

    /** A v2.4 frame; sizes are syncsafe there and plain big-endian in v2.3. */
    private fun frame(id: String, body: ByteArray, major: Int = 4, formatFlags: Int = 0): ByteArray =
        ascii(id) +
            (if (major == 4) syncsafe(body.size) else be32(body.size.toLong())) +
            byteArrayOf(0, formatFlags.toByte()) +
            body

    /** ISO-8859-1 text frame body: one encoding byte then the string. */
    private fun text(s: String) = byteArrayOf(0) + s.toByteArray(Charsets.ISO_8859_1)

    private fun tag(vararg frames: ByteArray, major: Int = 4, flags: Int = 0, padding: Int = 0): ByteArray {
        val body = frames.fold(ByteArray(0)) { a, b -> a + b } + ByteArray(padding)
        return ascii("ID3") + byteArrayOf(major.toByte(), 0, flags.toByte()) +
            syncsafe(body.size) + body
    }

    /** Serves everything from one array, as a file would. */
    private fun fetcherFor(all: ByteArray): (Long, Int) -> ByteArray? = { pos, len ->
        if (pos >= all.size) null
        else all.copyOfRange(pos.toInt(), minOf(all.size, pos.toInt() + len))
    }

    private fun read(all: ByteArray, headBytes: Int = Int.MAX_VALUE): Id3Tags.Tags? {
        val head = all.copyOfRange(0, minOf(all.size, headBytes))
        return Id3Tags.read(head, fetcherFor(all))
    }

    private fun chapFrame(
        elementId: String,
        startMs: Long,
        title: String?,
        major: Int = 4,
    ): ByteArray {
        val body = ascii(elementId) + byteArrayOf(0) +
            be32(startMs) + be32(startMs + 1000) + be32(0) + be32(0) +
            (title?.let { frame("TIT2", text(it), major) } ?: ByteArray(0))
        return frame("CHAP", body, major)
    }

    // ── genre ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `reads a plain genre`() {
        val tags = read(tag(frame("TCON", text("Hörbuch"))))
        assertEquals("Hörbuch", tags?.genre)
    }

    @Test
    fun `a multi-word genre survives intact`() {
        // Truncating at the first space instead of the terminator is an easy mistake to make.
        assertEquals("Hörspiel für Kinder", read(tag(frame("TCON", text("Hörspiel für Kinder"))))?.genre)
    }

    @Test
    fun `a numeric genre code is resolved`() {
        assertEquals("Vocal", read(tag(frame("TCON", text("(28)"))))?.genre)
        assertEquals("Vocal", read(tag(frame("TCON", text("28"))))?.genre)
    }

    @Test
    fun `a tag with no genre frame parses and reports none`() {
        // Not the same as failing: this is how "this book has no genre" gets settled for good.
        val tags = read(tag(frame("TIT2", text("Some title"))))
        assertNotNull(tags)
        assertNull(tags!!.genre)
        assertTrue(tags.chapters.isEmpty())
    }

    @Test
    fun `utf-8 and utf-16 text frames decode`() {
        val utf8 = byteArrayOf(3) + "Hörbuch".toByteArray(Charsets.UTF_8)
        assertEquals("Hörbuch", read(tag(frame("TCON", utf8)))?.genre)
        val utf16 = byteArrayOf(1) + "Hörbuch".toByteArray(Charsets.UTF_16)
        assertEquals("Hörbuch", read(tag(frame("TCON", utf16)))?.genre)
    }

    // ── v2.3 vs v2.4 sizes ───────────────────────────────────────────────────────────────────

    @Test
    fun `v2_3 frame sizes are plain big-endian, not syncsafe`() {
        // A 200-byte frame differs between the two encodings, so reading one as the other walks
        // off to the wrong offset and the whole tag is misread.
        val padded = text("Hörbuch") + ByteArray(190)
        val v23 = tag(frame("TCON", padded, major = 3), frame("TIT2", text("t"), major = 3), major = 3)
        assertEquals("Hörbuch", read(v23)?.genre)
    }

    // ── chapters ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `reads chapters in start order with their titles`() {
        val tags = read(
            tag(
                chapFrame("ch2", 60_000, "Second"),
                chapFrame("ch1", 0, "First"),
                frame("TCON", text("Hörbuch")),
            ),
        )
        assertNotNull(tags)
        assertEquals(listOf(0L, 60_000L), tags!!.chapters.map { it.startMs })
        assertEquals(listOf("First", "Second"), tags.chapters.map { it.title })
        assertEquals("Hörbuch", tags.genre)
    }

    @Test
    fun `a chapter without a title still counts`() {
        val tags = read(tag(chapFrame("ch1", 5_000, null)))
        assertEquals(listOf(5_000L), tags?.chapters?.map { it.startMs })
        assertNull(tags!!.chapters.single().title)
    }

    @Test
    fun `duplicate chapter starts collapse`() {
        val tags = read(tag(chapFrame("a", 0, "One"), chapFrame("b", 0, "Two")))
        assertEquals(1, tags?.chapters?.size)
    }

    // ── stepping over the cover ──────────────────────────────────────────────────────────────

    @Test
    fun `a huge picture frame is stepped over, not fetched`() {
        val apic = frame("APIC", ByteArray(300_000) { 0x7F })
        val all = tag(apic, frame("TCON", text("Hörbuch")), chapFrame("ch1", 0, "First"))
        var fetchedBytes = 0L
        val head = all.copyOfRange(0, 8 * 1024)
        val tags = Id3Tags.read(head) { pos, len ->
            fetchedBytes += len
            if (pos >= all.size) null else all.copyOfRange(pos.toInt(), minOf(all.size, pos.toInt() + len))
        }
        assertEquals("Hörbuch", tags?.genre)
        assertEquals(listOf(0L), tags?.chapters?.map { it.startMs })
        // The point of the whole class: the 300 KB of art was never asked for.
        assertTrue("fetched $fetchedBytes bytes", fetchedBytes < 64 * 1024)
    }

    @Test
    fun `an absurdly large text frame is skipped rather than pulled down`() {
        // What MAX_FRAME_BYTES is for. A corrupt tag claiming a 1 MB genre must not turn a
        // metadata read into a download; the frame is stepped over and the rest still parses.
        val all = tag(frame("TCON", text("x") + ByteArray(1024 * 1024)), frame("TIT2", text("t")))
        var fetched = 0L
        val tags = Id3Tags.read(all.copyOfRange(0, 8 * 1024)) { pos, len ->
            fetched += len
            if (pos >= all.size) null else all.copyOfRange(pos.toInt(), minOf(all.size, pos.toInt() + len))
        }
        assertNotNull(tags)
        assertNull(tags!!.genre)
        assertTrue("fetched $fetched bytes", fetched < 64 * 1024)
    }

    @Test
    fun `a compressed or encrypted frame is skipped, not fatal`() {
        // v2.4 format flags: 0x08 compressed, 0x04 encrypted.
        val all = tag(frame("TXXX", ByteArray(32), formatFlags = 0x08), frame("TCON", text("Hörbuch")))
        assertEquals("Hörbuch", read(all)?.genre)
    }

    @Test
    fun `padding ends the walk`() {
        val tags = read(tag(frame("TCON", text("Hörbuch")), padding = 2048))
        assertEquals("Hörbuch", tags?.genre)
    }

    // ── refusals ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `declines anything it cannot walk`() {
        // No tag at all.
        assertNull(read(ByteArray(64)))
        // ID3v2.2: three-character ids and three-byte sizes.
        assertNull(read(tag(frame("TCON", text("Hörbuch")), major = 2)))
        // Unsynchronised (0x80) and extended header (0x40) both move the frames.
        assertNull(read(tag(frame("TCON", text("Hörbuch")), flags = 0x80)))
        assertNull(read(tag(frame("TCON", text("Hörbuch")), flags = 0x40)))
    }

    @Test
    fun `declines when a frame runs past the end of the tag`() {
        val good = tag(frame("TCON", text("Hörbuch")))
        // Claim a frame far larger than the tag actually holds.
        val broken = good.copyOf()
        val frameSizeAt = 10 + 4
        syncsafe(9_000_000).copyInto(broken, frameSizeAt)
        assertNull(Id3Tags.read(broken, fetcherFor(broken)))
    }

    @Test
    fun `declines when the bytes run out mid-tag`() {
        // A tag whose frames are past the head, and nothing can fetch more: an absence of
        // chapters here would be a guess, so it must refuse instead.
        val all = tag(frame("APIC", ByteArray(200_000)), chapFrame("ch1", 0, "First"))
        assertNull(Id3Tags.read(all.copyOfRange(0, 4096)) { _, _ -> null })
    }

    @Test
    fun `declines a frame id that is not an id`() {
        val all = tag(frame("TCON", text("Hörbuch")))
        all[10] = 0x01 // corrupt the first frame's id, but not to all-zero padding
        assertNull(Id3Tags.read(all, fetcherFor(all)))
    }
}
