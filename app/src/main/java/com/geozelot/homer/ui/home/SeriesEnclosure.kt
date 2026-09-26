package com.geozelot.homer.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.Surface1

// ── Expanded series enclosure ─────────────────────────────────────────────────
//
// An opened series reads as ONE faint-bordered card with a clear start and end. It used to be
// exactly that — a single grid item wrapping a header and a 3-up grid of episodes — but that
// composed every episode at once, so a 100-book series hitched on open. It is now emitted as a
// run of separate lazy items that each draw their slice of the same enclosure, so the container
// is back and only what's on screen composes.

/** Corner radius of the enclosure's two caps. */
internal val SeriesEnclosureRadius = 12.dp

/** Inset between the enclosure's border and the header/cards inside it, in grid view. */
private val SeriesEnclosurePad = 12.dp

/**
 * The same inset in list view. Matches [SeriesShelfRow]'s own padding, so expanding a shelf slips
 * a border around it without nudging its contents sideways.
 */
internal val SeriesListEnclosurePad = 8.dp

/**
 * Half the grid's `verticalArrangement` spacing. Each slice paints this far into the gaps above
 * and below it, so the side rails meet across the gap instead of the enclosure looking like a
 * stack of separate boxes. Keep it at half of [LibraryGridSpacing].
 */
private val SeriesEnclosureBleed = LibraryGridSpacing / 2

/**
 * Draws one slice of the enclosure that wraps an opened series.
 *
 * The trick is to draw the WHOLE rounded rectangle oversized — running past this slice's top
 * and/or bottom edge for any slice that isn't the cap — and then clip back to the slice plus its
 * half-gap. What survives inside the clip is just the two side rails; the rounded caps and the
 * horizontal rules fall outside it. Stack the slices and they read as one continuous container.
 *
 * Drawn rather than composed because a lazy grid gives no way to paint behind a run of items:
 * anything spanning them would have to be a single item, which is the composition cost this
 * replaced.
 */
internal fun Modifier.seriesEnclosure(top: Boolean, bottom: Boolean): Modifier = this.drawBehind {
    val radius = SeriesEnclosureRadius.toPx()
    val stroke = 1.dp.toPx()
    val bleed = SeriesEnclosureBleed.toPx()
    // What stays visible: this slice, plus the half-gap on any open side.
    val clipTop = if (top) 0f else -bleed
    val clipBottom = if (bottom) size.height else size.height + bleed
    // Where the rectangle itself runs to: inset by half a stroke on a capped side so the border
    // lands fully inside, pushed a full corner radius past the clip on an open one so neither the
    // rounding nor the horizontal rule can show up mid-shelf.
    val rectTop = if (top) stroke / 2f else clipTop - radius
    val rectBottom = if (bottom) size.height - stroke / 2f else clipBottom + radius
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(stroke / 2f, rectTop, size.width - stroke / 2f, rectBottom),
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
    clipRect(top = clipTop, bottom = clipBottom) {
        drawPath(path, Surface1)
        drawPath(path, Line, style = Stroke(stroke))
    }
}

/**
 * Top slice of an opened series: the title banner, tapped to collapse. No bottom padding — the
 * grid's own 12dp item gap separates it from the first row of episodes, and the enclosure paints
 * straight through that gap.
 */
@Composable
internal fun ExpandedSeriesHeader(
    series: LibraryEntry.Series,
    ctx: RowContext,
    flat: Boolean,
    onOrderChange: (Boolean) -> Unit,
    onFilter: (FilterToken) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .seriesEnclosure(top = true, bottom = false)
            // Clip AFTER the enclosure so the ripple is bounded by the rounded top without also
            // clipping away the bleed the enclosure draws into the gap below.
            .clip(RoundedCornerShape(topStart = SeriesEnclosureRadius, topEnd = SeriesEnclosureRadius))
            .clickable(onClick = onCollapse),
    ) {
        Row(
            modifier = Modifier.padding(
                start = SeriesEnclosurePad,
                end = SeriesEnclosurePad,
                top = SeriesEnclosurePad,
                bottom = SeriesEnclosurePad,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    series.name,
                    // Same face and size as the list view's shelf row, so a series title reads
                    // identically whichever view it is in. The serif stays for the empty states,
                    // which are the only headings on this screen that aren't a row of something.
                    color = Parchment,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = ListRowTitleLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = MetaChipSlot.TextInset),
                )
                // The same header the list view draws, built the same way: what this shelf is,
                // then what it holds, on one line.
                MetaChipSlot(
                    chips = shelfKindChip(series),
                    ctx = ctx,
                    onFilter = { kind, value -> onFilter(chipToken(kind, value)) },
                    trailing = seriesMeta(series, ctx, LocalContext.current, expanded = true),
                    modifier = Modifier.padding(top = MetaChipSlot.TitleGap),
                )
            }
            if (series.hasThreads()) {
                // Held off the chevron: the two were touching, so a chip that is a control and an
                // arrowhead that is a state read as one cluster and invited a tap on the wrong half.
                CollectionOrderChip(
                    flat = flat,
                    onChange = onOrderChange,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            // The list view's arrowhead, not a keyboard chevron — the same glyph for the same state,
            // so an open shelf looks open in both views. It points at the contents below it rather
            // than at the action, exactly as the list's does.
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.home_cd_collapse_series),
                tint = Amber,
            )
        }
        // Separates the shelf's own title from its episodes. Inset from the enclosure's side rails
        // so it reads as a rule inside the card rather than a second edge of it.
        HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = SeriesEnclosurePad))
    }
}

