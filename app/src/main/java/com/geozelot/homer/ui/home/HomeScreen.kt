package com.geozelot.homer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.storage.StorageMigrator
import com.geozelot.homer.ui.components.HomerSwitch
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.components.DropdownChip
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.components.EditableBook
import com.geozelot.homer.ui.components.MiniPlayer
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Ground
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Sage
import com.geozelot.homer.ui.theme.SageSoft
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.SerifDisplay
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Studio
import com.geozelot.homer.ui.theme.Surface1
import com.geozelot.homer.ui.theme.Surface2

@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val libraryLoaded by viewModel.libraryLoaded.collectAsStateWithLifecycle()
    val continueShelf by viewModel.continueShelf.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val gridView by viewModel.gridView.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val shelfMode by viewModel.shelfMode.collectAsStateWithLifecycle()
    val seriesMode by viewModel.seriesMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val miniPlayerBook by viewModel.miniPlayerBook.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Dialog targets are ids, not row snapshots. A snapshot went stale the moment the dialog wrote
    // through the ViewModel — picking a cover left the button saying "Choose cover" until the dialog
    // was reopened — and being a plain `remember` it also closed the dialog on every rotation. The
    // live row is re-derived from `entries` below on each recomposition.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
    // rememberSaveable so a rotation doesn't drop the user out of search: `searching` used to be
    // lost while the query stayed in the ViewModel, leaving the library filtered with no search
    // field to clear it.
    var searching by rememberSaveable { mutableStateOf(false) }
    // Open series shelves, anchored on the book ids they contain (see `isOpen` in libraryContent)
    // and saved across configuration changes — a rotation used to collapse every shelf the user
    // had opened.
    val expanded = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }

    // Back should leave search rather than leave the app with the library still filtered.
    BackHandler(enabled = searching) {
        searching = false
        viewModel.setSearchQuery("")
    }

    val actions = remember(viewModel) {
        BookActions(
            onDownload = viewModel::download,
            onRemove = viewModel::deleteDownload,
            onEdit = { editingId = it.id },
            onSetHidden = viewModel::setHidden,
            onMarkCompleted = viewModel::markCompleted,
            onEditSeries = { editingSeriesKey = it.expandKey },
            // Already-complete episodes are skipped: re-enqueueing one resets its row to QUEUED and
            // wakes a worker that has nothing to fetch, for no gain.
            onDownloadSeries = { series ->
                viewModel.downloadAll(series.books.filterNot { it.isDownloaded }.map { it.id })
            },
            onRemoveSeries = { series -> viewModel.deleteDownloads(series.books.map { it.id }) },
            onPause = viewModel::pauseDownload,
            onResume = viewModel::resumeDownload,
        )
    }

    val gridState = rememberLazyGridState()

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            searching = searching,
            query = searchQuery,
            onQueryChange = viewModel::setSearchQuery,
            onOpenSearch = { searching = true },
            onCloseSearch = {
                searching = false
                viewModel.setSearchQuery("")
            },
            onSettings = onOpenSettings,
        )

        // The Continue shelf is pinned here — above the scrolling library rather than being its
        // first item — and shrinks to a slim strip once the user scrolls into the library. Its
        // LazyRow state is hoisted so the horizontal scroll position survives collapsing and is
        // no longer reset by the item being disposed. derivedStateOf keeps the collapse flag from
        // recomposing on every scroll pixel.
        val shelfRowState = rememberLazyListState()
        val shelfCollapsed by remember(gridState) {
            derivedStateOf {
                gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 48
            }
        }
        val pinnedShelf = if (searching) emptyList() else continueShelf
        if (pinnedShelf.isNotEmpty() && entries.isNotEmpty()) {
            ContinuePinnedShelf(
                books = pinnedShelf,
                collapsed = shelfCollapsed,
                rowState = shelfRowState,
                onOpen = onBookClick,
                actions = actions,
            )
        }

        // Sort, group and the grid/list toggle are pinned here rather than scrolled away as the
        // grid's first two items: they are the controls for what is being scrolled, so having to
        // scroll back to the top to reach them was the wrong way round. The Continue strip above
        // collapses to make room for them; these stay put.
        if (entries.isNotEmpty()) {
            // `searching` alone means the field is open, not that anything is filtered — labelling
            // the untouched library "309 results" before a character is typed.
            val filtering = searching && searchQuery.isNotBlank()
            // The library's header and controls are pinned chrome, not part of the list they act
            // on. The wash fades from a raised surface at the header down to the page colour by
            // the time it meets the list, so the band hands off to what it scrolls rather than
            // stopping at a hard edge.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Surface1, Ground))),
            ) {
                // Faint above, solid below: the strip overhead is a sibling shelf, the list beneath
                // is what these controls are pointed at.
                HorizontalDivider(color = Line.copy(alpha = 0.45f))
                LibraryControlBar(
                    count = if (filtering) entries.bookCount() else bookCount,
                    searching = filtering,
                    sort = sortMode,
                    shelving = shelfMode,
                    series = seriesMode,
                    gridView = gridView,
                    collapsed = shelfCollapsed,
                    onSortChange = viewModel::setSortMode,
                    onShelfChange = viewModel::setShelfMode,
                    onSeriesChange = viewModel::setSeriesMode,
                    onToggleView = viewModel::setGridView,
                    modifier = Modifier.padding(horizontal = LibraryGridPadding),
                )
                HorizontalDivider(color = Line)
            }
        }

        if (entries.isEmpty()) {
            when {
                // Room hasn't delivered yet (or a scan is running): show a discovery phase rather
                // than flashing "your shelf is empty" on every launch.
                !libraryLoaded || scanState is ScanState.Scanning ->
                    LibraryLoading(scanState = scanState, modifier = Modifier.weight(1f))
                searching && searchQuery.isNotBlank() ->
                    EmptyResults(modifier = Modifier.weight(1f))
                else ->
                    EmptyLibrary(
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.weight(1f),
                    )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (gridView) LibraryGridColumns else 1),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = LibraryGridPadding, end = LibraryGridPadding, top = 4.dp, bottom = 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
                verticalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
            ) {
                libraryContent(
                    entries = entries,
                    gridView = gridView,
                    ctx = RowContext(shelfMode, seriesMode),
                    expanded = expanded,
                    onBookClick = onBookClick,
                    actions = actions,
                )
            }
        }

        // The mini-player insets itself (its gradient runs behind the navigation bar). When there's
        // nothing playing it emits nothing at all, so the space has to be reserved here or the last
        // row of books ends up under the navigation bar.
        if (playback.bookId != null) {
            MiniPlayer(
                state = playback,
                onOpenPlayer = onBookClick,
                onPlayPause = viewModel::playPause,
                onRetry = viewModel::retry,
                liveCover = miniPlayerBook?.coverModel,
                liveTitle = miniPlayerBook?.title,
            )
        } else {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars),
            )
        }
    }

    // Both dialogs re-derive their subject from the live list every recomposition, so edits made
    // inside them (a cover pick) are reflected at once and a rotation doesn't lose the dialog.
    entries.findBook(editingId)?.let { book ->
        EditBookDialog(
            book = book.toEditable(),
            onSave = { title, author, series, index, genre, tags, hidden, downloadOnPlay ->
                viewModel.saveOverride(book.id, title, author, series, index, genre, tags, hidden, downloadOnPlay)
                editingId = null
            },
            onReset = {
                viewModel.clearOverride(book.id)
                editingId = null
            },
            onPickCover = { uri -> viewModel.setCustomCover(book.id, uri) },
            onClearCover = { viewModel.clearCustomCover(book.id) },
            onDismiss = { editingId = null },
        )
    }

    entries.findSeries(editingSeriesKey)?.let { series ->
        SeriesEditDialog(
            series = series,
            onSave = { name, author ->
                viewModel.saveSeriesOverride(series.books.map { it.id }, name, author)
                editingSeriesKey = null
            },
            onDismiss = { editingSeriesKey = null },
        )
    }

}

