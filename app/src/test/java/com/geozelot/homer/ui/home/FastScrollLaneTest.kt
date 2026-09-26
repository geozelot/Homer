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
        assertEquals(listOf("A", "M"), laneLetters(slots, LibrarySort.TITLE, shelving = LibraryShelving.ITEM).map { it.label })
    }

    @Test
    fun `the lane follows the device's own filing preference`() {
        // The case the lane exists to get right: by title these are A and M, by first name N and
        // T, and by surname G and P. Only the last agrees with how the shelf was ordered.
        val slots = flat(
            book("1", "Anansi Boys", "Neil Gaiman"),
            book("2", "Mort", "Terry Pratchett"),
        )
        assertEquals(
            listOf("G", "P"),
            laneLetters(slots, LibrarySort.AUTHOR, LibraryShelving.ITEM, bySurname = true).map { it.label },
        )
        assertEquals(
            listOf("N", "T"),
            laneLetters(slots, LibrarySort.AUTHOR, LibraryShelving.ITEM, bySurname = false).map { it.label },
        )
    }

    @Test
    fun `an unshelved list with no alphabet offers no lane`() {
        val slots = flat(book("1", "Mort", "Terry Pratchett"))
        assertTrue(laneLetters(slots, LibrarySort.RECENT, shelving = LibraryShelving.ITEM).isEmpty())
        assertTrue(laneLetters(slots, LibrarySort.DURATION, shelving = LibraryShelving.ITEM).isEmpty())
    }

    @Test
    fun `a shelved list keeps its lane whatever the books under it are sorted by`() {
        // The headings are alphabetical no matter what orders the books beneath them, so refusing
        // the lane on the SORT alone took it away from a perfectly good alphabet of shelves.
        val entries = listOf(
            LibraryEntry.Header("Adams", fileKey = "adams"),
            LibraryEntry.Standalone(book("1", "Mostly Harmless", "Adams")),
        )
        val slots = librarySlots(entries, true, 3, emptySet()) { false }
        assertEquals(
            listOf("A"),
            laneLetters(slots, LibrarySort.RECENT, LibraryShelving.AUTHOR).map { it.label },
        )
    }

    @Test
    fun `a heading's letter follows how it FILES, not how it reads`() {
        // A shelf filed by surname reads "Terry Pratchett" and sits under P. Taking the initial
        // from the drawn title would offer T — a letter in a place the library is not ordered by.
        val entries = listOf(
            LibraryEntry.Header("Douglas Adams", fileKey = "adams douglas"),
            LibraryEntry.Standalone(book("1", "Mostly Harmless", "Douglas Adams")),
            LibraryEntry.Header("Terry Pratchett", fileKey = "pratchett terry"),
            LibraryEntry.Standalone(book("2", "Mort", "Terry Pratchett")),
        )
        val slots = librarySlots(entries, true, 3, emptySet()) { false }
        val lane = laneLetters(slots, LibrarySort.TITLE, LibraryShelving.AUTHOR)
        assertEquals(listOf("A", "P"), lane.map { it.label })
        assertEquals(listOf(0, 2), lane.map { it.index })
    }

    @Test
    fun `a letter appears once and points at its first book`() {
        val slots = flat(
            book("1", "Anansi Boys", "A"),
            book("2", "American Gods", "A"),
            book("3", "Mort", "B"),
        )
        val lane = laneLetters(slots, LibrarySort.TITLE, shelving = LibraryShelving.ITEM)
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
        val lane = laneLetters(slots, LibrarySort.AUTHOR, shelving = LibraryShelving.AUTHOR)
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
