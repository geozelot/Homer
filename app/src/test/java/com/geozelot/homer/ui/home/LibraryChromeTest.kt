package com.geozelot.homer.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the library gives up, and at what size.
 *
 * Written against real window sizes rather than the thresholds themselves, because the thresholds
 * only mean anything as answers about actual devices — and because the rule is asymmetric in a way
 * that is easy to "simplify" back into a bug: folding the panel needs no width, merging the chrome
 * needs plenty.
 */
class LibraryChromeTest {

    @Test
    fun `a phone in portrait keeps everything`() {
        val chrome = libraryChromeFor(360.dp, 800.dp)
        assertFalse(chrome.foldListening)
        assertFalse(chrome.mergeTopBar)
    }

    @Test
    fun `a phone in landscape gives up both`() {
        // 800x360: the case this exists for. Expanded, the furniture came to ~355dp of 360.
        assertEquals(LibraryChrome(foldListening = true, mergeTopBar = true), libraryChromeFor(800.dp, 360.dp))
    }

    @Test
    fun `the largest phones in landscape are still short`() {
        // 926x428 — a 6.7" phone. Comfortably above the tallest, comfortably below portrait.
        assertTrue(libraryChromeFor(926.dp, 428.dp).foldListening)
    }

    @Test
    fun `a tablet keeps everything in both orientations`() {
        assertEquals(LibraryChrome(false, false), libraryChromeFor(1280.dp, 800.dp))
        assertEquals(LibraryChrome(false, false), libraryChromeFor(800.dp, 1280.dp))
    }

    @Test
    fun `an unfolded foldable is a tablet, not a wide phone`() {
        assertEquals(LibraryChrome(false, false), libraryChromeFor(841.dp, 674.dp))
    }

    @Test
    fun `a window both short and narrow folds the panel but keeps the top bar`() {
        // The asymmetry, and the reason there are two rules. A split-screen half has the same
        // height problem, but not the width to carry six controls on one row — merging there would
        // clip the settings button off the end, which is worse than the 64dp it saves.
        val chrome = libraryChromeFor(360.dp, 400.dp)
        assertTrue("no room to open the panel into", chrome.foldListening)
        assertFalse("no room for the merged row either", chrome.mergeTopBar)
    }
}