/**
 * Stable identity of a series shelf, used for lazy-item keys and to address an open series-edit
 * dialog. [LibraryEntry.Series.key] is "author|series", so it changes the instant the user renames
 * the series — which made the lazy layout treat the shelf as a brand new item. Book ids survive
 * metadata edits, and a series always has at least two books.
 *
 * Expand/collapse state is NOT keyed on this: it anchors on the shelf's whole membership instead
 * (see `isOpen` in `libraryContent`), so moving the lowest-id book out of a series can't collapse
 * it either.
 */
private val LibraryEntry.Series.expandKey: String
    get() = "series:${books.minOf { it.id }}"

/** The live row for an open edit dialog, or null when there's no target (or it's gone). */
private fun List<LibraryEntry>.findBook(id: String?): BookListItem? {
    if (id == null) return null
    return firstNotNullOfOrNull { entry ->
        when (entry) {
            is LibraryEntry.Header -> null
            is LibraryEntry.Standalone -> entry.book.takeIf { it.id == id }
            is LibraryEntry.Series -> entry.books.firstOrNull { it.id == id }
        }
    }
}

/** The live series for an open series-edit dialog, matched on its stable [expandKey]. */
private fun List<LibraryEntry>.findSeries(key: String?): LibraryEntry.Series? {
    if (key == null) return null
    return filterIsInstance<LibraryEntry.Series>().firstOrNull { it.expandKey == key }
}

/** Maps a library row to the shared edit dialog's minimal model (effective values). */
private fun BookListItem.toEditable() = EditableBook(
    id = id,
    title = title,
    author = author,
    series = series,
    seriesIndex = seriesIndex,
    genre = genre,
    tags = tags,
    hidden = hidden,
    hasCustomCover = hasCustomCover,
    downloadOnPlay = downloadOnPlayOverride,
)

/** Callbacks a card/row needs for its context menu, bundled to keep signatures small. */
private class BookActions(
    val onDownload: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onEdit: (BookListItem) -> Unit,
    val onSetHidden: (String, Boolean) -> Unit,
    val onMarkCompleted: (String) -> Unit,
    val onEditSeries: (LibraryEntry.Series) -> Unit,
    val onDownloadSeries: (LibraryEntry.Series) -> Unit,
    val onRemoveSeries: (LibraryEntry.Series) -> Unit,
    val onPause: (String) -> Unit,
    val onResume: (String) -> Unit,
)

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (searching) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onClose = onCloseSearch,
                modifier = Modifier.weight(1f),
            )
        } else {
            Wordmark(stringResource(R.string.app_name))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.home_cd_search), tint = Muted)
                }
                // Straight to settings. This was an overflow menu holding exactly one item ever
                // since the library folder, sync and storage became their own destinations — two
                // taps and a popup to reach the only thing in it.
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.home_cd_settings),
                        tint = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.home_cd_close_search), tint = Muted)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Faint) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear), tint = Muted)
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
    }
}

/** "Homer" with an amber initial, in the serif voice. */
@Composable
private fun Wordmark(text: String) {
    Text(
        text = androidx.compose.ui.text.buildAnnotatedString {
            if (text.isNotEmpty()) {
                withAmber(text.first().toString())
                append(text.drop(1))
            }
        },
        style = SerifDisplay,
        color = Parchment,
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.withAmber(s: String) {
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Amber))
    append(s)
    pop()
}

// ── Library content (single scrolling grid) ───────────────────────────────────

/** Columns in grid view. Shared with the expanded-series rows, which lay out their own cells. */
private const val LibraryGridColumns = 3

/** Gap between grid cells, both axes. The series enclosure paints across half of it. */
private val LibraryGridSpacing = 12.dp

/** The grid's horizontal content padding. */
private val LibraryGridPadding = 16.dp

/**
 * The collapsed Continue strip's cover as a fraction of a grid cover's width. The strip is a
 * recognise-and-tap affordance while the library scrolls under it, so the cover is sized to be
 * identifiable rather than readable — tune this one number if it wants to be bigger or smaller.
 */
private const val ContinueSlimCoverFraction = 2.5f

/**
 * Width of one grid cell at [totalWidth] — the LazyVerticalGrid's own arithmetic, factored out so
 * the collapsed Continue strip can size itself against a real cover instead of guessing at a
 * literal dp that drifts the moment the grid's padding or column count changes.
 */
