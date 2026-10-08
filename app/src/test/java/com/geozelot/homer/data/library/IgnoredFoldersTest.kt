package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.sync.facet.BookCorrection
import com.geozelot.homer.data.sync.facet.CorrectionsFacet
import com.geozelot.homer.data.sync.facet.FacetMerge
import com.geozelot.homer.data.sync.facet.IgnoreRule
import com.geozelot.homer.ui.home.LibraryFilterEngine
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules behind ignored folders, and the three places they reach: the shelf, the merge, the file. */
class IgnoredFoldersTest {

    // ── which paths a folder covers ──────────────────────────────────────────────────────────

    @Test
    fun `a folder covers itself and everything inside it`() {
        val ignored = listOf("Incoming")
        assertTrue(IgnoredFolders.covers(ignored, "Incoming"))
        assertTrue(IgnoredFolders.covers(ignored, "Incoming/Author/Book"))
    }

    @Test
    fun `a folder inside a book is not honoured, since skipping it would shorten the book`() {
        val books = listOf("Author/Book", "Other")
        val ignored = listOf("Author/Book/CD2", "Author/Book", "Incoming", "Other/Sub")
        assertEquals(listOf("Author/Book", "Incoming"), IgnoredFolders.outsideBooks(ignored, books))
    }

    @Test
    fun `a folder does not cover a sibling whose name only starts the same`() {
        // A plain prefix test would ignore `Author/Book 2` along with `Author/Book`.
        assertFalse(IgnoredFolders.covers(listOf("Author/Book"), "Author/Book 2"))
        assertFalse(IgnoredFolders.covers(listOf("Author/Book"), "Author"))
    }

    @Test
    fun `slashes either side do not change what is meant`() {
        assertTrue(IgnoredFolders.covers(listOf("Incoming"), "/Incoming/Book/"))
        assertEquals("Incoming/New", IgnoredFolders.normalise(" /Incoming/New/ "))
    }

    @Test
    fun `nothing is covered by an empty list`() {
        assertFalse(IgnoredFolders.covers(emptyList(), "Anything"))
    }

    // ── the stored list ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the stored list has no duplicates, nothing inside something else, and a fixed order`() {
        val tidy = IgnoredFolders.tidy(listOf("b", "/a/", "a/inside", "b", " ", "c/d"))
        assertEquals(listOf("a", "b", "c/d"), tidy)
    }

    @Test
    fun `every folder above one is named, outermost first`() {
        // What the crawl ETags are dropped for, so un-ignoring a folder makes the next scan go back in.
        assertEquals(listOf("A", "A/B", "A/B/C"), IgnoredFolders.ancestorsOf("/A/B/C/"))
        assertEquals(emptyList<String>(), IgnoredFolders.ancestorsOf(""))
    }

    // ── the shelf ────────────────────────────────────────────────────────────────────────────

    private fun book(id: String) = BookEntity(
        id = id,
        title = id.substringAfterLast('/'),
        author = null,
        series = null,
        seriesIndex = null,
        genre = null,
        language = null,
        relativePath = id,
        coverFilePath = null,
        localCoverPath = null,
        chapterTier = 3,
        isMultiFile = false,
        fileCount = 1,
        totalDurationMs = null,
        addedAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `books under an ignored folder are left off the shelf, even with hidden books shown`() {
        val books = listOf(book("Author/Kept"), book("Incoming/New"))
        val hiddenOverride = BookOverrideEntity(
            bookId = "Author/Kept", title = null, author = null, series = null, seriesIndex = null,
            hidden = true, updatedAt = 1,
        )
        val shown = LibraryFilterEngine().effective(
            books, listOf(hiddenOverride), showHidden = true, ignored = listOf("Incoming"),
        ) { null }
        assertEquals(listOf("Author/Kept"), shown.map { it.book.id })
    }

    // ── the shared index ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the newer ignore list wins whole`() {
        val older = CorrectionsFacet(ignoredFolders = IgnoreRule(listOf("a", "b"), editedAt = 100))
        val newer = CorrectionsFacet(ignoredFolders = IgnoreRule(listOf("c"), editedAt = 200))
        assertEquals(listOf("c"), FacetMerge.corrections(older, newer).ignoredFolders?.folders)
        assertEquals(listOf("c"), FacetMerge.corrections(newer, older).ignoredFolders?.folders)
    }

    @Test
    fun `one side's list survives a side that has none, and the books merge as before`() {
        val withList = CorrectionsFacet(ignoredFolders = IgnoreRule(listOf("a"), editedAt = 100))
        val withBook = CorrectionsFacet(books = mapOf("x" to BookCorrection(title = "T", editedAt = 5)))
        val merged = FacetMerge.corrections(withBook, withList)
        assertEquals(listOf("a"), merged.ignoredFolders?.folders)
        assertEquals("T", merged.books["x"]?.title)
        assertNull(FacetMerge.corrections(withBook, withBook).ignoredFolders)
    }

    @Test
    fun `an index written before the list existed reads as no opinion`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(CorrectionsFacet.serializer(), """{"version":3,"books":{},"templates":{}}""")
        assertNull(old.ignoredFolders)
    }

    @Test
    fun `the list round-trips through the file`() {
        val json = Json { ignoreUnknownKeys = true }
        val facet = CorrectionsFacet(ignoredFolders = IgnoreRule(listOf("Incoming", "Neu/Unsortiert"), editedAt = 42, by = "phone"))
        assertEquals(facet, json.decodeFromString(CorrectionsFacet.serializer(), json.encodeToString(CorrectionsFacet.serializer(), facet)))
    }
}
