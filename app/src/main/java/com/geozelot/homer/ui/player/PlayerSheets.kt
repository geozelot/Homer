package com.geozelot.homer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkKind
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.rememberTextWidth
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import java.util.concurrent.TimeUnit

// ── List dialogs (bookmarks, chapters) ───────────────────────────────────────

/**
 * Height cap for a dialog's scrolling list. Derived from the actual screen rather than hardcoded:
 * fixed caps (360dp / 280dp) exceeded the whole dialog in landscape and on short screens, which
 * crushed the surrounding content and pushed the dialog's own buttons off-screen.
 */
@Composable
private fun dialogContentMaxHeight(fraction: Float = 0.45f): Dp =
    (LocalConfiguration.current.screenHeightDp * fraction).dp

// ── Bookmarks ──────────────────────────────────────────────────────────────

@Composable
internal fun BookmarksDialog(
    bookmarks: List<BookmarkEntity>,
    /**
     * Whether cutting is offered at all. A multi-file book's files ARE its chapters, so there is
     * nothing to cut — and offering it would invite somebody to publish a chapter list that
     * contradicts the file list every other reader navigates by.
     */
    canCut: Boolean,
    onAdd: (kind: String) -> Unit,
    onJump: (BookmarkEntity) -> Unit,
    onDelete: (BookmarkEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_bookmarks_title)) },
        text = {
            // One scrolling list with the "Add" button as its first item, capped against the real
            // screen height: a fixed 280dp cap plus an unscrollable button around it overflowed and
            // got crushed on a short screen (and in landscape).
            LazyColumn(modifier = Modifier.heightIn(max = dialogContentMaxHeight())) {
                item(key = "add") {
                    Button(
                        onClick = { onAdd(BookmarkKind.NOTE) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.player_bookmark_add))
                    }
                }
                if (canCut) {
                    item(key = "cut") {
                        // Outlined, not filled: marking a place for yourself is the everyday act,
                        // and cutting a chapter is a change everyone reading this folder will get.
                        OutlinedButton(
                            onClick = { onAdd(BookmarkKind.CUT) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            Text(stringResource(R.string.player_chapter_cut_add))
                        }
                        Text(
                            stringResource(R.string.player_chapter_cut_desc),
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                if (bookmarks.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.player_bookmark_empty),
                            color = Muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                } else {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJump(bookmark) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val chapterFallback = stringResource(R.string.player_chapter_fallback)
                            Column(modifier = Modifier.weight(1f)) {
                                if (bookmark.kind == BookmarkKind.CUT) {
                                    // Said out loud, because the two look identical in a list and
                                    // only one of them other people can see.
                                    Text(
                                        stringResource(R.string.player_chapter_cut_tag),
                                        color = Amber,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Text(
                                    bookmark.chapterTitle.ifEmpty { chapterFallback },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(formatTime(bookmark.positionMs), color = Muted, fontSize = 12.sp)
                            }
                            HomerTextButton(onClick = { onDelete(bookmark) }) { Text(stringResource(R.string.action_remove)) }
                        }
                    }
                }
            }
        },
        // The way out sits in the confirm slot, in the accent — the same place and the same colour
        // in every dialog that exists to be read. See DetailsCard for the rule.
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Amber) }
        },
    )
}

// ── Chapters ─────────────────────────────────────────────────────────────────

