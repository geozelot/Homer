package com.geozelot.homer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a skip lands in a book that is several files.
 *
 * The failure this exists for is quiet: a skip-back near the top of a chapter went to that
 * chapter's first frame and looked like it had worked. Nothing was broken on screen — the twenty
 * seconds being reached for were simply not where the player went.
 */
class SeekWalkTest {

    /** Three ten-minute chapters. */
    private val ten = 600_000L
    private val even: (Int) -> Long = { ten }

    private fun walk(index: Int, positionMs: Long, deltaMs: Long, count: Int = 3, durations: (Int) -> Long = even) =
        seekTarget(index, positionMs, deltaMs, count, durations)

    @Test
    fun `a seek that fits stays where it is`() {
        assertEquals(SeekTarget(1, 70_000), walk(1, 100_000, -30_000))
        assertEquals(SeekTarget(1, 130_000), walk(1, 100_000, 30_000))
    }

    @Test
    fun `back across a boundary lands the leftover at the end of the chapter before`() {
        // Ten seconds in, thirty back: twenty seconds from the end of the previous chapter.
        assertEquals(SeekTarget(0, ten - 20_000), walk(1, 10_000, -30_000))
    }

    @Test
    fun `forward across a boundary lands the leftover after the start of the next`() {
        assertEquals(SeekTarget(2, 20_000), walk(1, ten - 10_000, 30_000))
    }

    @Test
    fun `a delta larger than a whole chapter keeps going`() {
        assertEquals(SeekTarget(0, 60_000), walk(2, 60_000, -(ten * 2)))
    }

    @Test
    fun `the first chapter's start and the last chapter's end are the ends of the book`() {
        assertEquals(SeekTarget(0, 0), walk(0, 5_000, -30_000))
        assertEquals(SeekTarget(2, ten), walk(2, ten - 5_000, 30_000))
    }

    @Test
    fun `an unmeasured neighbour stops the walk instead of being treated as empty`() {
        // Zero means "nobody has measured this". Crossing it as if it were an instant would let
        // one skip run through every unmeasured chapter to the start of the book.
        val unmeasuredFirst: (Int) -> Long = { if (it == 0) 0L else ten }
        assertEquals(SeekTarget(1, 0), walk(1, 10_000, -30_000, durations = unmeasuredFirst))
    }

    @Test
    fun `an unmeasured chapter cannot be walked out of forwards either`() {
        val unmeasuredHere: (Int) -> Long = { if (it == 1) 0L else ten }
        // No length means no known end, so the position is kept rather than clamped to nothing.
        assertEquals(SeekTarget(1, 630_000), walk(1, ten, 30_000, durations = unmeasuredHere))
    }

    @Test
    fun `a single-file book behaves exactly as it always did`() {
        assertEquals(SeekTarget(0, 0), walk(0, 10_000, -30_000, count = 1))
        assertEquals(SeekTarget(0, ten), walk(0, ten - 5_000, 30_000, count = 1))
    }
}