private fun gridCellWidth(totalWidth: Dp): Dp =
    ((totalWidth - LibraryGridPadding * 2 - LibraryGridSpacing * (LibraryGridColumns - 1)) / LibraryGridColumns)
        .coerceAtLeast(0.dp)

private fun LazyGridScope.libraryContent(
    entries: List<LibraryEntry>,
    gridView: Boolean,
    ctx: RowContext,
    /** Book ids anchoring the open series shelves — see [isOpen]. */
    expanded: MutableList<String>,
    onBookClick: (String) -> Unit,
    actions: BookActions,
) {
    // Membership test for the open shelves, hoisted out of the per-entry loop: `expanded` holds
    // one id per book of every open series, so a linear scan per entry would be quadratic on a
    // large library. Reading the state list here still subscribes the grid content to changes.
    val openAnchors = expanded.toHashSet()

    /**
     * A shelf is open while ANY of its books is anchored, and opening one anchors all of them.
     * Anchoring the whole membership is what makes the state survive an edit: keying on the series
     * name collapsed the shelf when the user renamed it, and keying on the lowest book id (the fix
     * for that) collapsed it when precisely that book was moved out of the series.
     */
    fun isOpen(entry: LibraryEntry.Series) = entry.books.any { it.id in openAnchors }

    // Tested against the live list, not `openAnchors`: that snapshot is from the last time this
    // ran, so two taps landing in one frame would anchor the same shelf twice.
    fun open(entry: LibraryEntry.Series) {
        entry.books.forEach { if (it.id !in expanded) expanded.add(it.id) }
    }

    fun close(entry: LibraryEntry.Series) {
        expanded.removeAll(entry.books.mapTo(HashSet()) { it.id })
    }

    // Neither the Continue shelf nor the library's own header and sort/group bar are items here:
    // HomeScreen pins all three above the grid so they stay reachable while it scrolls.

    // Two headers really can carry the same title — a book whose author metadata literally reads
    // "Unknown author" gets its own section beside the fallback one — and duplicate keys make the
    // lazy layout throw. Disambiguating by list position did the job but tied every header's key to
    // how many rows happened to precede it, so adding one book above re-created the lot; counting
    // repeats of the title is just as unique and only changes when the titles themselves do.
    val headerOrdinals = HashMap<String, Int>()

    entries.forEach { entry ->
        when (entry) {
            is LibraryEntry.Header -> item(
                span = { GridItemSpan(maxLineSpan) },
                key = "header:${entry.title}#${headerOrdinals.merge(entry.title, 1, Int::plus)}",
            ) { SectionLabelRow(entry.title) }
            is LibraryEntry.Standalone -> {
                if (gridView) {
                    item(key = entry.book.id) {
                        BookGridCard(entry.book, ctx, onOpen = onBookClick, actions = actions)
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }, key = entry.book.id) {
                        BookListRow(entry.book, startPadding = 0.dp, ctx = ctx, onOpen = onBookClick, actions = actions)
                    }
                }
            }
            is LibraryEntry.Series -> {
                val shelfKey = entry.expandKey
                val shelfOpen = isOpen(entry)
                if (gridView) {
                    if (shelfOpen) {
                        // A header banner, then the episodes a row at a time — every one of them a
                        // separate lazy item drawing its own slice of the enclosure that wraps the
                        // whole shelf. See `seriesEnclosure`.
                        item(span = { GridItemSpan(maxLineSpan) }, key = "series-open:$shelfKey") {
                            ExpandedSeriesHeader(series = entry, onCollapse = { close(entry) })
                        }
                        val rows = entry.books.chunked(LibraryGridColumns)
                        itemsIndexed(
                            rows,
                            span = { _, _ -> GridItemSpan(maxLineSpan) },
                            // First id in the row: unique across the library (a book sits in one
                            // series) and stable while the row's membership holds.
                            key = { _, row -> "sep:${row.first().id}" },
                        ) { index, row ->
                            ExpandedSeriesRow(
                                books = row,
                                last = index == rows.lastIndex,
                                ctx = ctx,
                                onOpen = onBookClick,
                                actions = actions,
                            )
                        }
                    } else {
                        item(key = shelfKey) {
                            SeriesGridCard(
                                series = entry,
                                onOpen = { open(entry) },
                                actions = actions,
                            )
                        }
                    }
                } else {
                    // Same enclosure as the grid: the shelf row is its top slice and each episode
                    // draws a slice below, so an open series reads as one bordered card here too.
                    // Collapsed, the row stays the self-contained card it has always been.
                    item(span = { GridItemSpan(maxLineSpan) }, key = shelfKey) {
                        SeriesShelfRow(
                            series = entry,
                            expanded = shelfOpen,
                            onToggle = { if (shelfOpen) close(entry) else open(entry) },
                            actions = actions,
                        )
                    }
                    if (shelfOpen) {
                        itemsIndexed(
                            entry.books,
                            span = { _, _ -> GridItemSpan(maxLineSpan) },
                            key = { _, book -> "ep:${book.id}" },
                        ) { index, book ->
                            val last = index == entry.books.lastIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .seriesEnclosure(top = false, bottom = last)
                                    .padding(horizontal = SeriesListEnclosurePad)
                                    .padding(bottom = if (last) SeriesListEnclosurePad else 0.dp),
                            ) {
                                // 2dp on top of the enclosure's own inset keeps each episode at
                                // exactly the indent it had before the border went round them.
                                BookListRow(
                                    book,
                                    startPadding = 2.dp,
                                    ctx = ctx,
                                    onOpen = onBookClick,
                                    actions = actions,
                                    bordered = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Both pinned headers, and the in-list section labels.
 *
 * [large] is the resting size for the two pinned ones: they title whole regions rather than
 * separating rows inside a list, so they carry a little more weight. Scrolling into the library
 * drops them back to the in-list size along with the listening panel, since every pixel the pinned
 * block holds on to is a pixel of library the user can't see.
 */
@Composable
private fun SectionLabelRow(
    text: String,
    topPadding: Dp = 12.dp,
    bottomPadding: Dp = 8.dp,
    large: Boolean = false,
) {
    Text(
        text = text.uppercase(),
        style = SectionLabel,
        fontSize = if (large) SectionLabelLargeSize else SectionLabel.fontSize,
        color = Muted,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding, start = 2.dp),
    )
}

/** Resting size of the two pinned headers; they fall back to [SectionLabel]'s 12sp on scroll. */
private val SectionLabelLargeSize = 14.sp

/**
 * The library's header and its three controls — shelve, series, sort — plus the view toggle.
 *
 * The prose summary that used to sit beneath is gone: the header carries the count, and the chips
 * already say what they are set to, so it was restating both in a full sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryControlBar(
    count: Int,
    searching: Boolean,
    sort: LibrarySort,
    shelving: LibraryShelving,
    series: LibrarySeriesMode,
    gridView: Boolean,
    /** Scrolled into the library: the header gives its extra size back. */
    collapsed: Boolean,
    onSortChange: (LibrarySort) -> Unit,
    onShelfChange: (LibraryShelving) -> Unit,
    onSeriesChange: (LibrarySeriesMode) -> Unit,
    onToggleView: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = 2.dp, bottom = 6.dp)) {
        SectionLabelRow(
            if (searching) {
                stringResource(R.string.home_section_results, count)
            } else {
                stringResource(R.string.home_section_library, count)
            },
            topPadding = 8.dp,
            bottomPadding = 4.dp,
            large = !collapsed,
        )
        // FlowRow, not a weighted Row. Equal weights cap every chip at a third of the width, so a
        // short one strands allowance a long one is truncating for. Here each takes the width it
        // needs and the row wraps only when they genuinely do not fit.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DropdownChip(
                label = stringResource(R.string.home_chip_shelve, shelving.label),
                options = LibraryShelving.values().toList(),
                selected = shelving,
                labelOf = { it.label },
                onSelect = onShelfChange,
            )
            DropdownChip(
                label = stringResource(R.string.home_chip_series, series.label),
                options = LibrarySeriesMode.values().toList(),
                selected = series,
                labelOf = { it.label },
                onSelect = onSeriesChange,
            )
            // Only the sorts that still do something — see LibrarySort.offeredFor.
            DropdownChip(
                label = stringResource(R.string.home_chip_sort, sort.label),
                options = LibrarySort.offeredFor(shelving),
                selected = sort,
                labelOf = { it.label },
                onSelect = onSortChange,
            )
            ViewToggleGroup(gridView = gridView, onToggleView = onToggleView)
        }
    }
}

/** Height of the toggle's visible pill — the same as a [DropdownChip]'s, which it sits beside. */
private val ViewTogglePillHeight = 28.dp

/**
 * Grid / list, drawn to the same height as the chips next to it.
 *
 * The outline is painted behind rather than applied with `Modifier.border`, because a real border
 * wraps the layout — and the layout has to stay 48dp tall to keep both halves tappable. Drawing it
 * lets the pill be chip-height while the tap targets stay full size, which is the same split
 * DropdownChip makes with its own 26dp pill inside a 48dp box.
 */
@Composable
private fun ViewToggleGroup(gridView: Boolean, onToggleView: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.drawBehind {
            val h = ViewTogglePillHeight.toPx()
            val top = (size.height - h) / 2f
            val stroke = 1.dp.toPx()
            val radius = CornerRadius(8.dp.toPx())
            drawRoundRect(Surface1, Offset(0f, top), Size(size.width, h), radius)
            drawRoundRect(
                color = Line,
                topLeft = Offset(stroke / 2f, top + stroke / 2f),
                size = Size(size.width - stroke, h - stroke),
                cornerRadius = radius,
                style = Stroke(stroke),
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewToggleButton(Icons.Filled.GridView, selected = gridView, desc = stringResource(R.string.home_cd_grid_view)) {
            onToggleView(true)
        }
        ViewToggleButton(Icons.AutoMirrored.Filled.ViewList, selected = !gridView, desc = stringResource(R.string.home_cd_list_view)) {
            onToggleView(false)
        }
    }
}

@Composable
private fun ViewToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    desc: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            // sizeIn, not size: the segment was 32×28dp, well under the 48dp minimum touch target.
            // The icon keeps its size; only the tappable segment grows.
            .sizeIn(minWidth = 44.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            // Confined to the pill, like the outline around it — filling the whole tap target
            // would put an amber block a head taller than the chips beside it.
            .drawBehind {
                if (!selected) return@drawBehind
                val h = ViewTogglePillHeight.toPx() - 2.dp.toPx()
                drawRoundRect(
                    color = AmberSoft,
                    topLeft = Offset(1.dp.toPx(), (size.height - h) / 2f),
                    size = Size(size.width - 2.dp.toPx(), h),
                    cornerRadius = CornerRadius(7.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = if (selected) Amber else Faint,
            modifier = Modifier.size(16.dp),
        )
    }
}


/** Books across the visible entries — the count while a search is narrowing the library. */
private fun List<LibraryEntry>.bookCount(): Int = sumOf { entry ->
    when (entry) {
        is LibraryEntry.Header -> 0
        is LibraryEntry.Standalone -> 1
        is LibraryEntry.Series -> entry.books.size
    }
}

// ── Continue shelf ─────────────────────────────────────────────────────────

/**
 * The Continue shelf, pinned above the library list. Shows full cover cards at rest and collapses
 * to a slim strip of rows once the library is scrolled, so it stays reachable without eating the
 * screen. [rowState] is hoisted by the caller so the horizontal position survives both the
 * collapse and scrolling the library.
 */
@Composable
private fun ContinuePinnedShelf(
    books: List<BookListItem>,
    collapsed: Boolean,
    rowState: LazyListState,
    onOpen: (String) -> Unit,
    actions: BookActions,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // The collapsed strip is sized against a real grid cover rather than a literal dp, so it
        // stays proportional on every screen width: half a cover, same 2:3 proportions.
        val slimCoverWidth = gridCellWidth(maxWidth) / ContinueSlimCoverFraction

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A flat, quiet surface. The wash moved down to the chrome band, where fading
                // into the page actually marks a boundary — here it was a gradient with nothing
                // underneath it to hand off to.
                .background(Surface1.copy(alpha = 0.5f))
                .animateContentSize(),
        ) {
            // Stays put when the strip collapses. It used to disappear, which left a row of bare
            // covers under the top bar with nothing saying what they were.
            Box(modifier = Modifier.padding(horizontal = LibraryGridPadding)) {
                SectionLabelRow(
                    stringResource(R.string.home_section_continue, books.size),
                    topPadding = if (collapsed) 8.dp else 12.dp,
                    bottomPadding = if (collapsed) 2.dp else 8.dp,
                    large = !collapsed,
                )
            }
            LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
                // Padding on the row (not the parent) so cards bleed off the edge while scrolling
                // instead of stopping at a hard margin.
                contentPadding = PaddingValues(
                    horizontal = LibraryGridPadding,
                    vertical = if (collapsed) 6.dp else 0.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(books, key = { "cont:${it.id}" }) { book ->
                    if (collapsed) {
                        ContinueSlim(book, coverWidth = slimCoverWidth, onOpen = onOpen)
                    } else {
                        ContinueCard(book, onOpen, actions)
                    }
                }
            }
        }
    }
}

/**
 * A book in the COLLAPSED Continue strip: just the cover, with its progress bar directly beneath.
 *
 * No title and no time left. The strip exists to be recognised and tapped mid-scroll, and at this
 * size a cover does that on its own — a title would only be a truncated echo of the one on the
 * card the strip collapsed from, and it forced the item into a wide row that fitted three books
 * across where this fits six.
 */
@Composable
private fun ContinueSlim(book: BookListItem, coverWidth: Dp, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(coverWidth)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpen(book.id) }
            // Nothing inside carries text any more, and the cover art is deliberately decorative,
            // so without this the strip is a row of unlabelled buttons to a screen reader.
            .semantics { contentDescription = book.title },
    ) {
        CoverArt(
            model = book.coverModel,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.5f)
                .clip(RoundedCornerShape(5.dp))
                // Same hairline the grid cards carry, so a cover reads as a cover everywhere.
                .border(1.dp, Line, RoundedCornerShape(5.dp)),
        )
        ProgressBar(
            fraction = book.progress ?: 0f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(book: BookListItem, onOpen: (String) -> Unit, actions: BookActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .width(132.dp)
                .combinedClickable(
                    onClick = { onOpen(book.id) },
                    onLongClick = { menuOpen = true },
                ),
        ) {
            CoverArt(
                model = book.coverModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.5f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Line, RoundedCornerShape(10.dp)),
            )
            ProgressBar(
                fraction = book.progress ?: 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp, bottom = 6.dp),
            )
            Text(
                book.title,
                color = Parchment,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val timeLeftMs = book.timeLeftMs
            val meta = when {
                timeLeftMs == null -> book.author ?: stringResource(R.string.unknown_author)
                timeLeftMs <= 0 -> stringResource(R.string.status_finished)
                else -> stringResource(R.string.time_left, formatCompactDuration(timeLeftMs))
            }
            Text(
                text = meta,
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        BookMenu(book, menuOpen, actions) { menuOpen = false }
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
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

// ── Grid card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridCard(
    book: BookListItem,
    ctx: RowContext,
    onOpen: (String) -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val showRing = book.progress?.let { it > 0.01f && it < 0.995f } == true && !book.finished

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (book.hidden) 0.5f else 1f)
            .combinedClickable(
                onClick = { onOpen(book.id) },
                onLongClick = { menuOpen = true },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.5f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Line, RoundedCornerShape(10.dp)),
        ) {
            CoverArt(model = book.coverModel, modifier = Modifier.fillMaxSize())
            if (book.isDownloaded) {
                DownloadBadge(modifier = Modifier.align(Alignment.TopEnd).padding(7.dp))
            }
            if (showRing) {
                ProgressRing(
                    progress = book.progress ?: 0f,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .size(24.dp),
                )
            }
        }
        Box {
            GridCardText(title = book.title, meta = bookMeta(book, ctx, LocalContext.current)) {
                menuOpen = true
            }
            BookMenu(book, menuOpen, actions) { menuOpen = false }
        }
    }
}

