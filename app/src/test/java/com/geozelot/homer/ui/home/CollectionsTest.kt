package com.geozelot.homer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules the whole collection design rests on, and the guarantee they exist to protect:
 * a library that has never nested a folder must render identically at every stacking depth.
 */
class CollectionsTest {

    private fun book(
        id: String,
        title: String = id,
        author: String? = "Pratchett",
        series: String? = null,
        seriesIndex: Int? = null,
        collection: String? = null,
        collectionIndex: Int? = null,
    ) = BookListItem(
        id = id,
        title = title,
        author = author,
        isMultiFile = false,
        fileCount = 1,
        coverModel = null,
        hasCustomCover = false,
        series = series,
        seriesIndex = seriesIndex,
        collection = collection,
        collectionIndex = collectionIndex,
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

    /** A shelf of [n] books, the ones named in [withArt] carrying a cover. */
    private fun shelf(n: Int, withArt: Set<Int> = (1..n).toSet()) = LibraryEntry.Series(
        key = "k",
        name = "Discworld",
        author = "Pratchett",
        books = (1..n).map { i ->
            book("b$i").copy(coverModel = if (i in withArt) "art$i" else null)
        },
    )

    // ── the pile: how many, and whose artwork ────────────────────────────────────────────────

    @Test
    fun `a shelf draws one cover fewer than it holds, up to the cap`() {
        assertEquals(3, shelf(8).stackSheets(3).size)
        assertEquals(3, shelf(4).stackSheets(3).size)
        assertEquals(2, shelf(3).stackSheets(3).size)
        assertEquals(1, shelf(2).stackSheets(3).size)
        assertEquals(0, shelf(1).stackSheets(3).size)
    }

    @Test
    fun `the sheets are the volumes after the front cover, in order`() {
        assertEquals(listOf<Any?>("art2", "art3", "art4"), shelf(8).stackSheets(3))
    }

    @Test
    fun `the front cover is never repeated in the pile`() {
        // `frontCover` takes the first volume WITH art, so the pile has to drop that same one or the
        // same picture appears twice and reads as a rendering fault.
        val s = shelf(8, withArt = setOf(3, 4, 5, 6))
        assertEquals("art3", s.frontCover())
        assertEquals(listOf<Any?>("art4", "art5", "art6"), s.stackSheets(3))
    }

    @Test
    fun `the COUNT comes from the books, not from how many have artwork`() {
        // An eight-volume shelf whose later volumes are unillustrated is still eight volumes. Taking
        // the count from the art would draw it as a two-volume one.
        val s = shelf(8, withArt = setOf(1, 2))
        assertEquals(3, s.stackSheets(3).size)
        assertEquals(listOf<Any?>("art2", null, null), s.stackSheets(3))
    }

    @Test
    fun `a shelf with no artwork at all still draws its pile`() {
        val s = shelf(5, withArt = emptySet())
        assertEquals(listOf<Any?>(null, null, null), s.stackSheets(3))
    }

    @Test
    fun `the row cap is honoured independently of the grid's`() {
        assertEquals(2, shelf(8).stackSheets(2).size)
        assertEquals(1, shelf(2).stackSheets(2).size)
    }

    // ── rule one: a series with no collection is its own collection ──────────────────────────

    @Test
    fun `a series with no collection stands in as its own`() {
        val b = book("1", series = "The Expanse", author = "Corey")
        assertEquals("The Expanse", b.effectiveCollection())
    }

    @Test
    fun `a real collection wins over the series`() {
        val b = book("1", series = "Rincewind", collection = "Discworld")
        assertEquals("Discworld", b.effectiveCollection())
    }

    @Test
    fun `a standalone belongs to no collection at all`() {
        assertEquals(null, book("1").effectiveCollection())
    }

    @Test
    fun `the same collection name under two authors does not merge`() {
        val a = book("1", author = "Adams", series = "Chronicles")
        val b = book("2", author = "Baker", series = "Chronicles")
        assertTrue(a.collectionKey() != b.collectionKey())
    }

    // ── the guarantee: an un-nested library is identical at both depths ──────────────────────

    @Test
    fun `The Expanse stacks at collection depth exactly as it does at series depth`() {
        val books = listOf(
            book("1", title = "Leviathan Wakes", author = "Corey", series = "The Expanse", seriesIndex = 1),
            book("2", title = "Caliban's War", author = "Corey", series = "The Expanse", seriesIndex = 2),
        )
        val atSeries = collapseIntoUnits(books, LibraryDepth.SERIES)
        val atCollection = collapseIntoUnits(books, LibraryDepth.COLLECTION)

        // One shelf either way, same name, same books in the same order — the whole non-conflict
        // guarantee for a library that has never nested a folder.
        assertEquals(1, atSeries.size)
        assertEquals(1, atCollection.size)
        val s = (atSeries.single() as SortUnit.Ser).series
        val c = (atCollection.single() as SortUnit.Ser).series
        assertEquals(s.name, c.name)
        assertEquals(s.books.map { it.id }, c.books.map { it.id })
        // …but it is not BADGED as a collection, because nobody made it one.
        assertFalse(c.isCollection)
    }

    @Test
    fun `flat depth leaves every book loose`() {
        val books = listOf(
            book("1", series = "The Expanse", author = "Corey"),
            book("2", series = "The Expanse", author = "Corey"),
        )
        val units = collapseIntoUnits(books, LibraryDepth.FLAT)
        assertEquals(2, units.size)
        assertTrue(units.all { it is SortUnit.Solo })
    }

    // ── Discworld: one collection, several threads ───────────────────────────────────────────

    private val discworld = listOf(
        book("c", title = "Sourcery", series = "Rincewind", seriesIndex = 3, collection = "Discworld", collectionIndex = 5),
        book("a", title = "The Colour of Magic", series = "Rincewind", seriesIndex = 1, collection = "Discworld", collectionIndex = 1),
        book("b", title = "Mort", series = "Death", seriesIndex = 1, collection = "Discworld", collectionIndex = 4),
        book("d", title = "Pyramids", collection = "Discworld", collectionIndex = 7),
    )

    @Test
    fun `at collection depth Discworld is one shelf in publication order`() {
        val units = collapseIntoUnits(discworld, LibraryDepth.COLLECTION)
        assertEquals(1, units.size)
        val shelf = (units.single() as SortUnit.Ser).series
        assertEquals("Discworld", shelf.name)
        assertTrue("a real parent must be badged as one", shelf.isCollection)
        assertEquals(listOf("a", "b", "c", "d"), shelf.books.map { it.id })
    }

    @Test
    fun `at series depth the threads stand alone and the unaffiliated book sits loose`() {
        val units = collapseIntoUnits(discworld, LibraryDepth.SERIES)
        val shelves = units.filterIsInstance<SortUnit.Ser>().map { it.series.name }.sorted()
        val loose = units.filterIsInstance<SortUnit.Solo>().map { it.book.id }
        assertEquals(listOf("Death", "Rincewind"), shelves)
        assertEquals(listOf("d"), loose)
    }

    // ── rule two: an index is what makes a collection also a series ──────────────────────────

    @Test
    fun `a numbered collection reads as a series too`() {
        assertTrue(discworld.collectionHasReadingOrder())
    }

    @Test
    fun `an unnumbered collection is a parent and nothing more`() {
        val loose = listOf(
            book("1", series = "Thrawn", collection = "Star Wars Legends"),
            book("2", series = "Rogue Squadron", collection = "Star Wars Legends"),
        )
        assertFalse(loose.collectionHasReadingOrder())
    }

    @Test
    fun `a series standing in for itself never counts as having a collection order`() {
        // Otherwise every ordinary numbered series in the library would answer yes to a question
        // that is only meaningful about a real parent.
        val expanse = listOf(book("1", series = "The Expanse", seriesIndex = 1, author = "Corey"))
        assertFalse(expanse.collectionHasReadingOrder())
    }

    @Test
    fun `unnumbered members sort after numbered ones rather than among them`() {
        val mixed = listOf(
            book("z", title = "Zzz", collection = "C"),
            book("n", title = "Aaa", collection = "C", collectionIndex = 2),
        )
        assertEquals(listOf("n", "z"), mixed.sortedWith(inCollectionOrder).map { it.id })
    }

    // ── the stored preference ────────────────────────────────────────────────────────────────

    @Test
    fun `the old stacked preference lands on series, not collection`() {
        // A saved preference must not silently deepen under somebody who never asked for it.
        assertEquals(LibraryDepth.SERIES, LibraryDepth.from("stacked"))
    }

    @Test
    fun `an unknown or absent preference falls back to series`() {
        assertEquals(LibraryDepth.SERIES, LibraryDepth.from(null))
        assertEquals(LibraryDepth.SERIES, LibraryDepth.from("nonsense-from-a-later-build"))
    }

    @Test
    fun `each depth round-trips through its key`() {
        LibraryDepth.entries.forEach { assertEquals(it, LibraryDepth.from(it.key)) }
    }

    // ── how an opened shelf breaks up ────────────────────────────────────────────────────────

    @Test
    fun `an opened plain series is a flat run of rows, as it always was`() {
        val shelf = (collapseIntoUnits(
            listOf(
                book("1", series = "The Expanse", author = "Corey", seriesIndex = 1),
                book("2", series = "The Expanse", author = "Corey", seriesIndex = 2),
                book("3", series = "The Expanse", author = "Corey", seriesIndex = 3),
            ),
            LibraryDepth.SERIES,
        ).single() as SortUnit.Ser).series

        val rows = shelf.expandedRows(columns = 2)
        // No headings: labelling a series with its own name inside a card already titled that
        // would be saying the same word twice.
        assertTrue(rows.none { it is ShelfRow.SubHeader })
        assertEquals(2, rows.size)
    }

    @Test
    fun `an opened collection is grouped by thread, in the order the collection reads`() {
        val shelf = (collapseIntoUnits(discworld, LibraryDepth.COLLECTION).single() as SortUnit.Ser).series
        val rows = shelf.expandedRows(columns = 3)

        // Rincewind before Death because Rincewind's first book is Discworld #1 — publication
        // order, not alphabetical.
        assertEquals(
            listOf("Rincewind", "Death"),
            rows.filterIsInstance<ShelfRow.SubHeader>().map { it.label },
        )
    }

    @Test
    fun `books belonging to no thread come last, under a heading of their own`() {
        val shelf = (collapseIntoUnits(discworld, LibraryDepth.COLLECTION).single() as SortUnit.Ser).series
        val rows = shelf.expandedRows(columns = 3)
        val lastBooks = rows.filterIsInstance<ShelfRow.Books>().last().books
        assertEquals(listOf("d"), lastBooks.map { it.id })
        assertEquals(2, rows.filterIsInstance<ShelfRow.SubHeader>().size)
        // They used to come under NO heading, on the reasoning that they are not a group but what is
        // left. True, and unreadable: with nothing between them and the thread above they read as
        // more of that series.
        assertTrue(ShelfRow.LooseHeader in rows)
        assertEquals("and it is the last heading, not a stray one", rows.indexOf(ShelfRow.LooseHeader), rows.size - 2)
    }

    @Test
    fun `a collection that is only loose books gets no heading`() {
        // Nothing to be distinguished FROM, so a heading would be labelling the card with its own
        // contents.
        val loose = listOf(
            book("x", collection = "Odds", author = "A", collectionIndex = 1),
            book("y", collection = "Odds", author = "A", collectionIndex = 2),
        )
        val shelf = (collapseIntoUnits(loose, LibraryDepth.COLLECTION).single() as SortUnit.Ser).series
        val rows = shelf.expandedRows(columns = 3)
        assertTrue(ShelfRow.LooseHeader !in rows)
        assertTrue(rows.none { it is ShelfRow.SubHeader })
    }

    @Test
    fun `a collection with no loose books gets no loose heading`() {
        val threaded = listOf(
            book("p", collection = "Odds", series = "One", author = "A", collectionIndex = 1),
            book("q", collection = "Odds", series = "Two", author = "A", collectionIndex = 2),
        )
        val shelf = (collapseIntoUnits(threaded, LibraryDepth.COLLECTION).single() as SortUnit.Ser).series
        val rows = shelf.expandedRows(columns = 3)
        assertTrue(ShelfRow.LooseHeader !in rows)
        assertEquals(2, rows.filterIsInstance<ShelfRow.SubHeader>().size)
    }

    @Test
    fun `every book survives the split`() {
        val shelf = (collapseIntoUnits(discworld, LibraryDepth.COLLECTION).single() as SortUnit.Ser).series
        for (columns in 1..4) {
            val emitted = shelf.expandedRows(columns).filterIsInstance<ShelfRow.Books>().flatMap { it.books }
            assertEquals("columns=$columns", discworld.size, emitted.size)
        }
    }

    @Test
    fun `a row never holds more books than there are columns`() {
        val shelf = (collapseIntoUnits(discworld, LibraryDepth.COLLECTION).single() as SortUnit.Ser).series
        // Chunked per thread rather than across the whole shelf, so a thread's last row is short
        // and the next thread starts on a fresh one instead of sharing it.
        assertTrue(shelf.expandedRows(2).filterIsInstance<ShelfRow.Books>().all { it.books.size <= 2 })
    }
}
