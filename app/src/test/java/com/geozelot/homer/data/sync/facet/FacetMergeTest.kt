package com.geozelot.homer.data.sync.facet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reconciliation rules. Each facet merges differently, and the differences are the point —
 * so the tests are grouped by the property each rule is supposed to guarantee.
 */
class FacetMergeTest {

    private fun book(title: String = "T", updatedAt: Long = 0, hash: String? = null) =
        StructureBook(title = title, contentHash = hash, updatedAt = updatedAt)

    private fun structure(
        vararg books: Pair<String, StructureBook>,
        crawl: CrawlMarker? = null,
    ) = StructureFacet(lastFullCrawl = crawl, books = books.toMap())

    private fun derived(vararg books: Pair<String, DerivedBook>) = DerivedFacet(books = books.toMap())

    private fun corrections(vararg books: Pair<String, BookCorrection>) =
        CorrectionsFacet(books = books.toMap())

    // ── structure: union and recency ─────────────────────────────────────────────────────────

    @Test
    fun `books from both sides survive`() {
        val merged = FacetMerge.structure(
            structure("a" to book("A")),
            structure("b" to book("B")),
        )
        assertEquals(setOf("a", "b"), merged.books.keys)
    }

    @Test
    fun `the newer entry for a book wins`() {
        val merged = FacetMerge.structure(
            structure("a" to book("Old", updatedAt = 100)),
            structure("a" to book("New", updatedAt = 200)),
        )
        assertEquals("New", merged.books.getValue("a").title)
    }

    @Test
    fun `a tie keeps the local entry`() {
        val merged = FacetMerge.structure(
            structure("a" to book("Local", updatedAt = 100)),
            structure("a" to book("Remote", updatedAt = 100)),
        )
        assertEquals("Local", merged.books.getValue("a").title)
    }

    // ── structure: deletion, the whole reason for the marker ─────────────────────────────────

    @Test
    fun `without a full crawl nothing is ever deleted`() {
        // The old union behaviour, and correct in the absence of evidence: no device has seen the
        // whole tree, so an absence proves nothing.
        val merged = FacetMerge.structure(
            structure("gone" to book(updatedAt = 100)),
            structure("kept" to book(updatedAt = 100)),
        )
        assertEquals(setOf("gone", "kept"), merged.books.keys)
    }

    @Test
    fun `a complete crawl removes what it did not see`() {
        // This is the resurrection bug: the scanner drops a book, the union puts it back, forever.
        val merged = FacetMerge.structure(
            local = structure("stale" to book(updatedAt = 100), "kept" to book(updatedAt = 100)),
            remote = structure("kept" to book(updatedAt = 100), crawl = CrawlMarker(at = 200, by = "d2")),
        )
        assertEquals(setOf("kept"), merged.books.keys)
    }

    @Test
    fun `a crawl cannot delete a book added after it ran`() {
        // Device 2 crawled at 200; device 1 added a book at 300. The crawl never had a chance to
        // see it, so its silence says nothing about it.
        val merged = FacetMerge.structure(
            local = structure("added-later" to book(updatedAt = 300)),
            remote = structure("known" to book(updatedAt = 100), crawl = CrawlMarker(at = 200, by = "d2")),
        )
        assertEquals(setOf("added-later", "known"), merged.books.keys)
    }

    @Test
    fun `the older crawl does not get to prune`() {
        val merged = FacetMerge.structure(
            local = structure("only-in-new" to book(updatedAt = 50), crawl = CrawlMarker(at = 400, by = "d1")),
            remote = structure("only-in-old" to book(updatedAt = 50), crawl = CrawlMarker(at = 100, by = "d2")),
        )
        // d1's crawl is newer, so d1's view decides: the book only d2 knew about is gone.
        assertEquals(setOf("only-in-new"), merged.books.keys)
        assertEquals(400L, merged.lastFullCrawl?.at)
        assertEquals("d1", merged.lastFullCrawl?.by)
    }

    @Test
    fun `the newer crawl marker is carried forward`() {
        val merged = FacetMerge.structure(
            structure("a" to book(), crawl = CrawlMarker(at = 100, by = "d1")),
            structure("a" to book(), crawl = CrawlMarker(at = 300, by = "d2")),
        )
        assertEquals(CrawlMarker(at = 300, by = "d2"), merged.lastFullCrawl)
    }

    @Test
    fun `an incremental scan carries no marker and prunes nothing`() {
        val merged = FacetMerge.structure(
            local = structure("a" to book(updatedAt = 10), "b" to book(updatedAt = 10)),
            remote = structure("a" to book(updatedAt = 20)),
        )
        assertEquals(setOf("a", "b"), merged.books.keys)
        assertNull(merged.lastFullCrawl)
    }

    // ── derived: partial truths must combine ─────────────────────────────────────────────────

    @Test
    fun `one device's durations and another's genre both survive`() {
        // The failure the whole rework exists for: whole-book merging lost one of these.
        val merged = FacetMerge.derived(
            derived("a" to DerivedBook(fileDurationsMs = mapOf("f1" to 1000), updatedAt = 100)),
            derived("a" to DerivedBook(genre = "Fantasy", updatedAt = 200)),
        )
        val b = merged.books.getValue("a")
        assertEquals("Fantasy", b.genre)
        assertEquals(mapOf("f1" to 1000L), b.fileDurationsMs)
    }

