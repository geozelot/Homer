package com.geozelot.homer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [applyCollapse] — which rows a folded shelf hides, and which covers stand in for them.
 *
 * The rule it implements is that a header owns every entry until the next header, which is the one
 * assumption that lets folding be a post-pass over the built list instead of part of the grouping.
 * The two properties worth pinning are at the edges: the LAST shelf in the list has no following
 * header to close it, and a search must be able to see inside a folded shelf or its matches simply
 * vanish.
 */
class ShelfCollapseTest {

    private fun book(id: String) = BookListItem(
        id = id,
        title = id,
        author = "A",
        isMultiFile = false,
        fileCount = 1,
        coverModel = null,
        hasCustomCover = false,
        series = null,
        seriesIndex = null,
        genre = null,
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

    private fun header(title: String, count: Int) =
        LibraryEntry.Header(title = title, key = "author:$title", count = count)

    private fun shelf(title: String, vararg ids: String): List<LibraryEntry> =
        listOf(header(title, ids.size)) + ids.map { LibraryEntry.Standalone(book(it)) }

    private val twoShelves = shelf("Lawrence", "a", "b", "c") + shelf("Rothfuss", "d", "e")

    @Test
    fun `nothing folded leaves the list untouched`() {
        assertEquals(twoShelves, applyCollapse(twoShelves, emptySet(), searching = false))
    }

    @Test
    fun `a folded shelf keeps its header and drops its rows`() {
        val out = applyCollapse(twoShelves, setOf("author:Lawrence"), searching = false)
        assertEquals(
            listOf("Lawrence", "Rothfuss"),
            out.filterIsInstance<LibraryEntry.Header>().map { it.title },
        )
        assertEquals(
            "only the open shelf's books remain",
            listOf("d", "e"),
            out.filterIsInstance<LibraryEntry.Standalone>().map { it.book.id },
        )
    }

    @Test
    fun `a folded shelf carries its covers as a preview`() {
        val out = applyCollapse(twoShelves, setOf("author:Lawrence"), searching = false)
        val folded = out.filterIsInstance<LibraryEntry.Header>().first { it.title == "Lawrence" }
        assertTrue(folded.collapsed)
        assertEquals(listOf("a", "b", "c"), folded.preview.map { it.id })
    }

    @Test
    fun `the preview stops at the display limit`() {
        val many = shelf("Lawrence", *(1..12).map { "b$it" }.toTypedArray())
        val folded = applyCollapse(many, setOf("author:Lawrence"), searching = false)
            .filterIsInstance<LibraryEntry.Header>()
            .single()
        assertEquals(PREVIEW_COVERS, folded.preview.size)
        assertEquals("the count still reports the whole shelf", 12, folded.count)
    }

    @Test
    fun `the last shelf folds even with no header after it`() {
        // The loop closes a fold when it meets the NEXT header; the final shelf never does, so it
        // needs closing after the loop. Without that its preview is silently empty.
        val out = applyCollapse(twoShelves, setOf("author:Rothfuss"), searching = false)
        val folded = out.filterIsInstance<LibraryEntry.Header>().first { it.title == "Rothfuss" }
        assertTrue(folded.collapsed)
        assertEquals(listOf("d", "e"), folded.preview.map { it.id })
        assertEquals(
            "and the shelf above it is untouched",
            listOf("a", "b", "c"),
            out.filterIsInstance<LibraryEntry.Standalone>().map { it.book.id },
        )
    }

    @Test
    fun `every shelf can be folded at once`() {
        val out = applyCollapse(
            twoShelves,
            setOf("author:Lawrence", "author:Rothfuss"),
            searching = false,
        )
        assertEquals(2, out.size)
        assertTrue(out.all { it is LibraryEntry.Header && it.collapsed })
    }

    @Test
    fun `searching ignores folding entirely`() {
        // A match inside a folded shelf would otherwise be invisible, and a search that silently
        // hides results is worse than one that ignores a browsing preference.
        val out = applyCollapse(twoShelves, setOf("author:Lawrence"), searching = true)
        assertEquals(twoShelves, out)
        assertFalse(out.filterIsInstance<LibraryEntry.Header>().any { it.collapsed })
    }

    @Test
    fun `a series inside a folded shelf contributes its books to the preview`() {
        // A stacked series is ONE entry and several books; a fold that treated it as one cover
        // would show a nearly empty strip for a shelf that is mostly series.
        val entries = listOf(header("Lawrence", 4)) + listOf(
            LibraryEntry.Series(
                key = "k",
                name = "Thorns",
                author = "A",
                books = listOf(book("a"), book("b"), book("c")),
            ),
            LibraryEntry.Standalone(book("d")),
        )
        val folded = applyCollapse(entries, setOf("author:Lawrence"), searching = false)
            .filterIsInstance<LibraryEntry.Header>()
            .single()
        assertEquals(listOf("a", "b", "c", "d"), folded.preview.map { it.id })
    }

    @Test
    fun `a key for a shelf that is not there folds nothing`() {
        // What a stale key from a shelving mode the user has since changed looks like.
        assertEquals(twoShelves, applyCollapse(twoShelves, setOf("genre:Fantasy"), searching = false))
    }
}
