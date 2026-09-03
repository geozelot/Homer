package com.geozelot.homer.data.metadata

import java.util.Locale

/**
 * The genres a book can be filed under: a closed, translated vocabulary.
 *
 * ## Why closed
 *
 * Genre was free text, and free text in a library shared between devices and interface languages
 * splits: "Kurzgeschichten" and "Short Stories" become two shelves meaning one thing, and whichever
 * language the tagger happened to use is the language every reader gets. Language shelves do not
 * have this problem because Android ships the translation table for languages. There is no such
 * table for genres, so Homer has to own one — and owning one only works if the set is finite.
 *
 * A genre is therefore a [key] that gets stored and a label that gets shown, resolved in the
 * reader's locale when it is drawn. Two people filing the same book in two languages land on the
 * same shelf.
 *
 * ## Why the labels are HERE and not in `strings.xml`
 *
 * Every other piece of text in Homer is a string resource, and this is the deliberate exception.
 * The labels are not only shown, they are also *recognised*: a tag written "Wissenschaft" has to
 * resolve to [SCIENCE], which means the German label has to be readable by [resolve] — and [resolve]
 * has no `Context`, being pure so it can be tested.
 *
 * The alternative was to keep labels in resources and repeat the German spellings in an alias table.
 * That is two lists of the same facts, which is the single most reliable source of bugs in this
 * codebase: five copies of "the fields an edit can touch" drifted and cost a data-loss defect. Here
 * a missing translation is a *compile error*, because the constructor demands one — a stronger
 * guarantee than lint's missing-translation warning, which is what resources would have given.
 *
 * Adding a language means adding a parameter and filling in 47 blanks the compiler will not let you
 * skip.
 *
 * ## Why the closure applies to EDITING only
 *
 * Detection still surfaces whatever a tag says. A book tagged `Blues`, or something nobody
 * anticipated, keeps that genre: it displays as written, it filters, it shelves. [resolve] returns
 * null and callers fall back to the raw string.
 *
 * A closed vocabulary that also policed detection would silently delete genres the files actually
 * carry, and dropping them to "no genre" throws information away on the say-so of a list this file
 * happens to contain today. The picker is how a genre is *chosen*; editing a book is how an
 * unrecognised one gets replaced.
 *
 * ## The list
 *
 * Authored for audiobooks rather than adapted from ID3, whose ~150 codes are almost entirely music —
 * Blues, Ska, Eurodance — and which has no entry at all for [RADIO_PLAY], a category much of a
 * German library may sit in. [Id3Genres] still decodes numeric tag codes to names; this is what
 * those names then resolve onto.
 */
