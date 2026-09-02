package com.geozelot.homer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What counts as a shake.
 *
 * These exist because the bug they describe shipped: the sleep timer extended itself with nobody
 * touching the phone, and the countdown consequently never reached zero. The rule was one line —
 * fire above 2.7g — living inside a `SensorEventListener`, where a `SensorEvent` cannot be
 * constructed and so nothing could be asserted about it.
 *
 * The first test is the whole point. Everything else guards the ways a weaker rule could let an
 * impact back through.
 */
class ShakeGestureTest {

    private val rest = 1.0f
    private val hard = 4.0f
    private val past = ShakeGesture.SHAKE_THRESHOLD_G + 0.1f

    /** Feeds one reading and reports whether it completed a shake. */
    private fun ShakeGesture.at(t: Long, g: Float) = onMagnitude(g, t)

    /** Two excursions with a return to rest between them — a shake. */
    private fun ShakeGesture.shake(t: Long, g: Float = hard): Boolean {
        at(t, g)
        at(t + 60, rest)
        return at(t + 120, g)
    }

    // ── the bug ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `one hard knock is not a shake, however hard`() {
        // Setting the phone down. This is the reading that used to add fifteen minutes to a running
        // sleep timer, and it is the whole of the reported defect.
        val g = ShakeGesture()
        assertFalse(g.at(1_000, 9.0f))
    }

    @Test
    fun `a knock spread over several samples is still one knock`() {
        // The trap a weaker rule falls into: consecutive readings above the bar are ONE excursion,
        // not several. Without the settle requirement this passes and the bug is back.
        val g = ShakeGesture()
        assertFalse(g.at(1_000, 5.0f))
        assertFalse(g.at(1_020, 6.0f))
        assertFalse(g.at(1_040, 4.5f))
        assertFalse(g.at(1_060, 5.5f))
    }

    @Test
    fun `a run of knocks minutes apart never adds up`() {
        // Carrying the phone: an occasional jolt, none of them near each other.
        val g = ShakeGesture()
        for (i in 0..20) {
            assertFalse("jolt $i", g.shakeless(60_000L * i))
        }
    }

    private fun ShakeGesture.shakeless(t: Long): Boolean {
        at(t, 5.0f)
        return at(t + 300, rest)
    }

    // ── what IS a shake ──────────────────────────────────────────────────────────────────────

    @Test
    fun `two excursions inside the window are a shake`() {
        assertTrue(ShakeGesture().shake(1_000))
    }

    @Test
    fun `it fires exactly once per gesture`() {
        val g = ShakeGesture()
        assertTrue(g.shake(1_000))
        // Still being shaken, within the debounce.
        assertFalse(g.shake(1_400))
    }

    @Test
    fun `a later shake fires again once the debounce has passed`() {
        val g = ShakeGesture()
        assertTrue(g.shake(1_000))
        assertTrue(g.shake(1_000 + ShakeGesture.SHAKE_DEBOUNCE_MS + 200))
    }

    // ── the boundaries ───────────────────────────────────────────────────────────────────────

    @Test
    fun `just under the bar is not a crossing`() {
        val g = ShakeGesture()
        val under = ShakeGesture.SHAKE_THRESHOLD_G - 0.1f
        assertFalse(g.at(1_000, under))
        assertFalse(g.at(1_060, rest))
        assertFalse(g.at(1_120, under))
    }

    @Test
    fun `just over the bar twice is a shake`() {
        assertTrue(ShakeGesture().shake(1_000, past))
    }

    @Test
    fun `excursions too far apart are not one gesture`() {
        val g = ShakeGesture()
        g.at(1_000, hard)
        g.at(1_060, rest)
        assertFalse(g.at(1_000 + ShakeGesture.SHAKE_WINDOW_MS + 100, hard))
    }

    @Test
    fun `a second excursion without settling in between does not count`() {
        // Never drops below the settle bar, so it is one long excursion.
        val g = ShakeGesture()
        assertFalse(g.at(1_000, hard))
        assertFalse(g.at(1_100, ShakeGesture.SETTLE_THRESHOLD_G + 0.1f))
        assertFalse(g.at(1_200, hard))
    }

    @Test
    fun `settling below the bar but above rest still counts as settled`() {
        // The settle bar is 1.6g, not 1g: a phone in a hand is never perfectly still, and requiring
        // true rest between excursions would make a real shake impossible to perform.
        val g = ShakeGesture()
        g.at(1_000, hard)
        g.at(1_060, 1.4f)
        assertTrue(g.at(1_120, hard))
    }

    // ── starting over ────────────────────────────────────────────────────────────────────────

    @Test
    fun `reset abandons a gesture in progress`() {
        // What `start()` does, so a timer armed later does not inherit half a shake from before.
        val g = ShakeGesture()
        g.at(1_000, hard)
        g.at(1_060, rest)
        g.reset()
        assertFalse(g.at(1_120, hard))
    }

    @Test
    fun `an abandoned gesture does not pair with a much later crossing`() {
        val g = ShakeGesture()
        g.at(1_000, hard)
        // Long stretch at rest — the gesture times out rather than waiting for a partner.
        for (i in 1..10) g.at(1_000 + 200L * i, rest)
        assertFalse(g.at(5_000, hard))
    }
}