    @Test
    fun `two partial duration sweeps union`() {
        val merged = FacetMerge.derived(
            derived("a" to DerivedBook(fileDurationsMs = mapOf("f1" to 1, "f2" to 2), updatedAt = 100)),
            derived("a" to DerivedBook(fileDurationsMs = mapOf("f3" to 3), updatedAt = 200)),
        )
        assertEquals(mapOf("f1" to 1L, "f2" to 2L, "f3" to 3L), merged.books.getValue("a").fileDurationsMs)
    }

    @Test
    fun `a value always beats no value, whichever side is newer`() {
        val staleHasIt = FacetMerge.derived(
            derived("a" to DerivedBook(genre = "Fantasy", updatedAt = 100)),
            derived("a" to DerivedBook(genre = null, updatedAt = 999)),
        )
        assertEquals("Fantasy", staleHasIt.books.getValue("a").genre)
    }

    @Test
    fun `a real disagreement goes to the newer side`() {
        val merged = FacetMerge.derived(
            derived("a" to DerivedBook(genre = "Other", updatedAt = 100)),
            derived("a" to DerivedBook(genre = "Fantasy", updatedAt = 200)),
        )
        assertEquals("Fantasy", merged.books.getValue("a").genre)
    }

    @Test
    fun `a cached cover is a fact about the server, so either side asserting it is enough`() {
        val merged = FacetMerge.derived(
            derived("a" to DerivedBook(hasCachedCover = true, updatedAt = 100)),
            derived("a" to DerivedBook(hasCachedCover = false, updatedAt = 900)),
        )
        assertTrue(merged.books.getValue("a").hasCachedCover)
    }

    @Test
    fun `chapters travel with the tier that established them`() {
        // An empty list from a device that never probed must not erase a real chapter list.
        val merged = FacetMerge.derived(
            derived("a" to DerivedBook(chapterTier = "EMBEDDED", chapters = listOf(DerivedChapter(0, "One")), updatedAt = 100)),
            derived("a" to DerivedBook(chapterTier = null, chapters = emptyList(), updatedAt = 900)),
        )
        val b = merged.books.getValue("a")
        assertEquals("EMBEDDED", b.chapterTier)
        assertEquals(listOf(DerivedChapter(0, "One")), b.chapters)
    }

    @Test
    fun `an established absence of chapters is still an answer`() {
        val merged = FacetMerge.derived(
            derived("a" to DerivedBook(chapterTier = null, updatedAt = 100)),
            derived("a" to DerivedBook(chapterTier = "NONE", updatedAt = 200)),
        )
        assertEquals("NONE", merged.books.getValue("a").chapterTier)
    }

    // ── corrections: a deliberate act, replaced whole ────────────────────────────────────────

    @Test
    fun `the newest edit wins`() {
        val merged = FacetMerge.corrections(
            corrections("a" to BookCorrection(title = "Old", editedAt = 100)),
            corrections("a" to BookCorrection(title = "New", editedAt = 200)),
        )
        assertEquals("New", merged.books.getValue("a").title)
    }

    @Test
    fun `a correction can be cleared by a newer entry that omits it`() {
        // Per-field non-null-wins would make this impossible: the stale title would always win.
        val merged = FacetMerge.corrections(
            corrections("a" to BookCorrection(title = "Wrong", genre = "Fantasy", editedAt = 100)),
            corrections("a" to BookCorrection(genre = "Fantasy", editedAt = 200)),
        )
        assertNull(merged.books.getValue("a").title)
        assertEquals("Fantasy", merged.books.getValue("a").genre)
    }

    @Test
    fun `corrections from different books both survive`() {
        val merged = FacetMerge.corrections(
            corrections("a" to BookCorrection(title = "A", editedAt = 100)),
            corrections("b" to BookCorrection(title = "B", editedAt = 100)),
        )
        assertEquals(setOf("a", "b"), merged.books.keys)
    }

    @Test
    fun `the editing device is carried so the UI can say where a change came from`() {
        val merged = FacetMerge.corrections(
            corrections("a" to BookCorrection(title = "A", editedAt = 100, editedBy = "phone")),
            corrections("a" to BookCorrection(title = "B", editedAt = 200, editedBy = "tablet")),
        )
        assertEquals("tablet", merged.books.getValue("a").editedBy)
    }

    // ── the facets are independent ───────────────────────────────────────────────────────────

    @Test
    fun `merging is stable when a side is empty`() {
        val s = structure("a" to book("A", updatedAt = 5))
        assertEquals(s.books, FacetMerge.structure(s, StructureFacet()).books)
        assertEquals(s.books, FacetMerge.structure(StructureFacet(), s).books)

        val d = derived("a" to DerivedBook(genre = "G", updatedAt = 5))
        assertEquals(d.books, FacetMerge.derived(d, DerivedFacet()).books)
        assertEquals(d.books, FacetMerge.derived(DerivedFacet(), d).books)

        val c = corrections("a" to BookCorrection(title = "C", editedAt = 5))
        assertEquals(c.books, FacetMerge.corrections(c, CorrectionsFacet()).books)
        assertEquals(c.books, FacetMerge.corrections(CorrectionsFacet(), c).books)
    }

    @Test
    fun `an empty derived book does not claim a cached cover`() {
        val merged = FacetMerge.derived(derived("a" to DerivedBook()), DerivedFacet())
        assertFalse(merged.books.getValue("a").hasCachedCover)
    }
}
