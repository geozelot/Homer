package com.geozelot.homer.ui.home

import androidx.annotation.StringRes
import com.geozelot.homer.R

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
 *  - **AND across facets.** `language:de author:Pratchett` is German books by Pratchett. Each facet
 *    you add narrows.
 *  - **OR within a facet.** `author:Pratchett author:Gaiman` is either of them. Two values on the
 *    same axis is the only reading that is ever useful — a book has one author, so ANDing them
 *    would guarantee an empty shelf.
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

/** The values a book presents on one axis. Author and series are single; tags are many. */
internal fun BookListItem.valuesFor(facet: FilterFacet): List<String> = when (facet) {
    FilterFacet.AUTHOR -> listOfNotNull(author)
    FilterFacet.SERIES -> listOfNotNull(series)
    // The effective collection, so filtering by a collection also finds the books of a plain series
    // standing in as its own — the same fallback the shelf stacks by.
    FilterFacet.COLLECTION -> listOfNotNull(effectiveCollection())
    FilterFacet.GENRE -> listOfNotNull(genre)
    FilterFacet.LANGUAGE -> listOfNotNull(language)
    FilterFacet.TAG -> tags
    // Every state that currently holds. A book is "downloaded" and "started" at once, and listing
    // both is what lets two state tokens OR together like two authors do.
    FilterFacet.STATE -> BookState.entries.filter { it.holds(this) }.map { it.key }
}

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

    /** Whether [book] survives every committed facet and the free text. */
    fun matches(book: BookListItem): Boolean {
        // Grouped by facet so the OR-within / AND-across rule falls out of the structure rather
        // than out of a chain of conditions somebody has to read carefully.
        for ((facet, group) in tokens.groupBy { it.facet }) {
            val values = book.valuesFor(facet)
            if (group.none { token -> values.any { it.equals(token.value, ignoreCase = true) } }) return false
        }
        val needle = text.trim()
        return needle.isEmpty() || book.matchesText(needle)
    }
}

/** Free-text match, across everything a token could have been placed on plus the title. */
internal fun BookListItem.matchesText(needle: String): Boolean =
    title.contains(needle, ignoreCase = true) ||
        author?.contains(needle, ignoreCase = true) == true ||
        series?.contains(needle, ignoreCase = true) == true ||
        collection?.contains(needle, ignoreCase = true) == true ||
        genre?.contains(needle, ignoreCase = true) == true ||
        tags.any { it.contains(needle, ignoreCase = true) }

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

    val counts = LinkedHashMap<Pair<FilterFacet, String>, Int>()
    for (book in books) {
        for (facet in FilterFacet.entries) {
            for (value in book.valuesFor(facet)) {
                if (!value.contains(needle, ignoreCase = true)) continue
                if (facet to value.lowercase() in taken) continue
                counts[facet to value] = (counts[facet to value] ?: 0) + 1
            }
        }
    }
    return counts.entries
        .map { (key, count) -> FilterSuggestion(key.first, key.second, count) }
        .sortedWith(
            compareBy(
                { !it.value.startsWith(needle, ignoreCase = true) },
                { -it.count },
                { it.value.lowercase() },
            ),
        )
        .take(limit)
}
