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

    /**
     * One finger-drag delta. Positive travels back towards the top of the list.
     *
     * There used to be a `fling` helper beside this, passing `fromUser = false`. Momentum cannot
     * reach the fold any more — the input is raw pointer travel, so only a finger gets here — and the
     * flag it set went with it. The rule it asserted, that a flick home must not open the panel,
     * now holds because there is nothing for a flick to send.
     */
    private fun ListeningFold.drag(dy: Float, atTop: Boolean = true) =
        onScroll(dy, atTop = atTop, threshold = threshold)

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
        fold.onPanelTapped()
        assertTrue(fold.expanded)
        // Scrolling down a second time must fold it again — there is no once-only latch.
        fold.drag(-30f, atTop = false)
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
    fun `pulling while already expanded changes nothing`() {
        val fold = ListeningFold()
        fold.drag(500f, atTop = true)
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
    fun `scrolling home is not itself a pull`() {
        // Every delta of the trip back up is below the top, so none of it counts — the count starts
        // over each time rather than banking travel that was really just scrolling.
        val fold = ListeningFold()
        fold.drag(-30f)
        assertFalse(fold.expanded)
        repeat(10) { fold.drag(200f, atTop = false) }
        assertFalse("2000dp of scrolling home must not open it", fold.expanded)
    }

    @Test
    fun `travel either side of reaching the top does not add up`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        fold.drag(60f, atTop = false)
        fold.drag(20f, atTop = true)
        assertFalse("the 60 was scrolling, not pulling", fold.expanded)
    }

    @Test
    fun `pulling at the top opens it whatever came before`() {
        // The point of the rewrite: no state carried from an earlier gesture can leave this stuck.
        val fold = ListeningFold()
        fold.drag(-30f)
        fold.drag(30f, atTop = false)
        assertFalse(fold.expanded)
        fold.drag(70f, atTop = true)
        assertTrue(fold.expanded)
    }

    @Test
    fun `nothing needs a gesture to end for it to work again`() {
        // There is no end-of-gesture callback any more. Pulling works on the very next delta after
        // any amount of scrolling, which is what the missed onPostFling used to make impossible.
        val fold = ListeningFold()
        repeat(5) {
            fold.drag(-40f)
            fold.drag(40f, atTop = false)
        }
        fold.drag(70f, atTop = true)
        assertTrue(fold.expanded)
    }

    // ── what survives a rotation ─────────────────────────────────────────────────────────────

    @Test
    fun `only the fold state is saved`() {
        val fold = ListeningFold()
        fold.onSearchOpened()
        assertFalse(fold.expanded)
        val restored = ListeningFold.Saver.run { restore(false) }!!
        assertFalse(restored.expanded)
        restored.drag(70f, atTop = true)
        assertTrue(restored.expanded)
    }
}
