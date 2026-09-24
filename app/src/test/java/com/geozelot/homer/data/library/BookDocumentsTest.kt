package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which folders a book takes its PDFs from.
 *
 * Every folder from the book up to (but not including) the library root, plus the part folders its
 * audio is in. Each half of that has a failure invisible until somebody's library shows it: not
 * climbing loses every map filed at the series level, not reading the part folders loses the
 * booklet of every book split across discs, and climbing one step too far staples a stray PDF at
 * the root onto the entire library.
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
    fun `every level is offered, nearest first`() {
        // A booklet, a series map and an author's biography are three documents about this book,
        // not three candidates for one slot. Nearest first, so the book's own leads.
        val docs = mapOf(
            "lib/Author/Series/Book" to listOf("lib/Author/Series/Book/Booklet.pdf"),
            "lib/Author/Series" to listOf("lib/Author/Series/Map.pdf"),
            "lib/Author" to listOf("lib/Author/Bio.pdf"),
        )
        assertEquals(
            listOf(
                "lib/Author/Series/Book/Booklet.pdf",
                "lib/Author/Series/Map.pdf",
                "lib/Author/Bio.pdf",
            ),
            documentPathsFor("lib/Author/Series/Book", root, docs),
        )
    }

    @Test
    fun `a PDF filed with the audio in a part folder belongs to the book`() {
        // The book folder holds no audio at all — it is all under CD1/CD2 — so nothing that only
        // climbs would ever look where the files actually are.
        val docs = mapOf("lib/Author/Book/CD1" to listOf("lib/Author/Book/CD1/Booklet.pdf"))
        assertEquals(
            listOf("lib/Author/Book/CD1/Booklet.pdf"),
            documentPathsFor(
                "lib/Author/Book", root, docs,
                partPaths = listOf("lib/Author/Book/CD1", "lib/Author/Book/CD2"),
            ),
        )
    }

    @Test
    fun `the book folder leads its part folders, and nothing is offered twice`() {
        // A book with no part folders passes its own path as its only member, so the book folder
        // is reached twice — once as itself and once as a "part".
        val docs = mapOf(
            "lib/Author/Book" to listOf("lib/Author/Book/Booklet.pdf"),
            "lib/Author/Book/CD2" to listOf("lib/Author/Book/CD2/Disc notes.pdf"),
        )
        assertEquals(
            listOf("lib/Author/Book/Booklet.pdf", "lib/Author/Book/CD2/Disc notes.pdf"),
            documentPathsFor(
                "lib/Author/Book", root, docs,
                partPaths = listOf("lib/Author/Book", "lib/Author/Book/CD2"),
            ),
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
    fun `a stray PDF at the root reaches nothing, even now everything else accumulates`() {
        val docs = mapOf(
            "lib" to listOf("lib/Backup Manifest.pdf"),
            "lib/Author" to listOf("lib/Author/Bio.pdf"),
        )
        assertEquals(
            listOf("lib/Author/Bio.pdf"),
            documentPathsFor("lib/Author/Book", root, docs),
        )
    }

    @Test
    fun `a book that IS the root keeps the PDFs beside it`() {
        // Degenerate layout — the root directly holds the audio — so the root is that one book's
        // own folder, and a booklet beside it is plainly its own.
        val docs = mapOf("lib" to listOf("lib/Booklet.pdf"))
        assertEquals(listOf("lib/Booklet.pdf"), documentPathsFor("lib", root, docs))
    }

    @Test
    fun `an empty entry contributes nothing and the climb goes on`() {
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
    fun `two documents of the same name are told apart by their folder`() {
        // The collision accumulation made possible: a book folder and its series folder both
        // holding a perfectly reasonably named Booklet.pdf.
        val paths = listOf(
            "Author/Discworld/Mort/Booklet.pdf",
            "Author/Discworld/Booklet.pdf",
            "Author/Discworld/Map.pdf",
        )
        assertEquals(
            listOf("Booklet · Mort", "Booklet · Discworld", "Map"),
            documentLabels(paths),
        )
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
