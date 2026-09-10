package com.geozelot.homer.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.metadata.BookGenre
import com.geozelot.homer.ui.components.ControlPillHeight
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel

// ── The library grid ─────────────────────────────────────────────────────────
//
// The single lazy grid every view is built from — list view included, which is this grid at one
// column — together with the metrics that decide a cell's size and the headers it pins. Everything
// here answers "where does an item go"; what an item looks like is in LibraryCards / LibraryRows.

/** Columns in grid view. Shared with the expanded-series rows, which lay out their own cells. */
/**
 * The width a grid cell aims for, and what the column count is derived from.
 *
 * Chosen so a 360dp phone still gets the three columns the grid was designed around; anything wider
 * gets more rather than three stretched ones. It is a *target*, not a minimum — the cells share out
 * whatever is left over, so the real width lands within about 15dp of this either way.
 */
private val LibraryGridTargetCell = 100.dp

/**
 * How many columns fit in [width].
 *
 * The count used to be the literal 3, which is the right answer for the screen it was written on and
 * wrong at both ends: on a small phone three cells left ~95dp each for a cover, a two-line title and
 * a meta line, and on a tablet the same three sat in the middle of a very wide sheet.
 *
 * Note what this does NOT do: it does not scale anything with density. A `dp` is already a physical
 * size — a 100dp cell is the same width in millimetres on every screen — so the thing that actually
 * varies between devices is how many of them fit, which is exactly what this counts. Type follows
 * the OS font scale on its own, because it is all `sp`.
 *
 * Floored at two, because one column of cover-plus-footer is a worse list than list view already is.
 */
internal fun gridColumnsFor(width: Dp): Int {
    val usable = width - LibraryGridPadding * 2 + LibraryGridSpacing
    val each = LibraryGridTargetCell + LibraryGridSpacing
    return (usable / each).toInt().coerceIn(2, 6)
}

/**
 * The height a control's tap target claims, whatever the pill inside it measures.
 *
 * Named because the control row's alignment is built on the difference between the two.
 */
internal val ControlTapHeight = 48.dp

/**
 * How far below the row's top edge a collapsed pill actually starts.
 *
 * A chip is a 28dp pill centred in a 48dp target, so its outline begins 10dp down. A field that
 * replaces that chip and starts at the row's own top edge therefore begins 10dp HIGHER than the
 * chip it replaced — the control appears to jump upward on opening, which is the one thing an
 * in-place expansion is supposed not to do. Both expanded fields carry this inset, so their top
 * border lands exactly where the icons' did.
 *
 * Applied top and bottom, so the arrange field — which is one pill tall — also leaves the row
 * exactly as high as it was and the library below it does not shift.
 */
internal val ControlRowInset = (ControlTapHeight - ControlPillHeight) / 2

/** Gap between grid cells, both axes. The series enclosure paints across half of it. */
internal val LibraryGridSpacing = 12.dp

/**
 * Extra air under a grid card's text block, so its last line belongs to it rather than to the
 * cover beneath.
 *
 * Small on purpose: the grid's own 12dp is nearly right, and a card that stands too far off its
 * neighbours stops reading as part of a shelf.
 */
internal val GridCardFooterGap = 5.dp

/** The grid's horizontal content padding. */
internal val LibraryGridPadding = 16.dp

/**
 * Width of one grid cell at [totalWidth] — the LazyVerticalGrid's own arithmetic, factored out so
 * the collapsed listening strip can size itself against a real cover instead of guessing at a
 * literal dp that drifts the moment the grid's padding or column count changes.
 */
internal fun gridCellWidth(totalWidth: Dp): Dp {
    val columns = gridColumnsFor(totalWidth)
    return ((totalWidth - LibraryGridPadding * 2 - LibraryGridSpacing * (columns - 1)) / columns)
        .coerceAtLeast(0.dp)
}

