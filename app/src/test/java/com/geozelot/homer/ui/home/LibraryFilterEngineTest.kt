package com.geozelot.homer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.geozelot.homer.data.library.filedAuthor

/**
 * The pipeline as one function: books in, shelves out.
 *
 * These are golden cases pinned during the extraction of [LibraryFilterEngine] out of
 * `HomeViewModel` — the same inputs must arrange to the same output the ViewModel used to produce
 * inline, so the move stays a move. The collapse/genre internals have tests of their own
 * (CollectionsTest, SeriesGenreTest); this file covers the seams: filter before grouping, sort
 * across units, sectioning, and the listening shelf's admission rules.
 */
class LibraryFilterEngineTest {

    private val engine = LibraryFilterEngine()

    private fun book(
        id: String,
        title: String = id,
        author: String? = null,
        series: String? = null,
        seriesIndex: Int? = null,
        genres: List<String> = emptyList(),
        totalDurationMs: Long? = null,
        timeLeftMs: Long? = null,
        lastPlayedAt: Long? = null,
        started: Boolean = false,
        finishedOverride: Boolean? = null,
        hidden: Boolean = false,
    ) = BookListItem(
        id = id,
        title = title,
        authors = listOfNotNull(author),
        isMultiFile = false,
        fileCount = 1,
        coverModel = null,
        hasCustomCover = false,
        series = series,
        seriesIndex = seriesIndex,
        genres = genres,
        language = null,
        tags = emptyList(),
        totalDurationMs = totalDurationMs,
        timeLeftMs = timeLeftMs,
        progress = null,
        lastPlayedAt = lastPlayedAt,
        started = started,
        finishedOverride = finishedOverride,
        downloadOnPlayOverride = null,
        downloadStatus = null,
        downloadedFiles = 0,
        hidden = hidden,
    )

    private fun arrange(
        books: List<BookListItem>,
        filter: LibraryFilter = LibraryFilter(),
        sort: LibrarySort = LibrarySort.TITLE,
        shelving: LibraryShelving = LibraryShelving.ITEM,
        series: LibraryDepth = LibraryDepth.SERIES,
    ) = engine.arrange(books, filter, sort, shelving, series)

    private fun titlesOf(entries: List<LibraryEntry>): List<String> = entries.map {
        when (it) {
            is LibraryEntry.Header -> "[${it.title}]"
            is LibraryEntry.Standalone -> it.book.title
            is LibraryEntry.Series -> "{${it.name}}"
        }
    }

