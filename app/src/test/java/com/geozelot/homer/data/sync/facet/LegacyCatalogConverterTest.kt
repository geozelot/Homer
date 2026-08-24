package com.geozelot.homer.data.sync.facet

import com.geozelot.homer.data.sync.CatalogBook
import com.geozelot.homer.data.sync.CatalogFile
import com.geozelot.homer.data.sync.HomerCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-time v1 migration. What it refuses to do matters more than what it copies: a legacy
 * catalog cannot tell a human correction from a detected value, and cannot testify that anyone
 * saw the whole library.
 */
class LegacyCatalogConverterTest {

    private fun legacy(vararg books: Pair<String, CatalogBook>) = HomerCatalog(books = books.toMap())

    private fun file(path: String, duration: Long? = null, index: Int = 0) = CatalogFile(
        relativePath = path,
        fileName = path.substringAfterLast('/'),
        sortIndex = index,
        sizeBytes = 4096,
        durationMs = duration,
        etag = "etag-$path",
        lastModifiedMs = 1700000000000,
        contentType = "audio/mpeg",
    )

    private val full = CatalogBook(
        title = "Der Wüstenplanet",
        author = "Frank Herbert",
        series = "Dune",
        seriesIndex = 1,
        genre = "Science-Fiction",
        contentHash = "hash-1",
        coverFilePath = "Dune/cover.jpg",
        hasCachedCover = true,
        totalDurationMs = 9_000,
        isMultiFile = true,
        files = listOf(file("Dune/01.mp3", 4_000, 0), file("Dune/02.mp3", 5_000, 1)),
        updatedAt = 1_700,
    )

    // ── what it carries ──────────────────────────────────────────────────────────────────────

    @Test
    fun `structural fields land in structure`() {
        val s = LegacyCatalogConverter.convert(legacy("dune" to full)).structure.books.getValue("dune")
        assertEquals("Der Wüstenplanet", s.title)
        assertEquals("Frank Herbert", s.author)
        assertEquals("Dune", s.series)
        assertEquals(1, s.seriesIndex)
        assertEquals("hash-1", s.contentHash)
        assertEquals("Dune/cover.jpg", s.coverFilePath)
        assertTrue(s.isMultiFile)
        assertEquals(1_700L, s.updatedAt)
    }

    @Test
    fun `the content hash survives, because losing it orphans a renamed book`() {
        // Re-linking after a folder rename requires a non-null hash; a book that arrives without
        // one can never be recognised again, taking its position and bookmarks with it.
        val s = LegacyCatalogConverter.convert(legacy("dune" to full)).structure.books.getValue("dune")
        assertEquals("hash-1", s.contentHash)
    }

    @Test
    fun `file identity goes to structure and file durations go to derived`() {
        val out = LegacyCatalogConverter.convert(legacy("dune" to full))
        val files = out.structure.books.getValue("dune").files
        assertEquals(listOf("Dune/01.mp3", "Dune/02.mp3"), files.map { it.relativePath })
        assertEquals(listOf("etag-Dune/01.mp3", "etag-Dune/02.mp3"), files.map { it.etag })
        assertEquals(
            mapOf("Dune/01.mp3" to 4_000L, "Dune/02.mp3" to 5_000L),
            out.derived.books.getValue("dune").fileDurationsMs,
        )
    }

    @Test
    fun `computed fields land in derived`() {
        val d = LegacyCatalogConverter.convert(legacy("dune" to full)).derived.books.getValue("dune")
        assertEquals("Science-Fiction", d.genre)
        assertEquals(9_000L, d.totalDurationMs)
        assertTrue(d.hasCachedCover)
        assertEquals(1_700L, d.updatedAt)
    }

    @Test
    fun `an unmeasured file contributes no duration`() {
        val partly = full.copy(files = listOf(file("Dune/01.mp3", 4_000, 0), file("Dune/02.mp3", null, 1)))
        val d = LegacyCatalogConverter.convert(legacy("dune" to partly)).derived.books.getValue("dune")
        assertEquals(mapOf("Dune/01.mp3" to 4_000L), d.fileDurationsMs)
    }

    // ── what it refuses to invent ────────────────────────────────────────────────────────────

    @Test
    fun `no corrections are claimed`() {
        // v1 published effective values, so a hand-fixed title is indistinguishable from a derived
        // one. Claiming any as deliberate would outrank every future detection on every device.
        val out = LegacyCatalogConverter.convert(legacy("dune" to full))
        assertTrue(out.corrections.books.isEmpty())
    }

    @Test
    fun `no crawl marker is stamped`() {
        // A legacy catalog is not evidence anyone saw the whole tree. Stamping one would hand it
        // the authority to delete books.
        val out = LegacyCatalogConverter.convert(legacy("dune" to full))
        assertNull(out.structure.lastFullCrawl)
    }

    @Test
    fun `a converted catalog cannot prune on a later merge`() {
        // The property the missing marker is there to guarantee, stated end to end.
        val converted = LegacyCatalogConverter.convert(legacy("dune" to full)).structure
        val other = StructureFacet(books = mapOf("other" to StructureBook(title = "Other", updatedAt = 1)))
        val merged = FacetMerge.structure(other, converted)
        assertEquals(setOf("other", "dune"), merged.books.keys)
    }

    @Test
    fun `chapters are not invented, because v1 never carried them`() {
        val d = LegacyCatalogConverter.convert(legacy("dune" to full)).derived.books.getValue("dune")
        assertNull(d.chapterTier)
        assertTrue(d.chapters.isEmpty())
    }

    @Test
    fun `a book that taught us nothing gets no derived entry`() {
        // An empty entry would merge into other devices as "I looked and found nothing", which is
        // a claim this catalog cannot support.
        val bare = CatalogBook(title = "Bare", files = listOf(file("Bare/01.mp3")), updatedAt = 5)
        val out = LegacyCatalogConverter.convert(legacy("bare" to bare))
        assertTrue(out.structure.books.containsKey("bare"))
        assertTrue(out.derived.books.isEmpty())
    }

    @Test
    fun `a cached cover alone is worth a derived entry`() {
        val coverOnly = CatalogBook(title = "Cover", hasCachedCover = true, updatedAt = 5)
        val out = LegacyCatalogConverter.convert(legacy("c" to coverOnly))
        assertTrue(out.derived.books.getValue("c").hasCachedCover)
    }

    @Test
    fun `an empty catalog converts to three empty facets`() {
        val out = LegacyCatalogConverter.convert(HomerCatalog())
        assertTrue(out.structure.books.isEmpty())
        assertTrue(out.derived.books.isEmpty())
        assertTrue(out.corrections.books.isEmpty())
        assertNull(out.structure.lastFullCrawl)
    }

    @Test
    fun `every book reaches structure even when only some are measured`() {
        val bare = CatalogBook(title = "Bare", updatedAt = 5)
        val out = LegacyCatalogConverter.convert(legacy("dune" to full, "bare" to bare))
        assertEquals(setOf("dune", "bare"), out.structure.books.keys)
        assertEquals(setOf("dune"), out.derived.books.keys)
    }
}
