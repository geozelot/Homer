package com.geozelot.homer.ui.home

import androidx.annotation.StringRes
import com.geozelot.homer.R

/**
 * How a book's series and collection resolve into the two levels the shelf can draw.
 *
 * Pure functions over [BookListItem], so every rule here is testable without a database or a device.
 *
 * The whole design rests on two rules, and neither of them is a flag the user has to set:
 *
 *  - **A series with no collection is its own collection.** Without this, switching the shelf to
 *    show collections would flatten every ordinary series in the library — The Expanse would come
 *    apart purely because nobody had put it inside anything. With it, The Expanse is a collection of
 *    one series and stacks whichever level is being shown.
 *  - **A collection carrying a [BookListItem.collectionIndex] is also a series.** Discworld is 41
 *    numbered novels *and* a parent of seven threads. If the books carry a number on the collection
 *    axis it has a reading order and can be read straight through; if they do not, it is a loose
 *    grouping. The distinction is data, not a mode.
 */

/**
 * The collection this book belongs to for grouping purposes — its own if it has one, otherwise its
 * series, otherwise nothing.
 *
 * The fallback is what makes a plain series survive being viewed at the collection level.
 */
internal fun BookListItem.effectiveCollection(): String? = collection ?: series

/**
 * A stable identity for a collection, qualified by author.
 *
 * Two authors may each have a "Chronicles"; without the author they would collapse into one shelf.
 * The same reasoning the series key already uses.
 */
internal fun BookListItem.collectionKey(): String? =
    effectiveCollection()?.let { "${author.orEmpty()}|$it" }

/**
 * Whether these books form a collection that ALSO reads as a series — one with an order of its own.
 *
 * True when any member carries a collection index. Any rather than all: a collection is often only
 * partly numbered, and one numbered volume is still evidence that an order exists.
 *
 * A collection that is only the series fallback is never "also a series" in this sense — it simply
 * IS the series, and asking the question of it would be asking whether a series is a series.
 */
internal fun List<BookListItem>.collectionHasReadingOrder(): Boolean =
    any { it.collection != null && it.collectionIndex != null }

/**
 * The order to read a collection in: by collection index where there is one, then by series, then
 * by the order within that series, then by title.
 *
 * Books with no collection index sort after those that have one rather than being dropped among
 * them — several Discworld novels belong to no thread, and they belong at the end of the shelf
 * rather than scattered through it.
 */
internal val inCollectionOrder: Comparator<BookListItem> = compareBy<BookListItem>(
    { it.collectionIndex == null },
    { it.collectionIndex ?: Int.MAX_VALUE },
    { it.series?.lowercase() ?: "" },
    { it.seriesIndex ?: Int.MAX_VALUE },
    { it.title.lowercase() },
)

/**
 * How deep the shelf stacks a collection and the series inside it.
 *
 * Replaces the two-way stacked/flat series switch. Only one nesting direction has meaning — a
 * collection contains series, never the reverse — so the choice is not *which contains which* but
 * how far down the stacking goes.
 *
 * [COLLECTION] and [SERIES] look identical in a library that has no collections, because every
 * series falls back to being its own collection. That is intended: nothing changes for a library
 * that never nested a folder.
 */
enum class LibraryDepth(val key: String, @StringRes val label: Int) {
    /** Discworld is one card; Rincewind and the rest are shelves inside it. */
    COLLECTION("collection", R.string.depth_collection),

    /** Rincewind, Death and the Watch stand alone; Discworld's unaffiliated books sit loose. */
    SERIES("series", R.string.depth_series),

    /** Every book loose in the list. */
    FLAT("flat", R.string.depth_flat),
    ;

    companion object {
        /**
         * [key] as a depth.
         *
         * "stacked" is the key the old two-way series switch stored for its stacked state, and it
         * maps to [SERIES] rather than [COLLECTION]: it is what that setting used to mean, and a
         * preference that silently deepens under someone is worse than one that stays put.
         */
        fun from(key: String?): LibraryDepth = when (key) {
            "stacked" -> SERIES
            else -> entries.firstOrNull { it.key == key } ?: SERIES
        }
    }
}

/**
 * The cover a stacked shelf shows: the first volume that HAS one.
 *
 * Not simply the first volume. A series whose opening book was ripped without art used to put a
 * blank plate at the front of the stack and hide the artwork of every volume behind it — the one
 * case where the shelf had something to show and showed nothing. Falling back to null (rather than
 * to the first book regardless) keeps the placeholder for a series where genuinely nothing has art.
 */
internal fun LibraryEntry.Series.frontCover(): Any? = books.firstNotNullOfOrNull { it.coverModel }

/**
 * Whether this book has progress worth drawing a bar for.
 *
 * Two guards beyond "progress is non-null". A book at 0% has not been started and a bar of nothing
 * is noise on every unread card in the library; a book at 99.5% has effectively finished and a bar
 * indistinguishable from full is worse than none. `started` is checked as well as the fraction
 * because marking a book completed RESETS it to position 0, which is perfectly measurable — so
 * progress comes back as 0f rather than null, and the card went on drawing a bar for a book that
 * had just been cleared.
 */
internal fun BookListItem.hasVisibleProgress(): Boolean =
    started && !finished && progress?.let { it > 0.01f && it < 0.995f } == true

/**
 * One piece of an opened shelf: either a sub-series heading, or some of its books.
 *
 * An opened COLLECTION is not a flat list — Discworld is seven threads and a handful of novels
 * belonging to none of them, and forty-one titles in a single run says nothing about which is which.
 * An opened plain series IS a flat list, and produces exactly one [Books] run per row, which is what
 * it produced before any of this existed.
 */
sealed interface ShelfRow {
    /** The name of a sub-series inside a collection. */
    data class SubHeader(val label: String) : ShelfRow

    /** As many books as fit one row of the view drawing it. */
    data class Books(val books: List<BookListItem>) : ShelfRow
}

/**
 * How an opened shelf breaks into rows, [columns] books at a time.
 *
 * A plain series gets no headings at all: it has one thread and labelling it with its own name
 * inside a card already titled that would be saying the same word twice.
 *
 * Inside a collection, the threads come in the order the collection reads — by the first book of
 * each, so a numbered collection lists its threads in publication order rather than alphabetically —
 * and the books belonging to no thread come last under no heading, because they are not a group,
 * they are what is left.
 */
internal fun LibraryEntry.Series.expandedRows(columns: Int): List<ShelfRow> {
    val perRow = columns.coerceAtLeast(1)
    if (!isCollection) return books.chunked(perRow).map { ShelfRow.Books(it) }

    val threads = LinkedHashMap<String, MutableList<BookListItem>>()
    val loose = mutableListOf<BookListItem>()
    // `books` is already in collection order, so first-seen IS the order the collection reads.
    for (book in books) {
        val thread = book.series
        if (thread == null) loose += book else threads.getOrPut(thread) { mutableListOf() } += book
    }

    return buildList {
        for ((thread, members) in threads) {
            add(ShelfRow.SubHeader(thread))
            members.chunked(perRow).forEach { add(ShelfRow.Books(it)) }
        }
        loose.chunked(perRow).forEach { add(ShelfRow.Books(it)) }
    }
}
