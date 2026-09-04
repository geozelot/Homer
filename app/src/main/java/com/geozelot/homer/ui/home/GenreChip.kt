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
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment

/**
 * What a book or a shelf is, as one chip: the genre it sits under, and how many more it carries.
 *
 * ## Why a chip rather than more words in the meta line
 *
 * The genres used to be part of the meta string — joined with the author, the length and the tags by
 * `·`, competing for the same two ellipsised lines. A book that was Krimi *and* Thriller *and*
 * Hörspiel spent its whole meta line saying so, and on a narrow grid cell the third one was cut off
 * mid-word anyway. Here the primary is always legible, the rest are a number, and the meta line goes
 * back to being about the author.
 *
 * ## It never wraps, and the space is always there
 *
 * [GenreChipSlot] reserves [SlotHeight] whether or not there is a chip in it, so every card in the
 * grid is the same height and their bottoms line up — which is the property the whole footer block
 * is built around. A book with no genres leaves the space empty rather than pulling the meta line up
 * into it.
 */
object GenreChipSlot {
    /**
     * The reserved height of the row, chip or no chip.
     *
     * The chip's own height: 10sp of text in a 3dp/3dp pill. Named because two call sites reserve it
     * and a card whose slot is a different height from its neighbour's is the one bug this whole
     * arrangement exists to prevent.
     */
    val SlotHeight: Dp = 18.dp
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
 * The slot under an item's title: the genre chip, or the space it would have taken.
 *
 * @param genres every genre this item carries, primary first — see [shelfGenres] for a shelf's.
 * @param onFilter applies a genre as a library filter. What makes the expanded list worth opening:
 *   the chip stops being a label and becomes the way to see everything else like it.
 */
@Composable
internal fun GenreChipSlot(
    genres: List<String>,
    ctx: RowContext,
    onFilter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.heightIn(min = GenreChipSlot.SlotHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Nothing to say, or nothing worth saying: shelved BY genre the heading overhead already
        // names it, and repeating it on every card underneath would spend the row on the one fact
        // the reader cannot be in any doubt about.
        if (genres.isEmpty() || ctx.shelving == LibraryShelving.GENRE) return@Box
        GenreChip(genres, ctx, onFilter)
    }
}

@Composable
private fun GenreChip(genres: List<String>, ctx: RowContext, onFilter: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(GenreChipSlot.SlotHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(AmberSoft)
                .border(1.dp, Amber, RoundedCornerShape(999.dp))
                // Its own click, so it does not open the book underneath it. The card still opens
                // everywhere else, and long-pressing the card still reaches the menu.
                .clickable(enabled = genres.size > 1) { open = true }
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                BookGenre.display(genres.first(), ctx.locale),
                color = Amber,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // weight(1f, fill = false) so a long genre gives way before the count does: on a
                // ~100dp cell "Kurzgeschichten +2" does not fit, and the half worth keeping is the
                // number — a truncated word still reads, a missing "+2" is a lie.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (genres.size > 1) {
                Text(
                    "+${genres.size - 1}",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        // A popup rather than growing in place. Inline expansion would wrap — the one thing this
        // arrangement rules out — and in a grid whose cards are a fixed height it would push every
        // neighbour down to show one card's second genre.
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (genre in genres) {
                DropdownMenuItem(
                    text = {
                        Text(
                            BookGenre.display(genre, ctx.locale),
                            color = if (genre == genres.first()) Amber else Parchment,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        open = false
                        onFilter(genre)
                    },
                )
            }
        }
    }
}
