package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AudioFormats.naturalCompare] decides `sortIndex`, which is persisted AND published — so it has to
 * be numeric-aware and it has to be a total order. Two names it called equal would be left in
 * whatever order the server listed them, and two devices could then disagree about chapter two.
 */
class AudioFormatsTest {

    private fun sorted(vararg names: String) =
        names.sortedWith { a, b -> AudioFormats.naturalCompare(a, b) }

    @Test
    fun `numbers order by value, not by digit`() {
        assertEquals(
            listOf("2 - foo.mp3", "10 - foo.mp3"),
            sorted("10 - foo.mp3", "2 - foo.mp3"),
        )
    }

    @Test
    fun `leading zeros do not change the value`() {
        assertEquals(
            listOf("Kapitel 002.mp3", "Kapitel 10.mp3"),
            sorted("Kapitel 10.mp3", "Kapitel 002.mp3"),
        )
    }

    @Test
    fun `a hundred chapters order correctly end to end`() {
        val names = (1..100).map { "Kapitel $it.mp3" }
        assertEquals(names, sorted(*names.shuffled().toTypedArray()))
    }

    @Test
    fun `comparison ignores case`() {
        assertTrue(AudioFormats.naturalCompare("Chapter 1.mp3", "chapter 1.MP3") != 0)
        assertTrue(AudioFormats.naturalCompare("chapter 2.mp3", "Chapter 10.mp3") < 0)
    }

    @Test
    fun `names differing only in leading zeros still order deterministically`() {
        // The order between them does not matter; that there IS one does. Returning 0 here left
        // them in server-listing order, which can differ between devices for the same folder.
        assertTrue(AudioFormats.naturalCompare("1.mp3", "01.mp3") != 0)
        assertEquals(
            AudioFormats.naturalCompare("1.mp3", "01.mp3") < 0,
            AudioFormats.naturalCompare("01.mp3", "1.mp3") > 0,
        )
    }

    @Test
    fun `names differing only in case still order deterministically`() {
        assertTrue(AudioFormats.naturalCompare("A.mp3", "a.mp3") != 0)
    }

    @Test
    fun `a true prefix sorts before the longer name`() {
        assertEquals(listOf("Kapitel 1", "Kapitel 1.mp3"), sorted("Kapitel 1.mp3", "Kapitel 1"))
    }

    @Test
    fun `divergence after a number is decided by the character, not the length`() {
        // "Kapitel 1 (…)" and "Kapitel 1.mp3" are not prefixes of each other: they part company at
        // ' ' versus '.', and the space sorts first. Worth pinning because it looks like a
        // prefix case and is not.
        assertTrue(AudioFormats.naturalCompare("Kapitel 1 (Teil 2).mp3", "Kapitel 1.mp3") < 0)
    }

    @Test
    fun `the empty name sorts first and does not crash`() {
        assertTrue(AudioFormats.naturalCompare("", "a") < 0)
        assertEquals(0, AudioFormats.naturalCompare("", ""))
    }

    @Test
    fun `multiple number runs are each compared numerically`() {
        assertEquals(
            listOf("CD 2 - Track 9.mp3", "CD 2 - Track 10.mp3", "CD 10 - Track 1.mp3"),
            sorted("CD 10 - Track 1.mp3", "CD 2 - Track 10.mp3", "CD 2 - Track 9.mp3"),
        )
    }

    @Test
    fun `extensions are recognised case-insensitively`() {
        assertTrue(AudioFormats.isAudio("x.MP3"))
        assertTrue(AudioFormats.isAudio("x.m4b"))
        assertTrue(AudioFormats.isImage("cover.JPEG"))
        assertTrue(!AudioFormats.isAudio("cover.jpg"))
        assertTrue(!AudioFormats.isAudio("noextension"))
    }
}