/**
 * Title (2 reserved lines) + meta (2 reserved lines) beside an overflow button — a fixed-height
 * block, so every grid card (book or series) is exactly the same total height and their bottoms
 * line up.
 *
 * The button is 32dp wide rather than the usual 48: a grid cell is only about 100dp across, and a
 * square target would take half of it away from the title. It is a full 48dp tall, the tap targets
 * either side of it are other cards 12dp away, and long-pressing the card still opens the same
 * menu — so the compromise is horizontal only and the affordance is reachable two ways.
 */
@Composable
private fun GridCardText(title: String, meta: String, onMenu: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Parchment,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                meta,
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 32.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more),
                tint = Faint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** What the arrangement already tells the reader, so a row can say something else instead. */
@Immutable
private data class RowContext(val shelving: LibraryShelving, val series: LibrarySeriesMode)

/**
 * The line under a book's title: the facts the current arrangement is NOT already showing.
 *
 * Shelved by author, the author is the section heading — repeating it on every row beneath is
 * noise, so the line carries the genre instead. Shelved by genre, the reverse. Shelved by item,
 * both. A series book loose in the list adds its episode number, which a stacked shelf conveys by
 * position and therefore omits. Length last, whenever it is known.
 *
 * Plain text rather than chips: the genre pill and the tag bubbles gave every row a different
 * height depending on whether that book happened to have either.
 */
