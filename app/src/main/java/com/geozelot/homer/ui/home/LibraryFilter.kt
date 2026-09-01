package com.geozelot.homer.ui.home

import androidx.annotation.StringRes
import com.geozelot.homer.R
import java.text.Normalizer

/**
 * What the library's one input box means.
 *
 * There are two operations here and they are kept distinct in the model even though they share a
 * field: a **filter** reduces the set to books whose author, series, genre, language or tag is
 * exactly a chosen value, and a **search** matches free text across all of those at once. Typing
 * does the second; committing a suggestion does the first.
 *
 * Combining rule, chosen once so it never has to be argued per facet:
 *
 *  - **AND, everywhere.** Every pill narrows. `language:de author:Pratchett` is German books by
 *    Pratchett, and `tag:Klassiker tag:Gelesen` is books carrying BOTH tags. One rule for every
 *    combination, so a pill always means the same thing: fewer books than before.
 *  - **The cost, accepted deliberately.** Two values on a SINGLE-valued axis now leave nothing —
 *    `author:Pratchett author:Gaiman` is empty, because no book has two authors. Within-facet used
 *    to be OR precisely to avoid that. It was traded away because OR made a pill ambiguous: adding
 *    one could widen or narrow depending on which axis it landed on, and "downloaded AND started"
 *    was unsayable while being the more useful of the two readings. An empty shelf at least states
 *    its own cause, and the count beside Clear says how many of how many.
 *  - **AND with the text.** Committed tokens stay in force while the free text narrows what is
 *    left, rather than replacing them.
 *
 * The vocabulary needs no storage of its own: every value a token can hold is already in the loaded
 * book list, so [suggest] reads it off the shelf rather than out of an index.
 */

/** An axis a filter token can sit on. */
enum class FilterFacet(val key: String, @StringRes val label: Int) {
    AUTHOR("author", R.string.filter_facet_author),
    SERIES("series", R.string.filter_facet_series),
    COLLECTION("collection", R.string.filter_facet_collection),
    GENRE("genre", R.string.filter_facet_genre),
    LANGUAGE("language", R.string.filter_facet_language),
    TAG("tag", R.string.filter_facet_tag),

    /**
     * A book's STATE rather than its metadata — downloaded, started, finished, edited.
     *
     * One facet holding several named states rather than one facet each, because they combine the
     * way the others do: two states OR together ("downloaded or started"), and adding a state
     * narrows against the metadata facets. A facet per state would make "downloaded AND started"
     * the reading, which is the less useful one and cannot express the other.
     */
    STATE("is", R.string.filter_facet_state),

    /**
     * Free text somebody committed rather than a value chosen off the shelf.
     *
     * The odd one out, and deliberately so: every other facet matches a value the library actually
     * holds, and this one matches the way TYPING matches — [BookListItem.matchesText], across every
     * field at once, forgiving accents and a slipped letter. It exists because pressing enter on a
     * query should keep it and clear the box for the next one, and a word that spans no single facet
     * ("hexen" before the series is known to exist) has no value to be committed as.
     *
     * [valuesFor] returns nothing for it, which is what keeps it out of the suggestion list: the box
     * cannot offer you text you have not typed yet.
     */
    TEXT("text", R.string.filter_facet_text),
    ;

