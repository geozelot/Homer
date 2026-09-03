package com.geozelot.homer.data.metadata

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The genre vocabulary.
 *
 * The load-bearing test is [no two genres answer to the same spelling] — a collision does not fail
 * loudly, it files books under whichever genre happened to be declared first, which looks like a
 * data problem rather than a table problem. Everything else here is cheap.
 */
class BookGenreTest {

    private val en = Locale.ENGLISH
    private val de = Locale.GERMAN

    // ── the guard that matters ───────────────────────────────────────────────────────────────

    @Test
    fun `no two genres answer to the same spelling`() {
        val byFolded = BookGenre.spellings().groupBy({ BookGenre.fold(it.second) }, { it.first })
        val clashes = byFolded.filterValues { it.distinct().size > 1 }
        assertEquals("spelling(s) claimed by more than one genre", emptyMap<String, Any>(), clashes)
    }

    @Test
    fun `every genre resolves from its own key and both labels`() {
        for (genre in BookGenre.entries) {
            assertSame("key ${genre.key}", genre, BookGenre.resolve(genre.key))
            assertSame("en ${genre.label(en)}", genre, BookGenre.resolve(genre.label(en)))
            assertSame("de ${genre.label(de)}", genre, BookGenre.resolve(genre.label(de)))
        }
    }

    @Test
    fun `every genre has a label in both languages, and they are not blank`() {
        // The compiler already demands both, since they are constructor parameters. This catches the
        // other way it can go wrong: a parameter filled in with nothing.
        for (genre in BookGenre.entries) {
            assertTrue("en ${genre.key}", genre.label(en).isNotBlank())
            assertTrue("de ${genre.key}", genre.label(de).isNotBlank())
        }
    }

    @Test
    fun `keys are stable-looking - lower case, no spaces or punctuation`() {
        // A key is stored on every book filed under it, so it has to survive being written to JSON,
        // matched case-insensitively, and read by an older build without surprises.
        for (genre in BookGenre.entries) {
            assertEquals(genre.key, BookGenre.fold(genre.key))
        }
    }

    // ── the two languages meet on one shelf ──────────────────────────────────────────────────

    @Test
    fun `the same concept in two languages is one genre`() {
        assertSame(BookGenre.SHORT_STORIES, BookGenre.resolve("Short Stories"))
        assertSame(BookGenre.SHORT_STORIES, BookGenre.resolve("Kurzgeschichten"))
        assertSame(BookGenre.SCIENCE, BookGenre.resolve("Science"))
        assertSame(BookGenre.SCIENCE, BookGenre.resolve("Wissenschaft"))
        assertSame(BookGenre.CRIME, BookGenre.resolve("Crime"))
        assertSame(BookGenre.CRIME, BookGenre.resolve("Krimi"))
    }

    @Test
    fun `a German tag reads in English and the other way round`() {
        assertEquals("Short Stories", BookGenre.display("Kurzgeschichten", en))
        assertEquals("Kurzgeschichten", BookGenre.display("Short Stories", de))
    }

    @Test
    fun `radio play exists, which ID3 has no code for at all`() {
        // The gap that most justifies authoring a list rather than adapting the ID3 table: much of a
        // German audiobook library is Hörspiel and ID3 cannot say it.
        assertSame(BookGenre.RADIO_PLAY, BookGenre.resolve("Hörspiel"))
        assertSame(BookGenre.RADIO_PLAY, BookGenre.resolve("Radio Play"))
        assertEquals("Hörspiel", BookGenre.display("Radio Play", de))
    }

