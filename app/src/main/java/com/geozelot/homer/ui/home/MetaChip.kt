package com.geozelot.homer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.geozelot.homer.R
import com.geozelot.homer.data.metadata.BookGenre
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.LineShelf
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface1
import com.geozelot.homer.ui.theme.Surface2

/**
 * The one fact under an item's title that the shelf it is standing on does not already say.
 *
 * ## Genre or author, never both, never the one overhead
 *
 * Shelved by author, the heading names the author, so the chip is the genre. Shelved by genre it is
 * the other way round. Unshelved, the genre leads and the author stays in the meta line beneath.
 * The rule is the same one `bookMeta` has always followed — say what the arrangement does not — and
 * making the chip follow it too is what stops a card repeating its own heading back at the reader.
 *
 * ## Why a chip rather than more words in the meta line
 *
 * These used to be part of the meta string — joined with the length and the tags by `·`, competing
 * for the same two ellipsised lines. A book that was Krimi *and* Thriller *and* Hörspiel spent its
 * whole meta line saying so, and on a narrow grid cell the third one was cut off mid-word anyway.
 * Here the primary is always legible, the rest are a number.
 *
 * ## Quiet on purpose
 *
 * Card furniture, not an accent: [Muted] on [Surface2] inside a [Line] hairline — the same tones the
 * meta text and the card's own border already use. It was amber, which is the palette's one
 * interaction colour, and what a book IS is a fact about it rather than something happening to it.
 *
 * ## It never wraps, and the space is always there
 *
 * [MetaChipSlot] reserves [SlotHeight] whether or not there is a chip in it, so every card in the
 * grid is the same height and their bottoms line up — which is the property the whole footer block
 * is built around. An item with nothing to say leaves the space empty rather than pulling the meta
 * line up into it.
 */
object MetaChipSlot {
    /**
     * The reserved height of the row, chip or no chip.
     *
     * Named because every item view reserves it, and a card whose slot is a different height from
     * its neighbour's is the one bug this whole arrangement exists to prevent.
     */
    val SlotHeight: Dp = 15.dp

    /**
     * How far the chip's TEXT sits from the chip's left edge: the hairline plus its padding.
     *
     * The title above is indented by this, so the two read as one left edge and the chip's outline
     * hangs into the margin rather than shunting the text it belongs to. Derived rather than typed
     * twice, because the alignment breaks silently the moment either number moves.
     */
    val TextInset: Dp = 7.dp

    /**
     * The gap between the title above and the chip.
     *
     * Bigger than the 2dp it started at, and the reason is what a chip IS: an outlined pill sitting
     * against a line of bare text has its own edge, and that edge lands a good deal closer to the
     * baseline above it than another line of text would. At a two-line title — which in the grid is
     * every title, since the block reserves both — the descenders came down onto the pill's border.
     *
     * Named because three views set it and they have to agree, or a book in the grid and the same
     * book in the list are spaced differently.
     */
    val TitleGap: Dp = 6.dp
}

/** Which fact the chip carries — see [MetaChipSlot] for why it is one or the other. */
internal enum class MetaChipKind {
    GENRE,
    AUTHOR,

    /**
     * What this shelf IS — "In a collection" or "In a series" — on an opened header.
     *
     * It carries a [BookState] key rather than a display string, because tapping it narrows the
     * library to everything of that kind. That is the one filter a header can offer which is not
     * about the shelf you are standing in: not "this collection", but every book that is in one.
     */
    SHELF,
}

/**
 * The genres of a shelf, in the order a chip should say them.
 *
 * The primary is the one **most of its books** shelve under, not the first book's: a TKKG shelf
 * whose opening volume happens to be tagged Jugend is still a Krimi shelf, and reading the front
 * cover's genre would let one book's tagging decide what eight of them are called. Ties go to
 * whichever appears first in shelf order, so the answer is stable between two runs.
 *
 * The rest are every other genre anywhere in the shelf, distinct, in the order they turn up — so a
 * shelf can say `Krimi +3` when no single volume carries four.
 */
internal fun List<BookListItem>.shelfGenres(): List<String> {
    if (isEmpty()) return emptyList()
    val primaries = mapNotNull { it.genres.firstOrNull() }
    // groupingBy preserves first-encounter order, so maxByOrNull settles a tie on shelf order
    // rather than on whatever the hash bucket happened to be.
    val leader = primaries.groupingBy { it }.eachCount().entries.maxByOrNull { it.value }?.key
    val rest = flatMap { it.genres }.distinct().filterNot { it == leader }
    return listOfNotNull(leader) + rest
}

/**
 * What a card's chip says — the single rule every item view and every meta line reads.
 *
 * One value, and never the one the heading overhead already gives:
 *
 * | Shelved by | Chip    | Because                                    |
 * |------------|---------|--------------------------------------------|
 * | author     | genre   | the heading is the author                  |
 * | genre      | author  | the heading is the genre                   |
 * | anything   | genre, or author when there is no genre    |
 *
 * Null when the item has neither, or when its only fact is the heading it is standing under.
 *
 * **`bookMeta` and `seriesMeta` subtract exactly what this returns**, rather than re-deriving the
 * same conditions on their own side. Two sides of one rule is how a card ends up printing its author
 * twice — once in the chip and once in the line beneath — or dropping it from both.
 */
internal fun metaChipFor(
    genres: List<String>,
    author: String?,
    shelving: LibraryShelving,
): Pair<MetaChipKind, List<String>>? {
    val genre = genres.takeIf { it.isNotEmpty() }
    val named = author?.takeIf { it.isNotBlank() }
    return when (shelving) {
        LibraryShelving.AUTHOR -> genre?.let { MetaChipKind.GENRE to it }
        LibraryShelving.GENRE -> named?.let { MetaChipKind.AUTHOR to listOf(it) }
        else -> genre?.let { MetaChipKind.GENRE to it }
            ?: named?.let { MetaChipKind.AUTHOR to listOf(it) }
    }
}

