package com.geozelot.homer.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The header arithmetic, exercised on bytes built here rather than on real files.
 *
 * A wrong duration is worse than no duration — it corrupts a book's length, its time-left and its
 * finished state — so the negative cases (refusing to answer) matter at least as much as the
 * positive ones.
 */
class HeaderDurationsTest {

    // ── builders ─────────────────────────────────────────────────────────────────────────────

    /** MPEG1 Layer III, 128 kbps, 44100 Hz, stereo — the ordinary audiobook MP3 frame. */
    private fun frameHeader(
        bitrateIndex: Int = 9, // 128 kbps in the MPEG1 Layer III table
        srIndex: Int = 0, // 44100
        versionBits: Int = 3, // MPEG1
        layerBits: Int = 1, // Layer III
        mono: Boolean = false,
        padding: Int = 0,
    ): ByteArray = byteArrayOf(
        0xFF.toByte(),
        (0xE0 or (versionBits shl 3) or (layerBits shl 1) or 1).toByte(),
        ((bitrateIndex shl 4) or (srIndex shl 2) or (padding shl 1)).toByte(),
        (if (mono) 0xC0 else 0x00).toByte(),
    )

    /** A frame header padded out to its full frame length, so a following frame lands correctly. */
    private fun frame(header: ByteArray = frameHeader(), length: Int = 417): ByteArray =
        header + ByteArray(length - header.size)

    private fun be32(v: Long) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun le32(v: Long) = byteArrayOf(
        v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte(),
    )

