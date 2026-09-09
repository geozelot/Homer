package com.geozelot.homer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkKind
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.playback.VolumeMode
import com.geozelot.homer.ui.components.EditableBook
import com.geozelot.homer.ui.components.HomerIcons
import com.geozelot.homer.ui.components.PlayerHelpCard
import com.geozelot.homer.ui.home.BookDetailsCard
import com.geozelot.homer.ui.home.BookListItem
import com.geozelot.homer.ui.home.FilterToken
import com.geozelot.homer.ui.home.FilterFacet
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.components.CustomNumberDialog
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.components.HomerSwitch
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.rememberTextWidth
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberDeep
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.LineShelf
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Studio
import com.geozelot.homer.ui.theme.TabularSmall
import com.geozelot.homer.ui.theme.Surface2
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeLeftMs by viewModel.timeLeftMs.collectAsStateWithLifecycle()
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
    val context = LocalContext.current

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
            started = state.bookElapsedMs > 0,
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
            sleepRemainingMs = state.sleepRemainingMs,
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

            // Where you are: which chapter, and how much of the book is left. Named for what it
            // says rather than "info", which is the block of facts ABOUT the book two regions up —
            // and calling both of them info is how a gap meant for one of them landed on the other.
            //
            // The chapter part is conditional (a single-file book with no embedded chapters has
            // none), the time-left part is NOT — it used to live inside the chapter branch, which
            // lost the remaining-time readout for exactly those books.
            val positionLine = buildString {
                if (chapterCount > 0) {
                    // The number as a string, unpadded: the picker pads its numbers so twenty
                    // rows line up in columns, and this is one line under a title where "Chapter
                    // 07" would just look like a typo.
                    append(context.getString(R.string.player_chapter_of, "$chapterNumber", chapterCount))
                }
                timeLeftMs?.let {
                    if (isNotEmpty()) append(" · ")
                    append(
                        if (it <= 0) {
                            context.getString(R.string.status_finished)
                        } else {
                            context.getString(R.string.time_left, formatCompactDuration(it))
                        },
                    )
                }
            }
            if (positionLine.isNotEmpty()) {
                Text(
                    text = positionLine,
                    color = Faint,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.hasError) {
                ErrorBanner(onRetry = viewModel::retry, modifier = Modifier.padding(top = 12.dp))
            }

            Scrubber(
                positionMs = state.positionMs,
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
                sleepLabel = sleepLabel(state.sleepRemainingMs, state.sleepEndOfChapter, context),
                sleepActive = state.sleepRemainingMs != null || state.sleepEndOfChapter,
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
            // No floor on the cover, deliberately: a minimum is what turns "the cover is small" —
            // recoverable, and only on a screen where something has to give — into "the transport
            // is off the bottom", which is not.
            Column(modifier = Modifier.fillMaxSize()) {
                topBar()
                artwork(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp.scaled(scale)),
                )
                controls(Modifier.fillMaxWidth(), scale)
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

// ── Artwork ──────────────────────────────────────────────────────────────────

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
    val byHeight = when {
        viewportHeight >= 760.dp -> 1f
        viewportHeight >= 680.dp -> 0.92f
        viewportHeight >= 600.dp -> 0.84f
        else -> 0.76f
    }
    val byWidth = (viewportWidth.value / TRANSPORT_NATURAL_DP).coerceAtMost(1f)
    return minOf(byHeight, byWidth).coerceAtLeast(0.7f)
}

/** [this] scaled and rounded to whole dp, so nothing lands on a half-pixel. */
private fun Dp.scaled(scale: Float): Dp = (value * scale).toInt().dp

/** Type scales too, but less far and never below [floor] — 11sp is the smallest readable here. */
private fun TextUnit.scaled(scale: Float, floor: Float): TextUnit =
    (value * (1f - (1f - scale) * 0.6f)).coerceAtLeast(floor).sp

/** The cover, centered in whatever slot it's given and sized to fit it in both dimensions. */
@Composable
private fun PlayerArtwork(
    model: Any?,
    /** The sleep timer's remaining time, or null when none is running. */
    sleepRemainingMs: Long?,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            // Swipe down anywhere on the artwork to collapse back to the mini-player.
            .pointerInput(Unit) {
                val threshold = 60.dp.toPx()
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragged > threshold) onCollapse()
                        dragged = 0f
                    },
                    onVerticalDrag = { _, delta -> dragged += delta },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Largest SQUARE cover that fits the slot both ways, floored so a pathological slot can't
        // reduce it to nothing (an unbounded slot height falls back to the width limit).
        //
        // It was 1:1.3, which cut 23% off a square cover — less brutal than the library's 2:3 but on
        // the one screen where the artwork IS the content, and where there is room to show all of it.
        // Whichever bound is smaller, and NO floor: this slot is whatever the cluster below left
        // over, so a cover insisting on a minimum would push the transport off the screen to keep
        // itself large. Small is a cost the artwork can bear; missing controls are not.
        val coverWidth = minOf(maxWidth * 0.82f, maxHeight)
        CoverImage(
            model = model,
            modifier = Modifier
                .width(coverWidth)
                .aspectRatio(1f)
                .shadow(
                    elevation = 30.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Amber,
                    spotColor = AmberDeep,
                )
                .clip(RoundedCornerShape(14.dp)),
        )
        // The countdown, on the artwork.
        //
        // A running sleep timer is the one piece of state that changes what is ABOUT to happen
        // rather than what is happening, and it had nowhere to be seen: the bottom row said "Sleep"
        // whether one was running or not once the labels came off the glyphs, and the amber tint
        // says a timer exists without saying how much of it is left.
        //
        // On the cover rather than beside it because the cover is the one region with space to
        // spare, and because a number floating over the artwork reads as temporary — which it is.
        // Seconds here, minutes in the notification: redrawing this costs nothing.
        sleepRemainingMs?.let { remaining ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Studio.copy(alpha = 0.82f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = stringResource(R.string.player_sleep),
                    tint = Amber,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    formatTime(remaining),
                    // TabularSmall is the app's own "numbers that must not jitter" style, which is
                    // exactly what a countdown is: without it the pill changes width every time a
                    // 1 ticks past.
                    style = TabularSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = Amber,
                )
            }
        }
    }
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun PlayerTopBar(
    started: Boolean,
    offline: Boolean,
    downloading: Boolean,
    canShowDetails: Boolean,
    onBack: () -> Unit,
    onMarkCompleted: () -> Unit,
    onToggleOffline: () -> Unit,
    onDetails: () -> Unit,
    onBookmarks: () -> Unit,
    onHelp: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_back), tint = Muted)
        }
        Text(stringResource(R.string.player_now_playing), style = SectionLabel, color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Beside the menu rather than inside it: it explains what is on the screen, which is
            // not the same kind of thing as the actions the menu holds.
            IconButton(onClick = onHelp) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = stringResource(R.string.home_cd_help),
                    tint = Muted,
                )
            }
            Box {
                IconButton(onClick = { overflowOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more), tint = Muted)
                }
                // The same order as the library's own book menu, and for the same reason a menu has an
                // order at all: what you look at, then what you change, then what this device does with
                // the file. Reading is first because it is what most taps are after; the destructive
                // one sits behind a rule of its own.
                DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    // DETAILS, not Edit. The library's own menus lead here too, and editing is one
                    // level inside it — which is the right order: you look at a book before deciding it
                    // is wrong. Reaching Edit directly from the player skipped the looking, and made the
                    // player the one place where a book's facts were unreachable while it was playing.
                    if (canShowDetails) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_details)) },
                            onClick = {
                                onDetails()
                                overflowOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_bookmarks)) },
                        onClick = {
                            onBookmarks()
                            overflowOpen = false
                        },
                    )
                    if (started) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mark_completed)) },
                            onClick = {
                                onMarkCompleted()
                                overflowOpen = false
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_offline)) },
                        trailingIcon = {
                            if (downloading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Amber, strokeWidth = 2.dp)
                            } else {
                                HomerSwitch(checked = offline, onCheckedChange = {
                                    onToggleOffline()
                                    overflowOpen = false
                                })
                            }
                        },
                        // Dismiss like the other items: leaving the menu open invited repeated taps
                        // that queued download → delete → download.
                        onClick = {
                            onToggleOffline()
                            overflowOpen = false
                        },
                    )
                }
            }
        }
    }
}

