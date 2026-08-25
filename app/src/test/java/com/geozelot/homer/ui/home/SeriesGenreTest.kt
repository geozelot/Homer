package com.geozelot.homer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what makes a series a series, and which genre shelf it lands on.
 *
 * Series used to fall into "No genre" wholesale, on the reasoning that a series can span genres.
 * That reasoning is sound and the conclusion was still wrong: it hid every series in the library
 * under the one heading nobody looks in. Agreement is the normal case, so the rule follows it and
 * resolves disagreement rather than giving up on it.
 */
class SeriesGenreTest {

    private fun book(
        id: String,
        genre: String? = null,
        series: String? = "S",
        index: Int? = null,
        language: String? = null,
    ) = blank(id).copy(genre = genre, series = series, seriesIndex = index, language = language)

    private fun blank(id: String) = BookListItem(
        id = id,
        title = id,
        author = "A",
        isMultiFile = false,
        fileCount = 1,
        coverModel = null,
        hasCustomCover = false,
        series = "S",
        seriesIndex = null,
        genre = null,
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

    // ── what counts as a series ───────────────────────────────────────────────

    @Test
    fun `a single volume is still a series`() {
        // It used to take two members, so a series you own one volume of was indistinguishable from
        // a standalone book — which hid that it belongs to something, and meant the shelf appeared
        // out of nowhere the day a second volume arrived.
        val units = collapseIntoUnits(listOf(book("a", series = "Thorns")))
        val series = units.filterIsInstance<SortUnit.Ser>().single()
        assertEquals("Thorns", series.series.name)
        assertEquals(listOf("a"), series.series.books.map { it.id })
    }

    @Test
    fun `a book with no series is not made into one`() {
        val units = collapseIntoUnits(listOf(book("a", series = null)))
        assertEquals(1, units.filterIsInstance<SortUnit.Solo>().size)
        assertTrue(units.none { it is SortUnit.Ser })
    }

    @Test
    fun `books of the same series under different authors are different series`() {
        // The grouping key is author+series, and it has to stay that way: two unrelated works can
        // share a series name, and merging them would put one author's books on another's shelf.
        val units = collapseIntoUnits(
            listOf(
                book("a", series = "Chronicles"),
                blank("b").copy(series = "Chronicles", author = "B"),
            ),
        )
        assertEquals(2, units.filterIsInstance<SortUnit.Ser>().size)
    }

    // ── which genre shelf ─────────────────────────────────────────────────────

    @Test
    fun `a series that agrees shelves under that genre`() {
        assertEquals(
            "Fantasy",
            seriesGenre(listOf(book("a", "Fantasy"), book("b", "Fantasy"), book("c", "Fantasy"))),
        )
    }

    @Test
    fun `a series of one shelves under its only genre`() {
        // The case this rule exists alongside: a single-volume series is a series now, and it must
        // not be the one book in the library that cannot be found by genre.
        assertEquals("Fantasy", seriesGenre(listOf(book("a", "Fantasy"))))
    }

    @Test
    fun `a partly tagged series shelves under the genre it does have`() {
        assertEquals("Fantasy", seriesGenre(listOf(book("a", null), book("b", "Fantasy"), book("c", null))))
    }

    @Test
    fun `a disagreeing series shelves under its commonest genre`() {
        assertEquals(
            "Fantasy",
            seriesGenre(listOf(book("a", "Sci-Fi"), book("b", "Fantasy"), book("c", "Fantasy"))),
        )
    }

    @Test
    fun `a tie goes to the earliest volume`() {
        // The books arrive in reading order and `groupingBy` preserves it, so a series that changed
        // genre halfway shelves under what it started as — arbitrary either way, but the same
        // answer every time, which is what stops a shelf moving between launches.
        assertEquals("Sci-Fi", seriesGenre(listOf(book("a", "Sci-Fi"), book("b", "Fantasy"))))
    }

    // ── which language shelf ──────────────────────────────────────────────────

    @Test
    fun `a series shelves under the language its books agree on`() {
        assertEquals(
            "de",
            seriesLanguage(listOf(book("a", language = "de"), book("b", language = "de"))),
        )
    }

    @Test
    fun `a partly tagged series shelves under the language it does have`() {
        assertEquals("de", seriesLanguage(listOf(book("a"), book("b", language = "de"))))
    }

    @Test
    fun `a series with no language at all has none`() {
        // The state of a whole library before anything has read a tag, and it must stay null rather
        // than pick a language for books nothing has said anything about.
        assertNull(seriesLanguage(listOf(book("a"), book("b"))))
        assertNull(seriesLanguage(emptyList()))
    }

    @Test
    fun `a series with no genre at all has none`() {
        assertNull(seriesGenre(listOf(book("a", null), book("b", null))))
        assertNull(seriesGenre(emptyList()))
    }
}
