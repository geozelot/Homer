package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two pure pieces of a scan's mutate-then-prune sequence: [idsToPrune] (which exists
 * because a `NOT IN (:keepIds)` prune blows SQLite's 999 host-parameter cap) and [relinkMediaId]
 * (which keeps a resumed chapter resolvable after its book's folder is renamed).
 */
class LibraryScannerPruneTest {

    @Test
    fun `only ids missing from the keep set are pruned`() {
        val all = listOf("A/One", "A/Two", "B/Three")
        assertEquals(listOf("A/Two"), idsToPrune(all, setOf("A/One", "B/Three")))
    }

    @Test
    fun `nothing is pruned when everything is kept`() {
        val all = listOf("A/One", "A/Two")
        assertTrue(idsToPrune(all, all.toSet()).isEmpty())
    }

    @Test
    fun `a keep id that is not indexed does not affect the result`() {
        // Freshly detected books are in keepIds but not yet in the table; they must not confuse
        // the inversion.
        assertEquals(listOf("A/Old"), idsToPrune(listOf("A/Old"), setOf("A/New")))
    }

    @Test
    fun `every id is pruned when the keep set is empty`() {
        // The caller guards this case (an empty crawl must never wipe the library); the helper
        // itself stays honest about what "keep nothing" means.
        val all = listOf("A/One", "A/Two")
        assertEquals(all, idsToPrune(all, emptySet()))
    }

    @Test
    fun `a library above the host-parameter limit prunes in full`() {
        val all = (1..2500).map { "Author/Book $it" }
        val keep = all.filter { it.endsWith("0") }.toSet()
        val pruned = idsToPrune(all, keep)
        assertEquals(all.size - keep.size, pruned.size)
        assertTrue(pruned.none { it in keep })
    }

    @Test
    fun `a saved chapter path follows its book to the new id`() {
        assertEquals(
            "Author/New Title/01.mp3",
            relinkMediaId("Author/Old Title/01.mp3", "Author/Old Title", "Author/New Title"),
        )
    }

    @Test
    fun `a nested part path keeps everything below the book folder`() {
        assertEquals(
            "New/Book/Part 2/03.mp3",
            relinkMediaId("Old/Book/Part 2/03.mp3", "Old/Book", "New/Book"),
        )
    }

    @Test
    fun `a path belonging to another book is untouched`() {
        // "Author/Old Title 2" starts with the old id as a string but is a different folder — a
        // bare startsWith without the separator would corrupt it.
        assertEquals(
            "Author/Old Title 2/01.mp3",
            relinkMediaId("Author/Old Title 2/01.mp3", "Author/Old Title", "Author/New Title"),
        )
    }

    @Test
    fun `an unrelated path is returned unchanged`() {
        assertEquals("Other/Book/01.mp3", relinkMediaId("Other/Book/01.mp3", "A/Old", "A/New"))
    }

    @Test
    fun `a media id equal to the book id maps to the new id`() {
        assertEquals("A/New", relinkMediaId("A/Old", "A/Old", "A/New"))
    }
}