@Composable
internal fun ChapterPickerDialog(
    chapters: List<PlayerChapter>,
    bookTotalMs: Long?,
    onJump: (PlayerChapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Opens CENTRED on the current chapter, not with it at the top edge.
    //
    // Where you are is a place in a list, and a place has two sides: the chapters just gone are as
    // much of the answer as the ones coming. Pinned to the top, the list said "you are at the
    // beginning of what is left", which is a different — and wrong — claim, and it hid the row
    // above the one thing most people open this to reach: the chapter they just finished.
    //
    // Two steps, because scrollToItem lands the item at the start: put it there, then measure it
    // and push it down by half the gap it leaves. Measuring after the fact is what makes this
    // right at any row height and any font scale.
    LaunchedEffect(Unit) {
        val current = chapters.indexOfFirst { it.isCurrent }
        if (current < 0) return@LaunchedEffect
        listState.scrollToItem(current)
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == current } ?: return@LaunchedEffect
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        // Negative scrolls back towards the start; clamped by the list itself at either end, so the
        // first and last chapters simply stay where they are rather than leaving a gap.
        listState.scrollBy(-(viewport - item.size) / 2f)
    }
    // As wide as the template needs, and no wider.
    //
    // Every row is one line of "Chapter 07 of 21 · 00:42:15 (at 02:10:05 · 24%)", a sentence of
    // numbers that means nothing truncated — so the card is measured from the line itself rather
    // than set to a fraction of the screen and hoped for. Every row is the same width by
    // construction (each field is padded to a fixed size), so one sample measures all of them.
    //
    // A LazyColumn cannot be asked for its intrinsic width — lazy layouts have none — which is why
    // this is measured from the string and not from the list.
    val sample = chapters.firstOrNull()?.let {
        chapterRowName(number = chapters.size, count = chapters.size, chapter = it, bookTotalMs = bookTotalMs)
    } ?: stringResource(R.string.player_chapters)
    val rowWidth = rememberTextWidth(listOf(sample), ChapterRowStyle)
    // Capped at the screen: a book of a thousand chapters pads its numbers wider, and a card is
    // still a card. Inside the cap the dialog is exactly the sentence plus the padding around it.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = (rowWidth + ChapterCardPadding).coerceAtMost(screenWidth - 24.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(cardWidth),
        title = {
            // Centred over its own card rather than left-aligned against rows of numbers.
            Text(
                stringResource(R.string.player_chapters),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = dialogContentMaxHeight())) {
                itemsIndexed(chapters) { index, chapter ->
                    val chapterFallback = stringResource(R.string.chapter_numbered, index + 1)
                    // A file name is not a chapter title. A multi-file book's chapters ARE its
                    // files, so its "titles" are whatever the folder happens to be called —
                    // "Der_Schwarm_007", the book's name repeated twenty times, a track number
                    // already said by the line above. Only a mark a book carries INSIDE it is a
                    // name somebody chose, and only that is worth a second line.
                    val title = chapter.title
                        .takeIf { chapter.mediaItemIndex == null && it.isNotBlank() && it != chapterFallback }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(chapter) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // The row's NAME is the template: which chapter, how long it runs,
                            // where it begins and what fraction of the book that is. This is the
                            // one screen where those numbers are what you are choosing on — "the
                            // short one", "the one an hour in".
                            //
                            // No number column beside it any more: it read "7" against a line
                            // beginning "Chapter 7 of 21", which is the same fact twice, 28dp
                            // apart, on a row that would rather spend the space on the sentence.
                            Text(
                                chapterRowName(
                                    number = index + 1,
                                    count = chapters.size,
                                    chapter = chapter,
                                    bookTotalMs = bookTotalMs,
                                ),
                                // Tabular figures, so the digits sit in columns down the list
                                // instead of drifting with whatever glyph widths a 1 and a 7
                                // happen to have. The zero-padding above only lines up if the
                                // figures themselves are the same width.
                                style = ChapterRowStyle,
                                color = if (chapter.isCurrent) Amber else Parchment,
                                fontWeight = if (chapter.isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            title?.let {
                                Text(
                                    it,
                                    color = Muted,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        // The way out sits in the confirm slot, in the accent — the same place and the same colour
        // in every dialog that exists to be read. See DetailsCard for the rule.
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Amber) }
        },
    )
}

/**
 * The one style the picker's rows are set in — and measured in, which is why it is a value and not
 * three arguments repeated at two call sites that could drift apart.
 */
private val ChapterRowStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/** The card's own padding around a row: the dialog's insets either side, plus a little air. */
private val ChapterCardPadding = 72.dp

/**
 * A picker row's name: "Chapter 07 of 21 · 00:42:15 (at 02:10:05 · 24%)".
 *
 * Every part after the count is dropped rather than guessed. A single-file book answers all of it
 * from its marks alone; a multi-file book knows none of it until each of its files has been
 * measured, and a running sum over a list with a hole in it is wrong for every chapter after the
 * hole. So an unmeasured book's picker reads "Chapter 7 of 21", which is true. See [PlayerChapter],
 * and [com.geozelot.homer.data.metadata.DurationEnricher] for what a download does about it.
 *
 * Assembled from resources rather than concatenated, so a locale can reorder it — "bei" is not
 * "at" in a position a format string could guess.
 */
@Composable
private fun chapterRowName(
    number: Int,
    count: Int,
    chapter: PlayerChapter,
    bookTotalMs: Long?,
): String {
    // Every field is drawn, always, at the same width — an unknown one as dashes rather than as an
    // absence. Twenty rows that each drop a different part are twenty different shapes, and the
    // list becomes unreadable exactly when it is least complete. Dashes say "not measured yet",
    // which is true, and keep the columns standing while it is.
    val which = stringResource(R.string.player_chapter_of, pad(number, count), count)
    val length = clock(chapter.lengthMs?.takeIf { it > 0 })
    val start = chapter.startInBookMs
    val percent = if (start != null && bookTotalMs != null && bookTotalMs > 0) {
        ((start.toFloat() / bookTotalMs) * 100).toInt().coerceIn(0, 100)
    } else {
        null
    }
    val where = stringResource(R.string.player_chapter_at_pct, clock(start), percentOrDashes(percent))
    return stringResource(R.string.player_chapter_line, which, length, where)
}

/**
 * `hh:mm:ss`, always — or `--:--:--` where the number is not known yet.
 *
 * Full width even under an hour, unlike [formatTime], which drops the hours because a scrubber has
 * one reading and no column to keep. Here there are twenty of them stacked, and a list where some
 * rows say 42:15 and others 2:10:05 has no columns at all.
 */
private fun clock(ms: Long?): String {
    if (ms == null || ms < 0) return "--:--:--"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/** A percentage, or the dashes that hold its place. Padded, so the bracket closes in one column. */
private fun percentOrDashes(percent: Int?): String =
    if (percent == null) "--" else "%2d".format(percent)

/** [number] zero-padded to the width of [count], so chapter 7 of 210 reads 007 and lines up. */
private fun pad(number: Int, count: Int): String =
    number.toString().padStart(count.toString().length, '0')
