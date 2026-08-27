package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two behaviours that decide whether this feature works at all: applying a template REPLACES
 * what was there, and a field the template says nothing about is left alone.
 *
 * [TemplateApplier.apply] and the template-list compilation are pure and live on the companion, so
 * they are tested directly; the DAO-driven halves are a loop and a chunked write over them.
 */
class TemplateApplierTest {

    private fun book(
        id: String,
        title: String = "old title",
        author: String? = "old author",
        series: String? = null,
        seriesIndex: Int? = null,
        collection: String? = null,
        genre: String? = null,
        language: String? = null,
    ) = BookEntity(
        id = id,
        title = title,
        author = author,
        series = series,
        seriesIndex = seriesIndex,
        collection = collection,
        genre = genre,
        language = language,
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

    private fun templates(vararg raw: String) = raw.mapNotNull { PathTemplate.compile(it) }

    // ── the trap this class exists for ───────────────────────────────────────────────────────

    @Test
    fun `applying REPLACES a value that is already there`() {
        // The enricher fills only nulls, which applied here would make the whole feature look
        // broken: the book already has an author, so fill-if-null would leave the wrong one.
        val before = book("Pratchett/Sourcery", title = "old title", author = "old author")
        val after = TemplateApplier.apply(before, templates("{author}/{title}"))
        assertEquals("Pratchett", after.author)
        assertEquals("Sourcery", after.title)
    }

    @Test
    fun `a field the template does not mention is left alone`() {
        // A template about folder names is not a claim that the book has no genre, and treating it
        // as one would wipe every genre in the library on first use.
        val before = book("Pratchett/Sourcery", genre = "Fantasy", language = "en")
        val after = TemplateApplier.apply(before, templates("{author}/{title}"))
        assertEquals("Fantasy", after.genre)
        assertEquals("en", after.language)
    }

    @Test
    fun `a path no template fits is returned untouched`() {
        val before = book("a/b/c/d/e/f/g", title = "kept")
        val after = TemplateApplier.apply(before, templates("{author}/{title}"))
        assertEquals(before, after)
    }

    @Test
    fun `the id is never touched`() {
        val before = book("Pratchett/Sourcery")
        val after = TemplateApplier.apply(before, templates("{author}/{title}"))
        assertEquals(before.id, after.id)
        assertEquals(before.relativePath, after.relativePath)
    }

    @Test
    fun `a title is never emptied`() {
        // A book with no title is unreachable on the shelf, so the book's own is the floor.
        val before = book("Pratchett/Sourcery", title = "kept")
        val after = TemplateApplier.apply(before, templates("{author}/{*}"))
        assertEquals("kept", after.title)
    }

    // ── what it reads ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the bracketed sub-series case`() {
        val before = book("Pratchett/Sourcery (Rincewind 2)")
        val after = TemplateApplier.apply(before, templates("{author}/{title} ({series} {index})"))
        assertEquals("Sourcery", after.title)
        assertEquals("Rincewind", after.series)
        assertEquals(2, after.seriesIndex)
    }

    @Test
    fun `a captured language is normalised like any other`() {
        // So a template capturing "German" and one capturing "de" produce the same shelf.
        val before = book("German/Sourcery")
        val after = TemplateApplier.apply(before, templates("{language}/{title}"))
        assertEquals("de", after.language)
    }

    @Test
    fun `a user template beats the conventional default`() {
        val before = book("Pratchett/Sourcery (Discworld)")
        val after = TemplateApplier.apply(
            before,
            templates("{author}/{title} ({collection})") + PathTemplate.DEFAULTS,
        )
        assertEquals("Discworld", after.collection)
        assertEquals("Sourcery", after.title)
    }

    // ── the no-change test that keeps a re-derive cheap ──────────────────────────────────────

    @Test
    fun `a book the templates agree with is reported as unchanged`() {
        // Only differences are written; without this every book's updatedAt would move and the
        // shared index would republish the entire library on every apply.
        val before = book("Pratchett/Sourcery", title = "Sourcery", author = "Pratchett")
        val after = TemplateApplier.apply(before, templates("{author}/{title}"))
        assertTrue(before.sameFieldsAs(after))
    }

    @Test
    fun `a changed book is not reported as unchanged`() {
        val before = book("Pratchett/Sourcery", title = "wrong")
        val after = TemplateApplier.apply(before, templates("{author}/{title}"))
        assertFalse(before.sameFieldsAs(after))
        assertNotEquals(before.title, after.title)
    }

    @Test
    fun `sameFieldsAs ignores fields a template cannot write`() {
        // Otherwise a cover cached between two runs would count as a change and rewrite the row.
        val a = book("x", title = "t")
        val b = a.copy(localCoverPath = "/cache/x.jpg", updatedAt = 99, coverAttempted = true)
        assertTrue(a.sameFieldsAs(b))
    }

    // ── compiling a stored list ──────────────────────────────────────────────────────────────

    @Test
    fun `a line that does not compile is dropped and the rest survive`() {
        // A typo must not take the working templates down with it.
        val active = TemplateApplier.templatesFrom(listOf("{author}/{narrator}/{title}", "{author}/{title}"))
        assertEquals(PathTemplate.DEFAULTS.size + 1, active.size)
    }

    @Test
    fun `the defaults always sit behind the user's own`() {
        val active = TemplateApplier.templatesFrom(emptyList())
        assertEquals(PathTemplate.DEFAULTS.size, active.size)
    }
}
