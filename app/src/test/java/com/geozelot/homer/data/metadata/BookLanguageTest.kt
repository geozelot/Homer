package com.geozelot.homer.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers language detection — the reading of tags and names, not any guessing.
 *
 * The rule the whole class is built around: **a wrong language is worse than none.** An absent
 * language leaves a book unfiltered; a wrong one hides it behind a filter the reader does not know
 * is excluding it. So the tests that matter most here are the ones that assert null.
 */
class BookLanguageTest {

    // ── normalise ─────────────────────────────────────────────────────────────

    @Test
    fun `a two-letter code is taken as it is`() {
        assertEquals("de", BookLanguage.normalise("de"))
        assertEquals("de", BookLanguage.normalise("DE"))
    }

    @Test
    fun `both three-letter forms of a language resolve`() {
        // ISO 639-2 gives many languages a bibliographic and a terminological code, and files in
        // the wild carry either.
        assertEquals("de", BookLanguage.normalise("ger"))
        assertEquals("de", BookLanguage.normalise("deu"))
        assertEquals("fr", BookLanguage.normalise("fre"))
        assertEquals("fr", BookLanguage.normalise("fra"))
    }

    @Test
    fun `a region subtag is dropped`() {
        // de-DE and de-AT are both German; the region says nothing about the language.
        assertEquals("de", BookLanguage.normalise("de-DE"))
        assertEquals("es", BookLanguage.normalise("es_419"))
    }

    @Test
    fun `a language name resolves`() {
        assertEquals("de", BookLanguage.normalise("Deutsch"))
        assertEquals("es", BookLanguage.normalise("Espanol"))
        assertEquals("es", BookLanguage.normalise("Español"))
    }

    @Test
    fun `undetermined is not a language`() {
        // What a great many MP4 muxers write by default. Reading it as a language would be the
        // exact mistake this class exists to avoid.
        assertNull(BookLanguage.normalise("und"))
        assertNull(BookLanguage.normalise("mul"))
        assertNull(BookLanguage.normalise("zxx"))
    }

    @Test
    fun `an unknown code is refused rather than passed through`() {
        // A code Homer cannot name is a code the filter chip cannot label.
        assertNull(BookLanguage.normalise("xx"))
        assertNull(BookLanguage.normalise("klingon"))
        assertNull(BookLanguage.normalise(""))
        assertNull(BookLanguage.normalise(null))
    }

    // ── names: delimited tokens ───────────────────────────────────────────────

    @Test
    fun `a bracketed code is taken at its word`() {
        assertEquals("de", BookLanguage.fromNames("Der Hobbit [DE]", listOf("01.mp3")))
        assertEquals("en", BookLanguage.fromNames("The Hobbit (eng)", listOf("01.mp3")))
    }

    @Test
    fun `a dotted code counts`() {
        assertEquals("fr", BookLanguage.fromNames("Le Hobbit", listOf("01.fr.mp3", "02.fr.mp3")))
    }

    @Test
    fun `a spelled-out language counts as a bare word`() {
        assertEquals("de", BookLanguage.fromNames("Der Hobbit - Deutsch", listOf("01.mp3")))
        assertEquals("nl", BookLanguage.fromNames("De Hobbit Nederlands", listOf("01.mp3")))
    }

    @Test
    fun `a bare two-letter word is never a language`() {
        // The guard the whole tier exists for: "de" and "it" are ordinary words in French, Spanish,
        // Italian and English titles.
        assertNull(BookLanguage.fromNames("Le Comte de Monte-Cristo", listOf("01.mp3")))
        assertNull(BookLanguage.fromNames("Gone With It", listOf("01.mp3")))
        assertNull(BookLanguage.fromNames("La Casa de Papel", listOf("01.mp3", "02.mp3")))
    }

    // ── names: chapter words ──────────────────────────────────────────────────