// ── Scrubber ─────────────────────────────────────────────────────────────────

@Composable
private fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val hasDuration = durationMs > 0
    val sliderValue = dragValue ?: if (hasDuration) positionMs.toFloat() else 0f
    val range = if (hasDuration) 0f..durationMs.toFloat() else 0f..1f

    Column(modifier = modifier) {
        Slider(
            value = sliderValue.coerceIn(range.start, range.endInclusive),
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(it.toLong()) }
                dragValue = null
            },
            valueRange = range,
            enabled = hasDuration,
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Surface2,
            ),
        )
        // Chapter-relative: elapsed on the left, time-to-end-of-chapter on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(sliderValue.toLong()), color = Muted, fontSize = 11.sp)
            val remaining = (durationMs - sliderValue.toLong()).coerceAtLeast(0)
            Text(
                text = if (hasDuration) stringResource(R.string.player_time_remaining, formatTime(remaining)) else stringResource(R.string.player_time_unknown),
                color = Muted,
                fontSize = 11.sp,
            )
        }
    }
}

// ── Transport ──────────────────────────────────────────────────────────────

@Composable
private fun Transport(
    isPlaying: Boolean,
    seekSeconds: Int,
    scale: Float,
    onPrev: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp.scaled(scale)),
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(52.dp.scaled(scale))) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_cd_previous), tint = Parchment, modifier = Modifier.size(38.dp.scaled(scale)))
        }
        SeekButton(seconds = seekSeconds, forward = false, scale = scale, onClick = onSeekBack)
        Box(
            modifier = Modifier
                .size(84.dp.scaled(scale))
                .shadow(12.dp, CircleShape, spotColor = AmberDeep)
                .clip(CircleShape)
                .background(Amber)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                tint = OnAmber,
                modifier = Modifier.size(40.dp.scaled(scale)),
            )
        }
        SeekButton(seconds = seekSeconds, forward = true, scale = scale, onClick = onSeekForward)
        IconButton(onClick = onNext, modifier = Modifier.size(52.dp.scaled(scale))) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_cd_next), tint = Parchment, modifier = Modifier.size(38.dp.scaled(scale)))
        }
    }
}

