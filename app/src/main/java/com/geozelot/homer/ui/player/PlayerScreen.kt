package com.geozelot.homer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.ui.components.PlayerHelpCard
import com.geozelot.homer.ui.home.BookDetailsCard
import com.geozelot.homer.ui.home.BookListItem
import com.geozelot.homer.ui.home.FilterToken
import com.geozelot.homer.ui.components.CustomNumberDialog
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Surface2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// ── The player screen ────────────────────────────────────────────────────────
//
// This file is the screen itself: the state it collects, the two layout branches (stacked and
// side-by-side), the scale one number draws the cluster at, and the dialogs it owns. Everything
// it draws with lives beside it in this package and is `internal` for that reason:
//
//   PlayerArtwork.kt   the cover, the collapse gesture and the sleep countdown pill
//   PlayerTopBar.kt    back, "Now playing", help and the overflow menu
//   Scrubber.kt        the slider, the time labels and the position line — the parts that tick
//   Transport.kt       play/pause, the drawn seek buttons, chapter prev/next
//   BookHeader.kt      author, title, lineage chips and the chapter pill
//   ToolRow.kt         sleep, speed, volume, cut-silence and mark
//   PlayerSheets.kt    the bookmark and chapter-picker dialogs
//   PlayerDialogs.kt   the custom speed dialog
//   PlayerFormat.kt    the two time/speed formatters the files above share
//
// They were one 1,800-line file until the split. Every declaration moved verbatim; what the split
// ADDED is the state discipline below — which is what the boundaries are for.

