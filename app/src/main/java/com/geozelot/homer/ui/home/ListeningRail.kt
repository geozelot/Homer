package com.geozelot.homer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface0

// ── The listening rail ───────────────────────────────────────────────────────
//
// Currently-listening down the side of a short, wide window instead of across the top of it.
//
// It is the same panel answering the same question, turned ninety degrees — and turning it is the
// whole point. Stacked, it costs 141dp of a 360dp landscape screen and the library gets what is
// left, which is almost nothing. Beside the grid it costs 180dp of width, and the grid drops from
// six columns to five: it caps at six however wide the screen is, so on a phone held sideways that
// width was never going to be spent on books anyway.
//
// **It does not fold, and there is no mechanism here for folding it.** [ListeningFold] exists
// because a strip above the library is in the library's way; a rail beside it is not in anything's
// way, so there is nothing to get out of. Scrolling the grid leaves the rail alone.

/**
 * How wide the rail is.
 *
 * Wide enough for a cover and a readable title beside it, and no wider. At 180dp the title gets
 * about 104dp — a dozen and a half characters, where the stacked panel's own documentation admits
 * to "roughly a dozen". Every dp past that is a column the grid does not get.
 */
internal val ListeningRailWidth = 180.dp

/** The cover, at the size the list row and the folded strip both draw theirs. */
private val ListeningRailCover = 44.dp

/**
 * Currently-listening as a column beside the library.
 *
 * Scrolls on its own: a reader with a dozen books on the go should be able to reach the twelfth
 * without the library moving, and the library should not move because they did.
 */
@Composable
internal fun ListeningRail(
    books: List<BookListItem>,
    /** Hoisted, so scrolling the rail and then opening a book does not send it back to the top. */
    railState: LazyListState,
    onOpen: (String) -> Unit,
    actions: BookActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(ListeningRailWidth)
            .fillMaxHeight()
            // The same flat tone the stacked panel carries, so the rail reads as the same surface
            // it always was rather than as a new region that happens to hold the same books.
            .background(Surface0),
    ) {
        Box(modifier = Modifier.padding(horizontal = RailPadding)) {
            SectionLabelRow(
                pluralStringResource(R.plurals.home_section_listening, books.size, books.size),
                topPadding = 8.dp,
                bottomPadding = 4.dp,
                // Small, like the stacked panel's own header: it titles a strip that is
                // deliberately not the subject of the screen.
                large = false,
            )
        }
        LazyColumn(state = railState, contentPadding = PaddingValues(bottom = 12.dp)) {
            items(books, key = { "rail:${it.id}" }) { book ->
                ListeningRailItem(book, onOpen, actions)
            }
        }
    }
}

private val RailPadding = 12.dp

/**
 * One book on the rail: cover, then title and how much of it is left.
 *
 * A row rather than the stacked panel's cover-above-text card. A card wide enough to be worth
 * looking at would be 180dp of cover per book and two of them on screen; laid on its side, the same
 * width holds six or seven — and a list of where-was-I is a list, not a gallery.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListeningRailItem(book: BookListItem, onOpen: (String) -> Unit, actions: BookActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onOpen(book.id) },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = RailPadding, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                model = book.coverModel,
                modifier = Modifier
                    .size(ListeningRailCover)
                    .clip(RoundedCornerShape(6.dp))
                    // The same hairline every other cover in the app carries.
                    .border(1.dp, Line, RoundedCornerShape(6.dp)),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    book.title,
                    color = Parchment,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressBar(
                    fraction = book.progress ?: 0f,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 3.dp),
                )
                // The time left is what this panel is FOR — the author only stands in when there is
                // no position yet. Same rule as the stacked item, deliberately: it is the same
                // panel, and two answers to "what does the second line say" would be one too many.
                val timeLeftMs = book.timeLeftMs
                Text(
                    text = when {
                        timeLeftMs == null -> book.shownAuthor ?: stringResource(R.string.unknown_author)
                        timeLeftMs <= 0 -> stringResource(R.string.status_finished)
                        else -> stringResource(R.string.time_left, formatCompactDuration(timeLeftMs))
                    },
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BookMenu(book, menuOpen, actions) { menuOpen = false }
    }
}