    // ── flat list ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `unshelved list is flat and sorted by title`() {
        val entries = arrange(listOf(book("b", "Beta"), book("a", "alpha"), book("c", "Gamma")))
        assertEquals(listOf("alpha", "Beta", "Gamma"), titlesOf(entries))
    }

    @Test
    fun `a series collapses into one unit placed by its name`() {
        val entries = arrange(
            listOf(
                book("m", "Mort", author = "Pratchett", series = "Discworld", seriesIndex = 4),
                book("z", "Zeno"),
                book("g", "Guards", author = "Pratchett", series = "Discworld", seriesIndex = 8),
            ),
        )
        assertEquals(listOf("{Discworld}", "Zeno"), titlesOf(entries))
        val shelf = entries.first() as LibraryEntry.Series
        // Episodes stay in reading order regardless of the outer sort.
        assertEquals(listOf("Mort", "Guards"), shelf.books.map { it.title })
    }

    @Test
    fun `flat depth keeps every volume its own row`() {
        val entries = arrange(
            listOf(
                book("m", "Mort", author = "Pratchett", series = "Discworld"),
                book("g", "Guards", author = "Pratchett", series = "Discworld"),
            ),
            series = LibraryDepth.FLAT,
        )
        assertEquals(listOf("Guards", "Mort"), titlesOf(entries))
    }

    // ── filter before grouping ───────────────────────────────────────────────────────────────

    @Test
    fun `a shelf that loses its last book disappears`() {
        val entries = arrange(
            listOf(
                book("m", "Mort", author = "Pratchett"),
                book("z", "Zeno", author = "Calvino"),
            ),
            filter = LibraryFilter(text = "zeno"),
            shelving = LibraryShelving.AUTHOR,
        )
        // No "[Pratchett]" header standing over nothing.
        assertEquals(listOf("[Calvino]", "Zeno"), titlesOf(entries))
    }

    // ── sectioning ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `author shelves sort by author with the fallback last`() {
        val entries = arrange(
            listOf(book("z", "Zeno", author = "Calvino"), book("n", "Nameless"), book("m", "Mort", author = "Pratchett")),
            shelving = LibraryShelving.AUTHOR,
        )
        assertEquals(
            listOf("[Calvino]", "Zeno", "[Pratchett]", "Mort", "[Unknown author]", "Nameless"),
            titlesOf(entries),
        )
    }

    @Test
    fun `recent sort puts the never-played last`() {
        val entries = arrange(
            listOf(book("old", lastPlayedAt = 1L), book("never"), book("new", lastPlayedAt = 2L)),
            sort = LibrarySort.RECENT,
        )
        assertEquals(listOf("new", "old", "never"), titlesOf(entries))
    }

    @Test
    fun `duration sort treats the unmeasured as shortest`() {
        val entries = arrange(
            listOf(book("short", totalDurationMs = 1_000L), book("unmeasured"), book("long", totalDurationMs = 9_000L)),
            sort = LibrarySort.DURATION,
        )
        assertEquals(listOf("long", "short", "unmeasured"), titlesOf(entries))
    }

    // ── the listening shelf ──────────────────────────────────────────────────────────────────

    @Test
    fun `listening keeps only the started, unfinished, visible books`() {
        val shelf = engine.listening(
            listOf(
                book("playing", started = true, lastPlayedAt = 5L, timeLeftMs = 60_000L, totalDurationMs = 100_000L),
                book("opened-only", started = false, lastPlayedAt = 4L),
                book("finished", started = true, lastPlayedAt = 3L, timeLeftMs = 0L),
                book("marked", started = true, lastPlayedAt = 2L, finishedOverride = true),
                book("hidden", started = true, lastPlayedAt = 1L, timeLeftMs = 60_000L, hidden = true),
            ),
        )
        assertEquals(listOf("playing"), shelf.map { it.id })
    }

    @Test
    fun `listening is most-recent first and capped`() {
        val many = (1..20).map { book("b$it", started = true, lastPlayedAt = it.toLong(), timeLeftMs = 60_000L) }
        val shelf = engine.listening(many.shuffled())
        assertEquals(12, shelf.size)
        assertEquals("b20", shelf.first().id)
        assertTrue(shelf.zipWithNext().all { (a, b) -> a.lastPlayedAt!! >= b.lastPlayedAt!! })
    }

    // ── Names drawn filed, compared spoken ────────────────────────────────────

    /** A book as `rows` produces it with names shown filed: spoken for logic, filed for paint. */
    private fun filedBook(id: String, author: String) =
        book(id, author = author).copy(shownAuthors = listOf(filedAuthor(author)))

    @Test
    fun `an author chip filters by the spoken name whichever way it is drawn`() {
        // The chip made before the display switch and the chip made after it are the same token,
        // because neither ever held the drawn text. It used to hold it, and went dead on a flip.
        val books = listOf(filedBook("mort", "Terry Pratchett"), filedBook("coraline", "Neil Gaiman"))
        val filter = LibraryFilter().plus(FilterToken(FilterFacet.AUTHOR, "Terry Pratchett"))
        assertEquals(listOf("mort"), books.filter { filter.matches(it) }.map { it.id })
    }

    @Test
    fun `search finds a name typed the way it is spoken while it is drawn filed`() {
        val book = filedBook("mort", "Terry Pratchett")
        assertTrue(book.matchesText("terry pratchett"))
    }

    @Test
    fun `an author heading draws filed and keeps its spoken identity`() {
        val entries = engine.arrange(
            listOf(filedBook("mort", "Terry Pratchett")),
            LibraryFilter(),
            LibrarySort.TITLE,
            LibraryShelving.AUTHOR,
            LibraryDepth.SERIES,
            bySurname = true,
            filedNames = true,
        )
        val heading = entries.filterIsInstance<LibraryEntry.Header>().single()
        assertEquals("Terry Pratchett", heading.title)
        assertEquals("Pratchett, Terry", heading.shown)
    }
}