/**
 * Skip-back / skip-forward button: a circular arrow with the seconds count inside it.
 *
 * The arc is drawn rather than taken from `Icons.Filled.Replay`, whose arrowhead reaches well into
 * the middle of the glyph — exactly where the number goes, so the two overlapped. Drawing it leaves
 * a deliberate gap at the top for the head and keeps the whole interior clear for the digits.
 */
@Composable
private fun SeekButton(seconds: Int, forward: Boolean, scale: Float, onClick: () -> Unit) {
    val label = if (forward) {
        stringResource(R.string.player_cd_skip_forward, seconds)
    } else {
        stringResource(R.string.player_cd_skip_back, seconds)
    }
    Box(
        modifier = Modifier
            .size(56.dp.scaled(scale))
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(SeekGlyphSize.scaled(scale))) {
            val stroke = 2.dp.toPx()
            // Inset by half the stroke so the arc's outer edge lands on the glyph bounds rather
            // than half a stroke outside them.
            val inset = stroke / 2f
            val d = size.minDimension - stroke
            // The gap the arrowhead occupies, left open at the top. Mirrored for the forward
            // button so both heads sit at the top with the tails running the other way.
            val gap = 74f
            val start = if (forward) -90f + gap / 2f else -90f - gap / 2f
            drawArc(
                color = Parchment,
                startAngle = start,
                sweepAngle = if (forward) 360f - gap else -(360f - gap),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(d, d),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // A filled triangle at the arc's open end, pointing along the direction of travel.
            val r = d / 2f
            val cx = inset + r
            val cy = inset + r
            val headAngle = Math.toRadians(start.toDouble())
            val hx = cx + r * kotlin.math.cos(headAngle).toFloat()
            val hy = cy + r * kotlin.math.sin(headAngle).toFloat()
            val h = 5.dp.toPx()
            val dir = if (forward) 1f else -1f
            drawPath(
                Path().apply {
                    // Tip points across the gap; the base straddles the stroke, so the head reads
                    // as the end of the line rather than a separate mark floating beside it.
                    moveTo(hx + dir * h * 0.9f, hy)
                    lineTo(hx - dir * h * 0.2f, hy - h * 0.75f)
                    lineTo(hx - dir * h * 0.2f, hy + h * 0.75f)
                    close()
                },
                color = Parchment,
            )
        }
        Text("$seconds", color = Parchment, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Outer size of the drawn circular arrow; the digits sit in the clear middle of it. */
/** How long to wait for the player to be ready for a bookmarked book before seeking regardless. */
private const val SEEK_READY_TIMEOUT_MS = 10_000L

private val SeekGlyphSize = 38.dp

/** Wide pill opening the chapter picker; sits below the title. */
@Composable
private fun ChapterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The tap area is expanded to the 48dp minimum around the pill rather than by inflating the
    // pill itself, so the visual stays the compact chip the layout was designed around.
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Surface2)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Parchment, modifier = Modifier.size(15.dp))
            Text(
                stringResource(R.string.player_chapters),
                color = Parchment,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Who wrote it, what it is called, and what it belongs to — in that order, in a block of a fixed
 * height.
 *
 * ## The order
 *
 * Author, title, series, collection. The two names a reader already knows lead — the author, then
 * the book — and what places it in a shelf follows, narrowest relation first: the series it is a
 * volume of, then the collection that series sits in. Reading down is reading outward.
 *
 * ## Why the space is reserved
 *
 * Every slot is drawn whether or not the book fills it. A standalone with no series and no
 * collection occupies exactly as much as a numbered volume of a nested series does, so the cover
 * above and the scrubber below sit at the same height for every book — and moving between two
 * books does not shuffle the transport under a thumb that is already reaching for it.
 *
 * ## The chips
 *
 * Series and collection are chips because they are the two things here you can act on: each one
 * narrows the library to it and leaves. They differ in weight on purpose. The series is the closer
 * relation and wears the accent; the collection is the outer one and wears the shelf hairline the
 * library already uses to mean exactly that. Both say "<name>, Volume n" — a number without the
 * thing it counts is not a fact.
 */
@Composable
private fun BookHeader(
    book: EditableBook?,
    title: String,
    scale: Float,
    onFilter: (FilterToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Reserved, like everything below it: a book with no author must not pull the title up.
        Box(modifier = Modifier.height(BookHeaderAuthorLine.scaled(scale)), contentAlignment = Alignment.Center) {
            book?.author?.takeIf { it.isNotBlank() }?.let {
                // A chip like the two below it. It was bare text, which made the one fact up here
                // you can act on look like the one fact you cannot — and three things that all
                // filter the library should not be drawn three different ways.
                LineageChip(
                    label = it,
                    icon = HomerIcons.Author,
                    scale = scale,
                    onClick = { onFilter(FilterToken(FilterFacet.AUTHOR, it)) },
                )
            }
        }
        // Two lines' worth, always — a one-line title leaves the second empty rather than letting
        // the block breathe differently for every book.
        Box(
            modifier = Modifier.height(BookHeaderTitleBlock.scaled(scale)).padding(top = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = SerifTitle.copy(
                    fontSize = 22.sp.scaled(scale, floor = 17f),
                    lineHeight = 27.sp.scaled(scale, floor = 21f),
                ),
                color = Parchment,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.height(BookHeaderChipRow.scaled(scale)), contentAlignment = Alignment.Center) {
            book?.series?.takeIf { it.isNotBlank() }?.let {
                LineageChip(
                    label = withVolume(it, book.seriesIndex),
                    icon = HomerIcons.SeriesBracket,
                    scale = scale,
                    onClick = { onFilter(FilterToken(FilterFacet.SERIES, it)) },
                )
            }
        }
        Box(modifier = Modifier.height(BookHeaderChipRow.scaled(scale)), contentAlignment = Alignment.Center) {
            book?.collection?.takeIf { it.isNotBlank() }?.let {
                LineageChip(
                    label = withVolume(it, book.collectionIndex),
                    icon = HomerIcons.CollectionBracket,
                    scale = scale,
                    onClick = { onFilter(FilterToken(FilterFacet.COLLECTION, it)) },
                )
            }
        }
    }
}

/**
 * One fact about the book, as a chip: a mark saying which kind of fact, and its value.
 *
 * ## One style for all three
 *
 * They were drawn three ways — the author as bare text, the series on the accent, the collection
 * quieter — which said the three were three different sorts of thing. They are not: each is one
 * fact about this book, and each narrows the library to it. The mark is what distinguishes them
 * now, which is what a mark is for, and it leaves the chips free to be identical.
 *
 * ## Why the accent went
 *
 * A filled amber chip is what Homer uses for a filter that is ON. None of these is on; each is an
 * offer. Wearing the accent, the series chip claimed the library was already narrowed to it.
 */
@Composable
private fun LineageChip(
    label: String,
    icon: ImageVector,
    scale: Float,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Surface2)
            .border(1.dp, LineShelf, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Faint,
            modifier = Modifier.size(11.dp.scaled(scale)),
        )
        Text(
            label,
            color = Muted,
            fontSize = 11.sp.scaled(scale, floor = 10f),
            lineHeight = 13.sp.scaled(scale, floor = 12f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Helgoland, Volume 1", or just "Helgoland" where the book carries no number in it. */
@Composable
private fun withVolume(name: String, index: Int?): String =
    if (index == null) name else stringResource(R.string.player_series_volume, name, index)

/** The author's line, reserved whether or not there is one. */
private val BookHeaderAuthorLine = 20.dp

/** Two lines of the serif title at 22sp, so a one-line title does not shorten the block. */
private val BookHeaderTitleBlock = 60.dp

/** One relation chip, reserved. Two of these, so a standalone book is as tall as a nested one. */
private val BookHeaderChipRow = 24.dp

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

// ── The bottom row: a timer, how it plays, and a mark ─────────────────────────────────────────

/**
 * Five controls, sorted into the three kinds of thing they actually are.
 *
 * They used to sit in one undifferentiated row of five: speed, sleep, volume, silence, mark. Read
 * left to right that is a timer, then two sound settings, then a third sound setting, then an
 * action — three different kinds interleaved, and five equally-spaced targets with nothing to say
 * which of them belong together.
 *
 * So: **sleep** at the leading edge, because when playback stops is a property of this sitting
 * rather than of the book. **Mark** at the trailing edge, because it is the only one that MAKES
 * something. And the three at-play settings — speed, volume, cut silence — banded together in the
 * middle, each still its own button, one tap deep, exactly as they were.
 *
 * ## Why a [Box] and not a [Row]
 *
 * The group is centred on the SCREEN, not on what is left after its neighbours. Sleep's label is a
 * live countdown, so it changes width every minute; in a row with weights, a centred middle would
 * drift sideways as the timer ticked down. Anchoring the sides and centring the group independently
 * is the only arrangement where the three settings hold still.
 */
@Composable
private fun ToolRow(
    speed: Float,
    sleepLabel: String,
    sleepActive: Boolean,
    volumeMode: String,
    skipSilence: Boolean,
    onSpeed: (Float) -> Unit,
    onSleepMinutes: (Long) -> Unit,
    onSleepEndOfChapter: () -> Unit,
    onSleepOff: () -> Unit,
    onCustomSpeed: () -> Unit,
    onCustomSleep: () -> Unit,
    onVolumeMode: (String) -> Unit,
    onToggleSkipSilence: () -> Unit,
    onMark: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
        // Sleep — quick-select menu.
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            var open by remember { mutableStateOf(false) }
            ToolButton(
                icon = Icons.Filled.Bedtime,
                label = sleepLabel,
                active = sleepActive,
                onClick = { open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                // The one header that is not just a noun. A running timer used to be readable on
                // the button itself; with the words gone, the moment it is opened is the moment to
                // say how long is left.
                MenuHeader(if (sleepActive) "${stringResource(R.string.player_sleep)} · $sleepLabel" else stringResource(R.string.player_sleep))
                listOf(15, 30, 45, 60).forEach { m ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_sleep_minutes, m)) },
                        onClick = { onSleepMinutes(m * 60_000L); open = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_sleep_end_of_chapter)) },
                    onClick = { onSleepEndOfChapter(); open = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_custom), color = Parchment) },
                    onClick = { open = false; onCustomSleep() },
                )
                if (sleepActive) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_sleep_turn_off), color = MaterialTheme.colorScheme.error) },
                        onClick = { onSleepOff(); open = false },
                    )
                }
            }
        }

        // The three at-play settings, held together by proximity alone.
        //
        // The box they sat in was one more line on a screen that already has a scrubber, a
        // transport row and a chapter pill drawing horizontals. Three glyphs set close with a gap
        // either side of the group say "these belong together" without adding an edge — which is
        // the same thing the enclosure was for, and quieter.
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Speed — quick-select menu.
            Box {
                var open by remember { mutableStateOf(false) }
                ToolButton(
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.player_speed_value, formatSpeed(speed)),
                    active = abs(speed - 1f) > 0.001f,
                    onClick = { open = true },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    MenuHeader(stringResource(R.string.player_speed))
                    SPEED_PRESETS.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.player_speed_value, formatSpeed(preset)),
                                    color = if (abs(preset - speed) < 0.001f) Amber else Parchment,
                                )
                            },
                            onClick = { onSpeed(preset); open = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_custom), color = Parchment) },
                        onClick = { open = false; onCustomSpeed() },
                    )
                }
            }

            // Volume override — quick-select menu.
            Box {
                var open by remember { mutableStateOf(false) }
                ToolButton(
                    icon = if (volumeMode == VolumeMode.REDUCED) {
                        Icons.AutoMirrored.Filled.VolumeDown
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    label = when (volumeMode) {
                        VolumeMode.REDUCED -> stringResource(R.string.player_volume_quiet)
                        VolumeMode.INCREASED -> stringResource(R.string.player_volume_boost)
                        else -> stringResource(R.string.player_volume_normal)
                    },
                    active = volumeMode != VolumeMode.NORMAL,
                    onClick = { open = true },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    MenuHeader(stringResource(R.string.player_volume))
                    listOf(
                        VolumeMode.REDUCED to stringResource(R.string.player_volume_reduced),
                        VolumeMode.NORMAL to stringResource(R.string.player_volume_normal),
                        VolumeMode.INCREASED to stringResource(R.string.player_volume_increased),
                    ).forEach { (mode, lbl) ->
                        DropdownMenuItem(
                            text = { Text(lbl, color = if (mode == volumeMode) Amber else Parchment) },
                            onClick = { onVolumeMode(mode); open = false },
                        )
                    }
                }
            }

            // Skip silence — toggle. The label says which way it is set rather than what it is
            // called: "Cut" and "Keep" are the two answers, and the button is the question.
            ToolButton(
                icon = Icons.Filled.ContentCut,
                label = stringResource(
                    if (skipSilence) R.string.player_silence_on else R.string.player_silence_off,
                ),
                active = skipSilence,
                onClick = onToggleSkipSilence,
            )
        }

        // Mark — bookmarks.
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            ToolButton(
                icon = Icons.Filled.BookmarkBorder,
                label = stringResource(R.string.player_mark),
                active = false,
                onClick = onMark,
            )
        }
    }
}

