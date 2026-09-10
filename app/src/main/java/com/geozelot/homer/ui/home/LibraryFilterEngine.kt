package com.geozelot.homer.ui.home

import androidx.annotation.StringRes
import com.geozelot.homer.R
import com.geozelot.homer.data.db.dao.BookProgress
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.library.decodeGenres
import com.geozelot.homer.data.library.hasMetadataEdit
import com.geozelot.homer.data.metadata.BookGenre
import javax.inject.Inject

// ── The list pipeline ─────────────────────────────────────────────────────────
//
// Every step between what Room delivers and what the grid draws, as pure functions of their
// inputs: apply the overrides, join the progress and download state, filter, collapse series,
// sort, shelve. [HomeViewModel] owns the flows and the scope; this file owns what happens on each
// emission, and none of it touches Android — which is what lets the whole pipeline run under a
// plain JVM test (see LibraryFilterEngineTest).

/** Detected book with its override applied, plus the override-only bits (not book fields). */
internal data class EffectiveBook(
    val book: BookEntity,
    val hidden: Boolean,
    /** Whether the override carries any metadata correction — not just a hidden flag or a tag. */
    val hasEdits: Boolean,
    val tags: List<String>,
    val finishedOverride: Boolean?,
    val downloadOnPlayOverride: Boolean?,
    /** Resolved cover model, computed here (rarely) rather than on every progress tick. */
    val coverModel: Any?,
)

/**
 * The filter/sort/shelving pipeline behind [HomeViewModel]'s library flows.
 *
 * Stateless — a class rather than free functions only so Hilt can hand it to the ViewModel as one
 * named collaborator. The cover model is taken as a LAMBDA rather than resolved here, because
 * resolving one needs credentials and a WebDAV client and everything else in this file is pure.
 */
class LibraryFilterEngine @Inject constructor() {

    /** Detection with user overrides applied (D2), hidden books filtered unless shown. */
    internal fun effective(
        books: List<BookEntity>,
        overrides: List<BookOverrideEntity>,
        showHidden: Boolean,
        coverModel: (BookEntity) -> Any?,
    ): List<EffectiveBook> {
        val overrideByBook = overrides.associateBy { it.bookId }
        return books
            .map { book ->
                val override = overrideByBook[book.id]
                val effective = book.applyOverride(override)
                EffectiveBook(
                    book = effective,
                    hidden = override?.hidden == true,
                    hasEdits = override != null && override.hasMetadataEdit(),
                    tags = override?.tags?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
                    finishedOverride = override?.finished,
                    downloadOnPlayOverride = override?.downloadOnPlay,
                    coverModel = coverModel(effective),
                )
            }
            .filter { showHidden || !it.hidden }
    }

    /** Joins progress and download state onto the effective books — the rows the UI renders. */
    internal fun rows(
        effective: List<EffectiveBook>,
        progress: List<BookProgress>,
        downloads: List<DownloadEntity>,
    ): List<BookListItem> {
        val progressByBook = progress.associateBy { it.bookId }
        val downloadByBook = downloads.associateBy { it.bookId }
        return effective.map { eff ->
            val book = eff.book
            val bookProgress = progressByBook[book.id]
            val elapsed = bookProgress?.elapsedMs
            val total = book.totalDurationMs
            val download = downloadByBook[book.id]
            // A trustworthy percentage / time-left needs a total, a saved position AND every
            // file measured. Without the completeness check a partially-measured book reports
            // elapsed > total, which reads as "finished" and hides it from the listening shelf.
            val measured = total != null && total > 0 && elapsed != null &&
                bookProgress.fullyMeasured
            BookListItem(
                id = book.id,
                title = book.title,
                author = book.author,
                isMultiFile = book.isMultiFile,
                fileCount = book.fileCount,
                coverModel = eff.coverModel,
                hasCustomCover = book.customCoverPath != null,
                series = book.series,
                seriesIndex = book.seriesIndex,
                collection = book.collection,
                collectionIndex = book.collectionIndex,
                genres = decodeGenres(book.genre),
                language = book.language,
                tags = eff.tags,
                hasEdits = eff.hasEdits,
                totalDurationMs = total,
                timeLeftMs = if (measured) (total!! - elapsed!!).coerceAtLeast(0) else null,
                progress = if (measured) (elapsed!!.toFloat() / total!!).coerceIn(0f, 1f) else null,
                lastPlayedAt = bookProgress?.updatedAt,
                started = bookProgress?.started == true,
                finishedOverride = eff.finishedOverride,
                downloadOnPlayOverride = eff.downloadOnPlayOverride,
                downloadStatus = download?.status,
                downloadedFiles = download?.downloadedFiles ?: 0,
                hidden = eff.hidden,
            )
        }
    }

