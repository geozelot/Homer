package com.geozelot.homer.data.db.entity

import com.geozelot.homer.data.sync.facet.BookCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That every representation of "the fields an edit can touch" still agrees with the others.
 *
 * Five hand-written copies of this list drifted, and three fields ended up in some of them and not
 * others. The damage was invisible at every individual site: a collection edit was applied locally,
 * counted as an edit by one check, ignored by the next, published by nothing, and nulled on the way
 * back in. Nothing in any one of those functions looked wrong.
 *
 * So the point of this class is not to test [EditFields] — it is a list. It is to fail when the list
 * and one of its representations disagree, including the two that cannot share code with it: the SQL
 * predicate, and the entities themselves.
 */
class EditFieldsTest {

    private val blank = BookOverrideEntity(
        bookId = "b",
        title = null,
        author = null,
        series = null,
        seriesIndex = null,
        hidden = false,
        updatedAt = 0,
    )

    private fun book(
        title: String = "t",
        author: String? = null,
        series: String? = null,
        seriesIndex: Int? = null,
        collection: String? = null,
        collectionIndex: Int? = null,
        genre: String? = null,
        language: String? = null,
    ) = BookEntity(
        id = "b",
        title = title,
        author = author,
        series = series,
        seriesIndex = seriesIndex,
        collection = collection,
        collectionIndex = collectionIndex,
        genre = genre,
        language = language,
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

    /**
     * One way to set each correction column, keyed by its name.
     *
     * Deliberately hand-written HERE, in the test, rather than in production: it is the independent
     * witness. If it were derived from the same accessors it is checking, a missing field would be
     * missing from both and the test would pass.
     */
    private val setCorrection: Map<String, (BookOverrideEntity) -> BookOverrideEntity> = mapOf(
        "title" to { it.copy(title = "x") },
        "author" to { it.copy(author = "x") },
        "series" to { it.copy(series = "x") },
        "seriesIndex" to { it.copy(seriesIndex = 1) },
        "collection" to { it.copy(collection = "x") },
        "collectionIndex" to { it.copy(collectionIndex = 1) },
        "genre" to { it.copy(genre = "x") },
        "language" to { it.copy(language = "de") },
        "tags" to { it.copy(tags = "x") },
    )

    /** The same, for the detected columns a template can write. */
    private val changeDetected: Map<String, (BookEntity) -> BookEntity> = mapOf(
        "title" to { it.copy(title = "other") },
        "author" to { it.copy(author = "other") },
        "series" to { it.copy(series = "other") },
        "seriesIndex" to { it.copy(seriesIndex = 99) },
        "collection" to { it.copy(collection = "other") },
        "collectionIndex" to { it.copy(collectionIndex = 99) },
        "genre" to { it.copy(genre = "other") },
        "language" to { it.copy(language = "fr") },
    )

    /**
     * The real properties of a data class, minus everything the compilers add.
     *
     * Filtered on STATIC rather than on a name pattern: a data class's properties are instance
     * fields, while `$stable` (the Compose compiler), `Companion` and `$childSerializers`
     * (kotlinx.serialization) are all static — and none of them is flagged synthetic, so the obvious
     * filter lets all three through.
     */
    private fun declaredFields(type: Class<*>): List<String> = type.declaredFields
        .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .map { it.name }

    // ── the guard that would have caught the original bug ────────────────────────────────────

    @Test
    fun `every field on the override row is either a correction or the reader's own`() {
        // ADDING A FIELD TO BookOverrideEntity BREAKS THIS until it is declared as one or the other.
        // That is the single question that kept getting answered by accident.
        val unaccounted = declaredFields(BookOverrideEntity::class.java) -
            EditFields.CORRECTION_COLUMNS.toSet() - EditFields.READER_COLUMNS.toSet()
        assertEquals("unaccounted field(s) on BookOverrideEntity", emptyList<String>(), unaccounted)
    }

    @Test
    fun `every field on the book row is either template-writable or explicitly not`() {
        val unaccounted = declaredFields(BookEntity::class.java) -
            EditFields.DETECTED_COLUMNS.toSet() - EditFields.BOOK_OTHER_COLUMNS.toSet()
        assertEquals("unaccounted field(s) on BookEntity", emptyList<String>(), unaccounted)
    }

    @Test
    fun `the published correction carries exactly the correction columns`() {
        // The facet is the third shape the same set takes. `collection` and `collectionIndex` were
        // declared here and on the entity while being read by neither direction.
        val bibliographic = declaredFields(BookCorrection::class.java) -
            setOf("chapters", "editedAt", "editedBy")
        assertEquals(EditFields.CORRECTION_COLUMNS, bibliographic)
    }

    @Test
    fun `no column is claimed by both halves`() {
        val both = EditFields.CORRECTION_COLUMNS.intersect(EditFields.READER_COLUMNS.toSet())
        assertEquals(emptySet<String>(), both)
    }

    // ── the SQL says the same thing as the Kotlin ────────────────────────────────────────────

    @Test
    fun `the SQL predicate names exactly the correction columns`() {
        // It cannot be built from the list — Room needs a compile-time constant — so it is the one
        // place the set is written twice, and this is what holds the two together.
        val expected = EditFields.CORRECTION_COLUMNS.joinToString(" OR ") { "$it IS NOT NULL" }
        assertEquals("($expected)", EditFields.CORRECTED)
    }

    // ── the accessors cover every name ──────────────────────────────────────────────────────

    @Test
    fun `each correction column on its own counts as an edit`() {
        for (column in EditFields.CORRECTION_COLUMNS) {
            val only = setCorrection.getValue(column)(blank)
            assertTrue("setting $column alone was not noticed", EditFields.corrected(only))
        }
    }

    @Test
    fun `each detected column on its own counts as a change`() {
        for (column in EditFields.DETECTED_COLUMNS) {
            val changed = changeDetected.getValue(column)(book())
            assertFalse("changing $column alone was not noticed", EditFields.sameDetected(book(), changed))
        }
    }

    @Test
    fun `the test's own field maps cover every column`() {
        // Without this, a column missing from a map above would make the loops silently shorter.
        assertEquals(EditFields.CORRECTION_COLUMNS.toSet(), setCorrection.keys)
        assertEquals(EditFields.DETECTED_COLUMNS.toSet(), changeDetected.keys)
    }

    @Test
    fun `there is one accessor per named column`() {
        assertEquals(EditFields.CORRECTION_COLUMNS.size, EditFields.correctionArity)
        assertEquals(EditFields.DETECTED_COLUMNS.size, EditFields.detectedArity)
    }

    // ── what must NOT count ─────────────────────────────────────────────────────────────────

    @Test
    fun `a bare row is not an edit`() {
        // A row exists to carry the hidden flag or as a cleared-correction tombstone. Counting those
        // would put every book somebody had ever hidden on the `is:edited` shelf.
        assertFalse(EditFields.corrected(blank))
    }

    @Test
    fun `the reader's own fields are not edits`() {
        for (row in listOf(
            blank.copy(hidden = true),
            blank.copy(finished = true),
            blank.copy(downloadOnPlay = true),
            blank.copy(updatedAt = 999),
        )) {
            assertFalse(EditFields.corrected(row))
        }
    }

    @Test
    fun `tags are a correction but never a detected field`() {
        // Nothing detects a tag, so a template has nothing to write and a re-derive nothing to
        // compare — which is why the two sets differ by exactly this one name.
        assertTrue("tags" in EditFields.CORRECTION_COLUMNS)
        assertFalse("tags" in EditFields.DETECTED_COLUMNS)
        assertEquals(EditFields.CORRECTION_COLUMNS - "tags", EditFields.DETECTED_COLUMNS)
    }
}
