package com.geozelot.homer.ui.settings

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The display size's arithmetic: what a stored value becomes, and what the activity is told about
 * its screen at a given size. The part that runs on a device — the recreate and the context wrap —
 * is not reachable from a JVM test.
 */
class DisplayScaleTest {

    private fun phone() = Configuration().apply {
        densityDpi = 420
        screenWidthDp = 411
        screenHeightDp = 890
        smallestScreenWidthDp = 411
    }

    @Test
    fun `a stored value lands on the slider's own steps`() {
        assertEquals(100, DisplayScale.snap(100))
        assertEquals(100, DisplayScale.snap(102))
        assertEquals(105, DisplayScale.snap(103))
        assertEquals(DisplayScale.MIN, DisplayScale.snap(10))
        assertEquals(DisplayScale.MAX, DisplayScale.snap(500))
    }

    @Test
    fun `a larger size is a higher density on a screen fewer dp across`() {
        val scaled = DisplayScale.scaled(phone(), 130)

        assertEquals(546, scaled.densityDpi)
        // 411dp at 420dpi is 1078px, which at 546dpi is 316.15dp.
        assertEquals(316, scaled.screenWidthDp)
        assertEquals(684, scaled.screenHeightDp)
        assertEquals(316, scaled.smallestScreenWidthDp)
    }

    @Test
    fun `a smaller size is the other way round`() {
        val scaled = DisplayScale.scaled(phone(), 80)

        assertEquals(336, scaled.densityDpi)
        assertEquals(513, scaled.screenWidthDp)
    }

    @Test
    fun `the screen is never reported wider than it is`() {
        val source = phone()
        for (percent in DisplayScale.MIN..DisplayScale.MAX step DisplayScale.STEP) {
            val scaled = DisplayScale.scaled(source, percent)
            // Compared in pixels, which is the one unit the two configurations share.
            assertTrue(
                "at $percent%",
                scaled.screenWidthDp * scaled.densityDpi <= source.screenWidthDp * source.densityDpi,
            )
            assertTrue(
                "at $percent%",
                scaled.screenHeightDp * scaled.densityDpi <= source.screenHeightDp * source.densityDpi,
            )
        }
    }

    @Test
    fun `a size the system has not reported stays unreported`() {
        val source = phone().apply { screenHeightDp = Configuration.SCREEN_HEIGHT_DP_UNDEFINED }

        assertEquals(Configuration.SCREEN_HEIGHT_DP_UNDEFINED, DisplayScale.scaled(source, 120).screenHeightDp)
    }
}