@Composable
fun PlayerScreen(
    bookId: String,
    /**
     * The playing book as the library sees it, for the Details card — null until the library list
     * has it, which is why the menu item is conditional.
     *
     * Passed in rather than rebuilt here. A `BookListItem` carries a resolved cover, a progress
     * fraction and a download state, all of which the library ViewModel already computes; a second
     * copy assembled in the player would be a second set of rules for the same facts.
     */
    details: BookListItem? = null,
    /** Applies a filter token and leaves for the library, which is the only place one means anything. */
    onFilter: (FilterToken) -> Unit = {},
    /** Seeds the template editor for this book's folder; null where patterns are somebody else's. */
    onReadFolderDifferently: (() -> Unit)? = null,
    /**
     * Where to start, in ms — or -1 to resume wherever the book was left, which is every arrival
     * but one. Set when the library opens a book AT a bookmark.
     */
    startAtMs: Long = -1L,
    /**
     * Leaves the player. Null in a docked pane, where the player is not covering anything and
     * there is nothing to go back to — the back arrow and the swipe-down both stand down.
     */
    onBack: (() -> Unit)?,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    // The playback state is held as a State OBJECT here, never read at this level. THREE of its
    // fields advance every second — positionMs, bookElapsedMs and, while a timer runs,
    // sleepRemainingMs — and one read of any of them in this scope would recompose the whole
    // screen per tick: header, transport, top bar, cover. Instead [state] below is a derived slice
    // with all three cleared, so from one tick to the next it compares equal and notifies nobody.
    //
    // The live values reach exactly four composables, as lambdas, and every one of them is small
    // and built to tick: the scrubber and the position line (Scrubber.kt), the countdown pill on
    // the cover, and the sleep glyph whose description carries the time for a screen reader.
    val stateHolder = viewModel.state.collectAsStateWithLifecycle()
    val state by remember {
        derivedStateOf {
            stateHolder.value.copy(positionMs = 0L, bookElapsedMs = 0L, sleepRemainingMs = null)
        }
    }
    // Whether listening has started, for the top bar's "Mark completed" — derived separately so
    // it flips once instead of ticking along with bookElapsedMs.
    val started by remember { derivedStateOf { stateHolder.value.bookElapsedMs > 0 } }
    // Likewise WHETHER a timer is running, which is what decides the glyph's tint and the menu's
    // "turn off" row. That flips twice a timer; the number behind it moves sixty times a minute.
    val sleepRunning by remember { derivedStateOf { stateHolder.value.sleepRemainingMs != null } }
    // Ticks every second too — held as a State object and read only inside PositionLine.
    val timeLeftMs = viewModel.timeLeftMs.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
    val seekSeconds by viewModel.seekSeconds.collectAsStateWithLifecycle()
    val volumeMode by viewModel.volumeMode.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val download by viewModel.downloadState.collectAsStateWithLifecycle()
    val editableBook by viewModel.editableBook.collectAsStateWithLifecycle()
    val cover by viewModel.cover.collectAsStateWithLifecycle()
    val bookDurationMs by viewModel.bookDurationMs.collectAsStateWithLifecycle()

    // The book's length, for the chapter line's percentage — with one fallback, and only one.
    //
    // A single-file book IS its file, so the player knows the total the moment it loads, long
    // before the measuring pass writes it to the book row. A multi-file book must NOT take the
    // same shortcut: there `durationMs` is the current file alone, and using it as the whole would
    // put chapter three at 90% of the book. Resolved once, here, so the pill and the picker cannot
    // quote different percentages for the same chapter.
    val bookTotal = bookDurationMs
        ?: state.durationMs.takeIf { it > 0 && chapters.firstOrNull()?.startMs != null }

    // rememberSaveable: a rotation used to close whichever dialog was open.
    var customSpeed by rememberSaveable { mutableStateOf(false) }
    var customSleep by rememberSaveable { mutableStateOf(false) }
    var showBookmarksDialog by rememberSaveable { mutableStateOf(false) }
    var showChaptersDialog by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    // Start playback when the screen opens for this book.
    LaunchedEffect(bookId) { viewModel.play(bookId) }

    // …and then, if the library sent us to a bookmark, go there.
    //
    // Waiting for the controller to actually be on THIS book is the whole of it. `play` returns
    // before the player is prepared — it hands the request to the connection and comes straight
    // back — so seeking in the next breath issued a seek against a controller still loading, or
    // still holding the PREVIOUS book, and it was simply dropped. The bookmark opened the book at
    // its saved position instead, which is the one thing tapping a bookmark must not do.
    //
    // The timeout is a floor rather than a deadline: if readiness never reports, seek anyway and
    // let the player do what it can with it, rather than silently abandoning the request.
    LaunchedEffect(bookId, startAtMs) {
        if (startAtMs < 0) return@LaunchedEffect
        withTimeoutOrNull(SEEK_READY_TIMEOUT_MS) {
            viewModel.state.first { it.bookId == bookId && it.durationMs > 0 }
        }
        viewModel.seekTo(startAtMs)
    }

    // The screen's three parts as slots, so the tall and short layouts below can arrange the very
    // same content without threading every piece of state through two more composables.
    val offline = download?.status == DownloadStatus.DONE
    val topBar: @Composable () -> Unit = {
        PlayerTopBar(
            started = started,
            offline = offline,
            downloading = DownloadStatus.isActive(download?.status),
            canShowDetails = details != null,
            onBack = onBack,
            onMarkCompleted = viewModel::markCompleted,
            onToggleOffline = { if (offline) viewModel.deleteDownload() else viewModel.download() },
            onDetails = { showDetails = true },
            onBookmarks = { showBookmarksDialog = true },
            onHelp = { showHelp = true },
        )
    }
    val artwork: @Composable (Modifier) -> Unit = { slotModifier ->
        PlayerArtwork(
            // Live cover (updates on refresh/extraction) → play-time snapshot → embedded art.
            model = cover ?: state.coverModel ?: state.artworkData?.bytes,
            sleepRemainingMs = { stateHolder.value.sleepRemainingMs },
            onCollapse = onBack,
            modifier = slotModifier,
        )
    }
    // Everything below the cover is fixed in dp, so on a small or low-density screen it measured
    // more than the viewport had and the transport ended up below the fold. One number sizes the
    // lot — see [playerScale]. Passed IN rather than held in state: only the layouts at the bottom
    // of this composable know the viewport, and state written during composition to be read later
    // in the same composition is how a recomposition loop starts.
    val controls: @Composable (Modifier, Float) -> Unit = { slotModifier, scale ->
        // Everything under the cover, in three regions:
        //
        //  - **Info** — who wrote it, what it is called, what it belongs to. Facts about the book.
        //  - **Player** — the chapter picker, where you are, the scrubber, the transport. Facts
        //    about this listening, and the controls for it.
        //  - **Playback** — sleep, the three at-play settings, mark. Settings, not position.
        //
        // The seams between them are the only generous gaps on the screen; inside a region things
        // sit close, because that is what says they belong to each other.
        Column(
            modifier = slotModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Info ─────────────────────────────────────────────────────────────────────
            val loadingLabel = stringResource(R.string.player_loading)
            BookHeader(
                scale = scale,
                book = editableBook,
                // Prefer the live (override-applied) title so an in-place edit updates
                // immediately; fall back to the playback snapshot before the book row has loaded.
                title = editableBook?.title?.ifBlank { null }
                    ?: state.bookTitle.ifEmpty { state.chapterTitle.ifEmpty { loadingLabel } },
                onFilter = onFilter,
            )
            val hasPicker = chapters.isNotEmpty()
            val chapterCount = if (hasPicker) chapters.size else state.chapterCount
            val chapterNumber =
                if (hasPicker) chapters.indexOfFirst { it.isCurrent }.let { if (it >= 0) it + 1 else 1 }
                else state.chapterIndex + 1

            // ── Player ───────────────────────────────────────────────────────────────────
            // The picker heads it: choosing a chapter, then the line saying where you are in one,
            // then the scrubber and the transport. The gap above is the seam between this and the
            // Info block — the pill belongs to the controls under it, not to the title over it,
            // and at 10dp it read as one more line of the header.
            if (hasPicker) {
                ChapterButton(onClick = { showChaptersDialog = true }, modifier = Modifier.padding(top = 20.dp))
            }

            PositionLine(
                chapterNumber = chapterNumber,
                chapterCount = chapterCount,
                timeLeftMs = { timeLeftMs.value },
                modifier = Modifier.padding(top = 8.dp),
            )

            if (state.hasError) {
                ErrorBanner(onRetry = viewModel::retry, modifier = Modifier.padding(top = 12.dp))
            }

            Scrubber(
                positionMs = { stateHolder.value.positionMs },
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            Transport(
                isPlaying = state.isPlaying,
                seekSeconds = seekSeconds,
                scale = scale,
                onPrev = viewModel::previousChapter,
                onSeekBack = { viewModel.seekBy(-seekSeconds) },
                onPlayPause = viewModel::playPause,
                onSeekForward = { viewModel.seekBy(seekSeconds) },
                onNext = viewModel::nextChapter,
                modifier = Modifier.padding(top = 8.dp),
            )

            // ── Playback ─────────────────────────────────────────────────────────────────
            ToolRow(
                speed = state.playbackSpeed,
                sleepRemainingMs = { stateHolder.value.sleepRemainingMs },
                sleepEndOfChapter = state.sleepEndOfChapter,
                sleepActive = sleepRunning || state.sleepEndOfChapter,
                volumeMode = volumeMode,
                skipSilence = skipSilence,
                onSpeed = viewModel::setSpeed,
                onSleepMinutes = viewModel::startSleepTimer,
                onSleepEndOfChapter = viewModel::startSleepTimerEndOfChapter,
                onSleepOff = viewModel::cancelSleepTimer,
                onCustomSpeed = { customSpeed = true },
                onCustomSleep = { customSleep = true },
                onVolumeMode = viewModel::setVolumeMode,
                onToggleSkipSilence = { viewModel.setSkipSilence(!skipSilence) },
                onMark = { showBookmarksDialog = true },
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        val viewportHeight = maxHeight
        val viewportWidth = maxWidth
        val scale = playerScale(viewportHeight, viewportWidth)
        if (viewportHeight < SIDE_BY_SIDE_BELOW) {
            // Short viewport (landscape, split screen): stacking cannot work here — the control
            // cluster is fixed-height, so it takes what it needs and a weighted cover above it
            // computes to ~0dp and disappears. Side by side instead: a Row's weights divide the
            // *width*, so both halves keep the full height and size independently.
            Column(modifier = Modifier.fillMaxSize()) {
                topBar()
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    artwork(Modifier.weight(0.42f).fillMaxHeight().padding(vertical = 8.dp))
                    controls(
                        Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp),
                        // Half the width is the cluster's here, so the transport is measured
                        // against that rather than against the whole screen.
                        playerScale(viewportHeight, viewportWidth * 0.58f),
                    )
                }
            }
        } else {
            // Tall viewport: the cover takes what is LEFT, and therefore the player always fits.
            //
            // It was a fraction of the viewport — 46% of it, scaled — which is a guess at how much
            // room the cluster below would need, and a guess is wrong on exactly the screens that
            // cannot afford it. On a mid-sized phone the sum came to more than the viewport and the
            // transport, the thing somebody opened the player to reach, sat below the fold.
            //
            // A Column measures its unweighted children at their natural height FIRST and hands
            // what remains to the weighted one. So the top bar and the control cluster take exactly
            // what they need, the cover gets the rest, and the total is the viewport by
            // construction. Nothing scrolls because there is nothing left to scroll past.
            //
            // The cluster is CAPPED, and scrolls inside that cap.
            //
            // Leftover-sizing alone had a failure of its own at the far end: a Column gives the
            // weighted child what is left, and when the cluster is taller than the viewport what is
            // left is nothing — the cover collapses to zero AND the bottom of the cluster is cut
            // off, with no scroll to reach it. Which is worse than the scrolling it replaced.
            //
            // Capping the cluster fixes both directions at once. Below the cap — every ordinary
            // screen, where the cluster measures around half the viewport — nothing changes and the
            // cover still takes the whole remainder. Above it, at the font scales that caused the
            // problem, the cluster stops growing, scrolls within its own area, and the cover keeps
            // the rest. So the transport is always reachable and the artwork never disappears.
            Column(modifier = Modifier.fillMaxSize()) {
                topBar()
                artwork(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp.scaled(scale)),
                )
                controls(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = viewportHeight * CLUSTER_MAX_FRACTION)
                        .verticalScroll(rememberScrollState()),
                    scale,
                )
            }
        }
    }

    if (showChaptersDialog) {
        ChapterPickerDialog(
            chapters = chapters,
            bookTotalMs = bookTotal,
            onJump = {
                viewModel.jumpToChapter(it)
                showChaptersDialog = false
            },
            onDismiss = { showChaptersDialog = false },
        )
    }

    if (showBookmarksDialog) {
        BookmarksDialog(
            bookmarks = bookmarks,
            // Only a single-file book has anything to cut: a multi-file book's files are already
            // its chapters.
            canCut = state.chapterCount <= 1,
            onAdd = viewModel::addBookmark,
            onJump = {
                viewModel.jumpToBookmark(it)
                showBookmarksDialog = false
            },
            onDelete = viewModel::deleteBookmark,
            onDismiss = { showBookmarksDialog = false },
        )
    }

    // Details, with Edit one level inside it — the same order the library uses.
    if (showDetails) {
        details?.let { book ->
            BookDetailsCard(
                book = book,
                onEdit = { showDetails = false; showEditDialog = true },
                onFilter = { showDetails = false; onFilter(it) },
                onDismiss = { showDetails = false },
            )
        }
    }

    if (showEditDialog) {
        editableBook?.let { editable ->
            EditBookDialog(
                book = editable,
                onSave = { title, author, series, index, collection, collectionIndex, genres, language, tags, hidden, downloadOnPlay ->
                    viewModel.saveOverride(
                        title, author, series, index, collection, collectionIndex,
                        genres, language, tags, hidden, downloadOnPlay,
                    )
                    showEditDialog = false
                },
                onReset = {
                    viewModel.clearOverride()
                    showEditDialog = false
                },
                onPickCover = viewModel::setCustomCover,
                onClearCover = viewModel::clearCustomCover,
                onReadFolderDifferently = onReadFolderDifferently?.let {
                    { showEditDialog = false; it() }
                },
                onDismiss = { showEditDialog = false },
            )
        }
    }

    if (showHelp) PlayerHelpCard(onDismiss = { showHelp = false })

    if (customSpeed) {
        CustomSpeedDialog(
            initial = state.playbackSpeed,
            onConfirm = viewModel::setSpeed,
            onDismiss = { customSpeed = false },
        )
    }
    if (customSleep) {
        CustomNumberDialog(
            title = stringResource(R.string.player_sleep_custom_title),
            unit = stringResource(R.string.settings_unit_minutes),
            initial = 45,
            range = 1..600,
            onConfirm = { minutes -> viewModel.startSleepTimer(minutes * 60_000L) },
            onDismiss = { customSleep = false },
        )
    }
}

