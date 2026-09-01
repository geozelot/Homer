package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "A collection is a level ABOVE a series", enforced.
 *
 * This exists because the shelf edit dialog spent a release writing a collection's name into every
 * member book's `series` field, so a real library ends up holding books where the two are the same
 * word and the sub-series that used to be there is gone. Every rule below is about either refusing
 * to create that state or healing it where it already exists.
 */
class ShelfInvariantTest {

    private fun book(
        series: String? = null,
        collection: String? = null,
        collectionIndex: Int? = null,
    ) = BookEntity(
        id = "b",
        title = "t",
        author = "a",
        series = series,
        seriesIndex = null,
        collection = collection,
        collectionIndex = collectionIndex,
        genre = null,
        language = null,
        relativePath = "b",
        coverFilePath = null,
        localCoverPath = null,
        chapterTier = 3,
        isMultiFile = false,
        fileCount = 1,
        totalDurationMs = null,
        addedAt = 0,
        updatedAt = 0,
    )

    private fun override(
        series: String? = null,
        collection: String? = null,
        collectionIndex: Int? = null,
    ) = BookOverrideEntity(
        bookId = "b",
        title = null,
        author = null,
        series = series,
        seriesIndex = null,
        collection = collection,
        collectionIndex = collectionIndex,
        hidden = false,
        updatedAt = 1,
    )

    // ── the predicate ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a collection naming the same thing as the series says nothing`() {
        assertTrue(redundantCollection("Discworld", "Discworld"))
    }

    @Test
    fun `a real parent is kept`() {
        assertFalse(redundantCollection("Rincewind", "Discworld"))
    }

    @Test
    fun `no collection is not redundant`() {
        assertFalse(redundantCollection("Discworld", null))
        assertFalse(redundantCollection(null, null))
    }

    @Test
    fun `a collection on a book with no series at all is kept`() {
        // A standalone joining a collection is the whole reason the field is editable per book.
        assertFalse(redundantCollection(null, "Discworld"))
    }

    @Test
    fun `a blank collection is not treated as a match`() {
        // "" == null-ish, and reporting it as redundant would be reporting the wrong reason.
        assertFalse(redundantCollection(null, "   "))
    }

    @Test
    fun `punctuation and case do not save it`() {
        // These arrive from a folder name, a template capture and a keyboard, which differ.
        assertTrue(redundantCollection("Discworld", " discworld "))
        assertTrue(redundantCollection(" Die Hexen", "DIE HEXEN "))
    }

    // ── the detected layer ───────────────────────────────────────────────────────────────────

    @Test
    fun `a detected book drops a collection that only repeats its series`() {
        val cleaned = book(series = "Discworld", collection = "Discworld").withoutRedundantCollection()
        assertNull(cleaned.collection)
    }

    @Test
    fun `the collection index goes with it`() {
        // A position within a collection that does not exist is a number about nothing, and it would
        // go on drawing a "#" on the cover.
        val cleaned = book(series = "Discworld", collection = "Discworld", collectionIndex = 12)
            .withoutRedundantCollection()
        assertNull(cleaned.collectionIndex)
    }

    @Test
    fun `a genuine hierarchy is untouched, index and all`() {
        val kept = book(series = "Rincewind", collection = "Discworld", collectionIndex = 12)
            .withoutRedundantCollection()
        assertEquals("Discworld", kept.collection)
        assertEquals(12, kept.collectionIndex)
    }

    // ── the correction layer ─────────────────────────────────────────────────────────────────

    @Test
    fun `a correction drops a collection that only repeats its series`() {
        val cleaned = override(series = "Discworld", collection = "Discworld", collectionIndex = 3)
            .withoutRedundantCollection()
        assertNull(cleaned.collection)
        assertNull(cleaned.collectionIndex)
    }

    @Test
    fun `a correction keeps a real parent`() {
        val kept = override(series = "Rincewind", collection = "Discworld").withoutRedundantCollection()
        assertEquals("Discworld", kept.collection)
    }

    // ── the read path, which is the one the shelf sees ───────────────────────────────────────

    @Test
    fun `the shelf never sees a collection equal to the series`() {
        val effective = book(series = "Discworld").applyOverride(override(collection = "Discworld"))
        assertNull("the correction only repeats the detected series", effective.collection)
    }

    @Test
    fun `a correction that names a real parent above a DETECTED series survives`() {
        // The case the override-only comparison would miss: the series comes from the folder tree,
        // the collection from a person, and neither field is set on the same side.
        val effective = book(series = "Rincewind").applyOverride(override(collection = "Discworld"))
        assertEquals("Discworld", effective.collection)
    }

    @Test
    fun `a corrected series that matches an existing detected collection drops it`() {
        // Renaming the series to what the collection is already called collapses the level.
        val effective = book(series = "Rincewind", collection = "Discworld")
            .applyOverride(override(series = "Discworld"))
        assertNull(effective.collection)
    }

    @Test
    fun `a book with no override at all is still healed`() {
        // The wreckage the old dialog left is on books that may carry no override — the damage was
        // written to the override table, but a rescan re-derives detection over the top.
        val effective = book(series = "Discworld", collection = "Discworld").applyOverride(null)
        assertNull(effective.collection)
    }

    // ── the re-derive heals rather than merely refusing ──────────────────────────────────────

    @Test
    fun `a re-derive with no matching template still drops a redundant collection`() {
        val cleaned = TemplateApplier.apply(
            book(series = "Discworld", collection = "Discworld", collectionIndex = 4),
            emptyList(),
        )
        assertNull(cleaned.collection)
        assertNull(cleaned.collectionIndex)
    }

    @Test
    fun `planWrites picks up a book that needs healing and stamps it`() {
        // `sameFieldsAs` sees the change, so the row is written and its timestamp moves — which is
        // what lets the fix win the structure facet's newer-wins merge against another device's
        // stale copy.
        val written = TemplateApplier.planWrites(
            listOf(book(series = "Discworld", collection = "Discworld")),
            emptyList(),
            now = 99L,
        )
        assertEquals(1, written.size)
        assertNull(written.single().collection)
        assertEquals(99L, written.single().updatedAt)
    }

    @Test
    fun `planWrites leaves a healthy book alone`() {
        val written = TemplateApplier.planWrites(
            listOf(book(series = "Rincewind", collection = "Discworld")),
            emptyList(),
            now = 99L,
        )
        assertTrue(written.isEmpty())
    }
}