/**
 * One control on the bottom row: a glyph, and nothing else.
 *
 * The label under it is gone. Five of them made the row a paragraph read at arm's length while
 * something is playing, and every one of those words is said again — in full, and in the reader's
 * own language — by the header of the menu the button opens. What the glyph still has to carry is
 * whether the setting is doing anything, and that is the tint: [Amber] when it is, [Muted] when it
 * sits at its default.
 *
 * [label] therefore survives as the content description. It is the same word the menu is headed
 * with, so what a screen reader announces and what a sighted reader sees on opening agree.
 */
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    activeColor: androidx.compose.ui.graphics.Color = Amber,
) {
    val tint = if (active) activeColor else Muted
    Box(
        // A glyph is a small target, and this one lost the words that used to make it a big one.
        // The 48dp is claimed here rather than by inflating the icon.
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * What the menu that just opened is about — the word the button under it no longer says.
 *
 * Set in [SectionLabel] and indented to the items' own text, the same way [DropdownChip] heads its
 * menus, so the two places in Homer where a small control opens a list of values name that list
 * identically.
 */
@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        style = SectionLabel,
        color = Faint,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
    )
}

private fun sleepLabel(remainingMs: Long?, endOfChapter: Boolean, context: android.content.Context): String = when {
    endOfChapter -> context.getString(R.string.player_sleep_chapter)
    remainingMs != null -> formatTime(remainingMs)
    else -> context.getString(R.string.player_sleep)
}