// ── Scale ────────────────────────────────────────────────────────────────────

/** Below this viewport height the cover and the control cluster sit side by side, not stacked. */
private val SIDE_BY_SIDE_BELOW = 520.dp

/**
 * How wide the transport row measures at full size: five controls and the gaps between them.
 *
 * Written down because it is the widest fixed thing on the screen, and therefore the thing that
 * decides whether the player fits a phone at all. At 348dp it does not fit a 360dp device once the
 * screen's own 22dp margins are taken off — which is why [playerScale] divides by it.
 */
private const val TRANSPORT_NATURAL_DP = 348f

/**
 * The most of the viewport the control cluster may take before it starts scrolling instead.
 *
 * Not a layout preference — a backstop. An ordinary cluster measures around half the viewport, so
 * this never binds; it exists for the accessibility font scales where it would otherwise grow past
 * the screen and take the cover and the transport with it. What is left over is the cover's, which
 * at this cap is always something rather than nothing.
 */
private const val CLUSTER_MAX_FRACTION = 0.74f

/**
 * One number the whole cluster is drawn at, between 0.7 and 1.
 *
 * ## Why a scale and not a breakpoint
 *
 * Everything below the cover is fixed in `dp` — an 84dp play button, a 38dp glyph, a reserved
 * header block — and a `dp` is a physical size. So on a small or low-density screen the cluster
 * measured exactly what it measures on a large one, took more of the viewport than there was, and
 * the player scrolled: the transport, the thing somebody opened the screen to reach, sat below the
 * fold. A breakpoint would fix one device and leave the next one wrong.
 *
 * ## Both axes, because both can be the binding one
 *
 * Height decides how much room the cluster has to stand in. Width decides whether the transport row
 * fits at all — five round controls in a row is the one piece of this screen that cannot wrap, and
 * on a 360dp phone it overflows before height ever becomes the problem.
 *
 * The floor is 0.7: below that the touch targets stop being touch targets, and a player nobody can
 * hit accurately is worse than one that scrolls.
 */
