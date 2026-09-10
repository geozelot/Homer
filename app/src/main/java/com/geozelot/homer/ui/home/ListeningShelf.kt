package com.geozelot.homer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface0
import com.geozelot.homer.ui.theme.Surface2

// ── Currently-listening shelf ─────────────────────────────────────────────────────────

/**
 * The Currently-listening shelf, pinned above the library list.
 *
 * Two states, and the reader owns the transition.
 *
 * The panel used to resize ITSELF as the library scrolled under it, which is the thing that made it
 * unusable: a filtered shelf one row taller than the viewport would grow the panel, push that row
 * out of reach, and leave the books being reached for permanently half-visible. A shelf that
 * changes size in response to scrolling cannot be scrolled to the bottom of.
 *
 * So the size still changes, but never on its own initiative except to get OUT of the way:
 *
 * **Every rule about WHEN is in [ListeningFold], which is a plain object with tests.** This
 * composable only draws the two states and reports taps. Folding is automatic and unfolding is
 * always asked for; there is no manual fold, so the header is a label rather than a control.
 *
 * **Folded, the whole panel is the button.** A tap anywhere on it opens it, and nothing inside is
 * tappable in that state — so the covers are part of the target instead of dead space beside the
 * header, and a 46dp thumbnail can never open a book the reader could not identify from it.
 *
 * Expanded, the panel itself is NOT clickable: the covers open books then, and there is no fold for
 * a panel-wide tap to perform.
 *
 * All of which is subordinate to the search-dismiss gesture the caller hands in. While the box is
 * open EVERY tap on this panel closes it and does nothing else, folded or not, because that is the
 * rule for the whole screen and a panel that quietly unfolded instead would be the one place a tap
 * meant something different. Handed in as a [modifier] and applied outside the clickables, so it
 * consumes on the Initial pass before either of them is offered the gesture.
 *
 * [rowState] is hoisted by the caller so the horizontal position survives both the fold and
 * scrolling the library.
 */
@Composable
internal fun ListeningShelf(
    books: List<BookListItem>,
    expanded: Boolean,
    onExpand: () -> Unit,
    rowState: LazyListState,
    onOpen: (String) -> Unit,
    actions: BookActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Sized against a real grid cover rather than a literal dp, so it stays proportional on
        // every screen width instead of drifting when the grid's padding or column count changes.
        val coverWidth = gridCellWidth(maxWidth) / ListeningCoverFraction

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A flat, quiet surface, and the exact tone the band's wash starts from, so the
                // whole pinned block reads as one raised region rather than two tinted strips
                // that happen to sit together.
                .background(Surface0)
                // FOLDED, the whole panel is one button that opens it — nothing inside it is
                // tappable, so the covers are part of the target rather than dead space beside the
                // header. EXPANDED it must not be clickable at all: the covers open books then, and
                // there is no manual fold for a tap to perform.
                .then(
                    if (expanded) Modifier else Modifier.clickable(onClick = onExpand),
                ),
        ) {
            // The header is a LABEL, and carries no glyph at all. There is no manual fold, so a
            // chevron pointing up would advertise an action that does not exist — and one pointing
            // down while folded was true but pointed at the wrong thing, since the target is the
            // whole panel rather than the corner the chevron sat in.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = LibraryGridPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionLabelRow(
                        stringResource(R.string.home_section_listening, books.size),
                        topPadding = 8.dp,
                        bottomPadding = 2.dp,
                        // Small, unlike the library header below it. That one titles the list being
                        // scrolled; this titles a strip that is deliberately not the subject.
                        large = false,
                    )
                }
            }
            LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
                // Padding on the row (not the parent) so cards bleed off the edge while scrolling
                // instead of stopping at a hard margin.
                contentPadding = PaddingValues(
                    horizontal = LibraryGridPadding,
                    vertical = if (expanded) 6.dp else 4.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(books, key = { "cont:${it.id}" }) { book ->
                    if (expanded) {
                        ListeningItem(book, coverWidth, onOpen, actions)
                    } else {
                        ListeningFolded(book)
                    }
                }
            }
        }
    }
}

