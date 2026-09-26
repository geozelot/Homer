package com.geozelot.homer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which grid cards keep a meta line: all of a row, or none of it.
 *
 * The cards in a grid row have to end level, and a card's ground shows where it ends — so a line
 * reserved on one card and not its neighbour is a ragged row, and a line reserved on a row that has
 * nothing to say is a blank strip under every card's chips.
 */
class GridMetaRowsTest {

    private fun book(id: String, tags: List<String> = emptyList(), genres: List<String> = listOf("Fantasy")) =
        BookListItem(
            id = id,
            title = id,
            authors = listOf("Pratchett"),
            genres = genres,
            isMultiFile = false,
            fileCount = 1,
            coverModel = null,
            hasCustomCover = false,
            series = null,
            seriesIndex = null,
            collection = null,
            collectionIndex = null,
            language = null,
            tags = tags,
            totalDurationMs = null,
            timeLeftMs = null,
            progress = null,
            lastPlayedAt = null,
            started = false,
            finishedOverride = null,
            downloadOnPlayOverride = null,
            downloadStatus = null,
            downloadedFiles = 0,
            hidden = false,
        )

    private fun cell(key: String) = GridSlot.Book(book(key), key, fullSpan = false)
    private fun heading(key: String) = GridSlot.Heading(LibraryEntry.Header(key), key)

    @Test
    fun `one card with something to say keeps the line for its whole row and no other`() {
        val slots = listOf(cell("a"), cell("b"), cell("c"), cell("d"), cell("e"))

        val kept = gridMetaRows(slots, columns = 3) { it.key == "b" }

        assertEquals(setOf("a", "b", "c"), kept)
    }

    @Test
    fun `a full-span item ends a row early`() {
        // a b | HEADING | c d — the heading breaks the row after two cells, as the grid does.
        val slots = listOf(cell("a"), cell("b"), heading("H"), cell("c"), cell("d"))

        val kept = gridMetaRows(slots, columns = 3) { it.key == "d" }

        assertEquals(setOf("c", "d"), kept)
    }

    @Test
    fun `a short last row is a row`() {
        val slots = listOf(cell("a"), cell("b"), cell("c"), cell("d"))

        assertEquals(setOf("d"), gridMetaRows(slots, columns = 3) { it.key == "d" })
    }

    @Test
    fun `shelved by author, a book's line is its tags`() {
        val ctx = RowContext(shelving = LibraryShelving.AUTHOR, series = LibraryDepth.SERIES)

        assertFalse(gridCardHasMeta(book("a"), ctx))
        assertTrue(gridCardHasMeta(book("a", tags = listOf("signed")), ctx))
    }

    @Test
    fun `shelved by genre, the chip carries the author, so the line is still only the tags`() {
        // Which is why the reserved line was blank on nearly every card: in the grid, every fact
        // but the tags already has a place of its own.
        val ctx = RowContext(shelving = LibraryShelving.GENRE, series = LibraryDepth.SERIES)

        assertFalse(gridCardHasMeta(book("a"), ctx))
        assertTrue(gridCardHasMeta(book("a", tags = listOf("signed")), ctx))
    }
}
