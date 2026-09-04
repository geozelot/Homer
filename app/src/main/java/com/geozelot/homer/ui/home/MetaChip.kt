package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.data.metadata.BookGenre
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
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
}

/** Which fact the chip carries — see [MetaChipSlot] for why it is one or the other. */
internal enum class MetaChipKind { GENRE, AUTHOR }

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
 * What a card's chip should say, given what the shelf overhead already says.
 *
 * Null where there is nothing left worth saying: no genre and no author, or the one fact this item
 * has is the heading it is sitting under.
 */
internal fun metaChipFor(
    genres: List<String>,
    author: String?,
    shelving: LibraryShelving,
): Pair<MetaChipKind, List<String>>? = when (shelving) {
    // The heading is the author, so the chip is the genre.
    LibraryShelving.AUTHOR -> genres.takeIf { it.isNotEmpty() }?.let { MetaChipKind.GENRE to it }
    // …and the other way round. This used to be blank, on the reasoning that the heading said it
    // all — but the row was reserved anyway, and an author is exactly the fact a genre shelf
    // strips out.
    LibraryShelving.GENRE -> author?.let { MetaChipKind.AUTHOR to listOf(it) }
    // Nothing overhead: the genre leads and the author stays in the meta line beneath it.
    else -> genres.takeIf { it.isNotEmpty() }?.let { MetaChipKind.GENRE to it }
}

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
) {
    Box(
        modifier = modifier.heightIn(min = MetaChipSlot.SlotHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (chip == null) return@Box
        MetaChip(chip.first, chip.second, ctx, onFilter)
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
    val label = { value: String ->
        if (kind == MetaChipKind.GENRE) BookGenre.display(value, ctx.locale) else value
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
        // A popup rather than growing in place. Inline expansion would wrap — the one thing this
        // arrangement rules out — and in a grid whose cards are a fixed height it would push every
        // neighbour down to show one card's second genre.
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (value in values) {
                DropdownMenuItem(
                    text = {
                        Text(
                            label(value),
                            color = if (value == values.first()) Parchment else Muted,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        open = false
                        onFilter(kind, value)
                    },
                )
            }
        }
    }
}