/**
 * One row of episodes inside the enclosure. Episodes are emitted a row at a time rather than as
 * individual grid cells because the enclosure has to be drawn by the items themselves — and a row
 * is still lazy, so at most one row of cards composes at once instead of the whole series.
 */
/**
 * A sub-series heading inside an opened collection.
 *
 * Draws its own slice of the same enclosure the books around it draw, so the shelf stays one
 * bordered card rather than breaking into pieces at every heading. Quieter and smaller than a shelf
 * heading in the library proper — it labels a thread inside a card that is already titled, and at
 * the same weight the two would compete.
 */
@Composable
internal fun ExpandedSubHeader(label: String, last: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .seriesEnclosure(top = false, bottom = last)
            .padding(horizontal = SeriesListEnclosurePad)
            .padding(bottom = if (last) SeriesListEnclosurePad else 0.dp),
    ) {
        Text(
            label.uppercase(),
            style = SectionLabel,
            // A step brighter, like the shelf headings outside the enclosure — a sub-series is the
            // structure of what is open, not a footnote to it.
            color = Muted,
            modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 4.dp),
        )
    }
}

@Composable
internal fun ExpandedSeriesRow(
    books: List<BookListItem>,
    last: Boolean,
    /** The grid's column count, so a short last row pads out to the same cell width as a full one. */
    columns: Int,
    ctx: RowContext,
    onOpen: (String) -> Unit,
    actions: BookActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .seriesEnclosure(top = false, bottom = last)
            .padding(
                start = SeriesEnclosurePad,
                end = SeriesEnclosurePad,
                bottom = if (last) SeriesEnclosurePad else 0.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
    ) {
        books.forEach { book ->
            Box(modifier = Modifier.weight(1f)) {
                BookGridCard(book, ctx, onOpen = onOpen, actions = actions)
            }
        }
        // Hold the last row's cards to the same width as a full row's.
        repeat(columns - books.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

/**
 * The line beside a shelf row.
 *
 * The volume count and the downloaded count moved onto the cover, so what is left is the author and
 * the length — the two facts that need words. Restating the counts here as well would put the same
 * number twice on one row, two centimetres apart.
 */
internal fun seriesMeta(
    series: LibraryEntry.Series,
    ctx: RowContext,
    context: android.content.Context,
    /**
     * Whether the shelf is open, which changes what the line is ABOUT.
     *
     * Folded, a shelf is one item standing among books and reads like one: the same chip above it,
     * and beneath it the same kind of line a book has. Opened, its books are on screen saying all of
     * that for themselves — so the line stops describing the shelf's contents one by one and starts
     * describing the shelf: how many, how long, how much of it is on this device.
     *
     * The counts used to be on BOTH, which is why a folded shelf carried a length, a book count, a
     * download count and an author while the book beside it carried a name.
     */
    expanded: Boolean = false,
): String = buildList {
    if (expanded) {
        add(
            context.resources.getQuantityString(
                R.plurals.home_series_book_count,
                series.books.size,
                series.books.size,
            ),
        )
        seriesTotalMs(series)?.let { add(formatCompactDuration(it)) }
        val downloaded = series.books.count { it.isDownloaded }
        when {
            downloaded == 0 -> Unit
            // "Offline" unqualified only when the WHOLE shelf is here; a partial count says how
            // many, or a series with one downloaded volume claims to be listenable on a train.
            downloaded == series.books.size -> add(context.getString(R.string.details_offline))
            else -> add(
                context.resources.getQuantityString(
                    R.plurals.home_meta_offline_count,
                    downloaded,
                    downloaded,
                ),
            )
        }
        return@buildList
    }
    // Folded: exactly what a book row says, by the same rule — the author, unless the heading or
    // the chip is already carrying it.
    if (!ctx.shelving.isByAuthor && !shelfChip(series, ctx).carriesAuthor()) {
        add(series.shownAuthors.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: context.getString(R.string.unknown_author))
    }
}.joinToString(" · ")

/** A series' length, but only once EVERY episode is measured — a partial sum understates it. */
internal fun seriesTotalMs(series: LibraryEntry.Series): Long? {
    val measured = series.books.mapNotNull { it.totalDurationMs?.takeIf { d -> d > 0 } }
    return if (measured.size == series.books.size && measured.isNotEmpty()) measured.sum() else null
}