    /** Library list, filtered by [filter], ordered by [sort], sectioned by [shelving]. */
    fun arrange(
        books: List<BookListItem>,
        filter: LibraryFilter,
        sort: LibrarySort,
        shelving: LibraryShelving,
        series: LibraryDepth,
    ): List<LibraryEntry> {
        // Filtering runs BEFORE the grouping: it changes which books are on which shelf, so a
        // shelf that loses its last book has to disappear rather than stand there empty.
        val filtered = if (filter.isEmpty) books else books.filter { filter.matches(it) }
        return buildEntries(filtered, sort, shelving, series)
    }

    /** In-progress books: actually started (real progress), not finished/at-end, not hidden;
     *  most-recently-played first. Merely opening a book (position 0) does NOT qualify. */
    fun listening(books: List<BookListItem>): List<BookListItem> =
        books.asSequence()
            .filter { it.started && !it.finished && !it.hidden }
            .sortedByDescending { it.lastPlayedAt }
            .take(LISTENING_LIMIT)
            .toList()

    private companion object {
        const val LISTENING_LIMIT = 12
    }
}

/**
 * Builds the render list. [shelving] alone decides sectioning and whether series collapse into
 * shelves (decoupled from [sort], which orders the units within): Author/Series group into shelves,
 * a series shelf positioned by [sort] but its episodes always in reading order; None is a flat
 * sorted list; Genre sections flat by genre.
 */
private fun buildEntries(
    books: List<BookListItem>,
    sort: LibrarySort,
    shelving: LibraryShelving,
    series: LibraryDepth,
): List<LibraryEntry> {
    // Collapsing follows the series control alone now. It used to be decided by the shelving, so
    // "by genre" silently flattened every series while "by author" kept them stacked — nobody
    // chose that, it fell out of one expression.
    val units = collapseIntoUnits(books, series)
    val ordered = units.sortedWith(unitComparator(sort))

    return when (shelving) {
        LibraryShelving.ITEM -> ordered.map { it.toEntry() }
        LibraryShelving.AUTHOR ->
            sectioned(ordered, "Unknown author", R.string.home_shelf_unknown_author) { it.author }
        // Grouped on the CANONICAL genre and sorted by it, so "Kurzgeschichten" and "Short Stories"
        // are one shelf rather than two that mean the same thing. The heading itself resolves to the
        // reader's language when it draws — see LibraryEntry.Header.genre.
        LibraryShelving.GENRE -> sectioned(
            ordered,
            "No genre",
            R.string.home_shelf_no_genre,
            asGenre = true,
        ) { unit ->
            when (unit) {
                is SortUnit.Solo -> unit.book.genre
                is SortUnit.Ser -> seriesGenre(unit.series.books)
            }?.let { BookGenre.canonical(it) }
        }
    }
}

/** A list unit awaiting placement: a standalone book or a collapsed series shelf. */
internal sealed interface SortUnit {
    data class Solo(val book: BookListItem) : SortUnit
    data class Ser(val series: LibraryEntry.Series) : SortUnit

    val author: String?
        get() = when (this) {
            is Solo -> book.author
            is Ser -> series.author
        }

    fun toEntry(): LibraryEntry = when (this) {
        is Solo -> LibraryEntry.Standalone(book)
        is Ser -> series
    }
}

/** Reading order within a series: by series index when known, then title. */
private val inSeriesOrder: Comparator<BookListItem> =
    compareBy({ it.seriesIndex == null }, { it.seriesIndex }, { it.title.lowercase() })

/**
 * Collapses author+series sets into ordered series units; everything else stays solo.
 *
 * A set of ONE counts. It used to need two members, so a series you own a single volume of was
 * indistinguishable from a standalone book — which hid the fact that it belongs to something, and
 * meant the shelf silently appeared the day a second volume arrived.
 */