internal fun LazyGridScope.libraryContent(
    entries: List<LibraryEntry>,
    gridView: Boolean,
    /** How many columns the grid resolved to — see [gridColumnsFor]. */
    columns: Int,
    /** Collections the reader has asked to see as one numbered run — see [LibrarySettings]. */
    flatCollections: Set<String>,
    onCollectionOrder: (collection: String, flat: Boolean) -> Unit,
    ctx: RowContext,
    /** Book ids anchoring the open series shelves — see [isOpen]. */
    expanded: MutableList<String>,
    onBookClick: (String) -> Unit,
    actions: BookActions,
) {
    // Membership test for the open shelves, hoisted out of the per-entry loop: `expanded` holds
    // one id per book of every open series, so a linear scan per entry would be quadratic on a
    // large library. Reading the state list here still subscribes the grid content to changes.
    val openAnchors = expanded.toHashSet()

    /**
     * A shelf is open while ANY of its books is anchored, and opening one anchors all of them.
     * Anchoring the whole membership is what makes the state survive an edit: keying on the series
     * name collapsed the shelf when the user renamed it, and keying on the lowest book id (the fix
     * for that) collapsed it when precisely that book was moved out of the series.
     */
    fun isOpen(entry: LibraryEntry.Series) = entry.books.any { it.id in openAnchors }

    // Tested against the live list, not `openAnchors`: that snapshot is from the last time this
    // ran, so two taps landing in one frame would anchor the same shelf twice.
    fun open(entry: LibraryEntry.Series) {
        entry.books.forEach { if (it.id !in expanded) expanded.add(it.id) }
    }

    fun close(entry: LibraryEntry.Series) {
        expanded.removeAll(entry.books.mapTo(HashSet()) { it.id })
    }

    // Neither the Currently-listening shelf nor the library's own header and sort/group bar are items here:
    // HomeScreen pins all three above the grid so they stay reachable while it scrolls.

    // Two headers really can carry the same title — a book whose author metadata literally reads
    // "Unknown author" gets its own section beside the fallback one — and duplicate keys make the
    // lazy layout throw. Disambiguating by list position did the job but tied every header's key to
    // how many rows happened to precede it, so adding one book above re-created the lot; counting
    // repeats of the title is just as unique and only changes when the titles themselves do.
    val headerOrdinals = HashMap<String, Int>()

    entries.forEach { entry ->
        when (entry) {
            is LibraryEntry.Header -> item(
                span = { GridItemSpan(maxLineSpan) },
                key = "header:${entry.title}#${headerOrdinals.merge(entry.title, 1, Int::plus)}",
            ) {
                // More air above a shelf heading in LIST view. The gap is the same 12dp in both,
                // but a grid row is a cover tall and a list row is 46dp — so the same measurement
                // reads as a pause in one and as a crowd in the other.
                SectionLabelRow(
                    headerLabel(entry),
                    // The grid's cards now carry their own footer gap, so a heading following a row
                    // would sit that much lower than one following a heading. Taking it back here
                    // keeps every heading the same distance from what precedes it.
                    topPadding = if (gridView) 12.dp - GridCardFooterGap else 20.dp,
                    // A step brighter than the rows under it. A heading that names a shelf is the
                    // structure of the list rather than a note about it, and at Muted it sat at the
                    // same weight as the meta lines it was organising.
                    color = Parchment,
                )
            }
            is LibraryEntry.Standalone -> {
                if (gridView) {
                    item(key = entry.book.id) {
                        BookGridCard(entry.book, ctx, onOpen = onBookClick, actions = actions)
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }, key = entry.book.id) {
                        BookListRow(entry.book, startPadding = 0.dp, ctx = ctx, onOpen = onBookClick, actions = actions)
                    }
                }
            }
            is LibraryEntry.Series -> {
                val shelfKey = entry.expandKey
                val shelfOpen = isOpen(entry)
                // Only a collection has two readings, and only one with threads AND numbers has a
                // choice worth offering — see CollectionOrderChip.
                val flat = entry.isCollection && entry.name in flatCollections
                // The books of a collection read flat are numbered by the collection, so that is
                // what their corners must show, even for one that also sits in a thread.
                val shelfCtx = if (flat) ctx.copy(collectionNumbered = true) else ctx
                if (gridView) {
                    if (shelfOpen) {
                        // A header banner, then the episodes a row at a time — every one of them a
                        // separate lazy item drawing its own slice of the enclosure that wraps the
                        // whole shelf. See `seriesEnclosure`.
                        item(span = { GridItemSpan(maxLineSpan) }, key = "series-open:$shelfKey") {
                            ExpandedSeriesHeader(
                                series = entry,
                                ctx = ctx,
                                flat = flat,
                                onOrderChange = { onCollectionOrder(entry.name, it) },
                                onFilter = actions.onFilter,
                                onCollapse = { close(entry) },
                            )
                        }
                        // An opened COLLECTION breaks into its threads; an opened plain series is
                        // one run, exactly as before. See `expandedRows`.
                        val rows = entry.expandedRows(columns, flat = flat)
                        itemsIndexed(
                            rows,
                            span = { _, _ -> GridItemSpan(maxLineSpan) },
                            // First id in the row: unique across the library (a book sits in one
                            // series) and stable while the row's membership holds. A sub-heading
                            // keys on its own label, which is unique within the shelf.
                            key = { _, row ->
                                when (row) {
                                    is ShelfRow.SubHeader -> "sesub:$shelfKey:${row.label}"
                                    ShelfRow.LooseHeader -> "seloose:$shelfKey"
                                    is ShelfRow.Books -> "sep:${row.books.first().id}"
                                }
                            },
                        ) { index, row ->
                            val last = index == rows.lastIndex
                            when (row) {
                                is ShelfRow.SubHeader -> ExpandedSubHeader(row.label, last = last)
                                ShelfRow.LooseHeader ->
                                    ExpandedSubHeader(stringResource(R.string.home_shelf_loose), last = last)
                                is ShelfRow.Books -> ExpandedSeriesRow(
                                    columns = columns,
                                    books = row.books,
                                    last = last,
                                    ctx = shelfCtx,
                                    onOpen = onBookClick,
                                    actions = actions,
                                )
                            }
                        }
                    } else {
                        item(key = shelfKey) {
                            SeriesGridCard(
                                series = entry,
                                ctx = ctx,
                                onOpen = { open(entry) },
                                actions = actions,
                            )
                        }
                    }
                } else {
                    // Same enclosure as the grid: the shelf row is its top slice and each episode
                    // draws a slice below, so an open series reads as one bordered card here too.
                    // Collapsed, the row stays the self-contained card it has always been.
                    item(span = { GridItemSpan(maxLineSpan) }, key = shelfKey) {
                        SeriesShelfRow(
                            series = entry,
                            ctx = ctx,
                            expanded = shelfOpen,
                            flat = flat,
                            onOrderChange = { onCollectionOrder(entry.name, it) },
                            onToggle = { if (shelfOpen) close(entry) else open(entry) },
                            actions = actions,
                        )
                    }
                    if (shelfOpen) {
                        // One book per row here, so the same split produces one Books row each and
                        // the sub-headings land between the threads.
                        val listRows = entry.expandedRows(columns = 1, flat = flat)
                        itemsIndexed(
                            listRows,
                            span = { _, _ -> GridItemSpan(maxLineSpan) },
                            key = { _, row ->
                                when (row) {
                                    is ShelfRow.SubHeader -> "epsub:$shelfKey:${row.label}"
                                    ShelfRow.LooseHeader -> "eploose:$shelfKey"
                                    is ShelfRow.Books -> "ep:${row.books.first().id}"
                                }
                            },
                        ) { index, row ->
                            val last = index == listRows.lastIndex
                            if (row is ShelfRow.SubHeader) {
                                ExpandedSubHeader(row.label, last = last)
                                return@itemsIndexed
                            }
                            if (row is ShelfRow.LooseHeader) {
                                ExpandedSubHeader(stringResource(R.string.home_shelf_loose), last = last)
                                return@itemsIndexed
                            }
                            val book = (row as ShelfRow.Books).books.first()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .seriesEnclosure(top = false, bottom = last)
                                    .padding(horizontal = SeriesListEnclosurePad)
                                    .padding(bottom = if (last) SeriesListEnclosurePad else 0.dp),
                            ) {
                                // 2dp on top of the enclosure's own inset keeps each episode at
                                // exactly the indent it had before the border went round them.
                                BookListRow(
                                    book,
                                    startPadding = 2.dp,
                                    ctx = shelfCtx,
                                    onOpen = onBookClick,
                                    actions = actions,
                                    bordered = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Both pinned headers, and the in-list section labels.
 *
 * [large] is the resting size for the two pinned ones: they title whole regions rather than
 * separating rows inside a list, so they carry a little more weight. Scrolling into the library
 * drops them back to the in-list size along with the listening panel, since every pixel the pinned
 * block holds on to is a pixel of library the user can't see.
 */
/**
 * A shelf heading's words, resolved at DRAW time.
 *
 * Three sources, in order: a string resource for the "no author"/"no genre" fallbacks, a language
 * name for the language shelving, and otherwise the key itself (an author, a genre). None of them
 * may be baked into the entry — the ViewModel that builds the list survives the activity recreation
 * a language change causes, so a heading resolved at build time would still be in the old language.
 */
@Composable
private fun headerLabel(entry: LibraryEntry.Header): String = when {
    entry.titleRes != null -> stringResource(entry.titleRes)
    entry.genre != null -> BookGenre.display(entry.genre, LocalConfiguration.current.locales[0])
    else -> entry.title
}

@Composable
internal fun SectionLabelRow(
    text: String,
    topPadding: Dp = 12.dp,
    bottomPadding: Dp = 8.dp,
    large: Boolean = false,
    color: Color = Muted,
) {
    Text(
        text = text.uppercase(),
        style = SectionLabel,
        fontSize = if (large) SectionLabelLargeSize else SectionLabel.fontSize,
        color = color,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding, start = 2.dp),
    )
}

/** Resting size of the two pinned headers; they fall back to [SectionLabel]'s 12sp on scroll. */
private val SectionLabelLargeSize = 14.sp
