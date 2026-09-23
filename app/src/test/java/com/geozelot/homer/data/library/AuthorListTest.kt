package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Several authors in one column, and the separator that makes it safe.
 *
 * The storage half is the same shape genres use and is tested for the same reason: a list of one
 * has to round-trip as the bare string every earlier build wrote, or a migration becomes necessary
 * where none was.
 *
 * The input half is where authors differ from every other list in the app. Tags are typed
 * comma-separated; authors cannot be, because a name may contain a comma — and the very next
 * feature on the list makes "Pratchett, Terry" a form a reader is invited to type.
 */
class AuthorListTest {

    @Test
    fun `one author round-trips as the bare string it always was`() {
        assertEquals("Terry Pratchett", encodeAuthors(listOf("Terry Pratchett")))
        assertEquals(listOf("Terry Pratchett"), decodeAuthors("Terry Pratchett"))
    }

    @Test
    fun `no author is an absence, not an empty string`() {
        assertNull(encodeAuthors(emptyList()))
        assertNull(encodeAuthors(listOf("   ")))
        assertEquals(emptyList<String>(), decodeAuthors(null))
        assertEquals(emptyList<String>(), decodeAuthors(""))
    }

    @Test
    fun `the first author is the one the book files under`() {
        assertEquals("Terry Pratchett", primaryAuthor("Terry Pratchett\nNeil Gaiman"))
        assertNull(primaryAuthor(null))
        assertNull(primaryAuthor("  "))
    }

    @Test
    fun `order survives the round trip, because it is the whole point`() {
        val two = listOf("Neil Gaiman", "Terry Pratchett")
        assertEquals(two, decodeAuthors(encodeAuthors(two)))
        // Reversed is a DIFFERENT value: it changes which author the book files under.
        assertEquals(listOf("Terry Pratchett", "Neil Gaiman"), decodeAuthors(encodeAuthors(two.reversed())))
    }

    @Test
    fun `a semicolon separates people and a comma does not`() {
        // The case this separator exists for: one author, deliberately filed surname-first.
        assertEquals(listOf("Pratchett, Terry"), authorsFromInput("Pratchett, Terry"))
        assertEquals(
            listOf("Pratchett, Terry", "Neil Gaiman"),
            authorsFromInput("Pratchett, Terry; Neil Gaiman"),
        )
    }

    @Test
    fun `the field tolerates the spacing people actually type`() {
        assertEquals(listOf("A", "B"), authorsFromInput("A;B"))
        assertEquals(listOf("A", "B"), authorsFromInput("  A  ;   B  "))
        assertEquals(listOf("A"), authorsFromInput("A;;"))
        assertEquals(emptyList<String>(), authorsFromInput("  ;  "))
    }

    @Test
    fun `the field shows back what it would parse`() {
        val typed = "Pratchett, Terry; Neil Gaiman"
        assertEquals(typed, authorsToInput(authorsFromInput(typed)))
    }
}
