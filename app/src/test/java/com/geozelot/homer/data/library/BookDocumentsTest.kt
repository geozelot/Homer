package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which folder a book takes its PDFs from.
 *
 * The rule under test is short — nearest folder wins, the climb stops short of the library root —
 * and both halves of it are here because both have a failure that is invisible until somebody's
 * library shows it: forgetting to climb loses every booklet filed at the series level, and
 * climbing one step too far staples a stray PDF at the root onto every book in the library.
 */
class BookDocumentsTest {

    private val root = "lib"

    @Test
    fun `a book takes the PDFs in its own folder`() {
        val docs = mapOf("lib/Author/Book" to listOf("lib/Author/Book/Booklet.pdf"))
        assertEquals(
            listOf("lib/Author/Book/Booklet.pdf"),
            documentPathsFor("lib/Author/Book", root, docs),
        )
    }

    @Test
    fun `a book with none of its own climbs to the series folder`() {
        val docs = mapOf("lib/Author/Series" to listOf("lib/Author/Series/Map.pdf"))
        assertEquals(
            listOf("lib/Author/Series/Map.pdf"),
            documentPathsFor("lib/Author/Series/Book", root, docs),
        )
    }

    @Test
    fun `the nearest folder wins outright, and the ones above it are not added`() {
        val docs = mapOf(
            "lib/Author/Series/Book" to listOf("lib/Author/Series/Book/Booklet.pdf"),
            "lib/Author/Series" to listOf("lib/Author/Series/Map.pdf"),
            "lib/Author" to listOf("lib/Author/Bio.pdf"),
        )
        assertEquals(
            listOf("lib/Author/Series/Book/Booklet.pdf"),
            documentPathsFor("lib/Author/Series/Book", root, docs),
        )
    }

    @Test
    fun `a PDF at the library root belongs to nothing`() {
        // The widest false positive there is: one stray export at the root would otherwise show up
        // as a booklet on every book in the library that has none of its own.
        val docs = mapOf("lib" to listOf("lib/Backup Manifest.pdf"))
        assertTrue(documentPathsFor("lib/Author/Book", root, docs).isEmpty())
    }

    @Test
    fun `a library mounted at the server root still stops short of it`() {
        val docs = mapOf(
            "" to listOf("Stray.pdf"),
            "Author" to listOf("Author/Bio.pdf"),
        )
        assertEquals(listOf("Author/Bio.pdf"), documentPathsFor("Author/Book", "", docs))
    }

    @Test
    fun `a book that IS the root keeps the PDFs beside it`() {
        // Degenerate layout — the root directly holds the audio — so the root is that one book's
        // own folder, and a booklet beside it is plainly its own.
        val docs = mapOf("lib" to listOf("lib/Booklet.pdf"))
        assertEquals(listOf("lib/Booklet.pdf"), documentPathsFor("lib", root, docs))
    }

    @Test
    fun `an empty entry is not an answer and the climb goes on`() {
        val docs = mapOf(
            "lib/Author/Series/Book" to emptyList<String>(),
            "lib/Author/Series" to listOf("lib/Author/Series/Map.pdf"),
        )
        assertEquals(
            listOf("lib/Author/Series/Map.pdf"),
            documentPathsFor("lib/Author/Series/Book", root, docs),
        )
    }

    @Test
    fun `the stored form round-trips, and no documents stays an absence`() {
        val paths = listOf("a/Booklet.pdf", "a/Score.pdf")
        assertEquals(paths, decodeDocuments(encodeDocuments(paths)))
        assertNull(encodeDocuments(emptyList()))
        assertNull(encodeDocuments(listOf("", "   ")))
        assertTrue(decodeDocuments(null).isEmpty())
    }

    @Test
    fun `a document is labelled by its file name, without the extension`() {
        assertEquals("Booklet", documentLabel("Author/Book/Booklet.pdf"))
        assertEquals("Booklet", documentLabel("Booklet.pdf"))
        assertEquals("a.b Notes", documentLabel("x/a.b Notes.pdf"))
    }

    @Test
    fun `only PDFs are documents`() {
        assertTrue(AudioFormats.isDocument("Booklet.pdf"))
        assertTrue(AudioFormats.isDocument("BOOKLET.PDF"))
        assertTrue(!AudioFormats.isDocument("cover.jpg"))
        assertTrue(!AudioFormats.isDocument("notes.epub"))
        assertTrue(!AudioFormats.isDocument("pdf"))
    }
}