internal fun playerScale(viewportHeight: Dp, viewportWidth: Dp): Float {
    // Stepped down a notch after the leftover-cover change: with the cluster no longer competing
    // with a fixed cover fraction, what was left was simply that the transport is large in absolute
    // terms — an 84dp disc is an 84dp disc on a small screen as much as on a tablet, and on the
    // smaller ones it dominated a player it was only supposed to sit at the bottom of.
    val byHeight = when {
        viewportHeight >= 800.dp -> 1f
        viewportHeight >= 700.dp -> 0.88f
        viewportHeight >= 620.dp -> 0.80f
        else -> 0.72f
    }
    val byWidth = (viewportWidth.value / TRANSPORT_NATURAL_DP).coerceAtMost(1f)
    return minOf(byHeight, byWidth).coerceAtLeast(0.7f)
}

/** [this] scaled and rounded to whole dp, so nothing lands on a half-pixel. */
internal fun Dp.scaled(scale: Float): Dp = (value * scale).toInt().dp

/** Type scales too, but less far and never below [floor] — 11sp is the smallest readable here. */
internal fun TextUnit.scaled(scale: Float, floor: Float): TextUnit =
    (value * (1f - (1f - scale) * 0.6f)).coerceAtLeast(floor).sp

/** How long to wait for the player to be ready for a bookmarked book before seeking regardless. */
private const val SEEK_READY_TIMEOUT_MS = 10_000L

/** Shown when the stream stalls on an error (typically a lost connection); tap re-prepares. */
@Composable
private fun ErrorBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Surface2)
            .clickable(onClick = onRetry)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Danger, modifier = Modifier.size(17.dp))
        Text(
            stringResource(R.string.player_error_banner),
            color = Danger,
            fontSize = 12.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