    @Test
    fun `the word a chapter file is named after counts`() {
        assertEquals(
            "de",
            BookLanguage.fromNames("Der Hobbit", listOf("Kapitel 01.mp3", "Kapitel 02.mp3")),
        )
        assertEquals("en", BookLanguage.fromNames("The Hobbit", listOf("Chapter 01.mp3")))
        assertEquals("fr", BookLanguage.fromNames("Le Hobbit", listOf("Chapitre 01.mp3")))
        // NOT Spanish. "capítulo" is the Portuguese word too, spelled identically, so the file
        // name alone cannot tell them apart — and it used to answer "Spanish" for both, shelving
        // every Portuguese library under the wrong language and publishing that to everyone
        // sharing it. A deliberate trade: Spanish loses chapter-word detection so that Portuguese
        // stops being actively mislabelled. Both are still detected from a tag or an explicit token.
        assertNull(BookLanguage.fromNames("El Hobbit", listOf("Capitulo 01.mp3")))
        assertNull(BookLanguage.fromNames("El Hobbit", listOf("Capítulo 01.mp3")))
        assertEquals("it", BookLanguage.fromNames("Lo Hobbit", listOf("Capitolo 01.mp3")))
    }

    @Test
    fun `a delimited token beats a chapter word`() {
        // Somebody wrote the token on purpose; the chapter word is incidental. A German-named rip of
        // an English reading is exactly this case.
        assertEquals(
            "en",
            BookLanguage.fromNames("Der Hobbit [EN]", listOf("Kapitel 01.mp3", "Kapitel 02.mp3")),
        )
    }

    @Test
    fun `the commonest chapter word wins`() {
        assertEquals(
            "de",
            BookLanguage.fromNames(
                "Sammlung",
                listOf("Kapitel 01.mp3", "Kapitel 02.mp3", "Chapter 03.mp3"),
            ),
        )
    }

    @Test
    fun `nothing recognisable means no language`() {
        // The commonest case in a library nobody has tagged, and it must stay null rather than
        // resolve to whatever language the code happens to check first.
        assertNull(BookLanguage.fromNames("Der Hobbit", listOf("01.mp3", "02.mp3")))
        assertNull(BookLanguage.fromNames("", emptyList()))
    }

    @Test
    fun `a three-letter code is not a language when it is also a common word`() {
        // "por" is an everyday Spanish and Portuguese word, "dan" a name, "ara" a Spanish verb.
        // Bare words may only name a language in a long form ("espanol"), never by their ISO code,
        // or a Spanish title turns its own library Portuguese.
        assertNull(BookLanguage.fromNames("El amor por la vida", listOf("01.mp3")))
        assertNull(BookLanguage.fromNames("Dan Brown - Origin", listOf("01.mp3")))
    }

    @Test
    fun `a word that names two languages names neither`() {
        // "part" is English and French, "band" is German and English. Neither is in the table, and
        // this pins that they stay out of it.
        assertNull(BookLanguage.fromNames("The Hobbit", listOf("Part 01.mp3", "Part 02.mp3")))
        assertNull(BookLanguage.fromNames("Band 1", listOf("01.mp3")))
    }

    // ── words two languages share ────────────────────────────────────────────────────────────

    @Test
    fun `a chapter word two languages share decides nothing`() {
        // Spanish and Portuguese both call it "capítulo". It used to go to Spanish alone, so every
        // Portuguese book named "Capítulo 03.mp3" was shelved as Spanish — a wrong answer where the
        // design prefers none.
        assertNull(BookLanguage.fromNames("Algum Livro", listOf("Capítulo 01.mp3", "Capítulo 02.mp3")))
    }

    @Test
    fun `an explicit token still identifies the language the shared word cannot`() {
        assertEquals("pt", BookLanguage.fromNames("Algum Livro [pt]", listOf("Capítulo 01.mp3")))
        assertEquals("es", BookLanguage.fromNames("Algún Libro (espanol)", listOf("Capítulo 01.mp3")))
    }

    @Test
    fun `an unshared chapter word still works`() {
        assertEquals("de", BookLanguage.fromNames("Irgendwas", listOf("Kapitel 01.mp3")))
        assertEquals("fr", BookLanguage.fromNames("Quelque chose", listOf("Chapitre 01.mp3")))
    }
}
