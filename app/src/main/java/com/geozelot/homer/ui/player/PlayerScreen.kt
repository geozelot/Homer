package com.geozelot.homer.ui.player

import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkKind
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.playback.VolumeMode
import com.geozelot.homer.data.metadata.BookGenre
import com.geozelot.homer.ui.components.EditableBook
import com.geozelot.homer.ui.home.BookDetailsCard
import com.geozelot.homer.ui.home.BookListItem
import com.geozelot.homer.ui.home.FilterToken
import com.geozelot.homer.ui.home.FilterFacet
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.components.CustomNumberDialog
import com.geozelot.homer.ui.components.DropdownChip
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.components.HomerSwitch
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberDeep
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface2
import com.geozelot.homer.ui.theme.Surface1
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
    val sleepExtend by viewModel.sleepExtend.collectAsStateWithLifecycle()
    val sleepFade by viewModel.sleepFadeOutSeconds.collectAsStateWithLifecycle()
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
        )
    }
    val artwork: @Composable (Modifier) -> Unit = { slotModifier ->
        PlayerArtwork(
            // Live cover (updates on refresh/extraction) → play-time snapshot → embedded art.
            model = cover ?: state.coverModel ?: state.artworkData?.bytes,
            onCollapse = onBack,
            modifier = slotModifier,
        )
    }
    val controls: @Composable (Modifier) -> Unit = { slotModifier ->
        // Title, chapter + book-time-left, scrubber, transport, quick-select.
        Column(
            modifier = slotModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val loadingLabel = stringResource(R.string.player_loading)
            Text(
                // Prefer the live (override-applied) title so an in-place edit updates immediately;
                // fall back to the playback snapshot before the book row has loaded.
                text = editableBook?.title?.ifBlank { null }
                    ?: state.bookTitle.ifEmpty { state.chapterTitle.ifEmpty { loadingLabel } },
                style = SerifTitle.copy(fontSize = 20.sp),
                color = Parchment,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // What the book IS, under what it is called: the author, its genre, the series it
            // belongs to. Tappable, so each is also the way back into a library narrowed to it —
            // the same thing the item chips do, which is what makes the player a way in rather
            // than a dead end.
            editableBook?.let { book ->
                BookFacts(
                    book = book,
                    locale = LocalConfiguration.current.locales[0],
                    onFilter = onFilter,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            val hasPicker = chapters.isNotEmpty()
            val chapterCount = if (hasPicker) chapters.size else state.chapterCount
            val chapterNumber =
                if (hasPicker) chapters.indexOfFirst { it.isCurrent }.let { if (it >= 0) it + 1 else 1 }
                else state.chapterIndex + 1

            // Chapter picker: a wide pill below the title, carrying the whole chapter line.
            if (hasPicker) {
                ChapterButton(
                    label = chapterLabel(
                        number = chapterNumber,
                        count = chapterCount,
                        chapter = chapters.firstOrNull { it.isCurrent },
                        bookTotalMs = bookTotal,
                    ),
                    onClick = { showChaptersDialog = true },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Info line. The chapter count moved into the pill above, where it sits beside the
            // rest of the same answer; what is left here is the one number that is about the BOOK
            // — how much of it remains — plus the chapter count for a book with no picker to put
            // it in.
            val infoLine = buildString {
                if (chapterCount > 0 && !hasPicker) {
                    append(context.getString(R.string.player_chapter_of, chapterNumber, chapterCount))
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
            if (infoLine.isNotEmpty()) {
                Text(
                    text = infoLine,
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
                onPrev = viewModel::previousChapter,
                onSeekBack = { viewModel.seekBy(-seekSeconds) },
                onPlayPause = viewModel::playPause,
                onSeekForward = { viewModel.seekBy(seekSeconds) },
                onNext = viewModel::nextChapter,
                modifier = Modifier.padding(top = 8.dp),
            )

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
                    )
                }
            }
        } else {
            // Tall viewport: the stacked layout, but inside a scroll container whose content is at
            // least one viewport tall. SpaceBetween keeps the cluster pinned to the bottom while
            // everything fits; at large font scales the content grows past the viewport and simply
            // scrolls rather than crushing the cover and clipping the transport row.
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    topBar()
                    artwork(
                        Modifier
                            .fillMaxWidth()
                            // A fraction of the viewport rather than the leftover space: the cover
                            // then has a real height no matter how tall the cluster measures.
                            .height((viewportHeight * 0.48f).coerceAtLeast(MIN_ARTWORK_HEIGHT))
                            .padding(vertical = 16.dp),
                    )
                    controls(Modifier.fillMaxWidth())
                }
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
                onReadFolderDifferently = onReadFolderDifferently?.let {
                    { showDetails = false; it() }
                },
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
                onDismiss = { showEditDialog = false },
            )
        }
    }

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

/** Floor for the artwork slot in the stacked layout. */
private val MIN_ARTWORK_HEIGHT = 150.dp

/** Floor for the cover itself, so it can never compute to a non-positive (invisible) size. */
private val MIN_COVER_WIDTH = 64.dp

/** The cover, centered in whatever slot it's given and sized to fit it in both dimensions. */
@Composable
private fun PlayerArtwork(model: Any?, onCollapse: () -> Unit, modifier: Modifier = Modifier) {
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
        val coverWidth = minOf(maxWidth * 0.82f, maxHeight).coerceAtLeast(MIN_COVER_WIDTH)
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
        Box {
            IconButton(onClick = { overflowOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more), tint = Muted)
            }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                if (started) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mark_completed)) },
                        onClick = {
                            onMarkCompleted()
                            overflowOpen = false
                        },
                    )
                }
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_cd_previous), tint = Parchment, modifier = Modifier.size(38.dp))
        }
        SeekButton(seconds = seekSeconds, forward = false, onClick = onSeekBack)
        Box(
            modifier = Modifier
                .size(84.dp)
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
                modifier = Modifier.size(40.dp),
            )
        }
        SeekButton(seconds = seekSeconds, forward = true, onClick = onSeekForward)
        IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_cd_next), tint = Parchment, modifier = Modifier.size(38.dp))
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
private fun SeekButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    val label = if (forward) {
        stringResource(R.string.player_cd_skip_forward, seconds)
    } else {
        stringResource(R.string.player_cd_skip_back, seconds)
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(SeekGlyphSize)) {
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

/**
 * Where you are in the book, in one line, and the way into the picker.
 *
 * It used to say "Chapters" and nothing else, with "Chapter 7 of 21" repeated on the info line
 * below it. Now the pill carries the whole answer — which chapter, how long it runs, where it
 * begins in the book and what fraction of the book that is — and the line below carries only the
 * time left, which is the one number that is about the book rather than about the chapter.
 *
 * **Every part after the count is dropped rather than guessed.** A multi-file book knows none of
 * them until each of its files has been measured, so what it shows in the meantime is "Chapter 7
 * of 21", which is true. See [PlayerChapter].
 */
@Composable
private fun ChapterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(R.string.player_chapters),
                tint = Parchment,
                modifier = Modifier.size(16.dp),
            )
            Text(
                label,
                color = Parchment,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                // One line, and it must be the whole line: the pill sizes to its text and the
                // column it sits in is the screen's width, so there is room for the long form in
                // both languages. Ellipsis is the backstop for a 200% font scale, not the plan.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Chapter 7 of 21 · 42:15 (at 2:10:05 · 24%)", with every part after the count optional.
 *
 * Assembled from resources rather than concatenated here, so a locale can reorder it — German puts
 * the same parts in the same order but not every language does, and "bei" is not "at" in a
 * position a format string can guess.
 */
@Composable
private fun chapterLabel(
    number: Int,
    count: Int,
    chapter: PlayerChapter?,
    bookTotalMs: Long?,
): String {
    val which = stringResource(R.string.player_chapter_of, number, count)
    val length = chapter?.lengthMs?.takeIf { it > 0 } ?: return which
    val start = chapter.startInBookMs
    // The percentage needs BOTH a start and a total, and the total arrives on its own schedule —
    // so a book can legitimately show "at 2:10:05" with no percentage beside it for a moment.
    val percent = if (start != null && bookTotalMs != null && bookTotalMs > 0) {
        ((start.toFloat() / bookTotalMs) * 100).toInt().coerceIn(0, 100)
    } else {
        null
    }
    val where = when {
        start == null -> null
        percent != null -> stringResource(R.string.player_chapter_at_pct, formatTime(start), percent)
        else -> stringResource(R.string.player_chapter_at, formatTime(start))
    }
    return if (where == null) {
        "$which · ${formatTime(length)}"
    } else {
        stringResource(R.string.player_chapter_line, which, formatTime(length), where)
    }
}

/**
 * What the book is, as three quiet chips: author, genre, series.
 *
 * Chips rather than a line of text because each one is a way somewhere — tapping narrows the
 * library to it and leaves — and a thing you can press should not look like a caption. Outlined
 * and muted on purpose: the title above is what the screen is about, and these sit under it the
 * way the library's own item chips sit under a card's title.
 *
 * Absent facts are absent, not blank: a book with no genre shows two chips, and one with nothing
 * known shows none and takes no space.
 */
@Composable
private fun BookFacts(
    book: EditableBook,
    locale: java.util.Locale,
    onFilter: (FilterToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val facts = buildList {
        book.author?.takeIf { it.isNotBlank() }?.let { add(it to FilterToken(FilterFacet.AUTHOR, it)) }
        // The primary alone. A book carrying four genres would otherwise spend the whole row on
        // them, and the rest are one tap away in the details card.
        book.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
            add(BookGenre.display(it, locale) to FilterToken(FilterFacet.GENRE, it))
        }
        book.series?.takeIf { it.isNotBlank() }?.let { add(it to FilterToken(FilterFacet.SERIES, it)) }
    }
    if (facts.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        facts.forEach { (label, token) ->
            Text(
                label,
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, Line, RoundedCornerShape(999.dp))
                    .clickable { onFilter(token) }
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * A picker row's second line: "42:15 · at 2:10:05 · 24%", or as much of it as is known.
 *
 * Flatter than the pill's version — no brackets — because here it is a caption under a title
 * rather than a sentence in its own right, and twenty of them stacked read better unbracketed.
 */
@Composable
private fun chapterRowDetail(chapter: PlayerChapter, bookTotalMs: Long?): String? {
    val parts = buildList {
        chapter.lengthMs?.takeIf { it > 0 }?.let { add(formatTime(it)) }
        chapter.startInBookMs?.let { start ->
            val percent = if (bookTotalMs != null && bookTotalMs > 0) {
                ((start.toFloat() / bookTotalMs) * 100).toInt().coerceIn(0, 100)
            } else {
                null
            }
            add(
                if (percent != null) {
                    stringResource(R.string.player_chapter_at_pct, formatTime(start), percent)
                } else {
                    stringResource(R.string.player_chapter_at, formatTime(start))
                },
            )
        }
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

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
        Text(stringResource(R.string.player_error_banner), color = Danger, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── The bottom row: a timer, how it plays, and a mark ─────────────────────────────────────────

/**
 * Five controls, sorted into the three kinds of thing they actually are.
 *
 * They used to sit in one undifferentiated row of five: speed, sleep, volume, silence, mark. Read
 * left to right that is a timer, then two sound settings, then a third sound setting, then an
 * action — three different kinds interleaved, and five targets across a phone with nothing between
 * them but equal spacing.
 *
 * So: **sleep** on the left, because when playback stops is a property of this sitting rather than
 * of the book. **Mark** on the right, because it is the only one that MAKES something. And the
 * three at-play settings — speed, volume, cut silence — behind one control in the middle, which
 * carries what they are set to as its own label and opens into a field holding all three.
 *
 * That middle control is the library's arrange field, in a player: one chip that becomes a row of
 * settings in place, with a way back at its left edge. Same idiom, same [DropdownChip], so the two
 * places in Homer where several settings live behind one control behave identically.
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
    // Survives a rotation: the field is a mode, and coming back to a collapsed row after turning
    // the phone loses whatever the reader had opened it to change.
    var playbackOpen by rememberSaveable { mutableStateOf(false) }
    // Back closes the field before it leaves the player. The library's arrange field — which this
    // is — already behaves this way, and without it the gesture that means "put that away" meant
    // "put the whole book away" instead.
    BackHandler(enabled = playbackOpen) { playbackOpen = false }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playbackOpen) {
            PlaybackField(
                speed = speed,
                volumeMode = volumeMode,
                skipSilence = skipSilence,
                onSpeed = onSpeed,
                onCustomSpeed = onCustomSpeed,
                onVolumeMode = onVolumeMode,
                onToggleSkipSilence = onToggleSkipSilence,
                onCollapse = { playbackOpen = false },
            )
            return@Row
        }

        // Sleep — quick-select menu.
        Box {
            var open by remember { mutableStateOf(false) }
            ToolButton(
                icon = Icons.Filled.Bedtime,
                label = sleepLabel,
                active = sleepActive,
                onClick = { open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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

        // The three at-play settings, as one control that says what they are set to.
        PlaybackButton(
            speed = speed,
            volumeMode = volumeMode,
            skipSilence = skipSilence,
            onClick = { playbackOpen = true },
            modifier = Modifier.weight(1f),
        )

        // Mark — bookmarks.
        ToolButton(
            icon = Icons.Filled.BookmarkBorder,
            label = stringResource(R.string.player_mark),
            active = false,
            onClick = onMark,
        )
    }
}

/**
 * The collapsed middle control: one button whose label IS the current sound state.
 *
 * "1.5× · Boost · Cut" when something is set, the word Playback when nothing is — so the row says
 * what it is doing without being opened, which is the whole reason the three settings can afford
 * to be one tap further away than they were.
 */
@Composable
private fun PlaybackButton(
    speed: Float,
    volumeMode: String,
    skipSilence: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parts = buildList {
        if (abs(speed - 1f) > 0.001f) add(stringResource(R.string.player_speed_value, formatSpeed(speed)))
        when (volumeMode) {
            VolumeMode.REDUCED -> add(stringResource(R.string.player_volume_quiet))
            VolumeMode.INCREASED -> add(stringResource(R.string.player_volume_boost))
            else -> Unit
        }
        if (skipSilence) add(stringResource(R.string.player_silence_on))
    }
    val active = parts.isNotEmpty()
    val tint = if (active) Amber else Muted
    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1)
                .border(1.dp, if (active) AmberDeep else Line, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = stringResource(R.string.player_playback),
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Text(
                if (active) parts.joinToString(" · ") else stringResource(R.string.player_playback),
                color = tint,
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The three settings, in place of the row that opened them.
 *
 * Deliberately the same shape as the library's arrange field — a back arrow in a leading slot at
 * the field's own edge, then equal shares carrying a [DropdownChip] each — because it is the same
 * gesture answering the same kind of question, and two different-looking versions of it would be
 * two things to learn.
 *
 * Cut-silence is a dropdown of two rather than a switch so that all three cells read the same way
 * and all three say what they are set to. A switch would have said only that it can be switched.
 */
@Composable
private fun PlaybackField(
    speed: Float,
    volumeMode: String,
    skipSilence: Boolean,
    onSpeed: (Float) -> Unit,
    onCustomSpeed: () -> Unit,
    onVolumeMode: (String) -> Unit,
    onToggleSkipSilence: () -> Unit,
    onCollapse: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface1)
            .border(1.dp, AmberDeep, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 40.dp, height = 48.dp).clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = Muted,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownChip(
            label = stringResource(R.string.player_speed_value, formatSpeed(speed)),
            options = SPEED_PRESETS,
            selected = speed,
            labelOf = { context.getString(R.string.player_speed_value, formatSpeed(it)) },
            onSelect = onSpeed,
            onCustom = onCustomSpeed,
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Speed,
            iconDescription = stringResource(R.string.player_playback_speed),
            menuHeader = stringResource(R.string.player_playback_speed),
            bordered = false,
        )
        DropdownChip(
            label = stringResource(
                when (volumeMode) {
                    VolumeMode.REDUCED -> R.string.player_volume_quiet
                    VolumeMode.INCREASED -> R.string.player_volume_boost
                    else -> R.string.player_volume_normal
                },
            ),
            options = listOf(VolumeMode.REDUCED, VolumeMode.NORMAL, VolumeMode.INCREASED),
            selected = volumeMode,
            labelOf = {
                context.getString(
                    when (it) {
                        VolumeMode.REDUCED -> R.string.player_volume_reduced
                        VolumeMode.INCREASED -> R.string.player_volume_increased
                        else -> R.string.player_volume_normal
                    },
                )
            },
            onSelect = onVolumeMode,
            modifier = Modifier.weight(1f),
            icon = if (volumeMode == VolumeMode.REDUCED) {
                Icons.AutoMirrored.Filled.VolumeDown
            } else {
                Icons.AutoMirrored.Filled.VolumeUp
            },
            iconDescription = stringResource(R.string.player_playback_volume),
            menuHeader = stringResource(R.string.player_playback_volume),
            bordered = false,
        )
        DropdownChip(
            label = stringResource(if (skipSilence) R.string.player_silence_on else R.string.player_silence_off),
            options = listOf(true, false),
            selected = skipSilence,
            labelOf = {
                context.getString(if (it) R.string.player_silence_on else R.string.player_silence_off)
            },
            // The caller owns a toggle, not a setter — so only a change is passed on, and picking
            // what is already set is the no-op it looks like.
            onSelect = { if (it != skipSilence) onToggleSkipSilence() },
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.ContentCut,
            iconDescription = stringResource(R.string.player_playback_silence),
            menuHeader = stringResource(R.string.player_playback_silence),
            bordered = false,
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    activeColor: androidx.compose.ui.graphics.Color = Amber,
) {
    val tint = if (active) activeColor else Muted
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 10.5.sp, maxLines = 1)
    }
}

private fun sleepLabel(remainingMs: Long?, endOfChapter: Boolean, context: android.content.Context): String = when {
    endOfChapter -> context.getString(R.string.player_sleep_chapter)
    remainingMs != null -> formatTime(remainingMs)
    else -> context.getString(R.string.player_sleep)
}

// ── Sleep settings (advanced: shake-extend + fade) ────────────────────────────

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
        confirmButton = {},
        dismissButton = { HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
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
    // Open scrolled to the current chapter so it's visible in a long list.
    LaunchedEffect(Unit) {
        val current = chapters.indexOfFirst { it.isCurrent }
        if (current > 0) listState.scrollToItem(current)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_chapters)) },
        text = {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = dialogContentMaxHeight())) {
                itemsIndexed(chapters) { index, chapter ->
                    val chapterFallback = stringResource(R.string.chapter_numbered, index + 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(chapter) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.width(28.dp),
                            color = if (chapter.isCurrent) Amber else Muted,
                            fontSize = 12.sp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                chapter.title.ifBlank { chapterFallback },
                                color = if (chapter.isCurrent) Amber else Parchment,
                                fontWeight = if (chapter.isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // The same three numbers the pill carries, for every row rather than
                            // just the current one — which is what makes this a list you can
                            // choose from ("the short one", "the one an hour in") instead of a
                            // list of names. Absent where they are not known; see PlayerChapter.
                            chapterRowDetail(chapter, bookTotalMs)?.let {
                                Text(
                                    it,
                                    color = Muted,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
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