private fun bookMeta(
    book: BookListItem,
    ctx: RowContext,
    context: android.content.Context,
    withStatus: Boolean = false,
): String = buildList {
    if (ctx.shelving != LibraryShelving.AUTHOR) {
        add(book.author ?: context.getString(R.string.unknown_author))
    }
    if (ctx.shelving != LibraryShelving.GENRE) book.genre?.let { add(it) }
    if (ctx.series == LibrarySeriesMode.FLAT && book.series != null && book.seriesIndex != null) {
        add(context.getString(R.string.home_meta_episode, book.seriesIndex))
    }
    book.totalDurationMs?.takeIf { it > 0 }?.let { add(formatCompactDuration(it)) }
    addAll(book.tags)
    if (withStatus) {
        when {
            book.finished -> add(context.getString(R.string.status_finished))
            // Gated on `started`, not merely on progress being computable: marking a book
            // completed RESETS it to position 0, which is measurable, so progress comes out 0f
            // rather than null and the row went on advertising "0%" for a book just cleared.
            book.started && book.progress != null ->
                add(context.getString(R.string.home_meta_percent_bare, (book.progress * 100).toInt()))
        }
    }
}.joinToString(" · ")

// The stack fans diagonally up-right and is scaled so the whole stack fills the cell — the
// same footprint as a single book card. STACK_SPREAD is the fraction of the cell the fan
// spans; the covers are (1 - STACK_SPREAD) of the cell in each dimension.
private const val STACK_SPREAD = 0.18f