/** Whether [metaChipFor] took the author, so a meta line knows not to print it again. */
internal fun Pair<MetaChipKind, List<String>>?.carriesAuthor(): Boolean =
    this?.first == MetaChipKind.AUTHOR

/**
 * The slot under an item's title: the chip, or the space it would have taken.
 *
 * @param onFilter commits the value as a library filter. What makes the chip worth tapping: it
 *   stops being a label and becomes the way to see everything else like it.
 */
@Composable
internal fun MetaChipSlot(
    chip: Pair<MetaChipKind, List<String>>?,
    ctx: RowContext,
    onFilter: (MetaChipKind, String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Facts set beside the chip on the same line, already joined.
     *
     * Beside it rather than under it, because a line of text below would make the block a different
     * height from a book's — and an opened shelf changing height is exactly the jump this slot
     * exists to prevent. The chip is the tallest thing in the row either way, so adding text costs
     * nothing.
     */
    trailing: String? = null,
) {
    Row(
        modifier = modifier.heightIn(min = MetaChipSlot.SlotHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip != null) MetaChip(chip.first, chip.second, ctx, onFilter)
        if (trailing.isNullOrBlank()) return@Row
        Text(
            // The separator belongs to the join, not to the caller: every one of these lines is
            // mid-dots and building it at four call sites is how one of them ends up with a comma.
            if (chip == null) trailing else " · $trailing",
            color = Muted,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (chip == null) MetaChipSlot.TextInset else 4.dp),
        )
    }
}

@Composable
private fun MetaChip(
    kind: MetaChipKind,
    values: List<String>,
    ctx: RowContext,
    onFilter: (MetaChipKind, String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Resolved here rather than by the caller: a chip's VALUE is what a filter needs — a genre key,
    // a state key — and its label is what a reader needs, and the two are only the same string for
    // an author.
    val label: @Composable (String) -> String = { value ->
        when (kind) {
            MetaChipKind.GENRE -> BookGenre.display(value, ctx.locale)
            MetaChipKind.SHELF ->
                BookState.from(value)?.let { stringResource(it.label) } ?: value
            MetaChipKind.AUTHOR -> value
        }
    }
    Box {
        Row(
            modifier = Modifier
                .height(MetaChipSlot.SlotHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(Surface2)
                .border(1.dp, Line, RoundedCornerShape(999.dp))
                // Its own click, so it does not open the book underneath it. The card still opens
                // everywhere else, and long-pressing the card still reaches the menu. One value is
                // committed straight to the filter; several open the list first.
                .clickable {
                    if (values.size > 1) open = true else onFilter(kind, values.first())
                }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label(values.first()),
                color = Muted,
                fontSize = 9.5.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // weight(1f, fill = false) so a long value gives way before the count does: on a
                // ~100dp cell "Kurzgeschichten +2" does not fit, and the half worth keeping is the
                // number — a truncated word still reads, a missing "+2" is a lie.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (values.size > 1) {
                Text(
                    "+${values.size - 1}",
                    color = Faint,
                    fontSize = 9.5.sp,
                    lineHeight = 9.5.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
        // The expansion, as the chip growing sideways rather than as a menu dropping out of it.
        //
        // A `Popup` because it has to be drawn over the grid, not in it: anything laid out inline
        // would push every card below it down to show one card's genres, and the whole arrangement
        // exists to keep those cards where they are. The provider pins it to the chip's own line and
        // to the window's left edge, so what a reader sees is this chip stretching across the
        // screen — which also settles where it should sit when the tapped card is in the right-hand
        // column: nowhere in particular, because it spans the whole width either way.
        if (open) {
            Popup(
                popupPositionProvider = MetaChipStripPosition,
                onDismissRequest = { open = false },
            ) {
                MetaChipStrip(
                    values = values,
                    label = label,
                    onPick = {
                        open = false
                        onFilter(kind, it)
                    },
                    onDismiss = { open = false },
                )
            }
        }
    }
}

/**
 * Pins the strip to the chip's line, at the window's left edge.
 *
 * The y comes from the anchor and the x does not — that asymmetry IS the effect: the strip appears
 * exactly where the chip is, and reaches across everything either side of it.
 */
private object MetaChipStripPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = 0,
        // Centred on the chip rather than hung below it, so the strip replaces the chip in place
        // instead of appearing to be a second thing underneath it.
        y = (anchorBounds.top - (popupContentSize.height - anchorBounds.height) / 2)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

/**
 * Every value on one line, scrolling sideways.
 *
 * One line always. A shelf can carry six genres and wrapping them would make the strip a block that
 * covers the row it came from — so the overflow scrolls, which is the one gesture that costs the
 * layout nothing.
 */
@Composable
private fun MetaChipStrip(
    values: List<String>,
    label: @Composable (String) -> String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val width = LocalConfiguration.current.screenWidthDp.dp
    AnimatedVisibility(
        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .width(width)
                .background(Surface2)
                .border(1.dp, Line)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (value in values) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Surface1)
                        .border(1.dp, if (value == values.first()) LineShelf else Line, RoundedCornerShape(999.dp))
                        .clickable { onPick(value) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        label(value),
                        color = if (value == values.first()) Parchment else Muted,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            // The way out that is not "tap somewhere else" — the strip covers the row it came from,
            // so the card underneath is not a safe place to aim at.
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = Faint,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(16.dp)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}
