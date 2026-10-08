package com.geozelot.homer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerIcons
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.LineShelf
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.RowGround
import com.geozelot.homer.ui.theme.Studio
import com.geozelot.homer.ui.theme.TabularSmall

// ── List rows ────────────────────────────────────────────────────────────────
//
// The list view's two rows — a book and a shelf — the flat counterparts of the grid cards. They
// borrow the enclosure geometry from SeriesEnclosure.kt so an opened shelf reads as one bordered
// block in both views.

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookListRow(
    book: BookListItem,
    ctx: RowContext,
    onOpen: (String) -> Unit,
    actions: BookActions,
    /**
     * Whether the row draws a border round its ground. True for a top-level row, so every item in
     * the list reads the way a collapsed series shelf does. False for an episode inside an opened
     * series: it keeps its ground, so a book reads as a card wherever it stands, but the enclosure's
     * outline round it already says where the shelf ends, and a second one inside it is noise.
     */
    bordered: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SeriesEnclosureRadius))
            .then(
                if (bordered) {
                    Modifier.border(1.dp, Line, RoundedCornerShape(SeriesEnclosureRadius))
                } else {
                    Modifier
                },
            )
            .background(RowGround)
            // Long-press opens the menu, the same as the grid card and the series shelf. The 3-dot
            // button stays — this is the shortcut, not a replacement for it — but list view was the
            // one place where holding a row did nothing, so the gesture learned in grid view
            // stopped working on switching.
            .combinedClickable(
                onClick = { onOpen(book.id) },
                onLongClick = { menuOpen = true },
            )
            .padding(SeriesListEnclosurePad)
            .alpha(if (book.hidden) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A bare cover. The grid's corner marks are beside the menu instead — see RowMarks.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CoverArt(
                model = book.coverModel,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
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
        RowMarks(index = volumeIndexFor(book, ctx), downloaded = book.isDownloaded)
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

/**
 * What the grid writes in a cover's corners, written beside a list row's menu instead.
 *
 * A 46dp cover could not carry them. The round badges that fitted were most of a corner each, so
 * two of them took a third of the artwork, and the row is the view where a cover is smallest and
 * a reader most needs to recognise it. Out here they are read in passing as the row's last words:
 * the volume number, then whether the book is on this device — the cover's own left-to-right.
 *
 * Quiet, like the corners were: one tone for all of them, so they read as one set of markings
 * rather than a row of differently coloured signals.
 */
@Composable
private fun RowMarks(
    /** The volume number — see `volumeIndexFor`; null for none. */
    index: Int? = null,
    downloaded: Boolean = false,
    /** A folded shelf's kind mark, which the book rows' number stands in for. */
    shelf: ImageVector? = null,
) {
    // Nothing at all rather than an empty run with its gap, which would take width from the title
    // for a row that has nothing to say here.
    if (index == null && !downloaded && shelf == null) return
    Row(
        modifier = Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shelf?.let { RowMarkIcon(it, null) }
        index?.let { Text("#$it", style = TabularSmall, color = Muted, fontWeight = FontWeight.SemiBold) }
        if (downloaded) RowMarkIcon(Icons.Filled.Download, stringResource(R.string.details_offline))
    }
}

@Composable
private fun RowMarkIcon(icon: ImageVector, description: String?) {
    Icon(icon, contentDescription = description, tint = Muted, modifier = Modifier.size(16.dp))
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
                    // Top slice of the enclosure that carries on down over the episodes, washing
                    // out of the row ground it wore folded. Clipped after it, so the ripple is
                    // bounded by the rounded top without cutting off the bleed the enclosure paints
                    // into the gap below.
                    Modifier
                        .seriesEnclosure(top = true, bottom = false, washFrom = RowGround)
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
                        .background(RowGround)
                },
            )
            .combinedClickable(
                // Said here now that a folded shelf draws no arrowhead: the whole row is the
                // control, and this is what a screen reader announces for tapping it.
                onClickLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                onClick = onToggle,
                onLongClick = { menuOpen = true },
            ),
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
            // One mark in the slot beside the menu, saying the one thing that changes.
            //
            // Folded: what KIND of shelf this is, where a book row keeps its number. No arrowhead
            // beside it — a folded shelf is recognisably a shelf by its stack and its mark, and an
            // arrow on every one of them was a column of controls saying "this opens" twenty times.
            //
            // Opened: the grid's own amber arrowhead in place of the mark, pointing down at the
            // books it opened onto — the same glyph in the same colour, so an open shelf looks open
            // in both views. The kind is said in words by the chip under the title by then.
            if (expanded) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.action_collapse),
                    tint = Amber,
                    // Held off whatever sits before it, as the folded mark is.
                    modifier = Modifier.padding(start = 8.dp),
                )
            } else {
                RowMarks(shelf = if (series.isCollection) HomerIcons.CollectionShelf else HomerIcons.SeriesShelf)
            }
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