/** A series as a grid cell the same size as a book card: a diagonal stack filling the cell. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesGridCard(
    series: LibraryEntry.Series,
    onOpen: () -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.5f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Line, RoundedCornerShape(10.dp)),
        ) {
            val covers = series.books.take(3)
            val steps = (covers.size - 1).coerceAtLeast(1)
            // Inset the stack so its covers never touch the card's outline.
            val pad = 6.dp
            val availW = maxWidth - pad * 2
            val availH = maxHeight - pad * 2
            val coverW = availW * (1f - STACK_SPREAD)
            val coverH = coverW * 1.5f
            val dx = (availW - coverW) / steps
            val dy = (availH - coverH) / steps
            // Deepest drawn first; the front book (depth 0) sits bottom-left, the rest fan
            // up-right. Each cover gets a background-coloured outline so overlapping sheets
            // read as separate books.
            for (depth in covers.indices.reversed()) {
                val book = covers[depth]
                CoverArt(
                    model = book.coverModel,
                    modifier = Modifier
                        .offset(x = pad + dx * depth, y = pad + dy * (steps - depth))
                        .width(coverW)
                        .aspectRatio(1f / 1.5f)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.5.dp, Ground, RoundedCornerShape(9.dp)),
                )
            }
        }
        Box {
            GridCardText(title = series.name, meta = seriesCardMeta(series, LocalContext.current)) {
                menuOpen = true
            }
            SeriesMenu(series, menuOpen, actions) { menuOpen = false }
        }
    }
}

private fun seriesCardMeta(series: LibraryEntry.Series, context: android.content.Context): String =
    // The grid card reserves two lines, so the length gets one of its own rather than trailing the
    // episode count as it does on the narrower shelf row.
    buildString {
        append(context.getString(R.string.home_series_episodes, series.books.size))
        val downloaded = series.books.count { it.isDownloaded }
        if (downloaded > 0) append(context.getString(R.string.home_series_offline_suffix, downloaded))
        seriesTotalMs(series)?.let { append('\n'); append(formatCompactDuration(it)) }
    }

// ── Expanded series enclosure ─────────────────────────────────────────────────
//
// An opened series reads as ONE faint-bordered card with a clear start and end. It used to be
// exactly that — a single grid item wrapping a header and a 3-up grid of episodes — but that
// composed every episode at once, so a 100-book series hitched on open. It is now emitted as a
// run of separate lazy items that each draw their slice of the same enclosure, so the container
// is back and only what's on screen composes.

/** Corner radius of the enclosure's two caps. */
private val SeriesEnclosureRadius = 12.dp

/** Inset between the enclosure's border and the header/cards inside it, in grid view. */
private val SeriesEnclosurePad = 12.dp

/**
 * The same inset in list view. Matches [SeriesShelfRow]'s own padding, so expanding a shelf slips
 * a border around it without nudging its contents sideways.
 */
private val SeriesListEnclosurePad = 10.dp

/**
 * Half the grid's `verticalArrangement` spacing. Each slice paints this far into the gaps above
 * and below it, so the side rails meet across the gap instead of the enclosure looking like a
 * stack of separate boxes. Keep it at half of [LibraryGridSpacing].
 */
private val SeriesEnclosureBleed = LibraryGridSpacing / 2

/**
 * Draws one slice of the enclosure that wraps an opened series.
 *
 * The trick is to draw the WHOLE rounded rectangle oversized — running past this slice's top
 * and/or bottom edge for any slice that isn't the cap — and then clip back to the slice plus its
 * half-gap. What survives inside the clip is just the two side rails; the rounded caps and the
 * horizontal rules fall outside it. Stack the slices and they read as one continuous container.
 *
 * Drawn rather than composed because a lazy grid gives no way to paint behind a run of items:
 * anything spanning them would have to be a single item, which is the composition cost this
 * replaced.
 */
private fun Modifier.seriesEnclosure(top: Boolean, bottom: Boolean): Modifier = this.drawBehind {
    val radius = SeriesEnclosureRadius.toPx()
    val stroke = 1.dp.toPx()
    val bleed = SeriesEnclosureBleed.toPx()
    // What stays visible: this slice, plus the half-gap on any open side.
    val clipTop = if (top) 0f else -bleed
    val clipBottom = if (bottom) size.height else size.height + bleed
    // Where the rectangle itself runs to: inset by half a stroke on a capped side so the border
    // lands fully inside, pushed a full corner radius past the clip on an open one so neither the
    // rounding nor the horizontal rule can show up mid-shelf.
    val rectTop = if (top) stroke / 2f else clipTop - radius
    val rectBottom = if (bottom) size.height - stroke / 2f else clipBottom + radius
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(stroke / 2f, rectTop, size.width - stroke / 2f, rectBottom),
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
    clipRect(top = clipTop, bottom = clipBottom) {
        drawPath(path, Surface1)
        drawPath(path, Line, style = Stroke(stroke))
    }
}

/**
 * Top slice of an opened series: the title banner, tapped to collapse. No bottom padding — the
 * grid's own 12dp item gap separates it from the first row of episodes, and the enclosure paints
 * straight through that gap.
 */
@Composable
private fun ExpandedSeriesHeader(
    series: LibraryEntry.Series,
    onCollapse: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .seriesEnclosure(top = true, bottom = false)
            // Clip AFTER the enclosure so the ripple is bounded by the rounded top without also
            // clipping away the bleed the enclosure draws into the gap below.
            .clip(RoundedCornerShape(topStart = SeriesEnclosureRadius, topEnd = SeriesEnclosureRadius))
            .clickable(onClick = onCollapse)
            .padding(start = SeriesEnclosurePad, end = SeriesEnclosurePad, top = SeriesEnclosurePad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(series.name, style = SerifTitle, color = Parchment, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(seriesMeta(series, LocalContext.current), color = Muted, fontSize = 11.5.sp)
        }
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.home_cd_collapse_series), tint = Amber)
    }
}

