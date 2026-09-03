package com.geozelot.homer.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pile's arithmetic.
 *
 * Worth asserting on because this geometry was written twice — once for the grid, once for the row —
 * and the two copies drifted immediately: one fanned by 6.5% of its box and the other by 3.6%, which
 * is obvious on a device and invisible in review.
 */
class CoverStackTest {

    private val cell = 111.dp
    private val pad = 4.dp
    private val steps = CoverStack.GridSteps

    // ── the size is the same whatever the pile holds ─────────────────────────────────────────

    @Test
    fun `the cover is the same size at every depth`() {
        // The whole reason room is reserved for the maximum: two series cards side by side must not
        // show covers of different sizes because one shelf happens to hold fewer books.
        val sizes = (0..steps.size).map { CoverStack.place(cell, pad, steps, it).cover }
        assertEquals(listOf(cell - pad * 2 - 16.dp), sizes.distinct())
    }

    @Test
    fun `the cover gives up the padding and the whole fan, and nothing else`() {
        assertEquals(111.dp - 8.dp - 16.dp, CoverStack.place(cell, pad, steps, 3).cover)
    }

    // ── the full pile fills the padded cell exactly ──────────────────────────────────────────

    @Test
    fun `a full pile reaches the padding on all four sides`() {
        val pile = CoverStack.place(cell, pad, steps, steps.size)
        val front = pile.positions.first()
        val deepest = pile.positions.last()
        assertEquals("front sits on the bottom inset", cell - pad, front.y + pile.cover)
        assertEquals("front sits on the left inset", pad, front.x)
        assertEquals("deepest reaches the right inset", cell - pad, deepest.x + pile.cover)
        assertEquals("deepest reaches the top inset", pad, deepest.y)
    }

    // ── the taper ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the gaps close as the pile recedes`() {
        val at = CoverStack.place(cell, pad, steps, 3).positions
        val gaps = at.zipWithNext { a, b -> b.x - a.x }
        assertEquals(listOf(8.dp, 5.dp, 3.dp), gaps)
        assertTrue("each gap smaller than the last", gaps.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun `the visible sliver is the FIRST step, not the average`() {
        // The number the whole design turns on: what you see is the nearest gap, what you pay is the
        // sum. A uniform fan costing the same 16dp would show 5.3dp here.
        val at = CoverStack.place(cell, pad, steps, 3).positions
        assertEquals(8.dp, at[1].x - at[0].x)
    }

    @Test
    fun `a shorter pile still shows the nearest gap, unchanged`() {
        // So the gap the eye compares between neighbouring cards is 8dp on every shelf, whatever
        // its depth.
        for (sheets in 1..steps.size) {
            val at = CoverStack.place(cell, pad, steps, sheets).positions
            assertEquals("sheets=$sheets", 8.dp, at[1].x - at[0].x)
        }
    }

    // ── centring ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a shorter pile is centred rather than anchored`() {
        // Anchored, a two-cover pile would leave a hole where the missing two would have been.
        val full = CoverStack.place(cell, pad, steps, 3).positions.first()
        val short = CoverStack.place(cell, pad, steps, 1).positions.first()
        assertTrue("a short pile starts further in", short.x > full.x)
        val pile = CoverStack.place(cell, pad, steps, 1)
        // The pile's bounding box runs from the FRONT cover's left edge to the DEEPEST one's right,
        // so those are the two edges whose clearance has to match — not the deepest cover's own.
        val left = pile.positions.first().x - pad
        val right = (cell - pad) - (pile.positions.last().x + pile.cover)
        assertEquals("the air left over is split evenly", left, right)
    }

    @Test
    fun `a single cover is centred in the whole reserved band`() {
        val pile = CoverStack.place(cell, pad, steps, 0)
        val only = pile.positions.single()
        assertEquals(pad + 8.dp, only.x)
        assertEquals(only.x, only.y)
    }

    // ── the shape of the answer ──────────────────────────────────────────────────────────────

    @Test
    fun `there is one position per cover, front first`() {
        for (sheets in 0..steps.size) {
            assertEquals(sheets + 1, CoverStack.place(cell, pad, steps, sheets).positions.size)
        }
    }

    @Test
    fun `every cover behind the front is up and to the right of it`() {
        val at = CoverStack.place(cell, pad, steps, 3).positions
        val front = at.first()
        for (behind in at.drop(1)) {
            assertTrue(behind.x > front.x)
            assertTrue(behind.y < front.y)
        }
    }

    @Test
    fun `asking for more covers than there are steps is clamped`() {
        // A caller cannot place a cover it has no step for.
        val pile = CoverStack.place(cell, pad, steps, 99)
        assertEquals(steps.size + 1, pile.positions.size)
        assertEquals(CoverStack.place(cell, pad, steps, steps.size).positions, pile.positions)
    }

    @Test
    fun `a negative count is clamped too`() {
        assertEquals(1, CoverStack.place(cell, pad, steps, -3).positions.size)
    }

    // ── the row uses the same arithmetic ─────────────────────────────────────────────────────

    @Test
    fun `the row fan tapers on the same rule and costs what the flat one did`() {
        // 4 + 2 is the 6dp the untapered 3 + 3 cost, redistributed so the visible gap grows.
        assertEquals(6.dp, CoverStack.RowSteps.fold(0.dp) { a, b -> a + b })
        assertEquals(4.dp, CoverStack.RowSteps.first())
        assertTrue(CoverStack.RowSteps.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun `a row pile fills its own box`() {
        val box = 46.dp
        val rowPad = 2.dp
        val pile = CoverStack.place(box, rowPad, CoverStack.RowSteps, CoverStack.RowSteps.size)
        assertEquals(box - rowPad * 2 - 6.dp, pile.cover)
        assertEquals(box - rowPad, pile.positions.first().y + pile.cover)
        assertEquals(box - rowPad, pile.positions.last().x + pile.cover)
    }
}
