package com.geozelot.homer.data.library

import java.util.Locale
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
        assertEquals("humour\nfantasy", genresFromInput("Humour, Fantasy"))
        assertEquals("fantasy\nhumour", genresFromInput("Fantasy, Humour"))
    }

    // ── typed input ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `commas separate what somebody typed`() {
        assertEquals("fantasy\nhumour", genresFromInput("Fantasy, Humour"))
    }

    @Test
    fun `whitespace and empty entries are forgiven`() {
        assertEquals("fantasy\nhumour", genresFromInput("  Fantasy ,, Humour  ,  "))
    }

    @Test
    fun `a duplicate is a slip, not a second genre`() {
        assertEquals("fantasy", genresFromInput("Fantasy, fantasy, FANTASY"))
    }

    @Test
    fun `an unknown genre keeps the spelling it was first given`() {
        // Recognised genres no longer have this problem — every spelling of one becomes its key. It
        // still matters for the ones the vocabulary does not know, where the typed text IS the value
        // and the first occurrence is the one the shelf heading shows.
        assertEquals("Hörbuchmagazin", genresFromInput("Hörbuchmagazin, hörbuchmagazin"))
        assertEquals("Blues\nEurodance", genresFromInput("Blues, Eurodance, BLUES"))
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
    fun `the dialog shows translated labels, comma-separated`() {
        // Not the stored keys: a field reading "shortstories, radioplay" is bookkeeping on screen.
        assertEquals("Fantasy, Humour", genresToInput(listOf("fantasy", "humour"), Locale.ENGLISH))
        assertEquals("Fantasy, Humor", genresToInput(listOf("fantasy", "humour"), Locale.GERMAN))
        assertEquals("", genresToInput(emptyList(), Locale.ENGLISH))
    }

    @Test
    fun `a genre the vocabulary does not know shows as written`() {
        assertEquals("Blues", genresToInput(listOf("Blues"), Locale.GERMAN))
    }

    @Test
    fun `what the dialog shows is what the dialog accepts, in either language`() {
        // The round trip that makes the free-text field safe: whatever language it is displayed in,
        // typing it straight back stores the same keys.
        val stored = listOf("fantasy", "humour", "shortstories")
        for (locale in listOf(Locale.ENGLISH, Locale.GERMAN)) {
            assertEquals(
                locale.toString(),
                encodeGenres(stored),
                genresFromInput(genresToInput(stored, locale)),
            )
        }
    }

    // ── canonicalising on the way in ─────────────────────────────────────────────────────────

    @Test
    fun `typing any spelling of a genre stores its key`() {
        assertEquals("shortstories", genresFromInput("Kurzgeschichten"))
        assertEquals("shortstories", genresFromInput("Short Stories"))
        assertEquals("scifi", genresFromInput("Science-Fiction"))
    }

    @Test
    fun `two spellings of one genre are one genre`() {
        // The whole point of a closed vocabulary: nobody has to know which spelling was blessed.
        assertEquals("shortstories", genresFromInput("Kurzgeschichten, Short Stories"))
    }

    @Test
    fun `an unknown genre typed by hand is kept verbatim`() {
        assertEquals("Blues", genresFromInput("Blues"))
    }
}