/**
 * One row of episodes inside the enclosure. Episodes are emitted a row at a time rather than as
 * individual grid cells because the enclosure has to be drawn by the items themselves — and a row
 * is still lazy, so at most [LibraryGridColumns] cards compose at once instead of the whole series.
 */
@Composable
private fun ExpandedSeriesRow(
    books: List<BookListItem>,
    last: Boolean,
    ctx: RowContext,
    onOpen: (String) -> Unit,
    actions: BookActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .seriesEnclosure(top = false, bottom = last)
            .padding(
                start = SeriesEnclosurePad,
                end = SeriesEnclosurePad,
                bottom = if (last) SeriesEnclosurePad else 0.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
    ) {
        books.forEach { book ->
            Box(modifier = Modifier.weight(1f)) {
                BookGridCard(book, ctx, onOpen = onOpen, actions = actions)
            }
        }
        // Hold the last row's cards to the same width as a full row's.
        repeat(LibraryGridColumns - books.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

private fun seriesMeta(series: LibraryEntry.Series, context: android.content.Context): String = buildString {
    append(context.getString(R.string.home_series_episodes, series.books.size))
    // Whole-series length, on the shelf row as well as the grid card — a book row shows its own
    // length, so a shelf that hid the sum was the one place the number went missing.
    seriesTotalMs(series)?.let { append(" · "); append(formatCompactDuration(it)) }
    val downloaded = series.books.count { it.isDownloaded }
    if (downloaded > 0) append(context.getString(R.string.home_series_offline_suffix, downloaded))
}

/** A series' length, but only once EVERY episode is measured — a partial sum understates it. */
private fun seriesTotalMs(series: LibraryEntry.Series): Long? {
    val measured = series.books.mapNotNull { it.totalDurationMs?.takeIf { d -> d > 0 } }
    return if (measured.size == series.books.size && measured.isNotEmpty()) measured.sum() else null
}

// ── List row ─────────────────────────────────────────────────────────────────

@Composable
private fun BookListRow(
    book: BookListItem,
    startPadding: Dp,
    ctx: RowContext,
    onOpen: (String) -> Unit,
    actions: BookActions,
    /**
     * Whether the row draws its own card. True for a top-level row, so every item in the list
     * reads the same way a collapsed series shelf always did. False for an episode inside an
     * opened series, where the enclosure already IS the card and a second border inside it would
     * just be noise.
     */
    bordered: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .then(
                if (bordered) {
                    Modifier
                        .clip(RoundedCornerShape(SeriesEnclosureRadius))
                        .border(1.dp, Line, RoundedCornerShape(SeriesEnclosureRadius))
                        .background(Surface1)
                } else {
                    Modifier
                },
            )
            .clickable { onOpen(book.id) }
            .padding(if (bordered) SeriesListEnclosurePad else 6.dp)
            .alpha(if (book.hidden) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CoverArt(
                model = book.coverModel,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (book.isDownloaded) {
                DownloadBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(0.dp)
                        .size(16.dp),
                    iconSize = 9.dp,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                book.title,
                color = Parchment,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
                // Two reserved lines: at one line, books whose names share a long prefix were
                // indistinguishable. minLines matches maxLines so every row stays the same height.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = bookMeta(book, ctx, LocalContext.current, withStatus = true),
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more), tint = Faint)
            }
            BookMenu(book, menuOpen, actions) { menuOpen = false }
        }
    }
}


// ── Series shelf row ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesShelfRow(
    series: LibraryEntry.Series,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expanded) {
                    // Top slice of the enclosure that carries on down over the episodes. Clipped
                    // after it, so the ripple is bounded by the rounded top without cutting off the
                    // bleed the enclosure paints into the gap below.
                    Modifier
                        .seriesEnclosure(top = true, bottom = false)
                        .clip(
                            RoundedCornerShape(
                                topStart = SeriesEnclosureRadius,
                                topEnd = SeriesEnclosureRadius,
                            ),
                        )
                } else {
                    Modifier
                        .clip(RoundedCornerShape(SeriesEnclosureRadius))
                        .border(1.dp, Line, RoundedCornerShape(SeriesEnclosureRadius))
                        .background(Surface1)
                },
            )
            .combinedClickable(onClick = onToggle, onLongClick = { menuOpen = true })
            .padding(
                start = SeriesListEnclosurePad,
                end = SeriesListEnclosurePad,
                top = SeriesListEnclosurePad,
                // Open, the grid's item gap supplies the separation and the enclosure paints
                // through it — a bottom inset here would double it.
                bottom = if (expanded) 0.dp else SeriesListEnclosurePad,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Stacked-covers glyph (up to three, fanned rightward; last drawn sits on top).
        Box(modifier = Modifier.size(width = 52.dp, height = 56.dp)) {
            series.books.take(3).forEachIndexed { i, book ->
                CoverArt(
                    model = book.coverModel,
                    modifier = Modifier
                        .offset(x = (i * 6).dp)
                        .size(width = 38.dp, height = 52.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                series.name,
                style = SerifTitle,
                color = Parchment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = seriesMeta(series, LocalContext.current),
                color = Muted,
                fontSize = 11.5.sp,
            )
        }
        // Chevron immediately left of the overflow button, so the two sit together at the trailing
        // edge and the overflow still lines up with the one on every book row. Leading it instead
        // pushed the covers out of line with the book rows above and below.
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
            tint = Faint,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more), tint = Faint)
            }
            SeriesMenu(series, menuOpen, actions) { menuOpen = false }
        }
    }
}

// ── Status badge, progress ring ───────────────────────────────────────────────

