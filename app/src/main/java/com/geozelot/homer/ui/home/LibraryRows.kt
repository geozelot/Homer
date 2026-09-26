package com.geozelot.homer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.LineShelf
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Studio
import com.geozelot.homer.ui.theme.Well

// ── List rows ────────────────────────────────────────────────────────────────
//
// The list view's two rows — a book and a shelf — the flat counterparts of the grid cards. They
// borrow the enclosure geometry from SeriesEnclosure.kt so an opened shelf reads as one bordered
// block in both views.

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookListRow(
    book: BookListItem,
    startPadding: Dp,
    ctx: RowContext,
    onOpen: (String) -> Unit,
    actions: BookActions,
    /**
     * Whether the row draws its own card. True for a top-level row, so every item in the list
     * reads the same way a collapsed series shelf always did. False for an episode inside an
     * opened series, where the enclosure already IS the card and a second border inside it would
     * just be noise.
     */
    bordered: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .then(
                if (bordered) {
                    Modifier
                        .clip(RoundedCornerShape(SeriesEnclosureRadius))
                        .border(1.dp, Line, RoundedCornerShape(SeriesEnclosureRadius))
                        .background(Well)
                } else {
                    Modifier
                },
            )
            // Long-press opens the menu, the same as the grid card and the series shelf. The 3-dot
            // button stays — this is the shortcut, not a replacement for it — but list view was the
            // one place where holding a row did nothing, so the gesture learned in grid view
            // stopped working on switching.
            .combinedClickable(
                onClick = { onOpen(book.id) },
                onLongClick = { menuOpen = true },
            )
            .padding(if (bordered) SeriesListEnclosurePad else 6.dp)
            .alpha(if (book.hidden) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row's cover keeps the round badge rather than the grid's cut-to-the-edge areas: at
        // 46dp a slanted quadrilateral with a glyph in it is mush, and the corner it would anchor
        // to is a third of the cover.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                CoverArt(
                    model = book.coverModel,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                // Compact, which is what fits a "#12" on a 46dp cover without the badge taking most
                // of the artwork. The corners came off these covers once because a glyph at this
                // size was a smudge; a two-character number is not a glyph, and it is the one fact
                // about a book in a series that the row's single line of text keeps running out of
                // room for.
                VolumeIndexBadge(
                    index = volumeIndexFor(book, ctx),
                    modifier = Modifier.align(Alignment.TopStart),
                    size = BadgeSize.SMALL,
                )
                // Back on the cover, because the meta line that used to carry the word is gone.
                // The grid card has always said it here; the list row said it in text only because
                // it had a line spare, and it does not any more.
                if (book.isDownloaded) {
                    OfflineBadge(
                        CoverCorner.TOP_END,
                        modifier = Modifier.align(Alignment.TopEnd),
                        size = BadgeSize.SMALL,
                    )
                }
            }
            // Same bar, same place, whatever view a book appears in.
            if (book.hasVisibleProgress()) {
                ProgressBar(
                    fraction = book.progress ?: 0f,
                    modifier = Modifier.width(46.dp).padding(top = 4.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            // One line, ellipsised.
            //
            // It was two reserved lines, on the reasoning that books whose names share a long
            // prefix are indistinguishable at one. That was true when the row had a meta line
            // underneath carrying the author — the block was going to be two lines tall regardless,
            // so the title might as well have both. With the row down to a title and a chip, two
            // reserved lines is a blank line on most rows, and it made every row taller than the
            // cover beside it for the sake of the few titles that use it.
            Text(
                book.title,
                color = Parchment,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = ListRowTitleLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = MetaChipSlot.TextInset),
            )
            // The chip, and nothing else. Everything the meta line used to carry is either on the
            // cover (the length, offline, the volume number), in the chip (author or genre), or
            // drawn rather than written (progress). What was left was tags and a percentage, and
            // neither is worth a second line on every row in the library.
            MetaChipSlot(
                chips = bookChip(book, ctx),
                ctx = ctx,
                onFilter = { kind, value -> actions.onFilter(chipToken(kind, value)) },
                modifier = Modifier.padding(top = MetaChipSlot.TitleGap),
            )
        }
        Box {
            // Not an IconButton. Its 48dp minimum was what actually set a list row's height — the
            // 46dp cover beside it never got the chance — so every row in the library was as tall
            // as a control nobody looks at. 40dp is still a comfortable target, the row is 46dp
            // regardless because the cover governs now, and long-pressing anywhere on the row opens
            // the same menu.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            BookMenu(book, menuOpen, actions) { menuOpen = false }
        }
    }
}

/** Line height of a list row's title; the reserved block is two of these. */
internal val ListRowTitleLineHeight = 16.sp

