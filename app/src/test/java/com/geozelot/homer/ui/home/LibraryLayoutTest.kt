package com.geozelot.homer.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which layout a window size gets.
 *
 * Written against real window sizes rather than the thresholds themselves, because the thresholds
 * only mean anything as answers about actual devices — and because the rule is asymmetric in a way
 * that is easy to "simplify" back into a bug: being short is what starts the decision, but only
 * being short AND wide earns the rail.
 */
class LibraryLayoutTest {

    @Test
    fun `a phone in portrait stacks everything`() {
        assertEquals(LibraryLayout.STACKED, libraryLayoutFor(360.dp, 800.dp))
    }

    @Test
    fun `a phone in landscape gets the rail`() {
        // 800x360: the case this exists for. Stacked, the furniture came to ~355dp of 360.
        assertEquals(LibraryLayout.RAIL, libraryLayoutFor(800.dp, 360.dp))
    }

    @Test
    fun `the largest phones in landscape are still short`() {
        // 926x428 — a 6.7" phone. Comfortably above the tallest, comfortably below portrait.
        assertEquals(LibraryLayout.RAIL, libraryLayoutFor(926.dp, 428.dp))
    }

    @Test
    fun `a tablet stacks in both orientations`() {
        assertEquals(LibraryLayout.STACKED, libraryLayoutFor(1280.dp, 800.dp))
        assertEquals(LibraryLayout.STACKED, libraryLayoutFor(800.dp, 1280.dp))
    }

    @Test
    fun `an unfolded foldable is a tablet, not a wide phone`() {
        assertEquals(LibraryLayout.STACKED, libraryLayoutFor(841.dp, 674.dp))
    }

    @Test
    fun `a window both short and narrow folds the panel and keeps its top bar`() {
        // The asymmetry, and the reason this is not one threshold. A split-screen half has the same
        // height problem, but neither the width to put a rail beside the grid nor the width to
        // carry six controls on one row — so it does the only thing left and folds.
        val layout = libraryLayoutFor(360.dp, 400.dp)
        assertEquals(LibraryLayout.STACKED_COMPACT, layout)
        assert(layout.foldListening) { "no room to open the panel into" }
        assert(!layout.listeningRail) { "no room to put it beside anything" }
        assert(!layout.mergeTopBar) { "no room for the merged row either" }
    }

    @Test
    fun `a rail never also folds — there is nothing to fold`() {
        val layout = libraryLayoutFor(800.dp, 360.dp)
        assert(layout.listeningRail)
        assert(!layout.foldListening) { "a rail is not in the library's way" }
        assert(layout.mergeTopBar)
    }
}
