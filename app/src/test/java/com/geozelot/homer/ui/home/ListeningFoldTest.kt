package com.geozelot.homer.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The listening panel's fold rules.
 *
 * These exist because the first version of this logic lived in a nested-scroll callback, read
 * correctly, compiled, and did not work on a device — and nothing could be asserted about it without
 * one. Every rule the panel is supposed to obey is now a test.
 */
class ListeningFoldTest {

    private val threshold = 64f

    /** One finger-drag delta. Positive travels back towards the top of the list. */
    private fun ListeningFold.drag(dy: Float, atTop: Boolean = true) =
        onScroll(dy, atTop = atTop, fromUser = true, threshold = threshold)

    /** The same, as fling momentum rather than a finger. */
    private fun ListeningFold.fling(dy: Float, atTop: Boolean = true) =
        onScroll(dy, atTop = atTop, fromUser = false, threshold = threshold)

    // ── where it starts ──────────────────────────────────────────────────────────────────────

    @Test
    fun `it starts expanded`() {
        assertTrue(ListeningFold().expanded)
    }

    // ── folding: automatic, every time ───────────────────────────────────────────────────────

    @Test
    fun `scrolling down into the library folds it`() {
        val fold = ListeningFold()
        fold.drag(-30f)
        assertFalse(fold.expanded)
    }

    @Test
    fun `it folds again on a later scroll, not only the first`() {
        val fold = ListeningFold()
        fold.drag(-30f)
        fold.onGestureEnd()
        fold.onPanelTapped()
        assertTrue(fold.expanded)
        // Scrolling down a second time must fold it again — there is no once-only latch.
        fold.drag(-30f, atTop = false)
        assertFalse(fold.expanded)
    }

    @Test
    fun `a fling downward folds it too`() {
        val fold = ListeningFold()
        fold.fling(-30f)
        assertFalse(fold.expanded)
    }

    @Test
    fun `search opening folds it, every time`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        assertFalse(fold.expanded)
        fold.onPanelTapped()
        fold.onSearchOpened()
        assertFalse(fold.expanded)
    }

    // ── unfolding: only ever asked for ───────────────────────────────────────────────────────

    @Test
    fun `a tap unfolds it`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        fold.onPanelTapped()
        assertTrue(fold.expanded)
    }

    @Test
    fun `a tap never folds it`() {
        val fold = ListeningFold()
        fold.onPanelTapped()
        assertTrue(fold.expanded)
    }

    @Test
    fun `pulling down past the threshold at the top unfolds it`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        fold.drag(40f, atTop = true)
        assertFalse("not yet — under the threshold", fold.expanded)
        fold.drag(40f, atTop = true)
        assertTrue(fold.expanded)
    }

    @Test
    fun `a pull short of the threshold leaves it folded`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        fold.drag(20f)
        fold.drag(20f)
        assertFalse(fold.expanded)
    }

    @Test
    fun `reversing mid-pull discards what was accumulated`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        fold.drag(60f)
        fold.drag(-5f)
        // Would have been over the line on the sum; is not, because the pull was abandoned.
        fold.drag(60f)
        assertFalse(fold.expanded)
    }

    // ── the rule that makes it feel deliberate ───────────────────────────────────────────────

    @Test
    fun `a gesture that began below the top can never unfold it, however far it carries`() {
        val fold = ListeningFold()
        fold.drag(-30f)
        fold.onGestureEnd()
        // One gesture: scrolls back to the top, then keeps pulling well past it.
        fold.drag(30f, atTop = false)
        fold.drag(200f, atTop = true)
        assertFalse("the trip home must not also open the panel", fold.expanded)
    }

    @Test
    fun `the NEXT gesture, starting at the top, does unfold it`() {
        val fold = ListeningFold()
        fold.drag(-30f)
        fold.onGestureEnd()
        fold.drag(30f, atTop = false)
        fold.drag(200f, atTop = true)
        assertFalse(fold.expanded)
        // Finger up, fling done — the refusal expires with the gesture that earned it.
        fold.onGestureEnd()
        fold.drag(70f, atTop = true)
        assertTrue(fold.expanded)
    }

    @Test
    fun `a fling can never unfold it, even from the top`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        fold.fling(500f, atTop = true)
        assertFalse("momentum must not open the panel", fold.expanded)
    }

    @Test
    fun `the fling phase of a refused gesture is still refused`() {
        // This is the bug that shipped once: clearing the refusal at the START of the fling handed
        // the blocked gesture's own momentum a clean slate, so a hard flick home opened the panel.
        val fold = ListeningFold()
        fold.drag(-30f)
        fold.onGestureEnd()
        fold.drag(30f, atTop = false)
        fold.fling(300f, atTop = true)
        assertFalse(fold.expanded)
    }

    @Test
    fun `pulling while already expanded changes nothing`() {
        val fold = ListeningFold()
        fold.drag(500f, atTop = true)
        assertTrue(fold.expanded)
    }

    @Test
    fun `folding disqualifies the gesture that did it`() {
        // Down and back up in ONE movement: the panel folds and must stay folded. Otherwise a
        // single flick both closes and reopens it, which looks like a bug whichever way it lands.
        val fold = ListeningFold()
        fold.drag(-30f, atTop = true)
        assertFalse(fold.expanded)
        fold.drag(200f, atTop = true)
        assertFalse("one gesture must not fold and then unfold", fold.expanded)
        // A fresh gesture from the top may of course open it.
        fold.onGestureEnd()
        fold.drag(70f, atTop = true)
        assertTrue(fold.expanded)
    }

    @Test
    fun `a downward delta below the top does not count as pull progress`() {
        // Scrolling up toward the top is positive too; only travel against the END counts.
        val fold = ListeningFold()
        fold.onSearchOpened()
        fold.drag(60f, atTop = false)
        fold.drag(60f, atTop = false)
        assertFalse(fold.expanded)
    }

    // ── what survives a rotation ─────────────────────────────────────────────────────────────

    @Test
    fun `only the fold state is saved, not the gesture in progress`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        fold.drag(-5f, atTop = false) // leaves a refusal armed

        val restored = ListeningFold.Saver.run { restore(false) }!!
        assertFalse(restored.expanded)
        // The refusal did NOT come back with it, so a pull at the top works immediately.
        restored.drag(70f, atTop = true)
        assertTrue(restored.expanded)
    }
}
