package com.geozelot.homer.data.metadata

import java.text.Normalizer
import java.util.Locale

/**
 * What language a book is in, worked out from what the files themselves say.
 *
 * Deliberately never *guesses* from a title's words or script. A wrong language is worse than no
 * language: it hides a book behind a filter the reader did not know was excluding it, whereas an
 * absent one merely leaves the book unfiltered. Everything here is evidence somebody put in the
 * file or its name on purpose.
 *
 * All matching is done ASCII-folded and lower-cased, so `Capítulo` and `Capitulo` are the same
 * needle and the table below needs no non-ASCII literals.
 */
object BookLanguage {

    /**
     * One language Homer can recognise, and everything it answers to.
     *
     * [code] is the two-letter ISO 639-1 tag actually stored. [iso3] are the three-letter ISO 639-2
     * forms a tag can carry — several languages have both a bibliographic and a terminological code
     * ("ger" and "deu"), and files in the wild use either. [names] are whole words that name the
     * language. [chapterWords] are words that merely *appear* in a chapter's filename, which is
     * weaker evidence and ranked below the rest.
     */
    private data class Lang(
        val code: String,
        val iso3: List<String>,
        val names: List<String> = emptyList(),
        val chapterWords: List<String> = emptyList(),
    )

    /**
     * The languages Homer knows. Adding one is a line here and nothing else.
     *
     * Chapter words are only listed where they are distinctive. "part" is both English and French,
     * "band" is both German and English, and a word that names two languages names neither — so
     * those are absent on purpose rather than forgotten.
     */
    private val TABLE = listOf(
        // "kapitel" is German, Danish AND Swedish. It resolves to German because that is the
        // overwhelmingly common case in audiobook filenames; a Danish or Swedish library will want
        // this line split, which is why it is one line.
        Lang("de", listOf("deu", "ger"), listOf("german", "deutsch"), listOf("kapitel", "teil", "hoerbuch", "horbuch")),
        Lang("en", listOf("eng"), listOf("english"), listOf("chapter", "audiobook", "unabridged")),
        Lang("fr", listOf("fra", "fre"), listOf("french", "francais"), listOf("chapitre")),
        // "capitulo" is deliberately listed for BOTH Spanish and Portuguese, which is what makes
        // byChapterWord drop it: the word cannot tell them apart, so it decides nothing.
        Lang("es", listOf("spa"), listOf("spanish", "espanol"), listOf("capitulo")),
        Lang("it", listOf("ita"), listOf("italian", "italiano"), listOf("capitolo")),
        Lang("nl", listOf("nld", "dut"), listOf("dutch", "nederlands"), listOf("hoofdstuk")),
        // Listed with the same word as Spanish ON PURPOSE. It used to carry the plural
        // "capitulos", which no file is ever named after, so it never fired — and the singular went
        // to Spanish alone, making every Portuguese book Spanish. Both claim it, so neither gets it,
        // and Portuguese is still detected from a tag or an explicit `[pt]` / "portugues".
        Lang("pt", listOf("por"), listOf("portuguese", "portugues"), listOf("capitulo")),
        Lang("sv", listOf("swe"), listOf("swedish", "svenska")),
        Lang("da", listOf("dan"), listOf("danish", "dansk")),
        Lang("no", listOf("nor", "nob", "nno"), listOf("norwegian", "norsk"), listOf("kapittel")),
        Lang("fi", listOf("fin"), listOf("finnish", "suomi"), listOf("luku")),
        Lang("pl", listOf("pol"), listOf("polish", "polski"), listOf("rozdzial")),
        Lang("cs", listOf("ces", "cze"), listOf("czech", "cesky"), listOf("kapitola")),
        Lang("ru", listOf("rus"), listOf("russian")),
        Lang("tr", listOf("tur"), listOf("turkish", "turkce")),
        Lang("hu", listOf("hun"), listOf("hungarian", "magyar")),
        Lang("ro", listOf("ron", "rum"), listOf("romanian", "romana")),
        Lang("el", listOf("ell", "gre"), listOf("greek")),
        Lang("ja", listOf("jpn"), listOf("japanese")),
        Lang("zh", listOf("zho", "chi"), listOf("chinese")),
        Lang("ko", listOf("kor"), listOf("korean")),
        Lang("ar", listOf("ara"), listOf("arabic")),
    )

    /**
     * ISO 639-2 codes that explicitly mean "no answer" — `und` in particular is what a great many
     * MP4 muxers write by default.
     *
     * **Redundant today, and kept deliberately.** None of these appears in [TABLE], so an unknown
     * code already resolves to null by falling off the end of [normalise]; a mutation removing this
     * check changes no behaviour. It stays because it says out loud that these mean "unknown"
     * rather than "unsupported", and because it becomes load-bearing the moment anybody makes
     * [normalise] more permissive about codes it does not recognise.
     */
    private val UNDETERMINED = setOf("und", "mul", "zxx", "mis", "qaa")

    private val byCode = TABLE.associateBy { it.code }
    private val byIso3 = TABLE.flatMap { l -> l.iso3.map { it to l } }.toMap()
    private val byName = TABLE.flatMap { l -> l.names.map { it to l } }.toMap()
    /**
     * Chapter words, minus any word more than one language claims.
     *
     * Spanish and Portuguese both call a chapter "capítulo", which folds to the same token — and
     * `toMap` silently let whichever entry came second win, so every Portuguese book named
     * `Capítulo 03.mp3` was detected as SPANISH. A word two languages share is not evidence for
     * either of them, and the design elsewhere prefers no answer to a wrong one: an unknown code is
     * rejected rather than passed through, precisely so the shelf never claims something it cannot
     * support.
     *
     * Computed rather than hand-pruned, so a future table edit that introduces a collision drops out
     * of tier 2 by itself instead of quietly handing one language another's books.
     */
    private val byChapterWord = TABLE
        .flatMap { l -> l.chapterWords.map { it to l } }
        .groupBy({ it.first }, { it.second })
        .filterValues { claimants -> claimants.distinctBy { it.code }.size == 1 }
        .mapValues { (_, claimants) -> claimants.first() }

