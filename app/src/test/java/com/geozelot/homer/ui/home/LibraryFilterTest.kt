package com.geozelot.homer.ui.home

import com.geozelot.homer.data.db.entity.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The combining rule, and the ranking that decides what the box offers you. */
class LibraryFilterTest {

    private fun book(
        id: String,
        title: String = id,
        author: String? = null,
        series: String? = null,
        collection: String? = null,
        genre: String? = null,
        language: String? = null,
        tags: List<String> = emptyList(),
    ) = BookListItem(
        id = id,
        title = title,
        author = author,
        isMultiFile = false,
        fileCount = 1,
        coverModel = null,
        hasCustomCover = false,
        series = series,
        seriesIndex = null,
        collection = collection,
        collectionIndex = null,
        genre = genre,
        language = language,
        tags = tags,
        totalDurationMs = null,
        timeLeftMs = null,
        progress = null,
        lastPlayedAt = null,
        started = false,
        finishedOverride = null,
        downloadOnPlayOverride = null,
        downloadStatus = null,
        downloadedFiles = 0,
        hidden = false,
    )

    private val pratchett = book("1", "Sourcery", author = "Pratchett", genre = "Fantasy", language = "en")
    private val gaiman = book("2", "Neverwhere", author = "Gaiman", genre = "Fantasy", language = "en")
    private val german = book("3", "Planetenwanderer", author = "Martin", genre = "SciFi", language = "de")
    private val all = listOf(pratchett, gaiman, german)

    // ── the combining rule ───────────────────────────────────────────────────────────────────

    @Test
    fun `an empty filter matches everything`() {
        assertTrue(all.all { LibraryFilter().matches(it) })
    }

    @Test
    fun `two values on a multi-valued facet are an AND`() {
        val both = book("b", tags = listOf("Klassiker", "Gelesen"))
        val one = book("o", tags = listOf("Klassiker"))
        val f = LibraryFilter()
            .plus(FilterToken(FilterFacet.TAG, "Klassiker"))
            .plus(FilterToken(FilterFacet.TAG, "Gelesen"))
        assertTrue(f.matches(both))
        assertFalse("one of the two tags must not be enough", f.matches(one))
    }

    @Test
    fun `two facets are an AND`() {
        val f = LibraryFilter()
            .plus(FilterToken(FilterFacet.GENRE, "Fantasy"))
            .plus(FilterToken(FilterFacet.LANGUAGE, "en"))
            .plus(FilterToken(FilterFacet.AUTHOR, "Gaiman"))
        assertEquals(listOf("2"), all.filter { f.matches(it) }.map { it.id })
    }

    @Test
    fun `two values of a single-valued facet leave nothing, which is the accepted cost of AND`() {
        // Pinned as a test because it is the price of the rule, not an oversight: no book has two
        // authors, so two author pills is an empty shelf. If this ever needs to widen again, this
        // is the test that says so out loud.
        val f = LibraryFilter()
            .plus(FilterToken(FilterFacet.AUTHOR, "Pratchett"))
            .plus(FilterToken(FilterFacet.AUTHOR, "Gaiman"))
        assertTrue("AND on one axis must be empty", all.none { f.matches(it) })
    }

    @Test
    fun `free text narrows what the tokens left, rather than replacing it`() {
        val f = LibraryFilter()
            .plus(FilterToken(FilterFacet.GENRE, "Fantasy"))
            .withText("sourcery")
        assertEquals(listOf("1"), all.filter { f.matches(it) }.map { it.id })
    }

    @Test
    fun `values match without regard to case`() {
        val f = LibraryFilter().plus(FilterToken(FilterFacet.AUTHOR, "pratchett"))
        assertTrue(f.matches(pratchett))
    }

    @Test
    fun `a tag matches any one of a book's tags`() {
        val tagged = book("4", tags = listOf("abridged", "gift"))
        assertTrue(LibraryFilter().plus(FilterToken(FilterFacet.TAG, "gift")).matches(tagged))
        assertFalse(LibraryFilter().plus(FilterToken(FilterFacet.TAG, "unabridged")).matches(tagged))
    }