internal fun collapseIntoUnits(
    books: List<BookListItem>,
    depth: LibraryDepth = LibraryDepth.SERIES,
): List<SortUnit> {
    if (depth == LibraryDepth.FLAT) return books.map { SortUnit.Solo(it) }

    // At COLLECTION depth the grouping key is the collection — which falls back to the series for
    // the overwhelming majority of books that are in no collection. That fallback is what keeps an
    // ordinary series stacked at this depth instead of coming apart merely because nobody nested
    // it inside anything, and it is why a library with no collections looks identical either way.
    val collectionDepth = depth == LibraryDepth.COLLECTION
    val keyOf: (BookListItem) -> String? =
        if (collectionDepth) BookListItem::collectionKey else { b -> b.series?.let { "${b.author.orEmpty()}|$it" } }
    val nameOf: (BookListItem) -> String? =
        if (collectionDepth) BookListItem::effectiveCollection else BookListItem::series
    val order = if (collectionDepth) inCollectionOrder else inSeriesOrder

    val grouped = books.filter { keyOf(it) != null }.groupBy { keyOf(it)!! }
    val consumed = HashSet<String>()
    val units = mutableListOf<SortUnit>()
    for ((key, members) in grouped) {
        units += SortUnit.Ser(
            LibraryEntry.Series(
                key = key,
                name = nameOf(members.first())!!,
                author = members.first().author,
                books = members.sortedWith(order),
                // Named only when it is a real parent. A collection that exists purely because a
                // series fell back to being its own is not a collection anybody made, and drawing
                // it as one would put a badge on every series in the library.
                isCollection = collectionDepth && members.any { it.collection != null },
            ),
        )
        members.forEach { consumed += it.id }
    }
    books.forEach { if (it.id !in consumed) units += SortUnit.Solo(it) }
    return units
}

private fun unitComparator(sort: LibrarySort): Comparator<SortUnit> = when (sort) {
    LibrarySort.TITLE -> compareBy { unitTitle(it).lowercase() }
    LibrarySort.AUTHOR -> compareBy(
        { it.author == null }, { it.author?.lowercase() }, { unitTitle(it).lowercase() },
    )
    // Never-played / unmeasured sort last under the descending orders.
    LibrarySort.RECENT -> compareByDescending { unitRecency(it) }
    LibrarySort.DURATION -> compareByDescending { unitDuration(it) }
}

private fun unitTitle(u: SortUnit): String = when (u) {
    is SortUnit.Solo -> u.book.title
    is SortUnit.Ser -> u.series.name
}

private fun unitRecency(u: SortUnit): Long = when (u) {
    is SortUnit.Solo -> u.book.lastPlayedAt ?: Long.MIN_VALUE
    is SortUnit.Ser -> u.series.books.maxOf { it.lastPlayedAt ?: Long.MIN_VALUE }
}

private fun unitDuration(u: SortUnit): Long = when (u) {
    is SortUnit.Solo -> u.book.totalDurationMs ?: -1L
    is SortUnit.Ser -> u.series.books.sumOf { it.totalDurationMs ?: 0L }
}

/**
 * The genre a series shelves under: the one its books agree on, or the commonest if they don't.
 *
 * Series used to fall into "No genre" wholesale, on the reasoning that a series can span genres —
 * but that made shelving by genre hide every series in the library under one heading nobody was
 * looking in. Disagreement is the rare case and it is now the user's to fix, since a series edit
 * can set the genre for every volume at once.
 *
 * Ties go to the earliest volume: [books] arrive in reading order and `groupingBy` preserves it, so
 * a two-genre series shelves under the one it started as. Null only when NO volume has a genre.
 */
internal fun seriesGenre(books: List<BookListItem>): String? =
    books.mapNotNull { it.genre }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

/** Groups [units] into sections keyed by [keyOf] (nulls last under [fallback]) with headers. */
private fun sectioned(
    units: List<SortUnit>,
    fallback: String,
    @StringRes fallbackRes: Int,
    sortBy: (String) -> String = { it },
    asGenre: Boolean = false,
    keyOf: (SortUnit) -> String?,
): List<LibraryEntry> {
    val byKey = units.groupBy(keyOf)
    val keys = byKey.keys.sortedWith(compareBy({ it == null }, { it?.let(sortBy)?.lowercase() }))
    return buildList {
        for (key in keys) {
            // `title` stays the English fallback even when `titleRes` replaces it on screen: it is
            // also the header's identity for the duplicate-title key in the grid, which must not
            // change with the interface language.
            add(
                LibraryEntry.Header(
                    title = key ?: fallback,
                    titleRes = if (key == null) fallbackRes else null,
                    genre = key.takeIf { asGenre },
                ),
            )
            byKey.getValue(key).forEach { add(it.toEntry()) }
        }
    }
}