    private fun be64(v: Long) = ByteArray(8) { (v ushr (56 - it * 8)).toByte() }

    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    // ── ID3v2 ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `no id3 tag means no offset`() {
        assertEquals(0L, HeaderDurations.id3v2Length(ByteArray(64)))
        assertEquals(0L, HeaderDurations.id3v2Length(frameHeader() + ByteArray(60)))
    }

    @Test
    fun `id3 size is syncsafe and includes the header`() {
        // 0x00 0x00 0x02 0x01 syncsafe = (2 << 7) | 1 = 257
        val head = ascii("ID3") + byteArrayOf(3, 0, 0, 0, 0, 2, 1) + ByteArray(32)
        assertEquals(10L + 257L, HeaderDurations.id3v2Length(head))
    }

    @Test
    fun `a footer flag adds another ten bytes`() {
        val head = ascii("ID3") + byteArrayOf(4, 0, 0x10, 0, 0, 2, 1) + ByteArray(32)
        assertEquals(10L + 257L + 10L, HeaderDurations.id3v2Length(head))
    }

    @Test
    fun `a size byte with its high bit set is not syncsafe and is refused`() {
        val head = ascii("ID3") + byteArrayOf(3, 0, 0, 0, 0, 0xFF.toByte(), 1) + ByteArray(32)
        assertEquals(0L, HeaderDurations.id3v2Length(head))
    }

    // ── MPEG frame headers ───────────────────────────────────────────────────────────────────

    @Test
    fun `parses an ordinary mpeg1 layer3 frame`() {
        val f = HeaderDurations.parseFrame(frameHeader(), 0)
        assertNotNull(f)
        assertEquals(128_000, f!!.bitrateBps)
        assertEquals(44_100, f.sampleRateHz)
        assertEquals(1152, f.samplesPerFrame)
        assertEquals(417, f.frameLengthBytes)
        assertTrue(f.mpeg1)
    }

    @Test
    fun `mpeg2 layer3 has half the samples per frame`() {
        // versionBits 2 = MPEG2, bitrate index 8 = 64 kbps, srIndex 0 = 22050
        val f = HeaderDurations.parseFrame(frameHeader(bitrateIndex = 8, versionBits = 2), 0)
        assertNotNull(f)
        assertEquals(64_000, f!!.bitrateBps)
        assertEquals(22_050, f.sampleRateHz)
        assertEquals(576, f.samplesPerFrame)
    }

    @Test
    fun `refuses reserved and free-form headers`() {
        // reserved version
        assertNull(HeaderDurations.parseFrame(frameHeader(versionBits = 1), 0))
        // reserved layer
        assertNull(HeaderDurations.parseFrame(frameHeader(layerBits = 0), 0))
        // free-form bitrate, and the invalid index
        assertNull(HeaderDurations.parseFrame(frameHeader(bitrateIndex = 0), 0))
        assertNull(HeaderDurations.parseFrame(frameHeader(bitrateIndex = 15), 0))
        // reserved sample rate
        assertNull(HeaderDurations.parseFrame(frameHeader(srIndex = 3), 0))
        // no sync word at all
        assertNull(HeaderDurations.parseFrame(ByteArray(8), 0))
    }

    // ── finding a frame in a buffer ──────────────────────────────────────────────────────────

    @Test
    fun `finds a frame after leading junk`() {
        val buffer = ByteArray(100) + frame() + frame()
        val f = HeaderDurations.findFrame(buffer)
        assertNotNull(f)
        assertEquals(100, f!!.offset)
    }

    @Test
    fun `a lone sync word that is not followed by a frame is rejected`() {
        // This is the case that produces nonsense durations: audio data contains byte pairs that
        // look exactly like a header. Only the following frame distinguishes them.
        val fake = frameHeader() + ByteArray(500) { 0x5A }
        assertNull(HeaderDurations.findFrame(fake))
    }

    @Test
    fun `a frame at the very end of the buffer is accepted without a successor`() {
        // The caller reads a window; refusing here would fail every frame near its edge.
        val buffer = ByteArray(16) + frameHeader()
        val f = HeaderDurations.findFrame(buffer)
        assertNotNull(f)
        assertEquals(16, f!!.offset)
    }

    @Test
    fun `a successor that disagrees on sample rate is not a confirmation`() {
        val buffer = frame() + frame(frameHeader(srIndex = 1)) // 48000 after 44100
        assertNull(HeaderDurations.findFrame(buffer, searchLimit = 1))
    }

    // ── VBR headers ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `xing frame count gives the duration`() {
        // MPEG1 stereo: side info is 32 bytes, so Xing sits 36 bytes into the frame.
        val body = ByteArray(32) + ascii("Xing") + be32(1) + be32(1000)
        val buffer = frameHeader() + body + ByteArray(417 - 4 - body.size) + frame()
        val f = HeaderDurations.findFrame(buffer)!!
        // 1000 frames * 1152 samples / 44100 Hz
        assertEquals(26_122L, HeaderDurations.vbrDurationMs(buffer, f))
    }

    @Test
    fun `an Info header counts the same as Xing`() {
        val body = ByteArray(32) + ascii("Info") + be32(1) + be32(1000)
        val buffer = frameHeader() + body + ByteArray(417 - 4 - body.size) + frame()
        val f = HeaderDurations.findFrame(buffer)!!
        assertEquals(26_122L, HeaderDurations.vbrDurationMs(buffer, f))
    }

    @Test
    fun `mono moves the xing header to the shorter side-info offset`() {
        val body = ByteArray(17) + ascii("Xing") + be32(1) + be32(1000)
        val header = frameHeader(mono = true)
        val buffer = header + body + ByteArray(417 - 4 - body.size) + frame(header)
        val f = HeaderDurations.findFrame(buffer)!!
        assertTrue(f.mono)
        assertEquals(26_122L, HeaderDurations.vbrDurationMs(buffer, f))
    }

    @Test
    fun `vbri is read at its own fixed offset`() {
        val body = ByteArray(32) + ascii("VBRI") + ByteArray(10) + be32(1000)
        val buffer = frameHeader() + body + ByteArray(417 - 4 - body.size) + frame()
        val f = HeaderDurations.findFrame(buffer)!!
        assertEquals(26_122L, HeaderDurations.vbrDurationMs(buffer, f))
    }

    @Test
    fun `a frame with no vbr header yields nothing`() {
        val buffer = frame() + frame()
        val f = HeaderDurations.findFrame(buffer)!!
        assertNull(HeaderDurations.vbrDurationMs(buffer, f))
    }

    @Test
    fun `a xing header claiming zero frames is refused`() {
        val body = ByteArray(32) + ascii("Xing") + be32(1) + be32(0)
        val buffer = frameHeader() + body + ByteArray(417 - 4 - body.size) + frame()
        val f = HeaderDurations.findFrame(buffer)!!
        assertNull(HeaderDurations.vbrDurationMs(buffer, f))
    }

    @Test
    fun `a xing header without the frames flag is refused`() {
        val body = ByteArray(32) + ascii("Xing") + be32(0b110) + be32(1000)
        val buffer = frameHeader() + body + ByteArray(417 - 4 - body.size) + frame()
        val f = HeaderDurations.findFrame(buffer)!!
        assertNull(HeaderDurations.vbrDurationMs(buffer, f))
    }

    // ── constant bitrate ─────────────────────────────────────────────────────────────────────

    @Test
    fun `cbr duration is bytes over bitrate`() {
        val f = HeaderDurations.parseFrame(frameHeader(), 0)!!
        assertEquals(62_500L, HeaderDurations.cbrDurationMs(1_000_000, f))
        assertNull(HeaderDurations.cbrDurationMs(0, f))
        assertNull(HeaderDurations.cbrDurationMs(-1, f))
    }

    // ── MP4 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `mvhd version 0`() {
        val content = byteArrayOf(0, 0, 0, 0) + be32(0) + be32(0) + be32(1000) + be32(123_456)
        assertEquals(123_456L, HeaderDurations.mvhdDurationMs(content))
    }

    @Test
    fun `mvhd version 1 uses 64-bit fields`() {
        val content = byteArrayOf(1, 0, 0, 0) + be64(0) + be64(0) + be32(44_100) + be64(1_323_000)
        assertEquals(30_000L, HeaderDurations.mvhdDurationMs(content))
    }

    @Test
    fun `mvhd refuses the unknown-duration sentinel and nonsense`() {
        val unknown = byteArrayOf(0, 0, 0, 0) + be32(0) + be32(0) + be32(1000) + be32(0xFFFFFFFFL)
        assertNull(HeaderDurations.mvhdDurationMs(unknown))
        val zeroScale = byteArrayOf(0, 0, 0, 0) + be32(0) + be32(0) + be32(0) + be32(500)
        assertNull(HeaderDurations.mvhdDurationMs(zeroScale))
        assertNull(HeaderDurations.mvhdDurationMs(byteArrayOf(9, 0, 0, 0) + ByteArray(32)))
        assertNull(HeaderDurations.mvhdDurationMs(ByteArray(2)))
    }

    // ── FLAC ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `flac streaminfo`() {
        val packed = (44_100L shl 44) or (1L shl 41) or (15L shl 36) or 441_000L
        val head = ascii("fLaC") + byteArrayOf(0, 0, 0, 34) + ByteArray(10) + be64(packed) +
            ByteArray(16)
        assertEquals(10_000L, HeaderDurations.flacDurationMs(head))
    }

    @Test
    fun `flac with an unknown sample count is refused`() {
        val packed = (44_100L shl 44) or (1L shl 41) or (15L shl 36) or 0L
        val head = ascii("fLaC") + byteArrayOf(0, 0, 0, 34) + ByteArray(10) + be64(packed) +
            ByteArray(16)
        assertNull(HeaderDurations.flacDurationMs(head))
        assertNull(HeaderDurations.flacDurationMs(ascii("OggS") + ByteArray(40)))
    }

    // ── WAV ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `wav data size over byte rate`() {
        val fmt = ascii("fmt ") + le32(16) +
            le32(0x00010002) + le32(44_100) + le32(176_400) + le32(0x00100004)
        val head = ascii("RIFF") + le32(0) + ascii("WAVE") + fmt + ascii("data") + le32(1_764_000)
        assertEquals(10_000L, HeaderDurations.wavDurationMs(head))
    }

    @Test
    fun `wav with a placeholder data size is refused`() {
        val fmt = ascii("fmt ") + le32(16) +
            le32(0x00010002) + le32(44_100) + le32(176_400) + le32(0x00100004)
        val head = ascii("RIFF") + le32(0) + ascii("WAVE") + fmt + ascii("data") + le32(0xFFFFFFFFL)
        assertNull(HeaderDurations.wavDurationMs(head))
        assertNull(HeaderDurations.wavDurationMs(ascii("RIFX") + ByteArray(40)))
    }

    @Test
    fun `wav without a fmt chunk before data is refused`() {
        val head = ascii("RIFF") + le32(0) + ascii("WAVE") + ascii("data") + le32(1_764_000)
        assertNull(HeaderDurations.wavDurationMs(head))
    }
}
