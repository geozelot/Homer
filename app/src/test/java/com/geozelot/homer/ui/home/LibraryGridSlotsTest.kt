package com.geozelot.homer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The order the grid draws in, asserted rather than believed.
 *
 * This exists because something outside the grid now depends on that order: a lazy grid is
 * addressed by index, so the fast-scroll lane can only jump to a book by knowing how many items
 * precede it. While the order lived inline in `libraryContent` there was nothing to assert against
 * — a composable emitting `item()` in a loop is not a value — and the lane would have needed its
 * own copy of the arithmetic, correct until the day the two disagreed.
 *
 * So what these tests really pin is the COUNT and the SEQUENCE: how many lazy items an entry
 * becomes, and in what order. Get either wrong and the lane scrolls to the wrong book, silently.
 */
class LibraryGridSlotsTest {

    private fun book(id: String, title: String = id, series: String? = null) = BookListItem(
        id = id,
        title = title,
        authors = listOf("A"),
        isMultiFile = false,
        fileCount = 1,
        coverModel = null,
        hasCustomCover = false,
        series = series,
        seriesIndex = null,
        collection = null,
        collectionIndex = null,
        language = null,
        tags = emptyList(),
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

    private fun slots(
        entries: List<LibraryEntry>,
        gridView: Boolean = true,
        columns: Int = 3,
        open: Set<String> = emptySet(),
    ) = librarySlots(entries, gridView, columns, emptySet()) { it.books.any { b -> b.id in open } }

    @Test
    fun `a heading is one item and a standalone is one item`() {
        val out = slots(
            listOf(
                LibraryEntry.Header("Adams"),
                LibraryEntry.Standalone(book("1")),
                LibraryEntry.Standalone(book("2")),
            ),
        )
        assertEquals(3, out.size)
        assertEquals(listOf("header:Adams#1", "1", "2"), out.map { it.key })
    }

    @Test
    fun `two headings with the same words still get different keys`() {
        // A book whose author metadata literally reads "Unknown author" sits beside the fallback
        // heading of the same name; duplicate keys make the lazy layout throw.
        val out = slots(listOf(LibraryEntry.Header("Unknown author"), LibraryEntry.Header("Unknown author")))
        assertEquals(listOf("header:Unknown author#1", "header:Unknown author#2"), out.map { it.key })
    }

    @Test
    fun `a heading spans the row and a grid card does not`() {
        val out = slots(listOf(LibraryEntry.Header("A"), LibraryEntry.Standalone(book("1"))))
        assertEquals(listOf(true, false), out.map { it.fullSpan })
    }

    @Test
    fun `in list view even a book takes the whole row`() {
        val out = slots(listOf(LibraryEntry.Standalone(book("1"))), gridView = false)
        assertEquals(listOf(true), out.map { it.fullSpan })
    }

    @Test
    fun `a closed shelf is one item however many books it holds`() {
        val shelf = LibraryEntry.Series("k", "Discworld", listOf("A"), (1..9).map { book("b$it") })
        assertEquals(1, slots(listOf(shelf)).size)
    }

    @Test
    fun `an open shelf in the grid is a banner plus one item per row of books`() {
        // Nine books at three columns: a banner and three rows.
        val shelf = LibraryEntry.Series("k", "Discworld", listOf("A"), (1..9).map { book("b$it") })
        val out = slots(listOf(shelf), columns = 3, open = setOf("b1"))
        assertEquals(4, out.size)
        assertEquals("series-open:series:b1", out.first().key)
        assertEquals(3, out.drop(1).count { it is GridSlot.ShelfEpisode })
    }

    @Test
    fun `an open shelf in the list is the row itself plus one item per book`() {
        val shelf = LibraryEntry.Series("k", "Discworld", listOf("A"), (1..4).map { book("b$it") })
        val out = slots(listOf(shelf), gridView = false, open = setOf("b1"))
        assertEquals(5, out.size)
        assertEquals("series:b1", out.first().key)
    }

    @Test
    fun `only the last episode of a shelf closes its enclosure`() {
        val shelf = LibraryEntry.Series("k", "Discworld", listOf("A"), (1..4).map { book("b$it") })
        val episodes = slots(listOf(shelf), gridView = false, open = setOf("b1"))
            .filterIsInstance<GridSlot.ShelfEpisode>()
        assertEquals(listOf(false, false, false, true), episodes.map { it.last })
    }

    @Test
    fun `an entry after an open shelf lands past every item the shelf produced`() {
        // The whole reason this is a list: the index of what FOLLOWS a shelf depends on how far
        // that shelf is unfolded, and the lane addresses the grid by index.
        val shelf = LibraryEntry.Series("k", "Discworld", listOf("A"), (1..9).map { book("b$it") })
        val after = LibraryEntry.Standalone(book("z"))
        val closed = slots(listOf(shelf, after))
        val opened = slots(listOf(shelf, after), open = setOf("b1"))
        assertEquals(1, closed.indexOfFirst { it.key == "z" })
        assertEquals(4, opened.indexOfFirst { it.key == "z" })
    }
}
