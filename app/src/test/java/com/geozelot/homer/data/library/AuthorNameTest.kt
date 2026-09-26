package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a name is shown, and how it files.
 *
 * The two halves are tested apart because they are held to different standards. **Display is a
 * rule** — a comma means the surname came first, and a comma cannot occur in a name written the
 * normal way round — so it is expected to be right every time. **Filing is a heuristic**, and is
 * allowed to be wrong: the key is compared and never drawn, so a bad guess puts a shelf in the
 * wrong place rather than rendering somebody's name incorrectly. The cases below are the ones
 * worth holding it to, not a claim that it handles every name on earth.
 */
class AuthorNameTest {

    // ── shown ────────────────────────────────────────────────────────────────

    @Test
    fun `a name written the normal way round is left alone`() {
        assertEquals("Terry Pratchett", displayAuthor("Terry Pratchett"))
        assertEquals("Homer", displayAuthor("Homer"))
        assertEquals("Ursula K. Le Guin", displayAuthor("Ursula K. Le Guin"))
    }

    @Test
    fun `a comma means the surname came first, so it flips`() {
        assertEquals("Terry Pratchett", displayAuthor("Pratchett, Terry"))
        assertEquals("Ursula K. Le Guin", displayAuthor("Le Guin, Ursula K."))
        assertEquals("Terry Pratchett", displayAuthor("  Pratchett ,  Terry  "))
    }

    @Test
    fun `what it cannot read, it does not rearrange`() {
        // Two commas is not a form this knows; inventing an order would be worse than leaving it.
        assertEquals("Smith, John, Jr.", displayAuthor("Smith, John, Jr."))
        assertEquals("Pratchett,", displayAuthor("Pratchett,"))
        assertEquals(", Terry", displayAuthor(", Terry"))
    }

    @Test
    fun `both stored forms of one person show identically`() {
        assertEquals(displayAuthor("Pratchett, Terry"), displayAuthor("Terry Pratchett"))
    }

    // ── filed ────────────────────────────────────────────────────────────────

    @Test
    fun `it files under the surname`() {
        assertEquals("pratchett terry", authorSortKey("Terry Pratchett"))
        assertEquals("andersen hans christian", authorSortKey("Hans Christian Andersen"))
    }

    @Test
    fun `both stored forms of one person file identically`() {
        assertEquals(authorSortKey("Terry Pratchett"), authorSortKey("Pratchett, Terry"))
    }

    @Test
    fun `particles travel with the surname`() {
        assertEquals("le guin ursula k.", authorSortKey("Ursula K. Le Guin"))
        assertEquals("van gogh vincent", authorSortKey("Vincent van Gogh"))
        assertEquals("von humboldt alexander", authorSortKey("Alexander von Humboldt"))
        assertEquals("van der berg ludwig", authorSortKey("Ludwig van der Berg"))
    }

    @Test
    fun `a generational suffix does not file under its own letter`() {
        assertEquals("davis sammy jr.", authorSortKey("Sammy Davis Jr."))
    }

    @Test
    fun `one name is its own key`() {
        assertEquals("homer", authorSortKey("Homer"))
        assertEquals("", authorSortKey("   "))
    }

    @Test
    fun `the key orders the way a shelf should read`() {
        val shelf = listOf("Terry Pratchett", "Douglas Adams", "Ursula K. Le Guin", "Neil Gaiman")
        assertEquals(
            listOf("Douglas Adams", "Neil Gaiman", "Ursula K. Le Guin", "Terry Pratchett"),
            shelf.sortedBy(::authorSortKey),
        )
    }

    // ── Showing a name the way it files ───────────────────────────────────────

    @Test
    fun `the filed form is built from the same split the sort key uses`() {
        // What a reader sees and what the list is ordered by cannot disagree, because they come
        // from one split: a heading reading "Le Guin, Ursula K." is under L because that is what
        // put it there.
        assertEquals("Pratchett, Terry", filedAuthor("Terry Pratchett"))
        assertEquals("Le Guin, Ursula K.", filedAuthor("Ursula K. Le Guin"))
        assertEquals("Davis, Sammy Jr.", filedAuthor("Sammy Davis Jr."))
    }

    @Test
    fun `a name with nothing in front of the surname is left alone`() {
        assertEquals("Homer", filedAuthor("Homer"))
    }

    @Test
    fun `an already-filed name is normalised before it is filed again`() {
        // Flipping stored text directly would leave this one untouched and turn its neighbour
        // around, so one shelf would carry both spellings of the same convention.
        assertEquals("Pratchett, Terry", displayAuthor("Pratchett, Terry", surnameFirst = true))
        assertEquals("Pratchett, Terry", displayAuthor("Terry Pratchett", surnameFirst = true))
    }

    @Test
    fun `showing is off by default and does not touch the stored form`() {
        assertEquals("Terry Pratchett", displayAuthor("Pratchett, Terry"))
        assertEquals("Terry Pratchett", displayAuthor("Terry Pratchett"))
    }
}