enum class BookGenre(
    /** What gets STORED. Stable — changing one orphans every book already filed under it. */
    val key: String,
    private val en: String,
    private val de: String,
    /** Which half of the library it belongs to, so a list of 47 is two scannable lists. */
    val shelf: Shelf,
    /**
     * Further spellings that resolve here.
     *
     * Only what real tags carry, not every synonym anybody might type: a wrong alias files books
     * under the wrong shelf and is far harder to notice than a missing one. [key], [en] and [de]
     * are always recognised and are not repeated here.
     */
    private vararg val aliases: String,
) {
    FANTASY("fantasy", "Fantasy", "Fantasy", Shelf.FICTION),
    SCI_FI("scifi", "Science Fiction", "Science-Fiction", Shelf.FICTION, "SciFi", "Sci-Fi", "SF"),
    MYSTERY("mystery", "Mystery", "Mystery", Shelf.FICTION),
    THRILLER("thriller", "Thriller", "Thriller", Shelf.FICTION),
    CRIME("crime", "Crime", "Krimi", Shelf.FICTION, "Kriminalroman", "Detective"),
    TRUE_CRIME("truecrime", "True Crime", "True Crime", Shelf.NONFICTION, "Wahre Verbrechen"),
    HORROR("horror", "Horror", "Horror", Shelf.FICTION),
    ROMANCE("romance", "Romance", "Liebesroman", Shelf.FICTION),
    HISTORICAL_FICTION("histfic", "Historical Fiction", "Historischer Roman", Shelf.FICTION, "Historical"),
    LITERARY("literary", "Literary Fiction", "Belletristik", Shelf.FICTION, "Literary", "Roman"),
    ADVENTURE("adventure", "Adventure", "Abenteuer", Shelf.FICTION),
    WESTERN("western", "Western", "Western", Shelf.FICTION),
    SHORT_STORIES("shortstories", "Short Stories", "Kurzgeschichten", Shelf.FICTION, "Short Story", "Kurzgeschichte"),
    POETRY("poetry", "Poetry", "Lyrik", Shelf.FICTION),
    DRAMA("drama", "Drama", "Drama", Shelf.FICTION),
    HUMOUR("humour", "Humour", "Humor", Shelf.FICTION, "Humor", "Comedy", "Komödie"),
    SATIRE("satire", "Satire", "Satire", Shelf.FICTION),
    DYSTOPIAN("dystopian", "Dystopian", "Dystopie", Shelf.FICTION, "Dystopie"),
    MYTHOLOGY("mythology", "Mythology", "Mythologie", Shelf.FICTION),
    FAIRY_TALES("fairytales", "Fairy Tales", "Märchen", Shelf.FICTION, "Fairy Tale"),
    CHILDRENS("childrens", "Children's", "Kinderbuch", Shelf.FICTION, "Childrens", "Children", "Kinder"),
    YOUNG_ADULT("youngadult", "Young Adult", "Jugendbuch", Shelf.FICTION, "YA", "Jugendroman"),
    RADIO_PLAY("radioplay", "Radio Play", "Hörspiel", Shelf.FICTION, "Radio Drama", "Hoerspiel"),
    CLASSICS("classics", "Classics", "Klassiker", Shelf.FICTION),
    NON_FICTION("nonfiction", "Non-Fiction", "Sachbuch", Shelf.NONFICTION, "Nonfiction", "Sachbücher"),
    BIOGRAPHY("biography", "Biography", "Biografie", Shelf.NONFICTION, "Biographie"),
    MEMOIR("memoir", "Memoir", "Autobiografie", Shelf.NONFICTION, "Autobiography", "Autobiographie"),
    HISTORY("history", "History", "Geschichte", Shelf.NONFICTION),
    SCIENCE("science", "Science", "Wissenschaft", Shelf.NONFICTION),
    NATURE("nature", "Nature", "Natur", Shelf.NONFICTION),
    TECHNOLOGY("technology", "Technology", "Technik", Shelf.NONFICTION),
    PHILOSOPHY("philosophy", "Philosophy", "Philosophie", Shelf.NONFICTION),
    PSYCHOLOGY("psychology", "Psychology", "Psychologie", Shelf.NONFICTION),
    POLITICS("politics", "Politics", "Politik", Shelf.NONFICTION),
    ECONOMICS("economics", "Economics", "Wirtschaft", Shelf.NONFICTION, "Business", "Wirtschaftsbuch"),
    SELF_HELP("selfhelp", "Self-Help", "Ratgeber", Shelf.NONFICTION, "Self Help"),
    HEALTH("health", "Health", "Gesundheit", Shelf.NONFICTION),
    TRAVEL("travel", "Travel", "Reise", Shelf.NONFICTION),
    RELIGION("religion", "Religion", "Religion", Shelf.NONFICTION),
    ART("art", "Art", "Kunst", Shelf.NONFICTION),
    MUSIC("music", "Music", "Musik", Shelf.NONFICTION),
    SPORT("sport", "Sport", "Sport", Shelf.NONFICTION),
    EDUCATION("education", "Education", "Bildung", Shelf.NONFICTION),
    LANGUAGE_LEARNING("language", "Language Learning", "Sprachkurs", Shelf.NONFICTION, "Language", "Sprache"),
    ESSAYS("essays", "Essays", "Essays", Shelf.NONFICTION),
    JOURNALISM("journalism", "Journalism", "Journalismus", Shelf.NONFICTION),
    REFERENCE("reference", "Reference", "Nachschlagewerk", Shelf.NONFICTION),
    ;

    /** The two groups the picker splits by. */
    enum class Shelf { FICTION, NONFICTION }

    /**
     * The name to show, written in [locale].
     *
     * Takes the locale rather than reading the default for the same reason `BookLanguage.displayName`
     * does: the caller that matters is a composable, and a shelf heading has to re-resolve when the
     * interface language changes — the list it labels is built by a ViewModel that survives the
     * activity recreation a language change causes.
     */
    fun label(locale: Locale): String = if (locale.language == "de") de else en

    /**
     * Every string that resolves to this genre.
     *
     * `aliases.toList()` rather than `+ aliases`: a vararg is an `Array`, and `List + Array` is
     * ambiguous enough that the compiler gives up on the whole expression and reports the private
     * properties as unresolved instead.
     */
    internal fun allSpellings(): List<String> = listOf(key, en, de) + aliases.toList()

    companion object {

        /**
         * The genre [raw] names, or null when nothing here does.
         *
         * Accepts a stored [key], either label, or an alias — so a value written by an older build
         * ("Fantasy"), by a German tagger ("Wissenschaft"), or by this one ("science") all land on
         * the same entry. Case, spacing and punctuation are ignored, since "Science Fiction",
         * "Science-Fiction" and "sciencefiction" are one answer to everything but a string compare.
         *
         * Null rather than a default: an unrecognised genre is kept and shown as written, and
         * guessing would file a book under something nobody chose.
         */
        fun resolve(raw: String?): BookGenre? {
            val folded = raw?.let(::fold)?.ifEmpty { null } ?: return null
            return byFolded[folded]
        }

        /** [raw] as it should be SHOWN — the translated label, or [raw] itself if unrecognised. */
        fun display(raw: String, locale: Locale): String = resolve(raw)?.label(locale) ?: raw.trim()

        /** [raw] as it should be STORED — the canonical key, or [raw] itself if unrecognised. */
        fun canonical(raw: String): String = resolve(raw)?.key ?: raw.trim()

        /** The vocabulary in one shelf, in the reader's alphabetical order. */
        fun offered(shelf: Shelf, locale: Locale): List<BookGenre> =
            entries.filter { it.shelf == shelf }.sortedBy { it.label(locale).lowercase(locale) }

        private val byFolded: Map<String, BookGenre> by lazy {
            buildMap {
                // BookGenre.entries, qualified: inside `buildMap` the receiver is a MutableMap, so
                // a bare `entries` is the MAP's entries and the loop silently changes meaning.
                for (genre in BookGenre.entries) {
                    for (spelling in genre.allSpellings()) {
                        // First writer wins, so a collision cannot silently re-point an earlier
                        // genre — and `duplicateSpellings` in the tests fails the build if one
                        // exists at all.
                        putIfAbsent(fold(spelling), genre)
                    }
                }
            }
        }

        /**
         * Lower-cased, trimmed, and stripped of everything that distinguishes nothing.
         *
         * `Locale.ROOT` on purpose: folding in the interface's locale would make a Turkish device
         * lower-case "I" to a dotless one and stop recognising its own vocabulary.
         */
        internal fun fold(value: String): String =
            value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        /** Every spelling that resolves, for the test that guards against a collision. */
        internal fun spellings(): List<Pair<BookGenre, String>> =
            entries.flatMap { genre -> genre.allSpellings().map { genre to it } }
    }
}
