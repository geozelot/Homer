package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Several genres in the column that used to hold one.
 *
 * The rules that matter are all about NOT breaking what is already stored: a list of one has to
 * round-trip as the bare string every earlier build wrote, and a detected value has to survive
 * whatever punctuation the tagger used.
 */
class GenreListTest {

    // ── the old shape still reads ────────────────────────────────────────────────────────────

    @Test
    fun `a single stored genre reads as a list of one`() {
        assertEquals(listOf("Fantasy"), decodeGenres("Fantasy"))
    }

    @Test
    fun `a single genre round-trips as the bare string it always was`() {
        // No migration exists, so this is the assertion that every row written before today is fine.
        assertEquals("Fantasy", encodeGenres(listOf("Fantasy")))
    }

    @Test
    fun `nothing stored is nothing, not an empty list of one`() {
        assertEquals(emptyList<String>(), decodeGenres(null))
        assertEquals(emptyList<String>(), decodeGenres(""))
        assertNull(primaryGenre(null))
    }

    @Test
    fun `no genres encodes to null rather than an empty string`() {
        // The shelf treats "no genre" as its own heading; "" would sort in among the real ones.
        assertNull(encodeGenres(emptyList()))
        assertNull(encodeGenres(listOf("  ", "")))
    }

    // ── order carries meaning ────────────────────────────────────────────────────────────────

    @Test
    fun `the first genre is the one it shelves under`() {
        assertEquals("Fantasy", primaryGenre("Fantasy\nHumour\nSatire"))
    }

    @Test
    fun `reordering changes the stored value, so it counts as an edit`() {
        // BookEditor compares the encoded form. If this were a Set, moving a genre to the front —
        // which is how the shelf is chosen — would be silently discarded as "no change".
        assertEquals("Humour\nFantasy", genresFromInput("Humour, Fantasy"))
        assertEquals("Fantasy\nHumour", genresFromInput("Fantasy, Humour"))
    }

    // ── typed input ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `commas separate what somebody typed`() {
        assertEquals("Fantasy\nHumour", genresFromInput("Fantasy, Humour"))
    }

    @Test
    fun `whitespace and empty entries are forgiven`() {
        assertEquals("Fantasy\nHumour", genresFromInput("  Fantasy ,, Humour  ,  "))
    }

    @Test
    fun `a duplicate is a slip, not a second genre`() {
        assertEquals("Fantasy", genresFromInput("Fantasy, fantasy, FANTASY"))
    }

    @Test
    fun `the first spelling wins, because it is the shelf heading`() {
        assertEquals("Fantasy\nHumour", genresFromInput("Fantasy, Humour, FANTASY"))
    }

    @Test
    fun `an empty field clears the genres`() {
        assertNull(genresFromInput(""))
        assertNull(genresFromInput("  ,  "))
    }

    // ── detected values are not reinterpreted ────────────────────────────────────────────────

    @Test
    fun `a comma inside a DETECTED genre is not a delimiter`() {
        // "Fantasy, Humor" in an ID3 frame is the tagger's punctuation. Splitting it would invent a
        // second genre nobody wrote, and it would do so for every book in the library at once.
        assertEquals(listOf("Fantasy, Humor"), decodeGenres("Fantasy, Humor"))
    }

    @Test
    fun `a semicolon is left alone too`() {
        assertEquals(listOf("Fantasy; Humor"), decodeGenres("Fantasy; Humor"))
    }

    // ── the display form ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the dialog shows the list comma-separated`() {
        assertEquals("Fantasy, Humour", genresToInput("Fantasy\nHumour"))
        assertEquals("", genresToInput(null))
    }

    @Test
    fun `what the dialog shows is what the dialog accepts`() {
        val stored = "Fantasy\nHumour\nSatire"
        assertEquals(stored, genresFromInput(genresToInput(stored)))
    }
}