/**
 * A book in the FOLDED strip: its cover at a list row's size, with its progress under it.
 *
 * Deliberately inert. A tap here does nothing — the panel is folded, and the only thing a tap can
 * mean in that state is "open it back up", which the header handles. A 46dp thumbnail that opened a
 * book would be a mystery button, and one sitting in a strip the reader has just folded away is a
 * mystery button they are likely to hit by accident.
 */
@Composable
private fun ListeningFolded(book: BookListItem) {
    Column(
        // Not clickable, but still named — otherwise the folded strip is a row of unlabelled images
        // to a screen reader, and the cover art is deliberately decorative.
        modifier = Modifier
            .width(ListeningFoldedCover)
            .semantics { contentDescription = book.title },
    ) {
        CoverArt(
            model = book.coverModel,
            modifier = Modifier
                .size(ListeningFoldedCover)
                .clip(RoundedCornerShape(6.dp))
                // Same hairline every other cover in the app carries.
                .border(1.dp, Line, RoundedCornerShape(6.dp)),
        )
        ProgressBar(
            fraction = book.progress ?: 0f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
        )
    }
}

/**
 * How far the library has to be pulled past its top to unfold the panel.
 *
 * Not a hair-trigger: `available.y` also carries the residue of a fling settling against the top, so
 * a couple of stray pixels must not read as a deliberate pull. Not a haul either — this is a reveal,
 * not a drag handle.
 */
internal val ListeningPullToExpand = 64.dp


/** The folded cover, at exactly the size [BookListRow] draws its own. */
private val ListeningFoldedCover = 46.dp

/**
 * How much smaller than a grid cover a listening item is.
 *
 * **This is the one number to turn if the panel wants to be taller or shorter.** The collapsed strip
 * used 2.5; this is a little above it, per the brief, and the panel's whole height follows from it
 * because the cover is square and everything else on the item is a fixed line of text.
 *
 * The panel lost about a third of its height when covers went square, which is a gift rather than a
 * problem — it was already the thing most often accused of taking up too much room.
 *
 * It is a trade: narrower keeps the panel out of the library's way, and wider gives the title room
 * before it ellipsises. At this width a title gets roughly a dozen characters.
 */
private const val ListeningCoverFraction = 1.8f

/**
 * One book on the listening shelf: cover, progress, title, time left.
 *
 * Single-line title and meta on purpose. At this width two lines would fit about six characters
 * each, so a wrapped title is less readable than an ellipsised one — and a fixed line count keeps
 * every item in the row exactly the same height, which is what stops the strip going ragged when one
 * title is long.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListeningItem(
    book: BookListItem,
    coverWidth: Dp,
    onOpen: (String) -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .width(coverWidth)
                .combinedClickable(
                    onClick = { onOpen(book.id) },
                    onLongClick = { menuOpen = true },
                ),
        ) {
            CoverArt(
                model = book.coverModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    // Same hairline the grid cards carry, so a cover reads as a cover everywhere.
                    .border(1.dp, Line, RoundedCornerShape(8.dp)),
            )
            ProgressBar(
                fraction = book.progress ?: 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp, bottom = 4.dp),
            )
            Text(
                book.title,
                color = Parchment,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The time left is what this shelf is FOR — it is the "where was I" panel — so it wins
            // the one remaining line. The author only stands in when there is no position yet.
            val timeLeftMs = book.timeLeftMs
            val meta = when {
                timeLeftMs == null -> book.author ?: stringResource(R.string.unknown_author)
                timeLeftMs <= 0 -> stringResource(R.string.status_finished)
                else -> stringResource(R.string.time_left, formatCompactDuration(timeLeftMs))
            }
            Text(
                text = meta,
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        BookMenu(book, menuOpen, actions) { menuOpen = false }
    }
}


@Composable
internal fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Amber),
        )
    }
}
