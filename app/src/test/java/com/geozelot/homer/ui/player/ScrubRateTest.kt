package com.geozelot.homer.ui.player

import com.geozelot.homer.ui.player.PrecisionBands
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The band mapping behind precision scrubbing.
 *
 * Small, and worth pinning anyway: the bands are declared as one list and read by a `firstOrNull`
 * whose comparison decides what a boundary belongs to. Getting that backwards makes the rate flip
 * a pixel early or a pixel late — invisible in review, and exactly the kind of thing that turns a
 * deliberate half-speed drag into a full-speed one at the moment somebody is aiming.
 *
 * It reads the REAL [PrecisionBands] rather than a copy. A copy passed whatever the production list
 * said — retune the distances, add a third band, reorder them so `firstOrNull` matches the wrong
 * entry, and every assertion below would still have gone green.
 */
class ScrubRateTest {

    /**
     * The production bands, in the units the gesture works in.
     *
     * `Dp.value` at density 1 stands in for the `toPx()` the gesture does, which keeps the numbers
     * below readable as the dp they are written as and needs no Density in a JVM test.
     */
    private val bands = PrecisionBands.map { (distance, rate) -> distance.value to rate }

    /** The first band's distance, whatever it is set to — the boundary the assertions turn on. */
    private val firstBand = bands.first().first
    private val secondBand = bands[1].first

    @Test
    fun `a finger still on the bar scrubs at full rate`() {
        assertEquals(1f, scrubRateFor(0f, bands))
        assertEquals(1f, scrubRateFor(firstBand - 1f, bands))
    }

    @Test
    fun `a band owns its own boundary`() {
        // Inclusive at the top: the first band's own distance is the last plain scrub.
        assertEquals(bands[0].second, scrubRateFor(firstBand, bands))
        assertEquals(bands[1].second, scrubRateFor(firstBand + 0.01f, bands))
        assertEquals(bands[1].second, scrubRateFor(secondBand, bands))
    }

    @Test
    fun `past the last band the rate is the finest one`() {
        assertEquals(ScrubFinestRate, scrubRateFor(secondBand + 0.01f, bands))
        assertEquals(ScrubFinestRate, scrubRateFor(10_000f, bands))
    }

    @Test
    fun `the bands are ordered nearest-first, which is what firstOrNull assumes`() {
        // scrubRateFor takes the FIRST band the distance falls inside. A list out of order would
        // silently answer with the wrong band for every distance past the smallest.
        assertEquals(bands.sortedBy { it.first }, bands)
        assertEquals(bands.sortedByDescending { it.second }, bands)
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