/**
 * Types an exact playback speed.
 *
 * Its own dialog rather than [CustomNumberDialog] because a speed is fractional, and the one thing
 * a listener must not be able to do by accident is set it to zero — which is silence that looks
 * like a stall. Anything unparseable leaves the speed alone; anything out of range is clamped.
 */
@Composable
private fun CustomSpeedDialog(initial: Float, onConfirm: (Float) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(formatSpeed(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_speed_custom_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    // One separator, digits only — and both separators accepted, because the
                    // keyboard offers whichever the locale uses and the parser below wants a dot.
                    onValueChange = { entered ->
                        text = entered.filter { it.isDigit() || it == '.' || it == ',' }.take(4)
                    },
                    label = { Text(stringResource(R.string.player_speed_unit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.player_speed_custom_range),
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            HomerTextButton(
                onClick = {
                    text.replace(',', '.').toFloatOrNull()
                        ?.let { onConfirm(it.coerceIn(MIN_SPEED, MAX_SPEED)) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

/** Bounds for a typed speed. Zero is silence that looks like a stall, so it is not reachable. */
private const val MIN_SPEED = 0.25f
private const val MAX_SPEED = 4.0f

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
private fun BookmarksDialog(
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
private fun ChapterPickerDialog(
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

// ── Formatting ─────────────────────────────────────────────────────────────

/** Trims trailing zeros: 1.0 -> "1", 1.25 -> "1.25", 1.5 -> "1.5". */
private fun formatSpeed(speed: Float): String =
    String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