@Composable
private fun DownloadBadge(modifier: Modifier = Modifier, iconSize: Dp = 11.dp) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Studio.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative: the badge sits inside a card/row that TalkBack already reads out, and an
        // extra "Downloaded" node in the middle of it only interrupts the title.
        Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = Sage, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Studio.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp).padding(5.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(
                color = Parchment.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = Amber,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

// ── Cover art (title-forward placeholder) ─────────────────────────────────────

@Composable
private fun CoverArt(model: Any?, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (model != null) {
            CoverImage(model = model, modifier = Modifier.fillMaxSize())
        } else {
            // No art: a clear, deliberate placeholder (the real title shows on the card/row itself,
            // so it isn't crammed in here). Scales with the tile, so it works in grid and list.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Surface2, Surface1))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    // Decorative, exactly like the real-cover case: announcing the title only when
                    // art happened to be missing made a row read differently for no reason.
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.fillMaxSize(0.34f),
                )
            }
        }
    }
}

// ── Context menu ─────────────────────────────────────────────────────────────

@Composable
private fun BookMenu(
    book: BookListItem,
    expanded: Boolean,
    actions: BookActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // No "Play" item: it was the first and most prominent entry in every menu and did nothing
        // but close the menu. Tapping the card itself opens the book.
        if (book.started) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mark_completed)) },
                leadingIcon = { Icon(Icons.Filled.Check, null, tint = Muted) },
                onClick = { actions.onMarkCompleted(book.id); onDismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) }, // hide/unhide lives in the edit dialog
            leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Muted) },
            onClick = { actions.onEdit(book); onDismiss() },
        )

        HorizontalDivider()

        // Offline sits at the bottom of every menu: on = keep offline, off = remove/abort. While a
        // download is in flight the trailing control is a spinner so the toggle clearly did something.
        val offlineEnabled = book.downloadStatus != null
        val downloading = DownloadStatus.isActive(book.downloadStatus)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_offline)) },
            leadingIcon = { Icon(Icons.Filled.Download, null, tint = if (book.isDownloaded) Sage else Muted) },
            trailingIcon = {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Amber, strokeWidth = 2.dp)
                } else {
                    HomerSwitch(
                        checked = offlineEnabled,
                        onCheckedChange = {
                            if (offlineEnabled) actions.onRemove(book.id) else actions.onDownload(book.id)
                            onDismiss()
                        },
                    )
                }
            },
            onClick = {
                if (offlineEnabled) actions.onRemove(book.id) else actions.onDownload(book.id)
                onDismiss()
            },
        )
        when (book.downloadStatus) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_pause_download)) },
                onClick = { actions.onPause(book.id); onDismiss() },
            )
            DownloadStatus.PAUSED -> DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_resume_download)) },
                onClick = { actions.onResume(book.id); onDismiss() },
            )
            DownloadStatus.FAILED -> DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_retry_download)) },
                onClick = { actions.onResume(book.id); onDismiss() },
            )
        }
    }
}

/**
 * The series counterpart of [BookMenu]: rename the series, or take the whole thing offline.
 *
 * The offline switch reads as on once ANY episode is downloading or downloaded, matching how a
 * book's own switch reads its single download row — a half-downloaded series is "offline, still
 * working" rather than a third state. Flipping it on downloads every episode that isn't already
 * queued; flipping it off drops all of them.
 */
@Composable
private fun SeriesMenu(
    series: LibraryEntry.Series,
    expanded: Boolean,
    actions: BookActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Muted) },
            onClick = { actions.onEditSeries(series); onDismiss() },
        )

        HorizontalDivider()

        val offlineEnabled = series.books.any { it.downloadStatus != null }
        val downloading = series.books.any { DownloadStatus.isActive(it.downloadStatus) }
        val allDownloaded = series.books.all { it.isDownloaded }
        val toggle = {
            if (offlineEnabled) actions.onRemoveSeries(series) else actions.onDownloadSeries(series)
            onDismiss()
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_offline_series)) },
            leadingIcon = {
                Icon(Icons.Filled.Download, null, tint = if (allDownloaded) Sage else Muted)
            },
            trailingIcon = {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Amber, strokeWidth = 2.dp)
                } else {
                    HomerSwitch(checked = offlineEnabled, onCheckedChange = { toggle() })
                }
            },
            onClick = toggle,
        )
        // Downloading one episode of a series makes the switch above read as on, so without this
        // the only thing the menu offered a part-downloaded series was "delete what you have" —
        // which is also what a user reaching for "finish the rest" would have tapped. Covers the
        // failed-episode case too: BookMenu has an explicit Retry item and this is its equivalent.
        if (offlineEnabled && !allDownloaded && !downloading) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_offline_series_rest)) },
                leadingIcon = { Icon(Icons.Filled.Download, null, tint = Muted) },
                onClick = { actions.onDownloadSeries(series); onDismiss() },
            )
        }
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

/**
 * Brief discovery phase shown while the library is being read from the database (or scanned), so
 * the empty-shelf screen never flashes on a launch that actually has books.
 */
@Composable
private fun LibraryLoading(scanState: ScanState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Amber)
        Spacer(Modifier.height(16.dp))
        val label = when (val s = scanState) {
            is ScanState.Scanning -> stringResource(R.string.home_scanning_progress, s.directoriesVisited, s.booksFound)
            else -> stringResource(R.string.home_opening_library)
        }
        Text(label, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun EmptyResults(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_no_matches), style = SerifTitle, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_no_matches_hint), color = Muted, fontSize = 13.sp)
    }
}

/** The shelf really is empty: no scanning branch here — [LibraryLoading] owns that state. */
@Composable
private fun EmptyLibrary(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_empty_title), style = SerifTitle, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_hint),
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.home_empty_open_settings)) }
    }
}

/** Edits the series-level fields (name + author) and pushes them to every book in the series. */
@Composable
private fun SeriesEditDialog(
    series: LibraryEntry.Series,
    onSave: (name: String, author: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberSaveable, like the book dialog: a rotation used to throw away what was typed.
    var name by rememberSaveable { mutableStateOf(series.name) }
    var author by rememberSaveable { mutableStateOf(series.author.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_series_edit_title)) },
        text = {
            // Scrollable: a dialog resizes for the keyboard, and without this the second field
            // gets crushed or clipped once the IME is up (EditBookDialog already does this).
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.home_series_edit_desc, series.books.size),
                    color = Muted,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.edit_field_series)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.edit_field_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, author) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