    companion object {
        fun from(key: String?): FilterFacet? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One committed filter.
 *
 * [value] is stored as it appears on the books, not as it was typed, so the pill reads the way the
 * library does and the comparison never has to case-fold at match time.
 */
data class FilterToken(val facet: FilterFacet, val value: String) {
    /** Round-trips through the saved-state string form. */
    fun encode(): String = "${facet.key}:$value"

    companion object {
        fun decode(raw: String): FilterToken? {
            val facet = FilterFacet.from(raw.substringBefore(':', "")) ?: return null
            return raw.substringAfter(':').takeIf { it.isNotBlank() }?.let { FilterToken(facet, it) }
        }
    }
}

/**
 * The states a book can be filtered on, and how to tell.
 *
 * [key] is what a token stores and what the suggestion list offers, so it is deliberately a plain
 * lower-case word rather than an enum name: `is:downloaded` reads as something a person wrote.
 */
enum class BookState(val key: String, @StringRes val label: Int, val holds: (BookListItem) -> Boolean) {
    DOWNLOADED("downloaded", R.string.filter_state_downloaded, { it.isDownloaded }),
    STARTED("started", R.string.filter_state_started, { it.started && !it.finished }),
    FINISHED("finished", R.string.filter_state_finished, { it.finished }),

    /** Never opened. Not the same as "not finished" — this is the unread shelf. */
    UNSTARTED("unstarted", R.string.filter_state_unstarted, { !it.started && !it.finished }),

    /** Carries a correction somebody made, which is how you find what you have already fixed. */
    EDITED("edited", R.string.filter_state_edited, { it.hasEdits }),

    /** In a series or a collection — the counterpart of a standalone. */
    IN_SERIES("series", R.string.filter_state_in_series, { it.series != null || it.collection != null }),

    /** Length unknown, so it has never been measured. The books a measure pass would work on. */
    UNMEASURED("unmeasured", R.string.filter_state_unmeasured, { it.totalDurationMs == null }),

    /** No artwork resolved — the books a cover pass would work on. */
    NO_COVER("no-cover", R.string.filter_state_no_cover, { it.coverModel == null }),
    ;

    companion object {
        fun from(key: String): BookState? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/**
 * The values a book presents on one axis, for MATCHING. Author and series are single; tags are many.
 *
 * See [suggestionValuesFor] for the deliberately narrower set the suggestion list offers.
 */
internal fun BookListItem.valuesFor(facet: FilterFacet): List<String> = when (facet) {
    FilterFacet.AUTHOR -> listOfNotNull(author)
    FilterFacet.SERIES -> listOfNotNull(series)
    // The effective collection, so filtering by a collection also finds the books of a plain series
    // standing in as its own — the same fallback the shelf stacks by.
    FilterFacet.COLLECTION -> listOfNotNull(effectiveCollection())
    // Every genre a book carries, not just the one it shelves under: `genre:Humor` should find a
    // book filed under Fantasy that is also funny. The shelf can only put it in one place; the
    // filter is under no such constraint.
    FilterFacet.GENRE -> genres
    FilterFacet.LANGUAGE -> listOfNotNull(language)
    FilterFacet.TAG -> tags
    // Every state that currently holds. A book is "downloaded" and "started" at once, and listing
    // both is what lets two state tokens OR together like two authors do.
    FilterFacet.STATE -> BookState.entries.filter { it.holds(this) }.map { it.key }
    // Nothing. A text token is not a value off the shelf, so there is nothing here to offer or to
    // compare against — see how [LibraryFilter.matches] singles it out.
    FilterFacet.TEXT -> emptyList()
}

/**
 * The values a book contributes to the SUGGESTION vocabulary.
 *
 * Identical to [valuesFor] on every axis but one. `collection:` offers only a collection somebody
 * actually expressed, never the series-standing-in-as-its-own fallback — because on that fallback
 * every single series in the library appeared twice in the box, once as `series:Die Hexen` and again
 * as `collection:Die Hexen`, two suggestions that select the same books. Half the vocabulary was a
 * duplicate of the other half.
 *
 * Matching keeps the fallback, and that asymmetry is the point rather than an oversight: a
 * `collection:` token already committed against a plain series has to go on working, and
 * `collection:Discworld` has to keep finding Discworld's unaffiliated books. What changes is only
 * what the box VOLUNTEERS.
 */
internal fun BookListItem.suggestionValuesFor(facet: FilterFacet): List<String> =
    if (facet == FilterFacet.COLLECTION) listOfNotNull(collection) else valuesFor(facet)

/**
 * The whole state of the input box: what has been committed, and what is still being typed.
 */
data class LibraryFilter(
    val tokens: List<FilterToken> = emptyList(),
    val text: String = "",
) {
    val isEmpty: Boolean get() = tokens.isEmpty() && text.isBlank()

    /** Adds a token, ignoring one already present — committing the same suggestion twice is a no-op. */
    fun plus(token: FilterToken): LibraryFilter =
        if (token in tokens) this else copy(tokens = tokens + token)

    fun minus(token: FilterToken): LibraryFilter = copy(tokens = tokens - token)

    fun withText(value: String): LibraryFilter = copy(text = value)

    /** Whether [book] survives every committed token and the free text. */
    fun matches(book: BookListItem): Boolean {
        // A flat AND over every token — no grouping by facet any more, because there is no longer a
        // within-facet rule for the grouping to express. Every pill is one more condition.
        for (token in tokens) {
            val holds = if (token.facet == FilterFacet.TEXT) {
                // Matched as typing is matched, not by equality: a committed word is the same
                // question as the word still in the box, only kept.
                book.matchesText(token.value)
            } else {
                book.valuesFor(token.facet).any { it.equals(token.value, ignoreCase = true) }
            }
            if (!holds) return false
        }
        val needle = text.trim()
        return needle.isEmpty() || book.matchesText(needle)
    }
}

/**
 * Everything free text is matched against — the token axes plus the title.
 *
 * Language is deliberately absent. It is stored as a two-letter code, and two letters match
 * something in almost every library by accident.
 */
internal fun BookListItem.searchFields(): List<String> = buildList {
    add(title)
    author?.let { add(it) }
    series?.let { add(it) }
    // The EFFECTIVE collection, so text search agrees with what a collection token would have
    // matched — a plain series standing in as its own collection answers to that name either way.
    effectiveCollection()?.let { add(it) }
    genre?.let { add(it) }
    addAll(tags)
}

/**
 * Free-text match: every WORD typed has to land somewhere, not the whole string in one field.
 *
 * The old rule was a single `contains` of the entire query against each field in turn, which meant
 * a query only ever worked if every word of it sat in the SAME field. `pratchett hexen` could not
 * match anything at all — the author holds one word, the series holds the other — and the reader
 * had no way to know that a query spanning two facets was the one shape the box could not answer.
 *
 * So: AND across the typed words, OR across the fields. Each word must be found in at least one
 * field, but they need not agree on which. That is the same shape as the committed-token rule one
 * level up (AND across facets, OR within), which is what makes typing and committing feel like the
 * same operation rather than two.
 *
 * Each word matches by [termMatches], which is forgiving in the two ways that actually come up in
 * a library: accents, and one slipped letter.
 */
internal fun BookListItem.matchesText(needle: String): Boolean {
    val terms = queryTerms(needle)
    if (terms.isEmpty()) return true
    val fields = searchFields().map { fold(it) }
    return terms.all { term -> fields.any { field -> termMatches(field, term) } }
}

/**
 * One typed word against one field.
 *
 * Two chances, cheapest first, because this runs for every word against every field of every book
 * on every keystroke:
 *
 *  1. **Substring.** Carries every prefix and every mid-word fragment — `pratch`, `welt`. A word of
 *     the field that STARTS with the term is a substring too, so that case needs no pass of its own
 *     here; it earns a better RANK in [suggest], which is a different question from matching.
 *  2. **Edit distance against a single word of the field**, and only for terms long enough that one
 *     wrong letter is a typo rather than a different word. `pratchet` finds Pratchett; `maerchen`
 *     finds Märchen, which matters here because folding an umlaut to its base letter (ä→a) and
 *     spelling it out (ä→ae) are both things a German reader will type.
 *
 * Terms of three characters or fewer get no fuzz at all. At that length an edit distance of one
 * reaches most of the alphabet, and the substring pass already covers what a short term is for.
 */
internal fun termMatches(foldedField: String, foldedTerm: String): Boolean {
    if (foldedTerm.isEmpty()) return true
    if (foldedField.contains(foldedTerm)) return true
    val tolerance = when {
        foldedTerm.length <= 3 -> return false
        foldedTerm.length <= 6 -> 1
        else -> 2
    }
    for (word in wordsOf(foldedField)) {
        // Length alone rules most words out before the matrix is ever built.
        if (kotlin.math.abs(word.length - foldedTerm.length) > tolerance) continue
        if (editDistanceAtMost(word, foldedTerm, tolerance)) return true
    }
    return false
}

/**
 * Levenshtein distance, but only ever asked whether it is within [max].
 *
 * Two rolling rows rather than a full matrix, and it abandons a row whose best cell already exceeds
 * the tolerance — for the common case of two words that are simply different, that is a handful of
 * comparisons rather than a product of their lengths.
 */
internal fun editDistanceAtMost(a: String, b: String, max: Int): Boolean {
    if (a == b) return true
    if (kotlin.math.abs(a.length - b.length) > max) return false
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var rowBest = current[0]
        for (j in 1..b.length) {
            val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            if (current[j] < rowBest) rowBest = current[j]
        }
        // Nothing later in the matrix can come back under the tolerance once a whole row is over it.
        if (rowBest > max) return false
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length] <= max
}

/**
 * The words a query was typed as.
 *
 * Split on whitespace by hand rather than with a regex. Unit tests here run on the JVM's
 * `java.util.regex` and the app runs on Android's ICU-backed engine, which is stricter — a pattern
 * that passes every test can still throw on a device, and this file is on the path of every scan
 * and every keystroke. See the notes on the template patterns for the time that took the app down.
 */
internal fun queryTerms(query: String): List<String> {
    val terms = ArrayList<String>()
    val word = StringBuilder()
    for (ch in query) {
        if (ch.isWhitespace()) {
            if (word.isNotEmpty()) { terms.add(fold(word.toString())); word.setLength(0) }
        } else {
            word.append(ch)
        }
    }
    if (word.isNotEmpty()) terms.add(fold(word.toString()))
    return terms
}

/** The words of an already-folded value, on the same no-regex rule as [queryTerms]. */
internal fun wordsOf(folded: String): List<String> {
    val words = ArrayList<String>()
    val word = StringBuilder()
    for (ch in folded) {
        if (ch.isLetterOrDigit()) {
            word.append(ch)
        } else if (word.isNotEmpty()) {
            words.add(word.toString()); word.setLength(0)
        }
    }
    if (word.isNotEmpty()) words.add(word.toString())
    return words
}

/**
 * Case and accents removed, so what is typed need not carry either.
 *
 * NFD splits a letter from its accent and the combining marks are then dropped, which turns ä into
 * a and é into e. ß is spelled out separately because it does not decompose — it is one letter with
 * no accent to remove — and a reader who types `strasse` should still find Straße.
 *
 * Deliberately NOT the German ä→ae convention: folding to the base letter keeps one rule for every
 * language in the library, and [termMatches]'s edit-distance pass picks up the spelled-out form on
 * its own, since ae for a is exactly one insertion.
 */
internal fun fold(value: String): String {
    // Plain ASCII has nothing to decompose and nothing to strip, and it is most of what most
    // libraries hold, so it skips the normaliser entirely rather than allocating twice over to
    // discover there was no work to do.
    var plain = true
    for (ch in value) {
        if (ch.code > 0x7F) {
            plain = false
            break
        }
    }
    if (plain) return value.lowercase()

    val expanded = if (value.indexOf('\u00df') >= 0) value.replace("\u00df", "ss") else value
    val decomposed = Normalizer.normalize(expanded, Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) out.append(ch)
    }
    return out.toString().lowercase()
}

/** A value the input box can offer to turn into a token. */
data class FilterSuggestion(val facet: FilterFacet, val value: String, val count: Int)

/**
 * What to offer for what has been typed so far.
 *
 * Ranked by how well the typed text lands rather than by how many books a value has: a value that
 * STARTS with what you typed is what you meant, and one that merely contains it is a second guess.
 * Size breaks the tie, because between two equally good matches the bigger shelf is the likelier
 * target.
 *
 * Values already committed are left out — offering a token that is already a pill above the list is
 * offering to do nothing.
 */
internal fun suggest(
    books: List<BookListItem>,
    typed: String,
    committed: List<FilterToken>,
    limit: Int = 8,
): List<FilterSuggestion> {
    val needle = typed.trim()
    val taken = committed.map { it.facet to it.value.lowercase() }.toSet()

    // Nothing typed: offer the STATES. They are the one axis whose values a reader cannot guess by
    // typing something they already know — you can type an author's name, but "which word does this
    // app use for downloaded" is unanswerable, and the keys are English so typing the German for it
    // matches nothing. Offered as a short fixed menu the moment the box opens, and only the states
    // that would actually leave something behind.
    if (needle.isEmpty()) {
        return BookState.entries
            .filter { FilterFacet.STATE to it.key !in taken }
            .map { state -> FilterSuggestion(FilterFacet.STATE, state.key, books.count(state.holds)) }
            .filter { it.count > 0 }
    }

    // The same words, matched the same way as the free text — see matchesText. Offering only values
    // that contain the WHOLE query meant the box went silent the moment a second word was typed:
    // `pratchett hexen` matched no single value, so the one thing worth committing out of that
    // query — the series — was never offered. A value earns its place by answering ANY of the
    // words, since each word is a different facet's worth of the question.
    val terms = queryTerms(needle)
    val lastTerm = terms.last()

    // One fold per DISTINCT value, not one per book. The same author is folded once however many
    // books carry it, which on a shelf of three hundred is the difference between a few dozen
    // normalisations per keystroke and a few thousand.
    val foldCache = HashMap<String, String>()
    fun foldedOf(value: String) = foldCache.getOrPut(value) { fold(value) }

    val counts = LinkedHashMap<Pair<FilterFacet, String>, Int>()
    for (book in books) {
        for (facet in FilterFacet.entries) {
            for (value in book.suggestionValuesFor(facet)) {
                if (terms.none { termMatches(foldedOf(value), it) }) continue
                if (facet to value.lowercase() in taken) continue
                counts[facet to value] = (counts[facet to value] ?: 0) + 1
            }
        }
    }
    // Ranked BEFORE sorting, not inside the comparator. `compareBy` calls its selectors on every
    // comparison, so ranking in there re-folded and re-split each value O(n log n) times — for the
    // one list that is rebuilt on every keystroke.
    //
    // Ranking is on WORD prefixes as well as the string's own start: "Die Hexen" is what somebody
    // typing `hexen` means, and whole-string prefix alone scored it below every value that merely
    // contained the word, because the series happens to begin with an article.
    return counts.entries
        .map { (key, count) ->
            val value = key.second
            Ranked(
                suggestion = FilterSuggestion(key.first, value, count),
                rank = suggestionRank(foldedOf(value), terms, lastTerm),
            )
        }
        .sortedWith(
            compareBy(
                { it.rank },
                { -it.suggestion.count },
                { it.suggestion.value.lowercase() },
            ),
        )
        .take(limit)
        .map { it.suggestion }
}

/** A suggestion with its rank already worked out — see [suggest]. */
private class Ranked(val suggestion: FilterSuggestion, val rank: Int)

/**
 * How well a value answers what was typed: 0 best, 4 worst.
 *
 * Two independent gradients, in this order:
 *
 *  - **Whole-value prefix beats word prefix beats neither.** Typing `Anders`, "Anderson" is a better
 *    answer than "Le Anderson" even though the latter has more books — what you typed begins it. But
 *    "Le Anderson" still beats a value that merely contains the letters somewhere, which is the tier
 *    that did not exist before and is what "Die Hexen" needs to be offered for `hexen`.
 *  - **The last word beats the earlier ones.** It is the one still being typed; the earlier words of
 *    a multi-word query have usually already found whatever they were for.
 */
private fun suggestionRank(folded: String, terms: List<String>, lastTerm: String): Int {
    val words = wordsOf(folded)
    if (folded.startsWith(lastTerm)) return 0
    if (words.any { it.startsWith(lastTerm) }) return 1
    if (terms.any { folded.startsWith(it) }) return 2
    if (terms.any { term -> words.any { it.startsWith(term) } }) return 3
    return 4
}