// ── Series shelf row ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesShelfRow(
    series: LibraryEntry.Series,
    ctx: RowContext,
    expanded: Boolean,
    flat: Boolean,
    onOrderChange: (Boolean) -> Unit,
    onToggle: () -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expanded) {
                    // Top slice of the enclosure that carries on down over the episodes. Clipped
                    // after it, so the ripple is bounded by the rounded top without cutting off the
                    // bleed the enclosure paints into the gap below.
                    Modifier
                        .seriesEnclosure(top = true, bottom = false)
                        .clip(
                            RoundedCornerShape(
                                topStart = SeriesEnclosureRadius,
                                topEnd = SeriesEnclosureRadius,
                            ),
                        )
                } else {
                    // A shelf's own border tone — one step up from a book's, so a stack is
                    // distinguishable from a single book at a glance without becoming a second
                    // accent colour on a screen that already has one.
                    Modifier
                        .clip(RoundedCornerShape(SeriesEnclosureRadius))
                        .border(1.dp, LineShelf, RoundedCornerShape(SeriesEnclosureRadius))
                        .background(Well)
                },
            )
            .combinedClickable(onClick = onToggle, onLongClick = { menuOpen = true }),
    ) {
        Row(
            modifier = Modifier.padding(SeriesListEnclosurePad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The same stack the grid card draws, at row scale: the front cover on the bottom-left
            // with the edges of two more receding to the top-right. It used to fan three real covers
            // with the LAST drawn on top, so the cover a series row showed was its third volume's.
            //
            // 46dp square, which is a BOOK row's cover exactly — so a shelf sitting among those rows
            // lines up with them and does not make its own row taller. It was 52x56.
            // Gone the moment the shelf opens: its books are on screen underneath, each with its
            // own cover, so a pile of three of them at the top is the same picture twice — and the
            // 46dp it was taking is exactly what a long collection name has too little of. The grid
            // view's open header has never drawn one, so this is also the two views agreeing.
            if (!expanded) Box(modifier = Modifier.size(ShelfRowBox)) {
                val sheets = series.stackSheets(CoverStack.RowSteps.size)
                val pile = CoverStack.place(ShelfRowBox, RowStackPad, CoverStack.RowSteps, sheets.size)
                val models = listOf(series.frontCover()) + sheets
                for ((index, at) in pile.positions.withIndex().reversed()) {
                    CoverArt(
                        model = models[index],
                        modifier = Modifier
                            .offset(x = at.x, y = at.y)
                            .size(pile.cover)
                            .clip(RoundedCornerShape(6.dp))
                            .border(RowStackEdge, Studio, RoundedCornerShape(6.dp)),
                    )
                }
                // On the ROW's cover space, like the grid's, so a shelf's corner is where a book's
                // corner is. ONE corner, and it carries no number: the count is in the text beside
                // the cover anyway ("8 books"), and the same number twice on one row two centimetres
                // apart is not twice as clear.
                ShelfBadge(
                    isCollection = series.isCollection,
                    modifier = Modifier.align(Alignment.TopStart),
                    size = BadgeSize.SMALL,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    // No cover to sit beside once the shelf is open, so no gap to hold it off.
                    .padding(start = if (expanded) 0.dp else 10.dp),
            ) {
                Text(
                    series.name,
                    // The book rows' face and size, one line, ellipsised — see BookListRow.
                    color = Parchment,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = ListRowTitleLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = MetaChipSlot.TextInset),
                )
                // One slot, one height, both states — which is what stops the title and the line
                // under it stepping up and down as a shelf is opened and closed. Folded it carries
                // what a book carries; opened, the kind of shelf this is and what it holds.
                MetaChipSlot(
                    chips = if (expanded) shelfKindChip(series) else shelfChip(series, ctx),
                    ctx = ctx,
                    onFilter = { kind, value -> actions.onFilter(chipToken(kind, value)) },
                    trailing = if (expanded) {
                        seriesMeta(series, ctx, LocalContext.current, expanded = true)
                    } else {
                        null
                    },
                    modifier = Modifier.padding(top = MetaChipSlot.TitleGap),
                )
            }
            // Only while open: folded, the shelf is one card and how its insides are arranged is not
            // yet a question the reader has asked.
            if (expanded && series.hasThreads()) {
                CollectionOrderChip(flat = flat, onChange = onOrderChange)
            }
            // Chevron immediately left of the overflow button, so the two sit together at the trailing
            // edge and the overflow still lines up with the one on every book row. Leading it instead
            // pushed the covers out of line with the book rows above and below.
            Icon(
                // Filled triangles rather than the thin chevrons. At this size a stroked chevron
                // reads as a decoration next to the solid 3-dot button beside it; a filled arrowhead
                // reads as a control, which is what it is.
                imageVector = if (expanded) Icons.Filled.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight,
                contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                tint = Faint,
            )
            Box {
                // The same 40dp target a book row uses, for the same reason — see BookListRow.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                        tint = Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                SeriesMenu(series, menuOpen, actions) { menuOpen = false }
            }
        }
        // Open, a rule separates the shelf's title from its episodes — inset from the enclosure's
        // side rails so it reads as a line inside the card, not another edge of it.
        if (expanded) {
            HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = SeriesListEnclosurePad))
        }
    }
}
