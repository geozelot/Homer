package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure pieces of a scan's mutate-then-prune sequence: [idsToPrune] (which exists because
 * a `NOT IN (:keepIds)` prune blows SQLite's 999 host-parameter cap), [relinkMediaId] (which keeps a
 * resumed chapter resolvable after its book's folder is renamed) and [likeDescendantsOf] (which
 * decides which books a skipped subtree protects).
 */
class LibraryScannerPruneTest {

    // ── keeping the books under a skipped subtree ─────────────────────────────

    @Test
    fun `an ordinary path becomes a prefix pattern`() {
        assertEquals("Author/Book/%", likeDescendantsOf("Author/Book"))
    }

    @Test
    fun `an underscore is not left as a wildcard`() {
        // LIKE's `_` matches any single character, and folder names are full of underscores — so
        // unescaped, this pattern also claims every book under `TheXHobbit`, and a book that should
        // have been pruned survives instead.
        assertEquals("The\\_Hobbit/%", likeDescendantsOf("The_Hobbit"))
    }

    @Test
    fun `a percent is not left as a wildcard`() {
        assertEquals("100\\%\\_Real/%", likeDescendantsOf("100%_Real"))
    }

    @Test
    fun `the escape character is escaped before the wildcards it introduces`() {
        // Escaping `\` last would double every backslash the earlier replacements just added, and
        // the pattern would stop matching the folder it names.
        assertEquals("a\\\\b\\_c/%", likeDescendantsOf("a\\b_c"))
    }

    @Test
    fun `a path with nothing to escape is untouched apart from the suffix`() {
        assertEquals("A/%", likeDescendantsOf("A"))
        assertEquals("/%", likeDescendantsOf(""))
    }

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