    @Test
    fun `filtering by collection finds a plain series standing in as its own`() {
        // The same fallback the shelf stacks by — otherwise collection: would find only the books
        // somebody had nested, which is almost none of them.
        val expanse = book("5", series = "The Expanse", author = "Corey")
        assertTrue(LibraryFilter().plus(FilterToken(FilterFacet.COLLECTION, "The Expanse")).matches(expanse))
    }

    @Test
    fun `adding the same token twice changes nothing`() {
        val t = FilterToken(FilterFacet.AUTHOR, "Pratchett")
        assertEquals(1, LibraryFilter().plus(t).plus(t).tokens.size)
    }

    // ── suggestions ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `nothing typed offers the states, which cannot be guessed at`() {
        // You can type an author's name; you cannot guess which word the app uses for "downloaded",
        // and the keys are English so typing the German matches nothing. So the states are a short
        // fixed menu the moment the box opens.
        val offered = suggest(all, "", emptyList())
        assertTrue(offered.isNotEmpty())
        assertTrue(offered.all { it.facet == FilterFacet.STATE })
    }

    @Test
    fun `a state nothing is in is not offered`() {
        // None of these are downloaded, so offering "Downloaded" would be offering an empty shelf.
        assertTrue(suggest(all, "", emptyList()).none { it.value == BookState.DOWNLOADED.key })
        assertTrue(suggest(all, "", emptyList()).any { it.value == BookState.UNSTARTED.key })
    }

    @Test
    fun `a state already committed is not offered again`() {
        val committed = listOf(FilterToken(FilterFacet.STATE, BookState.UNSTARTED.key))
        assertTrue(suggest(all, "", committed).none { it.value == BookState.UNSTARTED.key })
    }

    @Test
    fun `two states AND together, which is what makes them worth combining`() {
        // A book IS several states at once, so this is the combination AND was wanted for:
        // downloaded-and-started is the "on my commute, half read" shelf.
        val onTheTrain = book("s").copy(
            started = true,
            progress = 0.5f,
            downloadStatus = DownloadStatus.DONE,
            downloadedFiles = 1,
        )
        val f = LibraryFilter()
            .plus(FilterToken(FilterFacet.STATE, BookState.DOWNLOADED.key))
            .plus(FilterToken(FilterFacet.STATE, BookState.STARTED.key))
        assertTrue(f.matches(onTheTrain))
        // And the mutually exclusive pair now leaves nothing, which is the honest answer.
        val exclusive = LibraryFilter()
            .plus(FilterToken(FilterFacet.STATE, BookState.UNSTARTED.key))
            .plus(FilterToken(FilterFacet.STATE, BookState.STARTED.key))
        assertTrue(all.none { exclusive.matches(it) })
    }

    @Test
    fun `a state narrows against a metadata facet`() {
        val f = LibraryFilter()
            .plus(FilterToken(FilterFacet.STATE, BookState.UNSTARTED.key))
            .plus(FilterToken(FilterFacet.GENRE, "SciFi"))
        assertEquals(listOf("3"), all.filter { f.matches(it) }.map { it.id })
    }

    @Test
    fun `a prefix match outranks a mere containment`() {
        val books = listOf(
            book("a", author = "Anderson"),
            book("b", author = "Le Anderson"),
            book("c", author = "Le Anderson"),
        )
        // "Le Anderson" has two books to Anderson's one, and still loses: what you typed starts the
        // other one.
        val top = suggest(books, "Anders", emptyList()).first()
        assertEquals("Anderson", top.value)
    }

    @Test
    fun `size breaks a tie between equally good matches`() {
        val books = listOf(
            book("a", genre = "Fantasy"),
            book("b", genre = "Fantasy"),
            book("c", genre = "Fanfic"),
        )
        assertEquals("Fantasy", suggest(books, "Fan", emptyList()).first().value)
    }

    @Test
    fun `an already-committed value is not offered again`() {
        val committed = listOf(FilterToken(FilterFacet.AUTHOR, "Pratchett"))
        val offered = suggest(all, "Pratchett", committed)
        assertTrue(offered.none { it.facet == FilterFacet.AUTHOR && it.value == "Pratchett" })
    }

    @Test
    fun `one value can be offered on more than one facet`() {
        // A genre and a tag that read the same are two different filters, and the box has to say so.
        val odd = book("x", genre = "Classic", tags = listOf("Classic"))
        val facets = suggest(listOf(odd), "Classic", emptyList()).map { it.facet }
        assertTrue(FilterFacet.GENRE in facets && FilterFacet.TAG in facets)
    }

    @Test
    fun `suggestions carry how many books they would leave`() {
        assertEquals(2, suggest(all, "Fantasy", emptyList()).single { it.facet == FilterFacet.GENRE }.count)
    }

    // ── the saved form ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a token round-trips through its encoded form`() {
        val t = FilterToken(FilterFacet.SERIES, "The Expanse")
        assertEquals(t, FilterToken.decode(t.encode()))
    }

    @Test
    fun `a value containing a colon survives the round trip`() {
        val t = FilterToken(FilterFacet.SERIES, "Leviathan: The Expanse")
        assertEquals(t, FilterToken.decode(t.encode()))
    }

    @Test
    fun `a facet from a later build decodes to nothing rather than guessing`() {
        assertNull(FilterToken.decode("narrator:Briggs"))
        assertNull(FilterToken.decode("author:"))
    }

    // ── multi-word free text ─────────────────────────────────────────────────────────────────
    //
    // The case this was all built for: words that live in DIFFERENT fields. Before, a query only
    // worked if every word of it sat in one field, so this whole block was unanswerable.

    private val hexen = book(
        "h1",
        "Total Verhext",
        author = "Terry Pratchett",
        series = "Die Hexen",
        collection = "Scheibenwelt",
        genre = "Fantasy",
    )
    private val wache = book(
        "w1",
        "Helle Barden",
        author = "Terry Pratchett",
        series = "Die Wache",
        collection = "Scheibenwelt",
        genre = "Fantasy",
    )

    @Test
    fun `words spanning two fields both have to land`() {
        val filter = LibraryFilter(text = "pratchett hexen")
        assertTrue(filter.matches(hexen))
        // Same author, same collection, different series — the second word has nowhere to land.
        assertFalse(filter.matches(wache))
    }

    @Test
    fun `word order does not matter`() {
        assertTrue(LibraryFilter(text = "hexen pratchett").matches(hexen))
    }

    @Test
    fun `a word matching nothing rejects the book even when the others match`() {
        assertFalse(LibraryFilter(text = "pratchett hexen gaiman").matches(hexen))
    }

    @Test
    fun `the collection is reachable alongside the author`() {
        assertTrue(LibraryFilter(text = "scheibenwelt pratchett").matches(wache))
    }

    @Test
    fun `extra whitespace is not a word`() {
        assertTrue(LibraryFilter(text = "   pratchett    hexen  ").matches(hexen))
    }

    @Test
    fun `a single word still behaves as it always did`() {
        assertTrue(LibraryFilter(text = "verhext").matches(hexen))
        assertTrue(LibraryFilter(text = "welt").matches(hexen))
        assertFalse(LibraryFilter(text = "gaiman").matches(hexen))
    }

    // ── forgiving in the two ways that come up ───────────────────────────────────────────────

    @Test
    fun `one slipped letter still finds the book`() {
        assertTrue(LibraryFilter(text = "pratchet").matches(hexen))
        assertTrue(LibraryFilter(text = "pratchettt").matches(hexen))
    }

    @Test
    fun `a short word gets no fuzz, because at three letters it would match half the shelf`() {
        // "hex" is a real prefix and must work; "hax" is one letter out but too short to forgive.
        assertTrue(LibraryFilter(text = "hex").matches(hexen))
        assertFalse(LibraryFilter(text = "hax").matches(hexen))
    }

    @Test
    fun `a genuinely different word is still rejected`() {
        assertFalse(LibraryFilter(text = "pratchesque").matches(hexen))
        assertFalse(LibraryFilter(text = "wache").matches(hexen))
    }

    private val maerchen = book("m1", "Deutsche M\u00e4rchen", author = "Gr\u00fcnwald", series = "Stra\u00dfenfeger")

    @Test
    fun `an accent need not be typed`() {
        assertTrue(LibraryFilter(text = "marchen").matches(maerchen))
        assertTrue(LibraryFilter(text = "grunwald").matches(maerchen))
    }

    @Test
    fun `an accent may be typed`() {
        assertTrue(LibraryFilter(text = "m\u00e4rchen").matches(maerchen))
    }

    @Test
    fun `the spelled-out umlaut is picked up by the edit distance`() {
        assertTrue(LibraryFilter(text = "maerchen").matches(maerchen))
    }

    @Test
    fun `eszett answers to ss both ways`() {
        assertTrue(LibraryFilter(text = "strassenfeger").matches(maerchen))
        assertTrue(LibraryFilter(text = "stra\u00dfenfeger").matches(maerchen))
    }

    @Test
    fun `the language code is not searchable, because two letters match everything`() {
        // Asserted on the field list rather than through a query: almost any German title contains
        // "de" somewhere (Planetenwan-DE-rer did), so a query is the one way NOT to test this.
        assertFalse(german.searchFields().contains("de"))
        assertTrue(german.searchFields().contains("SciFi"))
    }

    // ── the edit-distance helper itself ──────────────────────────────────────────────────────

    @Test
    fun `edit distance counts the three operations and abandons early`() {
        assertTrue(editDistanceAtMost("hexen", "hexen", 0))
        assertTrue(editDistanceAtMost("hexen", "hexe", 1))
        assertTrue(editDistanceAtMost("hexen", "hexer", 1))
        assertTrue(editDistanceAtMost("hexen", "hexden", 1))
        assertFalse(editDistanceAtMost("hexen", "wache", 2))
        assertFalse(editDistanceAtMost("hexen", "hexen-und-mehr", 2))
    }

    @Test
    fun `folding is idempotent and lower-cases`() {
        assertEquals("marchen", fold("M\u00e4rchen"))
        assertEquals("marchen", fold(fold("M\u00e4rchen")))
        assertEquals("strasse", fold("Stra\u00dfe"))
    }

    @Test
    fun `the ascii fast path in folding agrees with the slow one`() {
        // Pure ASCII skips the normaliser entirely, so it has to be shown doing nothing else.
        for (v in listOf("Sourcery", "LE ANDERSON", "no-cover", "Book 01 - Part 2", "")) {
            assertEquals(v.lowercase(), fold(v))
        }
        // And the two paths must converge: one accented letter takes the slow route and lands in
        // the same place the fast route would have.
        assertEquals(fold("Sourcery"), fold("S\u00f6urcery"))
    }

    @Test
    fun `query terms split on any whitespace without a regex`() {
        assertEquals(listOf("pratchett", "hexen"), queryTerms("Pratchett\tHexen"))
        assertEquals(emptyList<String>(), queryTerms("   "))
    }

    // ── what the box offers for a multi-word query ───────────────────────────────────────────

    @Test
    fun `a second word still gets suggestions, which is what went silent before`() {
        val offered = suggest(listOf(hexen, wache), "pratchett hexen", emptyList())
        assertTrue(offered.any { it.facet == FilterFacet.SERIES && it.value == "Die Hexen" })
        assertTrue(offered.any { it.facet == FilterFacet.AUTHOR && it.value == "Terry Pratchett" })
    }

    @Test
    fun `a word inside a value outranks one that merely contains it`() {
        // "Die Hexen" does not START with "hexen" — the WORD does, and that is what ranking means.
        val offered = suggest(listOf(hexen, wache), "hexen", emptyList())
        assertEquals("Die Hexen", offered.first().value)
    }

    // -- committed free text -------------------------------------------------------------------
    //
    // A word kept as a chip has to behave exactly as the same word still in the box: matched across
    // every field at once, forgiving, and ANDed with everything else.

    @Test
    fun `a committed text token matches the way typing matches`() {
        val f = LibraryFilter().plus(FilterToken(FilterFacet.TEXT, "hexen"))
        assertTrue(f.matches(hexen))
        assertFalse(f.matches(wache))
    }

    @Test
    fun `two text tokens AND, like typing both words`() {
        val f = LibraryFilter()
            .plus(FilterToken(FilterFacet.TEXT, "pratchett"))
            .plus(FilterToken(FilterFacet.TEXT, "hexen"))
        assertTrue(f.matches(hexen))
        assertFalse(f.matches(wache))
    }

    @Test
    fun `a text token is as forgiving as the box is`() {
        assertTrue(LibraryFilter().plus(FilterToken(FilterFacet.TEXT, "pratchet")).matches(hexen))
        assertTrue(LibraryFilter().plus(FilterToken(FilterFacet.TEXT, "marchen")).matches(maerchen))
    }

    @Test
    fun `a text token narrows against a facet token`() {
        val f = LibraryFilter()
            .plus(FilterToken(FilterFacet.COLLECTION, "Scheibenwelt"))
            .plus(FilterToken(FilterFacet.TEXT, "hexen"))
        assertTrue(f.matches(hexen))
        assertFalse("same collection, and the text does not land", f.matches(wache))
    }

    @Test
    fun `text tokens still narrow what is left in the box`() {
        val f = LibraryFilter(text = "pratchett").plus(FilterToken(FilterFacet.TEXT, "hexen"))
        assertTrue(f.matches(hexen))
        assertFalse(f.matches(wache))
    }

    @Test
    fun `the box never offers a text token, because it has no values to offer`() {
        assertTrue(hexen.valuesFor(FilterFacet.TEXT).isEmpty())
        val offered = suggest(listOf(hexen, wache), "hexen", emptyList())
        assertTrue(offered.none { it.facet == FilterFacet.TEXT })
    }

    // ── what the box OFFERS is narrower than what a token MATCHES ────────────────────────────

    @Test
    fun `the box does not offer a plain series as a collection`() {
        // Every series in the library fell back to standing in as its own collection, so the box
        // offered `series:Die Hexen` and `collection:Die Hexen` side by side — two suggestions
        // selecting exactly the same books. Half the vocabulary was a duplicate of the other half.
        val plain = book("h1", series = "Die Hexen", author = "Pratchett")
        val offered = suggest(listOf(plain), "hexen", emptyList())
        assertTrue(offered.any { it.facet == FilterFacet.SERIES && it.value == "Die Hexen" })
        assertTrue(offered.none { it.facet == FilterFacet.COLLECTION })
    }

    @Test
    fun `the box does offer a collection somebody actually expressed`() {
        val nested = book("d1", series = "Die Hexen", collection = "Scheibenwelt", author = "Pratchett")
        val offered = suggest(listOf(nested), "scheiben", emptyList())
        assertTrue(offered.any { it.facet == FilterFacet.COLLECTION && it.value == "Scheibenwelt" })
    }

    @Test
    fun `a collection token still MATCHES a plain series standing in as its own`() {
        // The asymmetry is deliberate: a token already committed against a plain series — or typed
        // by hand — has to go on working even though the box no longer volunteers it.
        val plain = book("h1", series = "Die Hexen", author = "Pratchett")
        assertTrue(LibraryFilter(tokens = listOf(FilterToken(FilterFacet.COLLECTION, "Die Hexen"))).matches(plain))
    }

    @Test
    fun `a collection token still finds the books of that collection that are on no thread`() {
        val loose = book("d9", collection = "Scheibenwelt", author = "Pratchett")
        assertTrue(LibraryFilter(tokens = listOf(FilterToken(FilterFacet.COLLECTION, "Scheibenwelt"))).matches(loose))
    }

    @Test
    fun `a text token round-trips through its encoded form`() {
        val t = FilterToken(FilterFacet.TEXT, "pratchett hexen")
        assertEquals(t, FilterToken.decode(t.encode()))
    }
}
