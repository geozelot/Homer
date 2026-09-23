package com.geozelot.homer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the lane offers, and where each letter points.
 *
 * The rule worth protecting is that **the lane's letters come from the same key the list was
 * sorted by**. A lane built from titles over a shelf sorted by surname would look right and land
 * wrong on every jump — the kind of failure that reads as the app being broken rather than as a
 * bug with a cause.
 */
class FastScrollLaneTest {

    private fun book(id: String, title: String, author: String?) = BookListItem(
        id = id,
        title = title,
        authors = listOfNotNull(author),
        isMultiFile = false,
        fileCount = 1,
        coverModel = null,
        hasCustomCover = false,
        series = null,
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

    private fun flat(vararg books: BookListItem) =
        librarySlots(books.map { LibraryEntry.Standalone(it) }, true, 3, emptySet()) { false }

    @Test
    fun `under sort-by-title the letters are titles`() {
        val slots = flat(
            book("1", "Anansi Boys", "Neil Gaiman"),
            book("2", "Mort", "Terry Pratchett"),
        )
        assertEquals(listOf("A", "M"), laneLetters(slots, LibrarySort.TITLE, shelved = false).map { it.label })
    }

    @Test
    fun `under sort-by-surname the letters are surnames`() {
        // The case the lane exists to get right: by title these are A and M, by first name N and
        // T, and by surname G and P. Only the last agrees with how the shelf was ordered.
        val slots = flat(
            book("1", "Anansi Boys", "Neil Gaiman"),
            book("2", "Mort", "Terry Pratchett"),
        )
        assertEquals(listOf("G", "P"), laneLetters(slots, LibrarySort.AUTHOR_LAST, shelved = false).map { it.label })
        assertEquals(listOf("N", "T"), laneLetters(slots, LibrarySort.AUTHOR, shelved = false).map { it.label })
    }

    @Test
    fun `a sort with no alphabet offers no lane`() {
        val slots = flat(book("1", "Mort", "Terry Pratchett"))
        assertTrue(laneLetters(slots, LibrarySort.RECENT, shelved = false).isEmpty())
        assertTrue(laneLetters(slots, LibrarySort.DURATION, shelved = false).isEmpty())
    }

    @Test
    fun `a letter appears once and points at its first book`() {
        val slots = flat(
            book("1", "Anansi Boys", "A"),
            book("2", "American Gods", "A"),
            book("3", "Mort", "B"),
        )
        val lane = laneLetters(slots, LibrarySort.TITLE, shelved = false)
        assertEquals(listOf("A", "M"), lane.map { it.label })
        assertEquals(listOf(0, 2), lane.map { it.index })
    }

    @Test
    fun `a shelved library jumps to headings, not into the middle of a shelf`() {
        val entries = listOf(
            LibraryEntry.Header("Adams"),
            LibraryEntry.Standalone(book("1", "Mostly Harmless", "Adams")),
            LibraryEntry.Header("Pratchett"),
            LibraryEntry.Standalone(book("2", "Mort", "Pratchett")),
        )
        val slots = librarySlots(entries, true, 3, emptySet()) { false }
        val lane = laneLetters(slots, LibrarySort.AUTHOR, shelved = true)
        assertEquals(listOf("A", "P"), lane.map { it.label })
        // Indices 0 and 2 — the headings, not the books under them.
        assertEquals(listOf(0, 2), lane.map { it.index })
    }

    @Test
    fun `everything that is not a letter shares one bucket`() {
        assertEquals("#", initialOf("1984"))
        assertEquals("#", initialOf("(Prologue)"))
        assertEquals("M", initialOf("  mort "))
        assertEquals(null, initialOf("   "))
    }
}