    /**
     * A language's name in the reader's own language — "Deutsch" in a German interface, "German" in
     * an English one.
     *
     * The platform's table, not one of Homer's: it already knows every language's name in every
     * locale, and duplicating a slice of that here would be a second thing to translate.
     */
    fun displayName(code: String): String = displayName(code, Locale.getDefault())

    /**
     * The language's name, written in [locale] — "Deutsch" to a German interface, "German" to an
     * English one.
     *
     * Takes the locale rather than reading the default because the caller that matters is a
     * composable, and a shelf heading has to re-resolve when the interface language changes. The
     * list it labels is built by a ViewModel that SURVIVES the activity recreation a language change
     * causes, so anything baked in at build time would still be in the old language afterwards —
     * the same reason headings carry a string resource rather than a string.
     */
    fun displayName(code: String, locale: Locale): String =
        Locale.forLanguageTag(code).getDisplayLanguage(locale)
            .ifBlank { code.uppercase(Locale.ROOT) }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    /**
     * Anything a tag or a name might carry, reduced to a stored two-letter code — or null.
     *
     * Accepts `de`, `deu`, `ger`, `de-DE`, `German`, `deutsch`. Rejects an unknown code rather than
     * passing it through: a code Homer cannot name is a code the filter chip cannot label.
     */
    fun normalise(raw: String?): String? {
        val cleaned = fold(raw ?: return null).takeIf { it.isNotEmpty() } ?: return null
        // A region or script subtag says nothing about the language: de-DE and de-AT are both "de".
        val primary = cleaned.split('-', '_', '.').first()
        if (primary in UNDETERMINED) return null
        return when (primary.length) {
            2 -> primary.takeIf { it in byCode }
            3 -> byIso3[primary]?.code
            else -> byName[primary]?.code
        }
    }

    /**
     * The language a book's own folder and file names suggest, or null.
     *
     * Two tiers of evidence, and the stronger one wins outright:
     *
     *  1. A **delimited language token** — `[de]`, `(ger)`, `.de.`, `_deutsch_`. Somebody wrote that
     *     to say what the book is, so it is taken at its word.
     *  2. A **chapter word** — `Kapitel 03.mp3`, `Chapter 12.m4b`. Incidental evidence, but strong
     *     in practice, and it is what a library whose files nobody has tagged actually has.
     *
     * A bare two-letter run is never a token on its own: `de`, `en` and `it` are ordinary words in
     * French, Spanish, Italian and English titles, and "Le Comte de Monte-Cristo" is not a German
     * book. Two-letter codes count only inside brackets or between non-space separators.
     */
    fun fromNames(folderName: String, fileNames: List<String>): String? {
        val haystacks = (listOf(folderName) + fileNames).map(::fold)
        return mostCommon(haystacks.flatMap(::tokenMatches))
            ?: mostCommon(haystacks.flatMap(::chapterWordMatches))
    }

    /** Tier 1: a language named between delimiters, or a bare word for the long forms. */
    private fun tokenMatches(folded: String): List<String> {
        val out = mutableListOf<String>()
        for (m in DELIMITED.findAll(folded)) {
            val token = m.groupValues[1]
            (byCode[token] ?: byIso3[token] ?: byName[token])?.let { out += it.code }
        }
        // A whole word may name a language, but only in a form no other language borrows: "german",
        // "deutsch", "francais". Never a two- or three-letter code, which is why this runs over
        // `byName` alone.
        for (m in WORD.findAll(folded)) {
            byName[m.value]?.let { out += it.code }
        }
        return out
    }

    /** Tier 2: the word a chapter file is named after. */
    private fun chapterWordMatches(folded: String): List<String> =
        WORD.findAll(folded).mapNotNull { byChapterWord[it.value]?.code }.toList()

    /**
     * The commonest code, ties going to the first seen.
     *
     * `groupingBy` keeps insertion order, so "first seen" means the earliest of the folder name and
     * then the files in the order given — deterministic, which is what stops a book changing
     * language between scans.
     */
    private fun mostCommon(codes: List<String>): String? =
        codes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /**
     * Lower-cased and stripped of accents, so one spelling of a word is enough in the table above.
     *
     * NFD splits a letter into its base and its combining marks; dropping the marks leaves the
     * base. Anything outside the Latin script passes through untouched, which is why the table
     * lists no Cyrillic or CJK chapter words.
     */
    private fun fold(raw: String): String =
        Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING, "")

    private val COMBINING = "\\p{M}+".toRegex()

    /**
     * A token between delimiters. The opening side may be a bracket or one of `._-`, or the very
     * start of the name; the closing side likewise. A space is NOT a delimiter here — that is the
     * whole guard against matching the French "de".
     */
    private val DELIMITED = "(?:^|[\\[({._-])([a-z]{2,12})(?=$|[\\])}._-])".toRegex()

    /** A whole word, space- or punctuation-delimited. */
    private val WORD = "[a-z]{3,12}".toRegex()
}
