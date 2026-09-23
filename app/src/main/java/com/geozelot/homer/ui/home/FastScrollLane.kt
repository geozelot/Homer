package com.geozelot.homer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.library.authorSortKey
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.Surface2
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── The fast-scroll lane ─────────────────────────────────────────────────────
//
// A strip of initials down the edge of a long library. It appears while the library is moving and
// goes away when it stops, because a list you are not scrolling does not need a way to scroll it.
//
// **Its letters are the list's own sort key, never the title.** Under sort-by-surname the lane
// reads P for Terry Pratchett, because that is where the shelf put him — a lane that disagreed
// with the order would be worse than none, since every jump would land somewhere the reader did
// not ask for and could not predict. Under Recent or Duration there is no alphabet to offer and
// the lane does not appear at all.
//
// Only letters that EXIST get a slot. A full A–Z greyed out two thirds of the way through is
// mostly dead targets, and on a short landscape window there is no room for twenty-six of anything.

/** A letter and the grid item it jumps to. */
internal data class LaneLetter(val label: String, val index: Int)

/** Whether an alphabetical lane means anything under this sort. */
internal fun sortIsAlphabetical(sort: LibrarySort): Boolean =
    sort == LibrarySort.TITLE || sort == LibrarySort.AUTHOR || sort == LibrarySort.AUTHOR_LAST

/**
 * The lane for a drawn library: one entry per initial, in list order, pointing at the first grid
 * item under it.
 *
 * Reads [GridSlot]s rather than entries because the grid is addressed by ITEM index, and the two
 * differ by however many rows the open shelves have unfolded into — see LibraryGridSlots.kt.
 *
 * When the library is shelved, the targets are the HEADINGS: that is what a reader aiming at "P"
 * is aiming at, and jumping into the middle of a shelf would land past the heading that says where
 * you are. Unshelved, the targets are the books and shelves themselves.
 */
internal fun laneLetters(slots: List<GridSlot>, sort: LibrarySort, shelved: Boolean): List<LaneLetter> {
    if (!sortIsAlphabetical(sort)) return emptyList()
    val out = mutableListOf<LaneLetter>()
    slots.forEachIndexed { index, slot ->
        val key = when {
            shelved -> (slot as? GridSlot.Heading)?.entry?.title
            slot is GridSlot.Book -> bookLaneKey(slot.book, sort)
            slot is GridSlot.Shelf -> shelfLaneKey(slot.series, sort)
            else -> null
        } ?: return@forEachIndexed
        val letter = initialOf(key) ?: return@forEachIndexed
        if (out.lastOrNull()?.label != letter) out += LaneLetter(letter, index)
    }
    return out
}

/** The same value the list was ordered by, so the lane and the order cannot disagree. */
private fun bookLaneKey(book: BookListItem, sort: LibrarySort): String? = when (sort) {
    LibrarySort.TITLE -> book.title
    LibrarySort.AUTHOR -> book.author
    LibrarySort.AUTHOR_LAST -> book.author?.let(::authorSortKey)
    else -> null
}

private fun shelfLaneKey(series: LibraryEntry.Series, sort: LibrarySort): String? = when (sort) {
    LibrarySort.TITLE -> series.name
    LibrarySort.AUTHOR -> series.author
    LibrarySort.AUTHOR_LAST -> series.author?.let(::authorSortKey)
    else -> null
}

/**
 * The initial a key files under: a letter, or `#` for everything that is not one.
 *
 * Digits and punctuation share one bucket because they share one place in the order — a lane with
 * a slot for `1`, `4` and `(` would spend three of its very few rows on books nobody looks for by
 * their first character.
 */
internal fun initialOf(key: String): String? {
    val c = key.trim().firstOrNull() ?: return null
    return if (c.isLetter()) c.uppercase() else "#"
}

/** How long the lane lingers after the library stops moving. */
private const val LingerMs = 1_200L

/**
 * The lane, drawn over the right edge of the library.
 *
 * Shown while the grid is moving or while the lane itself is being used, then faded out. It draws
 * over the list rather than beside it: a strip that appears and disappears must not change the
 * width of what it sits next to, or every book on screen would shuffle sideways as it came and went.
 */
@Composable
internal fun FastScrollLane(
    letters: List<LaneLetter>,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var laneHeight by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    val description = stringResource(R.string.home_cd_fast_scroll)

    // Moving, or being used. The linger is what stops it blinking between the flings of a long
    // scroll, and what leaves it on screen long enough to be grabbed after one.
    val active = gridState.isScrollInProgress || dragging
    LaunchedEffect(active) {
        if (active) {
            shown = true
        } else {
            delay(LingerMs)
            shown = false
        }
    }

    fun jumpTo(y: Float) {
        if (laneHeight <= 0) return
        val slot = (y / laneHeight * letters.size).roundToInt().coerceIn(0, letters.lastIndex)
        scope.launch { gridState.scrollToItem(letters[slot].index) }
    }

    AnimatedVisibility(visible = shown, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(
            modifier = Modifier
                .width(LaneWidth)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Surface2.copy(alpha = 0.92f))
                .onSizeChanged { laneHeight = it.height }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; jumpTo(it.y) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ -> jumpTo(change.position.y) },
                    )
                }
                .semantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            letters.forEach { letter ->
                Text(
                    letter.label,
                    style = SectionLabel,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = if (dragging) Amber else Muted,
                )
            }
        }
    }
}

private val LaneWidth = 22.dp
