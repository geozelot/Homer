package com.geozelot.homer.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which layout a window gets.
 *
 * Written against real devices rather than against the thresholds, because the thresholds only mean
 * anything as answers about actual hardware — and because this has already been wrong once in a way
 * no threshold-shaped test would have caught: the rule keyed on an absolute height, a large phone at
 * a reduced display size reported more dp than the rule expected in BOTH directions, and a phone was
 * served the tablet layout. The case is in here now, with the numbers it actually reported.
 */
class LibraryLayoutTest {

    /** Portrait and landscape of the same device, since the point is that both must be right. */
    private fun portrait(w: Int, h: Int) = libraryLayoutFor(w.dp, h.dp, minOf(w, h).dp)
    private fun landscape(w: Int, h: Int) = libraryLayoutFor(h.dp, w.dp, minOf(w, h).dp)

    @Test
    fun `a phone stacks upright and rails on its side`() {
        assertEquals(LibraryLayout.STACKED, portrait(360, 800))
        assertEquals(LibraryLayout.RAIL, landscape(360, 800))
    }

    @Test
    fun `a large phone at a reduced display size is still a phone`() {
        // The regression. This device reports ~523x1164 upright — past every absolute threshold a
        // phone was assumed to stay under, in both directions at once. Its SHORTER edge still says
        // phone, which is the whole reason the rule reads that instead.
        assertEquals(LibraryLayout.STACKED, portrait(523, 1164))
        assertEquals(LibraryLayout.RAIL, landscape(523, 1164))
    }

    @Test
    fun `a tablet stacks whichever way up it is held`() {
        assertEquals(LibraryLayout.STACKED, portrait(800, 1280))
        assertEquals(LibraryLayout.STACKED, landscape(800, 1280))
        // A 7" tablet, right on the boundary Android itself draws.
        assertEquals(LibraryLayout.STACKED, landscape(600, 960))
    }

    @Test
    fun `an unfolded foldable is a tablet, not a wide phone`() {
        assertEquals(LibraryLayout.STACKED, portrait(674, 841))
        assertEquals(LibraryLayout.STACKED, landscape(674, 841))
    }

    @Test
    fun `a window short and too narrow for a rail folds what it can`() {
        // A split-screen half: the same height problem, without the width to put a rail beside the
        // grid or to carry six controls on one row. It folds the panel and keeps its top bar.
        val layout = libraryLayoutFor(360.dp, 400.dp, 360.dp)
        assertEquals(LibraryLayout.STACKED_COMPACT, layout)
        assert(layout.foldListening) { "no room to open the panel into" }
        assert(!layout.listeningRail) { "no room to put it beside anything" }
        assert(!layout.mergeTopBar) { "no room for the merged row either" }
    }

    @Test
    fun `a landscape window too narrow for the rail does not get one`() {
        // Half of a landscape phone, side by side with another app: wider than tall, and still not
        // 600dp across.
        assertEquals(LibraryLayout.STACKED_COMPACT, libraryLayoutFor(582.dp, 360.dp, 360.dp))
    }

    @Test
    fun `a rail never also folds — there is nothing to fold`() {
        val layout = landscape(360, 800)
        assert(layout.listeningRail)
        assert(!layout.foldListening) { "a rail is not in the library's way" }
        assert(layout.mergeTopBar)
    }
}
