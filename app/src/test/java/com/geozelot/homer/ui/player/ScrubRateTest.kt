package com.geozelot.homer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The band mapping behind precision scrubbing.
 *
 * Small, and worth pinning anyway: the bands are declared as one list and read by a `firstOrNull`
 * whose comparison decides what a boundary belongs to. Getting that backwards makes the rate flip
 * a pixel early or a pixel late — invisible in review, and exactly the kind of thing that turns a
 * deliberate half-speed drag into a full-speed one at the moment somebody is aiming.
 */
class ScrubRateTest {

    /** The real bands, in the units the gesture works in (already converted from dp). */
    private val bands = listOf(40f to 1f, 100f to 0.5f)

    @Test
    fun `a finger still on the bar scrubs at full rate`() {
        assertEquals(1f, scrubRateFor(0f, bands))
        assertEquals(1f, scrubRateFor(39f, bands))
    }

    @Test
    fun `a band owns its own boundary`() {
        // Inclusive at the top: 40 is the last distance that is still a plain scrub.
        assertEquals(1f, scrubRateFor(40f, bands))
        assertEquals(0.5f, scrubRateFor(40.01f, bands))
        assertEquals(0.5f, scrubRateFor(100f, bands))
    }

    @Test
    fun `past the last band the rate is the finest one`() {
        assertEquals(ScrubFinestRate, scrubRateFor(100.01f, bands))
        assertEquals(ScrubFinestRate, scrubRateFor(10_000f, bands))
    }

    @Test
    fun `the rate never widens again however far the finger goes`() {
        // Monotonic: further from the bar is never coarser. A reader who drags down and keeps
        // going must not find the scrub speeding back up.
        var previous = Float.MAX_VALUE
        var dy = 0f
        while (dy < 400f) {
            val rate = scrubRateFor(dy, bands)
            assert(rate <= previous) { "rate widened at dy=$dy: $previous -> $rate" }
            previous = rate
            dy += 1f
        }
    }
}
