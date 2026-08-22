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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.geozelot.homer.ui.components.TagChip
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
    val groupMode by viewModel.groupMode.collectAsStateWithLifecycle()
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
            Column(modifier = Modifier.padding(horizontal = LibraryGridPadding)) {
                LibraryHeader(
                    label = if (searching) {
                        stringResource(R.string.home_section_results)
                    } else {
                        stringResource(R.string.home_section_library, bookCount)
                    },
                    gridView = gridView,
                    onToggleView = viewModel::setGridView,
                )
                SortGroupBar(sortMode, groupMode, viewModel::setSortMode, viewModel::setGroupMode)
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
                        BookGridCard(entry.book, onOpen = onBookClick, actions = actions)
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }, key = entry.book.id) {
                        BookListRow(entry.book, startPadding = 0.dp, onOpen = onBookClick, actions = actions)
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
                                onOpen = onBookClick,
                                actions = actions,
                            )
                        }
                    } else {
                        item(key = shelfKey) {
                            SeriesGridCard(
                                series = entry,
                                onOpen = { open(entry) },
                                onEdit = { actions.onEditSeries(entry) },
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
                            onEdit = { actions.onEditSeries(entry) },
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
                                BookListRow(book, startPadding = 2.dp, onOpen = onBookClick, actions = actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabelRow(text: String) {
    Text(
        text = text.uppercase(),
        style = SectionLabel,
        color = Muted,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 2.dp),
    )
}

@Composable
private fun LibraryHeader(label: String, gridView: Boolean, onToggleView: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = SectionLabel, color = Muted)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Line, RoundedCornerShape(8.dp))
                .background(Surface1),
        ) {
            ViewToggleButton(Icons.Filled.GridView, selected = gridView, desc = stringResource(R.string.home_cd_grid_view)) {
                onToggleView(true)
            }
            ViewToggleButton(Icons.AutoMirrored.Filled.ViewList, selected = !gridView, desc = stringResource(R.string.home_cd_list_view)) {
                onToggleView(false)
            }
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
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .background(if (selected) AmberSoft else Color.Transparent)
            .clickable(onClick = onClick),
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

@Composable
private fun SortGroupBar(
    sort: LibrarySort,
    group: LibraryGroup,
    onSortChange: (LibrarySort) -> Unit,
    onGroupChange: (LibraryGroup) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, start = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownChip(
                label = stringResource(R.string.home_sort_group_label, group.label),
                options = LibraryGroup.values().toList(),
                selected = group,
                labelOf = { it.label },
                onSelect = onGroupChange,
            )
            DropdownChip(
                label = stringResource(R.string.home_sort_sort_label, sort.label),
                options = LibrarySort.values().toList(),
                selected = sort,
                labelOf = { it.label },
                onSelect = onSortChange,
            )
        }
        Text(
            text = arrangementSummary(group, sort, context),
            color = Faint,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}

/** Plain-language description of the active grouping + sort, e.g. "Grouped by author · sorted by title". */
private fun arrangementSummary(group: LibraryGroup, sort: LibrarySort, context: android.content.Context): String {
    val sortLabel = sort.label.lowercase()
    return when (group) {
        LibraryGroup.NONE -> context.getString(R.string.home_arrange_all, sortLabel)
        LibraryGroup.SERIES -> context.getString(R.string.home_arrange_series, sortLabel)
        else -> context.getString(R.string.home_arrange_grouped, group.label.lowercase(), sortLabel)
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

        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            if (!collapsed) {
                Box(modifier = Modifier.padding(horizontal = LibraryGridPadding)) {
                    SectionLabelRow(stringResource(R.string.home_section_continue))
                }
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
            // A hairline separates the pinned strip from the library scrolling beneath it.
            if (collapsed) HorizontalDivider(color = Line)
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
            .clickable { onOpen(book.id) },
    ) {
        CoverArt(
            model = book.coverModel,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.5f)
                .clip(RoundedCornerShape(5.dp)),
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
                    .clip(RoundedCornerShape(10.dp)),
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
private fun BookGridCard(book: BookListItem, onOpen: (String) -> Unit, actions: BookActions) {
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
            // Genre pill overlaid bottom-start (clear of the TopEnd badge and BottomEnd ring), so
            // it surfaces on grid cards without disturbing their fixed-height text block. Tags stay
            // a list-row feature (too many to read as overlays).
            book.genre?.let {
                TagChip(
                    it,
                    Amber,
                    AmberSoft,
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                )
            }
            BookMenu(book, menuOpen, actions) { menuOpen = false }
        }
        GridCardText(title = book.title, meta = bookCardMeta(book, LocalContext.current))
    }
}

/** Title (2 reserved lines) + meta (2 reserved lines) — a fixed-height block so every grid
 *  card (book or series) is exactly the same total height and their bottoms line up. */
@Composable
private fun GridCardText(title: String, meta: String) {
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

private fun bookCardMeta(book: BookListItem, context: android.content.Context): String = buildString {
    append(book.author ?: context.getString(R.string.unknown_author))
    book.totalDurationMs?.takeIf { it > 0 }?.let { append('\n'); append(formatCompactDuration(it)) }
}

// The stack fans diagonally up-right and is scaled so the whole stack fills the cell — the
// same footprint as a single book card. STACK_SPREAD is the fraction of the cell the fan
// spans; the covers are (1 - STACK_SPREAD) of the cell in each dimension.
private const val STACK_SPREAD = 0.18f

/** A series as a grid cell the same size as a book card: a diagonal stack filling the cell. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesGridCard(series: LibraryEntry.Series, onOpen: () -> Unit, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onEdit),
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
        GridCardText(title = series.name, meta = seriesCardMeta(series, LocalContext.current))
    }
}

private fun seriesCardMeta(series: LibraryEntry.Series, context: android.content.Context): String = buildString {
    append(seriesMeta(series, context))
    // Whole-series length as a second line, once every episode is measured.
    val measured = series.books.mapNotNull { it.totalDurationMs?.takeIf { d -> d > 0 } }
    if (measured.size == series.books.size && measured.isNotEmpty()) {
        append('\n'); append(formatCompactDuration(measured.sum()))
    }
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
                BookGridCard(book, onOpen = onOpen, actions = actions)
            }
        }
        // Hold the last row's cards to the same width as a full row's.
        repeat(LibraryGridColumns - books.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

private fun seriesMeta(series: LibraryEntry.Series, context: android.content.Context): String = buildString {
    append(context.getString(R.string.home_series_episodes, series.books.size))
    val downloaded = series.books.count { it.isDownloaded }
    if (downloaded > 0) append(context.getString(R.string.home_series_offline_suffix, downloaded))
}

// ── List row ─────────────────────────────────────────────────────────────────

@Composable
private fun BookListRow(
    book: BookListItem,
    startPadding: Dp,
    onOpen: (String) -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(book.id) }
            .padding(start = startPadding)
            .padding(vertical = 6.dp)
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
                text = listRowMeta(book, LocalContext.current),
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            // Offline is shown by the DownloadBadge on the cover; the chips carry genre + tags only.
            if (book.genre != null || book.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    book.genre?.let { TagChip(it, Amber, AmberSoft) }
                    book.tags.take(3).forEach { TagChip(it, Muted, Surface2) }
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more), tint = Faint)
            }
            BookMenu(book, menuOpen, actions) { menuOpen = false }
        }
    }
}

private fun listRowMeta(book: BookListItem, context: android.content.Context): String = buildString {
    append(book.author ?: context.getString(R.string.unknown_author))
    book.totalDurationMs?.takeIf { it > 0 }?.let { append(" · ${formatCompactDuration(it)}") }
    when {
        book.finished -> { append(" · "); append(context.getString(R.string.status_finished)) }
        book.progress != null -> append(context.getString(R.string.home_meta_percent, (book.progress * 100).toInt()))
    }
}

// ── Series shelf row ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesShelfRow(
    series: LibraryEntry.Series,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
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
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
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
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
            tint = Faint,
        )
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
