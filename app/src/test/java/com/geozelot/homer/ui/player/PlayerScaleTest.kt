package com.geozelot.homer.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player is fixed in `dp` below the cover, and a `dp` is a physical size — so on a small or
 * low-density screen the control cluster measured exactly what it measures on a large one, took
 * more room than the viewport had, and pushed the transport below the fold. These are the sizes it
 * has to survive.
 */
class PlayerScaleTest {

    @Test
    fun `a large phone is drawn at full size`() {
        assertEquals(1f, playerScale(viewportHeight = 800.dp, viewportWidth = 400.dp), 0.001f)
    }

    @Test
    fun `a 360dp phone is held back by its WIDTH, not its height`() {
        // The transport is 348dp of round controls that cannot wrap, and 360dp minus the screen's
        // own 22dp margins is 316dp. Width binds here long before height does.
        val scale = playerScale(viewportHeight = 800.dp, viewportWidth = 316.dp)
        assertTrue("expected width to bind, got $scale", scale < 1f)
        assertTrue("transport must fit", 348f * scale <= 316f + 0.5f)
    }

    @Test
    fun `a short viewport shrinks the cluster`() {
        assertTrue(playerScale(viewportHeight = 620.dp, viewportWidth = 400.dp) <= 0.8f)
    }

    @Test
    fun `a mid-sized phone is shrunk noticeably, not marginally`() {
        // The case that was still wrong after the cover started taking the leftover: everything
        // fitted, and the transport was simply too large for the screen it was on.
        val scale = playerScale(viewportHeight = 700.dp, viewportWidth = 400.dp)
        assertTrue("expected a real reduction, got $scale", scale <= 0.9f)
        assertTrue("play button should come in under 76dp", 84f * scale <= 76f)
    }

    @Test
    fun `the smallest supported screen still gets usable targets`() {
        // At the floor the play button is still 58dp and the skip buttons 36dp inside a 48dp tap
        // area. Below this the controls stop being reliably hittable, which is worse than scrolling.
        val scale = playerScale(viewportHeight = 480.dp, viewportWidth = 280.dp)
        assertTrue("floor is 0.7, got $scale", scale >= 0.7f)
        assertTrue("play button stays big enough", 84f * scale >= 55f)
    }

    @Test
    fun `scale never exceeds one, however large the screen`() {
        assertEquals(1f, playerScale(viewportHeight = 1600.dp, viewportWidth = 1200.dp), 0.001f)
    }
}
