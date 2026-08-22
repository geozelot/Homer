package com.geozelot.homer.data.sync

import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.ChapterTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shared-catalog → local-row mapping. The catalog carries the *library's* view of a
 * book, so everything device-local has to be carried over from the existing row instead of written
 * as null: this row is upserted straight over the old one. Losing `contentHash` here is the
 * expensive one — re-linking a moved or renamed book requires a non-null hash, so blanking it
 * permanently orphans that book's progress and bookmarks the next time its folder moves.
 */
class CatalogToBookTest {

    private fun catalogBook(contentHash: String? = "remote-hash") = CatalogBook(
        title = "Shared Title",
        author = "Shared Author",
        contentHash = contentHash,
        isMultiFile = true,
        totalDurationMs = 1_000L,
        updatedAt = 500L,
    )

    private fun localBook() = BookEntity(
        id = "Author/Book",
        contentHash = "local-hash",
        title = "Local Title",
        author = "Local Author",
        series = null,
        seriesIndex = null,
        relativePath = "Author/Book",
        coverFilePath = null,
        localCoverPath = "file:///covers/abc.img",
        customCoverPath = "file:///covers/custom.img",
        coverAttempted = true,
        metadataAttempted = true,
        chapterTier = ChapterTier.EMBEDDED,
        isMultiFile = true,
        fileCount = 3,
        totalDurationMs = 999L,
        addedAt = 42L,
        updatedAt = 100L,
    )

    @Test
    fun `device-local fields survive consuming the shared catalog`() {
        val result = catalogBook().toBook("Author/Book", localBook())

        // The library's view wins for shared metadata…
        assertEquals("Shared Title", result.title)
        assertEquals("Shared Author", result.author)
        // …but nothing device-local may be blanked by the upsert.
        assertEquals("file:///covers/abc.img", result.localCoverPath)
        assertEquals("file:///covers/custom.img", result.customCoverPath)
        assertTrue(result.coverAttempted)
        assertTrue(result.metadataAttempted)
        assertEquals(ChapterTier.EMBEDDED, result.chapterTier)
        assertEquals(42L, result.addedAt) // original add time, not "now"
    }

    @Test
    fun `catalog content hash is used when present`() {
        assertEquals("remote-hash", catalogBook().toBook("Author/Book", localBook()).contentHash)
    }

    @Test
    fun `a catalog without a content hash falls back to the local one`() {
        // Older builds published no hash. Taking the catalog's null would break move re-linking.
        assertEquals("local-hash", catalogBook(contentHash = null).toBook("Author/Book", localBook()).contentHash)
    }

    @Test
    fun `a book this device has never seen keeps the catalog values and no local state`() {
        val result = catalogBook().toBook("Author/Book", local = null)

        assertEquals("remote-hash", result.contentHash)
        assertNull(result.localCoverPath)
        assertNull(result.customCoverPath)
        assertEquals(ChapterTier.UNDETERMINED, result.chapterTier)
    }
}