    // ── folding ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `case, spacing and punctuation are ignored`() {
        for (spelling in listOf("Science Fiction", "science-fiction", "SCIENCE  FICTION", " SciFi ", "sci-fi")) {
            assertSame(spelling, BookGenre.SCI_FI, BookGenre.resolve(spelling))
        }
    }

    @Test
    fun `the apostrophe in Children's is not load-bearing`() {
        for (spelling in listOf("Children's", "Childrens", "children s", "CHILDREN'S")) {
            assertSame(spelling, BookGenre.CHILDRENS, BookGenre.resolve(spelling))
        }
    }

    @Test
    fun `folding uses the root locale`() {
        // In a Turkish locale "I".lowercase() is a dotless ı, which would stop a Turkish device
        // recognising its own vocabulary. The fold is locale-independent by construction.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertSame(BookGenre.HISTORY, BookGenre.resolve("History"))
            assertSame(BookGenre.POLITICS, BookGenre.resolve("Politics"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    // ── what must NOT resolve ────────────────────────────────────────────────────────────────

    @Test
    fun `an unknown genre resolves to nothing and is shown as written`() {
        assertNull(BookGenre.resolve("Blues"))
        assertNull(BookGenre.resolve("Eurodance"))
        assertEquals("Blues", BookGenre.display("Blues", en))
        assertEquals("Blues", BookGenre.canonical("Blues"))
    }

    @Test
    fun `nothing at all resolves to nothing`() {
        assertNull(BookGenre.resolve(null))
        assertNull(BookGenre.resolve(""))
        assertNull(BookGenre.resolve("   "))
        assertNull(BookGenre.resolve("—"))
    }

    @Test
    fun `Audiobook and Speech are not genres`() {
        // What a tagger reaches for when it has no better idea. They say the file is spoken word,
        // which every book in Homer already is, so resolving them would file half a library under
        // something nobody chose.
        assertNull(BookGenre.resolve("Audiobook"))
        assertNull(BookGenre.resolve("Speech"))
        assertNull(BookGenre.resolve("Spoken Word"))
    }

    // ── storage ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `canonical turns any spelling into the stored key`() {
        assertEquals("shortstories", BookGenre.canonical("Kurzgeschichten"))
        assertEquals("shortstories", BookGenre.canonical("Short Story"))
        assertEquals("scifi", BookGenre.canonical("Science-Fiction"))
    }

    @Test
    fun `canonical is idempotent`() {
        for (genre in BookGenre.entries) {
            val once = BookGenre.canonical(genre.label(de))
            assertEquals(genre.key, once)
            assertEquals(once, BookGenre.canonical(once))
        }
    }

    @Test
    fun `a value written by an older build still resolves`() {
        // Free-text genres are what is stored today, and what other devices may keep publishing.
        // They have to keep working without a migration pass.
        assertSame(BookGenre.FANTASY, BookGenre.resolve("Fantasy"))
        assertSame(BookGenre.THRILLER, BookGenre.resolve("Thriller"))
    }

    // ── the picker's view of it ──────────────────────────────────────────────────────────────

    @Test
    fun `the two shelves together are the whole vocabulary`() {
        val fiction = BookGenre.offered(BookGenre.Shelf.FICTION, en)
        val nonfiction = BookGenre.offered(BookGenre.Shelf.NONFICTION, en)
        assertEquals(BookGenre.entries.size, fiction.size + nonfiction.size)
        assertTrue(fiction.none { it in nonfiction })
    }

    @Test
    fun `each shelf is offered in the reader's alphabetical order`() {
        for (locale in listOf(en, de)) {
            for (shelf in BookGenre.Shelf.entries) {
                val labels = BookGenre.offered(shelf, locale).map { it.label(locale).lowercase(locale) }
                assertEquals("$shelf in $locale", labels.sorted(), labels)
            }
        }
    }

    @Test
    fun `the order differs between languages, which is the point of sorting by label`() {
        val enOrder = BookGenre.offered(BookGenre.Shelf.FICTION, en).map { it.key }
        val deOrder = BookGenre.offered(BookGenre.Shelf.FICTION, de).map { it.key }
        assertTrue("alphabetical in one language is not alphabetical in the other", enOrder != deOrder)
    }
}
