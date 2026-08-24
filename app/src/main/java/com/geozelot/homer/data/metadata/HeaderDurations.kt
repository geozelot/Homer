package com.geozelot.homer.data.metadata

/**
 * Duration arithmetic read straight out of a container header — no decoder, no player, no
 * streaming. Pure functions over bytes the caller has already fetched, so every rule here is
 * unit-testable without a device or a network.
 *
 * Why this exists: measuring a library was ~2.7s per file even after the decoder was removed,
 * because each probe was a whole WebDAV `GET` that ExoPlayer streamed until it happened to learn
 * the duration. A duration is a handful of bytes near the front of the file. Reading those bytes
 * directly turns the same question into one or two small ranged reads.
 *
 * **Everything here is allowed to answer "I don't know" and must do so rather than guess.** A null
 * costs a fallback to the slower probe; a wrong number silently corrupts a book's length, its
 * time-left and its finished state. That asymmetry decides every doubtful case below.
 */
internal object HeaderDurations {

    // ── ID3v2 ────────────────────────────────────────────────────────────────────────────────

    /**
     * Bytes of ID3v2 tag at the start of [head], or 0 if there is none.
     *
     * Audiobook MP3s routinely carry a cover image here, so this is often hundreds of kilobytes —
     * which is exactly why the caller must use it to seek to the audio rather than scan for it.
     */
    fun id3v2Length(head: ByteArray): Long {
        if (head.size < ID3_HEADER_BYTES) return 0
        if (head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return 0
        // Size is 4 syncsafe bytes: 7 bits each, high bit always clear.
        var size = 0L
        for (i in 6..9) {
            val b = head[i].toInt()
            if (b and 0x80 != 0) return 0 // not syncsafe → not a tag we understand
            size = (size shl 7) or (b and 0x7F).toLong()
        }
        val footer = if (head[5].toInt() and 0x10 != 0) ID3_HEADER_BYTES else 0
        return ID3_HEADER_BYTES + size + footer
    }

    // ── MPEG audio (MP3) ─────────────────────────────────────────────────────────────────────

    /** One MPEG audio frame header, decoded. */
    data class MpegFrame(
        val offset: Int,
        val bitrateBps: Int,
        val sampleRateHz: Int,
        val samplesPerFrame: Int,
        val frameLengthBytes: Int,
        val mono: Boolean,
        val mpeg1: Boolean,
    )

    /**
     * The first MPEG frame in [b] at or after [from], or null.
     *
     * A valid-looking header is not enough: two bytes of audio data in every kilobyte happen to
     * look like a sync word. A candidate is accepted only when the frame it describes is followed
     * by another frame that agrees on version, layer and sample rate — the standard confirmation,
     * and the difference between a reliable parse and an occasional nonsense duration.
     */
    fun findFrame(b: ByteArray, from: Int = 0, searchLimit: Int = Int.MAX_VALUE): MpegFrame? {
        val last = minOf(b.size - MPEG_HEADER_BYTES, from + searchLimit)
        var i = maxOf(0, from)
        while (i <= last) {
            val candidate = parseFrame(b, i)
            if (candidate != null && confirms(b, candidate)) return candidate
            i++
        }
        return null
    }

    /** Whether a second valid, consistent frame follows [frame]. Unknown (past the buffer) counts. */
    private fun confirms(b: ByteArray, frame: MpegFrame): Boolean {
        val next = frame.offset + frame.frameLengthBytes
        // The buffer ended before the next frame. Not evidence against — the caller reads a
        // window, and rejecting here would fail every frame near its edge.
        if (next + MPEG_HEADER_BYTES > b.size) return true
        val second = parseFrame(b, next) ?: return false
        return second.sampleRateHz == frame.sampleRateHz &&
            second.mpeg1 == frame.mpeg1 &&
            second.samplesPerFrame == frame.samplesPerFrame
    }

    /** Decodes a frame header at [o], or null when the bytes there are not one. */
    fun parseFrame(b: ByteArray, o: Int): MpegFrame? {
        if (o < 0 || o + MPEG_HEADER_BYTES > b.size) return null
        val b0 = b[o].toInt() and 0xFF
        val b1 = b[o + 1].toInt() and 0xFF
        val b2 = b[o + 2].toInt() and 0xFF
        val b3 = b[o + 3].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null

        val versionBits = (b1 shr 3) and 0x03
        if (versionBits == 1) return null // reserved
        val layerBits = (b1 shr 1) and 0x03
        if (layerBits == 0) return null // reserved
        val layer = 4 - layerBits // 1, 2 or 3
        val bitrateIndex = (b2 shr 4) and 0x0F
        if (bitrateIndex == 0 || bitrateIndex == 0x0F) return null // free-form or invalid
        val sampleRateIndex = (b2 shr 2) and 0x03
        if (sampleRateIndex == 3) return null // reserved
        val padding = (b2 shr 1) and 0x01
        val mono = ((b3 shr 6) and 0x03) == 3

        val mpeg1 = versionBits == 3
        val sampleRate = when (versionBits) {
            3 -> SAMPLE_RATES_MPEG1[sampleRateIndex]
            2 -> SAMPLE_RATES_MPEG2[sampleRateIndex]
            else -> SAMPLE_RATES_MPEG25[sampleRateIndex]
        }
        val bitrateKbps = when {
            mpeg1 -> when (layer) {
                1 -> BITRATES_M1_L1[bitrateIndex]
                2 -> BITRATES_M1_L2[bitrateIndex]
                else -> BITRATES_M1_L3[bitrateIndex]
            }
            layer == 1 -> BITRATES_M2_L1[bitrateIndex]
            else -> BITRATES_M2_L23[bitrateIndex]
        }
        if (bitrateKbps == 0 || sampleRate == 0) return null

        val samplesPerFrame = when {
            layer == 1 -> 384
            layer == 2 -> 1152
            mpeg1 -> 1152
            else -> 576
        }
        val bitrateBps = bitrateKbps * 1000
        val frameLength = if (layer == 1) {
            (12 * bitrateBps / sampleRate + padding) * 4
        } else {
            samplesPerFrame / 8 * bitrateBps / sampleRate + padding
        }
        if (frameLength <= MPEG_HEADER_BYTES) return null

        return MpegFrame(
            offset = o,
            bitrateBps = bitrateBps,
            sampleRateHz = sampleRate,
            samplesPerFrame = samplesPerFrame,
            frameLengthBytes = frameLength,
            mono = mono,
            mpeg1 = mpeg1,
        )
    }

    /**
     * Duration from a Xing/Info or VBRI header inside [frame], or null if it carries neither.
     *
     * This is the answer for a variable-bitrate file — the only one, short of decoding it — and
     * encoders write it for constant-bitrate files too ("Info"), where it is still exact.
     */
    fun vbrDurationMs(b: ByteArray, frame: MpegFrame): Long? {
        val sideInfo = when {
            frame.mpeg1 && frame.mono -> 17
            frame.mpeg1 -> 32
            frame.mono -> 9
            else -> 17
        }
        xingFrames(b, frame.offset + MPEG_HEADER_BYTES + sideInfo)?.let { frames ->
            return framesToMs(frames, frame)
        }
        // VBRI sits at a fixed offset instead, and only ever in MPEG1 (Fraunhofer's encoder).
        vbriFrames(b, frame.offset + MPEG_HEADER_BYTES + 32)?.let { frames ->
            return framesToMs(frames, frame)
        }
        return null
    }

    private fun framesToMs(frames: Long, frame: MpegFrame): Long? {
        if (frames <= 0) return null
        return frames * frame.samplesPerFrame * 1000L / frame.sampleRateHz
    }

    private fun xingFrames(b: ByteArray, o: Int): Long? {
        if (o < 0 || o + 12 > b.size) return null
        val tag = String(b, o, 4, Charsets.US_ASCII)
        if (tag != "Xing" && tag != "Info") return null
        val flags = u32be(b, o + 4)
        if (flags and 0x1L == 0L) return null // no frame count present
        return u32be(b, o + 8)
    }

    private fun vbriFrames(b: ByteArray, o: Int): Long? {
        if (o < 0 || o + 18 > b.size) return null
        if (String(b, o, 4, Charsets.US_ASCII) != "VBRI") return null
        return u32be(b, o + 14)
    }

    /**
     * Duration of a constant-bitrate stream of [audioBytes] at [frame]'s bitrate.
     *
     * Only sound when the bitrate really is constant, which the caller must establish by sampling
     * the file elsewhere — a variable-bitrate file with no Xing header would otherwise be reported
     * at whatever its first frame happened to use.
     */
    fun cbrDurationMs(audioBytes: Long, frame: MpegFrame): Long? {
        if (audioBytes <= 0 || frame.bitrateBps <= 0) return null
        return audioBytes * 8L * 1000L / frame.bitrateBps
    }

    // ── MP4 / M4A / M4B ──────────────────────────────────────────────────────────────────────

    /** Duration from an `mvhd` box's content (everything past its 8-byte box header). */
    fun mvhdDurationMs(content: ByteArray): Long? {
        if (content.size < 4) return null
        val version = content[0].toInt() and 0xFF
        val timescale: Long
        val duration: Long
        when (version) {
            0 -> {
                if (content.size < 20) return null
                timescale = u32be(content, 12)
                duration = u32be(content, 16)
                // 32-bit all-ones is the documented "unknown", not a 49-day book.
                if (duration == 0xFFFFFFFFL) return null
            }
            1 -> {
                if (content.size < 32) return null
                timescale = u32be(content, 20)
                duration = u64be(content, 24)
                if (duration == -1L) return null
            }
            else -> return null
        }
        if (timescale <= 0 || duration <= 0) return null
        return duration * 1000L / timescale
    }

    /** A box located inside a buffer already in hand; [contentEnd] is clamped to the buffer. */
    data class BufferBox(val contentStart: Int, val contentEnd: Int)

    /**
     * Finds box [type] among the boxes between [start] and [end] of [b], without any further I/O.
     *
     * The point is the common case: a "faststart" MP4 puts `moov` right after `ftyp`, and `mvhd`
     * is `moov`'s first child, so a single small read of the front of the file already contains
     * the duration. Returns null the moment the walk would need bytes the buffer does not hold —
     * the caller then falls back to ranged reads, which is the file with `moov` at the end.
     */
    fun findBoxInBuffer(b: ByteArray, start: Int, end: Int, type: String): BufferBox? {
        var cursor = start
        var guard = 0
        while (cursor + 8 <= end && guard++ < MAX_BOXES) {
            var size = u32be(b, cursor)
            var headerLen = 8
            if (size == 1L) {
                if (cursor + 16 > end) return null
                size = u64be(b, cursor + 8)
                headerLen = 16
            } else if (size == 0L) {
                size = (end - cursor).toLong() // "to the end of the container"
            }
            if (size < headerLen) return null
            val boxType = String(b, cursor + 4, 4, Charsets.US_ASCII)
            val contentEnd = cursor + size
            if (boxType == type) {
                return BufferBox(cursor + headerLen, minOf(contentEnd, end.toLong()).toInt())
            }
            // The next box starts past what we hold, so the walk cannot continue from here.
            if (contentEnd > end) return null
            cursor = contentEnd.toInt()
        }
        return null
    }

    /** Duration of an MP4 whose `moov/mvhd` is entirely inside [b], or null if it is not. */
    fun mvhdInBuffer(b: ByteArray): Long? {
        val moov = findBoxInBuffer(b, 0, b.size, "moov") ?: return null
        val mvhd = findBoxInBuffer(b, moov.contentStart, moov.contentEnd, "mvhd") ?: return null
        if (mvhd.contentEnd <= mvhd.contentStart) return null
        return mvhdDurationMs(b.copyOfRange(mvhd.contentStart, mvhd.contentEnd))
    }

    // ── FLAC ─────────────────────────────────────────────────────────────────────────────────

    /** Duration from a FLAC STREAMINFO block, which is always the first block after `fLaC`. */
    fun flacDurationMs(head: ByteArray): Long? {
        if (head.size < 4 + 4 + 18) return null
        if (String(head, 0, 4, Charsets.US_ASCII) != "fLaC") return null
        val blockType = head[4].toInt() and 0x7F
        if (blockType != 0) return null // STREAMINFO must come first; anything else is not FLAC
        // STREAMINFO payload starts at 8. Sample rate (20 bits) and total samples (36 bits) share
        // a 64-bit field starting 10 bytes in.
        val packed = u64be(head, 8 + 10)
        val sampleRate = (packed ushr 44).toInt() and 0xFFFFF
        val totalSamples = packed and 0xFFFFFFFFFL
        if (sampleRate <= 0 || totalSamples <= 0) return null // 0 = "unknown length"
        return totalSamples * 1000L / sampleRate
    }

    // ── WAV ──────────────────────────────────────────────────────────────────────────────────

    /** Duration from a RIFF/WAVE `fmt ` byte rate and `data` chunk size. */
    fun wavDurationMs(head: ByteArray): Long? {
        if (head.size < 12) return null
        if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(head, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var byteRate = 0L
        var i = 12
        var guard = 0
        while (i + 8 <= head.size && guard++ < MAX_RIFF_CHUNKS) {
            val id = String(head, i, 4, Charsets.US_ASCII)
            val size = u32le(head, i + 4)
            if (size < 0) return null
            val payload = i + 8
            when (id) {
                "fmt " -> if (payload + 12 <= head.size) byteRate = u32le(head, payload + 8)
                "data" -> {
                    if (byteRate <= 0) return null
                    // RF64 and streamed WAVs write a placeholder size; refuse rather than invent.
                    if (size <= 0 || size == 0xFFFFFFFFL) return null
                    return size * 1000L / byteRate
                }
            }
            // Chunks are word-aligned: an odd size is followed by a pad byte.
            i = payload + size.toInt() + (size.toInt() and 1)
        }
        return null
    }

    // ── bytes ────────────────────────────────────────────────────────────────────────────────

    private fun u32be(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun u32le(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

    private fun u64be(b: ByteArray, o: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = (v shl 8) or (b[o + k].toLong() and 0xFF)
        return v
    }

    const val ID3_HEADER_BYTES = 10L
    const val MPEG_HEADER_BYTES = 4
    private const val MAX_RIFF_CHUNKS = 64
    private const val MAX_BOXES = 256

    private val SAMPLE_RATES_MPEG1 = intArrayOf(44100, 48000, 32000, 0)
    private val SAMPLE_RATES_MPEG2 = intArrayOf(22050, 24000, 16000, 0)
    private val SAMPLE_RATES_MPEG25 = intArrayOf(11025, 12000, 8000, 0)

    private val BITRATES_M1_L1 =
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0)
    private val BITRATES_M1_L2 =
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0)
    private val BITRATES_M1_L3 =
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val BITRATES_M2_L1 =
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0)
    private val BITRATES_M2_L23 =
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
}
