package com.geozelot.homer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.library.IndexPass
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.metadata.BookLanguage
import com.geozelot.homer.data.storage.StorageMigrator
import com.geozelot.homer.data.sync.facet.IndexActivity
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.components.ControlPillHeight
import com.geozelot.homer.ui.components.DropdownChip
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.components.EditableBook
import com.geozelot.homer.ui.components.HomerSwitch
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.MiniPlayer
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.rememberTextWidth
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberDeep
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
import com.geozelot.homer.ui.theme.Surface0
import com.geozelot.homer.ui.theme.Surface1
import com.geozelot.homer.ui.theme.Surface2

@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onBookClickAt: (String, Long) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val libraryLoaded by viewModel.libraryLoaded.collectAsStateWithLifecycle()
    val listeningShelf by viewModel.listeningShelf.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val gridView by viewModel.gridView.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val shelfMode by viewModel.shelfMode.collectAsStateWithLifecycle()
    val seriesMode by viewModel.seriesMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val flatCollections by viewModel.flatCollections.collectAsStateWithLifecycle()
    val filterCount by viewModel.filterCount.collectAsStateWithLifecycle()
    val indexActivity by viewModel.indexActivity.collectAsStateWithLifecycle()
    val librarySetup by viewModel.librarySetup.collectAsStateWithLifecycle()
    val indexQueued by viewModel.indexQueued.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    // A device reading an index somebody else keeps: it never crawls, so an empty shelf here means
    // "not published yet", not "no audiobooks found".
    val readsSharedIndex by viewModel.readsSharedIndex.collectAsStateWithLifecycle()
    val maintainsLibrary by viewModel.maintainsLibrary.collectAsStateWithLifecycle()
    val libraryIsShare by viewModel.libraryIsShare.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val miniPlayerBook by viewModel.miniPlayerBook.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Dialog targets are ids, not row snapshots. A snapshot went stale the moment the dialog wrote
    // through the ViewModel — picking a cover left the button saying "Choose cover" until the dialog
    // was reopened — and being a plain `remember` it also closed the dialog on every rotation. The
    // live row is re-derived from `entries` below on each recomposition.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarksId by rememberSaveable { mutableStateOf<String?>(null) }
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

    // Closing the box KEEPS what was typed, as a chip.
    //
    // It used to drop it, which made leaving the field the one way to lose a query — and the only
    // ways out of search are leaving the field, so a word survived exactly as long as the keyboard
    // was up. Every exit now commits first: back, the arrow, and a tap on the library all mean "I am
    // done typing", not "forget that". Throwing it away has its own control, the X, which is the one
    // thing on the row that says so.
    //
    // Committed pills survive it too, as they always did: they have their own row and their own
    // Clear, and undoing them on a gesture meaning "put the keyboard away" would undo visible work.
    BackHandler(enabled = searching) {
        viewModel.commitSearchText()
        viewModel.setSearchQuery("")
        searching = false
    }

    val actions = remember(viewModel) {
        BookActions(
            onDownload = viewModel::download,
            onRemove = viewModel::deleteDownload,
            onEdit = { editingId = it.id },
            onDetails = { detailsId = it.id },
            onDetailsSeries = { detailsSeriesKey = it.expandKey },
            onBookmarks = { bookmarksId = it.id },
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

    // With the field open, the rest of the library takes one tap to CLOSE it and does nothing else.
    // That tap is consumed, so tapping a book dismisses the search rather than dismissing it and
    // opening the book at once — which read as two things happening for one deliberate action, and
    // left you in a player you had not asked for.
    //
    // The whole gesture is swallowed, not just its first touch: a tap that turns into a drag would
    // otherwise scroll a list it had just been refused permission to tap.
    //
    // Keyed on `Unit` with the flag read through `rememberUpdatedState`, and the flag cleared only
    // AFTER the gesture is drained. Keyed on `searching`, clearing it mid-gesture tore this block
    // down and the remaining move events reached the list anyway.
    val searchingNow by rememberUpdatedState(searching)
    val dismissSearch = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Not searching: leave the event entirely alone, so every ordinary tap behaves.
            if (!searchingNow) return@awaitEachGesture
            down.consume()
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                pressed = event.changes.any { it.pressed }
            }
            // Same rule as the back gesture: a tap on the library ends the typing, it does not
            // discard it. See the BackHandler above.
            viewModel.commitSearchText()
            viewModel.setSearchQuery("")
            searching = false
        }
    }

    // Every rule about when this panel folds lives in ListeningFold, with tests. The first attempt
    // at this read correctly, compiled, and did not work on a device — which is the argument for
    // taking it out of the callback and making it a thing that can be asserted on.
    val fold = rememberSaveable(saver = ListeningFold.Saver) { ListeningFold() }
    val pullToExpandPx = with(LocalDensity.current) { ListeningPullToExpand.toPx() }
    val listeningScroll = remember(gridState, fold, pullToExpandPx) {
        object : NestedScrollConnection {
            private fun atTop() =
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0

            // onPRE, so the delta is the raw pointer movement — before the grid, and before the
            // stretch overscroll that also sits in this chain, have taken anything out of it. The
            // version that read the leftover in onPostScroll is the one that did nothing on a
            // device. Nothing is consumed here; this only watches.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                fold.onScroll(
                    deltaY = available.y,
                    atTop = atTop(),
                    fromUser = source == NestedScrollSource.Drag,
                    threshold = pullToExpandPx,
                )
                return Offset.Zero
            }
        }
    }
    // Every time the box opens, not just the first.
    LaunchedEffect(searching) { if (searching) fold.onSearchOpened() }

    Column(modifier = modifier.fillMaxSize().nestedScroll(listeningScroll)) {
        // The wordmark and settings only. Search moved down to the control bar, where the rest of
        // the controls for the list already live — it acts on the library, not on the app.
        Box(modifier = dismissSearch) { TopBar(onSettings = onOpenSettings) }

        // The Currently-listening shelf is pinned here — above the scrolling library rather than
        // being its first item — and is now ONE fixed size whatever the library does beneath it; see
        // ListeningShelf for why the collapse went. Its LazyRow state is hoisted so the horizontal
        // scroll position survives scrolling the library and is not reset by the item being disposed.
        val shelfRowState = rememberLazyListState()
        // Expanded until something says otherwise, and only ever folded BY something — see
        // ListeningShelf. rememberSaveable so a rotation does not silently unfold it again.
        // The shelf STAYS while search is open. Hiding it was meant to give the results more room
        // and instead undid the whole point of moving search into the control bar: dropping it and
        // its divider out of this Column pulled everything below UP by the panel's height, so
        // opening search hoisted the library header to where the listening panel had been. The
        // control bar never moved a pixel by itself — the thing above it vanished. A control that
        // relocates the screen to appear is the one thing the inline field was built to stop being.
        //
        // `filtering` and not `entries.isNotEmpty()` for the same reason: a query that matches
        // nothing emptied `entries`, which tore down the shelf AND the control bar below it — so
        // typing one character too many closed the field being typed into, took the pills and their
        // Clear with it, and left "No matches" with no way back to the library but the back gesture.
        // The bar is the way out of a filter and has to outlive the filter finding nothing.
        val libraryPresent = entries.isNotEmpty() || !filter.isEmpty
        if (listeningShelf.isNotEmpty() && libraryPresent) {
            // Closes the top bar off from the panel below it — without it the wordmark row and the
            // listening shelf ran together as one undifferentiated block.
            HorizontalDivider(color = Line.copy(alpha = 0.45f))
            ListeningShelf(
                books = listeningShelf,
                expanded = fold.expanded,
                onExpand = fold::onPanelTapped,
                rowState = shelfRowState,
                onOpen = onBookClick,
                actions = actions,
                modifier = dismissSearch,
            )
        }

        // Sort, group and the grid/list toggle are pinned here rather than scrolled away as the
        // grid's first two items: they are the controls for what is being scrolled, so having to
        // scroll back to the top to reach them was the wrong way round. The listening strip above
        // collapses to make room for them; these stay put.
        if (libraryPresent) {
            // "Filtered" now means either half of the box is doing something: text being typed, OR
            // a committed pill. Keyed on the open field alone it labelled an untouched library "309
            // results" before a character was typed; keyed on the text alone it went on claiming
            // the full count while a pill above it said "41 of 313" — two numbers on one screen
            // disagreeing about the list between them.
            val filtering = !filter.isEmpty
            // The library's header and controls are pinned chrome, not part of the list they act
            // on. The wash runs dark to light down the band, so it lifts away from the listening
            // panel above and is at its brightest along the edge where the list begins. It starts
            // on Surface0 — the same flat tone the listening panel carries — so the two pinned
            // regions share a floor and only the band rises off it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Surface0, Surface1))),
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
                    tokens = filter.tokens,
                    shown = filterCount.first,
                    total = filterCount.second,
                    query = searchQuery,
                    searchOpen = searching,
                    onQueryChange = viewModel::setSearchQuery,
                    onOpenSearch = { searching = true },
                    onCloseSearch = {
                        viewModel.commitSearchText()
                        viewModel.setSearchQuery("")
                        searching = false
                    },
                    onRemoveToken = viewModel::removeFilterToken,
                    onClearFilter = viewModel::clearFilter,
                    onCommitQuery = viewModel::commitSearchText,
                    suggestions = suggestions,
                    onPickSuggestion = { viewModel.addFilterToken(FilterToken(it.facet, it.value)) },
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
                !libraryLoaded || scanState is ScanState.Scanning || indexActivity != IndexActivity.IDLE ->
                    LibraryLoading(
                        scanState = scanState,
                        indexActivity = indexActivity,
                        modifier = Modifier.weight(1f),
                    )
                // Any active filter, pills included. Keyed on the typed text alone, a pill
                // combination that matched nothing fell through to the SETUP panel and told the
                // reader their shelf was empty and to try a different folder — the third time that
                // wrong empty state has turned up, and this time reachable in two taps.
                !filter.isEmpty -> EmptyResults(modifier = Modifier.weight(1f))
                // Not "your shelf is empty" any more: on a first run this is where the library
                // gets found, chosen or named. Only a scanned-and-genuinely-empty library still
                // reads as empty, which is the one case where it is true.
                else ->
                    LibrarySetupPanel(
                        setup = librarySetup,
                        onAdopt = viewModel::adopt,
                        onNameFolder = viewModel::setupNameFolder,
                        isShare = libraryIsShare,
                        scanPending = IndexPass.BOOKS in indexQueued,
                        wifiOnly = wifiOnlyDownloads,
                        readsOnly = readsSharedIndex,
                        modifier = Modifier.weight(1f),
                    )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (gridView) LibraryGridColumns else 1),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(dismissSearch),
                contentPadding = PaddingValues(
                    start = LibraryGridPadding, end = LibraryGridPadding, top = 4.dp, bottom = 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
                verticalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
            ) {
                libraryContent(
                    entries = entries,
                    gridView = gridView,
                    flatCollections = flatCollections,
                    onCollectionOrder = viewModel::setCollectionFlat,
                    ctx = RowContext(shelfMode, seriesMode, mixedLanguages = languages.size > 1),
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
            onSave = { title, author, series, index, collection, collectionIndex, genre, language, tags, hidden, downloadOnPlay ->
                viewModel.saveOverride(
                    book.id, title, author, series, index, collection, collectionIndex,
                    genre, language, tags, hidden, downloadOnPlay,
                )
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

    // Details re-derives from the live list like the edit dialogs, so an edit made from inside it
    // is reflected the moment it lands rather than on the next open.
    entries.findBook(detailsId)?.let { book ->
        BookDetailsCard(
            book = book,
            onEdit = { detailsId = null; editingId = book.id },
            onFilter = { detailsId = null; searching = false; viewModel.addFilterToken(it) },
            onReadFolderDifferently = if (maintainsLibrary) {
                { detailsId = null; viewModel.seedTemplateFor(book.id); onOpenTemplates() }
            } else {
                null
            },
            onDismiss = { detailsId = null },
        )
    }
    entries.findSeries(detailsSeriesKey)?.let { series ->
        SeriesDetailsCard(
            series = series,
            onEdit = { detailsSeriesKey = null; editingSeriesKey = series.expandKey },
            onFilter = { detailsSeriesKey = null; searching = false; viewModel.addFilterToken(it) },
            onReadFolderDifferently = if (maintainsLibrary) {
                {
                    detailsSeriesKey = null
                    // The shape is read from a member book, the scope from what they all share.
                    viewModel.seedTemplateFor(
                        bookId = series.books.first().id,
                        scopeOverride = series.commonFolder(),
                    )
                    onOpenTemplates()
                }
            } else {
                null
            },
            onDismiss = { detailsSeriesKey = null },
        )
    }
    entries.findBook(bookmarksId)?.let { book ->
        val marks by viewModel.bookmarksFor(book.id).collectAsStateWithLifecycle(emptyList())
        LibraryBookmarksDialog(
            book = book,
            bookmarks = marks,
            onOpenAt = { ms -> bookmarksId = null; onBookClickAt(book.id, ms) },
            onDelete = viewModel::deleteBookmark,
            onDismiss = { bookmarksId = null },
        )
    }

    entries.findSeries(editingSeriesKey)?.let { series ->
        ShelfEditDialog(
            series = series,
            onSave = { name, author, genre, collection ->
                viewModel.saveShelfOverride(
                    bookIds = series.books.map { it.id },
                    name = name,
                    author = author,
                    genre = genre,
                    // The card knows what it drew. A collection card's name field names the
                    // COLLECTION, and writing it to `series` is what used to flatten the threads.
                    namesCollection = series.isCollection,
                    collection = collection,
                )
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
    collection = collection,
    collectionIndex = collectionIndex,
    genres = genres,
    language = language,
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
    val onDetails: (BookListItem) -> Unit,
    val onDetailsSeries: (LibraryEntry.Series) -> Unit,
    val onBookmarks: (BookListItem) -> Unit,
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
private fun TopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        run {
            Wordmark(stringResource(R.string.app_name))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
 * Width of one grid cell at [totalWidth] — the LazyVerticalGrid's own arithmetic, factored out so
 * the collapsed listening strip can size itself against a real cover instead of guessing at a
 * literal dp that drifts the moment the grid's padding or column count changes.
 */
private fun gridCellWidth(totalWidth: Dp): Dp =
    ((totalWidth - LibraryGridPadding * 2 - LibraryGridSpacing * (LibraryGridColumns - 1)) / LibraryGridColumns)
        .coerceAtLeast(0.dp)

private fun LazyGridScope.libraryContent(
    entries: List<LibraryEntry>,
    gridView: Boolean,
    /** Collections the reader has asked to see as one numbered run — see [LibrarySettings]. */
    flatCollections: Set<String>,
    onCollectionOrder: (collection: String, flat: Boolean) -> Unit,
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

    // Neither the Currently-listening shelf nor the library's own header and sort/group bar are items here:
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
            ) { SectionLabelRow(headerLabel(entry)) }
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
                // Only a collection has two readings, and only one with threads AND numbers has a
                // choice worth offering — see CollectionOrderChip.
                val flat = entry.isCollection && entry.name in flatCollections
                // The books of a collection read flat are numbered by the collection, so that is
                // what their corners must show, even for one that also sits in a thread.
                val shelfCtx = if (flat) ctx.copy(collectionNumbered = true) else ctx
                if (gridView) {
                    if (shelfOpen) {
                        // A header banner, then the episodes a row at a time — every one of them a
                        // separate lazy item drawing its own slice of the enclosure that wraps the
                        // whole shelf. See `seriesEnclosure`.
                        item(span = { GridItemSpan(maxLineSpan) }, key = "series-open:$shelfKey") {
                            ExpandedSeriesHeader(
                                series = entry,
                                ctx = ctx,
                                flat = flat,
                                onOrderChange = { onCollectionOrder(entry.name, it) },
                                onCollapse = { close(entry) },
                            )
                        }
                        // An opened COLLECTION breaks into its threads; an opened plain series is
                        // one run, exactly as before. See `expandedRows`.
                        val rows = entry.expandedRows(LibraryGridColumns, flat = flat)
                        itemsIndexed(
                            rows,
                            span = { _, _ -> GridItemSpan(maxLineSpan) },
                            // First id in the row: unique across the library (a book sits in one
                            // series) and stable while the row's membership holds. A sub-heading
                            // keys on its own label, which is unique within the shelf.
                            key = { _, row ->
                                when (row) {
                                    is ShelfRow.SubHeader -> "sesub:$shelfKey:${row.label}"
                                    ShelfRow.LooseHeader -> "seloose:$shelfKey"
                                    is ShelfRow.Books -> "sep:${row.books.first().id}"
                                }
                            },
                        ) { index, row ->
                            val last = index == rows.lastIndex
                            when (row) {
                                is ShelfRow.SubHeader -> ExpandedSubHeader(row.label, last = last)
                                ShelfRow.LooseHeader ->
                                    ExpandedSubHeader(stringResource(R.string.home_shelf_loose), last = last)
                                is ShelfRow.Books -> ExpandedSeriesRow(
                                    books = row.books,
                                    last = last,
                                    ctx = shelfCtx,
                                    onOpen = onBookClick,
                                    actions = actions,
                                )
                            }
                        }
                    } else {
                        item(key = shelfKey) {
                            SeriesGridCard(
                                series = entry,
                                ctx = ctx,
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
                            ctx = ctx,
                            expanded = shelfOpen,
                            flat = flat,
                            onOrderChange = { onCollectionOrder(entry.name, it) },
                            onToggle = { if (shelfOpen) close(entry) else open(entry) },
                            actions = actions,
                        )
                    }
                    if (shelfOpen) {
                        // One book per row here, so the same split produces one Books row each and
                        // the sub-headings land between the threads.
                        val listRows = entry.expandedRows(columns = 1, flat = flat)
                        itemsIndexed(
                            listRows,
                            span = { _, _ -> GridItemSpan(maxLineSpan) },
                            key = { _, row ->
                                when (row) {
                                    is ShelfRow.SubHeader -> "epsub:$shelfKey:${row.label}"
                                    ShelfRow.LooseHeader -> "eploose:$shelfKey"
                                    is ShelfRow.Books -> "ep:${row.books.first().id}"
                                }
                            },
                        ) { index, row ->
                            val last = index == listRows.lastIndex
                            if (row is ShelfRow.SubHeader) {
                                ExpandedSubHeader(row.label, last = last)
                                return@itemsIndexed
                            }
                            if (row is ShelfRow.LooseHeader) {
                                ExpandedSubHeader(stringResource(R.string.home_shelf_loose), last = last)
                                return@itemsIndexed
                            }
                            val book = (row as ShelfRow.Books).books.first()
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
                                    ctx = shelfCtx,
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
/**
 * A shelf heading's words, resolved at DRAW time.
 *
 * Three sources, in order: a string resource for the "no author"/"no genre" fallbacks, a language
 * name for the language shelving, and otherwise the key itself (an author, a genre). None of them
 * may be baked into the entry — the ViewModel that builds the list survives the activity recreation
 * a language change causes, so a heading resolved at build time would still be in the old language.
 */
@Composable
private fun headerLabel(entry: LibraryEntry.Header): String = when {
    entry.titleRes != null -> stringResource(entry.titleRes)
    entry.languageCode != null ->
        BookLanguage.displayName(entry.languageCode, LocalConfiguration.current.locales[0])
    else -> entry.title
}

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
@Composable
private fun LibraryControlBar(
    count: Int,
    searching: Boolean,
    sort: LibrarySort,
    shelving: LibraryShelving,
    series: LibraryDepth,
    gridView: Boolean,
    /** Committed filters, drawn between the header and the chips. */
    tokens: List<FilterToken>,
    shown: Int,
    total: Int,
    query: String,
    searchOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onRemoveToken: (FilterToken) -> Unit,
    onClearFilter: () -> Unit,
    /** Committed by the keyboard's own action key — see [InlineSearchField]. */
    onCommitQuery: () -> Unit,
    suggestions: List<FilterSuggestion>,
    onPickSuggestion: (FilterSuggestion) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onShelfChange: (LibraryShelving) -> Unit,
    onSeriesChange: (LibraryDepth) -> Unit,
    onToggleView: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = 2.dp, bottom = 6.dp)) {
        SectionLabelRow(
            // Always "Library". It titles the same region whatever is filtered, and renaming it to
            // "Results" made the shelf look like a different place rather than the same one with
            // less on it. The COUNT carries that instead.
            if (searching) {
                stringResource(R.string.home_section_library_filtered, shown, total)
            } else {
                stringResource(R.string.home_section_library, count)
            },
            topPadding = 8.dp,
            bottomPadding = 4.dp,
            // Stays large while scrolling. Unlike the listening panel, this header is not inside
            // anything that collapses — it titles the list being scrolled, so it holds its size.
            large = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Open, the field REPLACES the chips in place — same row, same height, no back arrow.
            // It used to be a full OutlinedTextField, half again as tall as the row it sat in, so
            // opening search shunted the whole library down the screen and closing it shunted it
            // back. A control that moves everything else to appear is a control you brace for.
            if (searchOpen) {
                InlineSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClose = onCloseSearch,
                    onCommit = onCommitQuery,
                    modifier = Modifier.weight(1f),
                )
                return@Row
            }
            // Drawn as a chip like its neighbours, but filled rather than outlined: it is the only
            // control here that changes what the list CONTAINS — the others only rearrange it — and
            // the fill is what says so at a glance. Amber once anything is filtered.
            SearchChip(active = tokens.isNotEmpty(), onClick = onOpenSearch)
            // ONE control where there were three.
            //
            // Shelve, depth and sort are genuinely orthogonal — sections, grouping, order — so their
            // menus cannot be merged without multiplying: four shelvings times three depths is
            // twelve entries to pick one thing from. What CAN be merged is the chrome. The three
            // dropdowns spent a third of a phone's width standing open at all times to show three
            // values that change rarely, on a row that also has to hold search and the view toggle.
            // They are one chip and a sheet now, and the row stopped needing to scroll at all.
            //
            // The cost, stated because it is real: the current arrangement is no longer legible at a
            // glance and takes a tap to see. That is the trade — the row is for reaching things, the
            // sheet is for reading them.
            var arranging by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                ArrangeChip(onClick = { arranging = true })
            }
            if (arranging) {
                ArrangeSheet(
                    sort = sort,
                    shelving = shelving,
                    series = series,
                    onSortChange = onSortChange,
                    onShelfChange = onShelfChange,
                    onSeriesChange = onSeriesChange,
                    onDismiss = { arranging = false },
                )
            }
            ViewToggleGroup(gridView = gridView, onToggleView = onToggleView)
        }
        // Offers first, then what has been taken. The suggestion row sits directly under the field
        // that produces it and the committed pills sit under THAT, so the three lines read top to
        // bottom as one thought: what you are typing, what it could become, what it already is.
        if (searchOpen) {
            FilterSuggestions(suggestions = suggestions, onPick = onPickSuggestion)
        }
        // BELOW the controls, not above them. Above, they pushed the chips away from the header
        // every time one was added; below, the bar keeps its place and the pills grow into the gap
        // before the list.
        FilterPills(
            tokens = tokens,
            shown = shown,
            total = total,
            onRemove = onRemoveToken,
            onClear = onClearFilter,
        )
    }
}

/**
 * The one band a run of adjacent controls is drawn inside.
 *
 * PAINTED behind rather than applied with `Modifier.border`, because a real border wraps the layout
 * — and the layout has to stay 48dp tall to keep every segment tappable. Drawing it lets the band be
 * chip-height while the tap targets stay full size, which is the same split [DropdownChip] makes
 * with its own pill inside a 48dp box.
 *
 * Shared by the view toggle and by the shelve/depth/sort chips, so "these three belong together" is
 * said the same way in both places and cannot drift into two slightly different pills.
 */
private fun Modifier.controlGroupPill(): Modifier = drawBehind {
    val h = ControlPillHeight.toPx()
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
}

/** The one control that opens [ArrangeSheet]. Shows no value: the sheet is where values are read. */
@Composable
private fun ArrangeChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp, minWidth = 44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .height(ControlPillHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(R.string.home_chip_arrange),
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * Everything about how the library is arranged, in one place and labelled.
 *
 * Each row names its axis, which the chips could not: in German the shelving's "no sections" and the
 * depth's "every book loose" are one word apart from each other by accident, and two glyph-and-value
 * chips side by side gave a reader nothing to tell them apart with. A row that says "Shelve" before
 * its value cannot have that problem.
 *
 * Changes apply as they are made — there is no Save, because there is nothing here to get wrong and
 * nothing that needs confirming. The dialog closes when the reader is done looking.
 */
@Composable
private fun ArrangeSheet(
    sort: LibrarySort,
    shelving: LibraryShelving,
    series: LibraryDepth,
    onSortChange: (LibrarySort) -> Unit,
    onShelfChange: (LibraryShelving) -> Unit,
    onSeriesChange: (LibraryDepth) -> Unit,
    onDismiss: () -> Unit,
) {
    // Resolved through the context because `labelOf` is a plain lambda, not a composable.
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_chip_arrange)) },
        text = {
            Column {
                SettingsRow(label = stringResource(R.string.arrange_shelve)) {
                    DropdownChip(
                        label = stringResource(shelving.label),
                        options = LibraryShelving.values().toList(),
                        selected = shelving,
                        labelOf = { context.getString(it.label) },
                        onSelect = onShelfChange,
                    )
                }
                SettingsRow(label = stringResource(R.string.arrange_group)) {
                    DropdownChip(
                        label = stringResource(series.label),
                        options = LibraryDepth.entries.toList(),
                        selected = series,
                        labelOf = { context.getString(it.label) },
                        onSelect = onSeriesChange,
                    )
                }
                // Only the sorts that still do something — see LibrarySort.offeredFor.
                SettingsRow(label = stringResource(R.string.arrange_sort)) {
                    DropdownChip(
                        label = stringResource(sort.label),
                        options = LibrarySort.offeredFor(shelving),
                        selected = sort,
                        labelOf = { context.getString(it.label) },
                        onSelect = onSortChange,
                    )
                }
            }
        },
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * The search control, drawn as a filled chip.
 *
 * Filled where the chips beside it are outlined, because it does a different KIND of thing: the
 * others choose how the list is arranged, this one chooses what is in it. Same pill height as every
 * other control on the row, so the bar is one consistent band rather than a line of mismatched
 * boxes.
 */
@Composable
private fun SearchChip(active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            // Chip-height pill inside a full-height tap target, the same split DropdownChip makes.
            .sizeIn(minHeight = 48.dp, minWidth = 44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(ControlPillHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) AmberSoft else Surface2)
                // Amber whether or not anything is filtered. The hairline every other control
                // wears said "one more of these" about the one control on the row that changes
                // what the list CONTAINS; the accent is what the rest of the app uses to mean
                // live, and this is the chip worth finding without looking for it.
                .border(1.dp, Amber, RoundedCornerShape(8.dp))
                // Wider than a glyph needs. It is the control that opens the box, so it gets a
                // little more presence than the chips that merely rearrange the list.
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(R.string.home_cd_search),
                // Muted, the same as the glyphs in the chips beside it. Parchment is the primary
                // TEXT tone and read as plain white next to them, which made the icon the loudest
                // thing on the row while the border around it was the quietest.
                tint = if (active) Amber else Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The search field, sized and shaped as one of the chips it replaces.
 *
 * A [BasicTextField] rather than an OutlinedTextField: the Material field carries its own label
 * slot, its own 56dp minimum and its own padding, none of which fit inside a 28dp pill. What is
 * wanted here is a chip somebody can type into.
 *
 * A back arrow leads, where the magnifier used to. The magnifier was decoration: the field is
 * plainly a field, it was opened from a magnifier one tap ago, and it spent the one slot at the head
 * of the pill saying what the reader had just done rather than offering them a way out. Closing had
 * been left to the X, a tap on the library, or the back gesture — none of them visible.
 *
 * Which frees the X to mean the one thing an X in a text field means everywhere else: clear the
 * text. It no longer has to double as the close button, so it is drawn only when there is something
 * to clear, and its label is simply true.
 */
@Composable
private fun InlineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        modifier = modifier.sizeIn(minHeight = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ControlPillHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2)
                .border(1.dp, AmberDeep, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Holds the arrow's width open, the same way the trailing spacer does for the X.
            Spacer(modifier = Modifier.width(LeadingActionWidth))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.home_search_placeholder),
                        color = Faint,
                        fontSize = 12.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Parchment, fontSize = 12.sp),
                    cursorBrush = SolidColor(Amber),
                    // The key that was doing nothing. On a single-line field the IME shows an action
                    // in place of a newline, and it went unhandled — so pressing it dismissed the
                    // keyboard and threw the query's momentum away. It keeps the words instead.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onCommit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            // Holds the X's width open so no typed text can end up underneath its tap target —
            // otherwise tapping the last word of a query would clear it instead of placing a cursor.
            Spacer(modifier = Modifier.width(TrailingActionWidth))
        }
        // Both targets sit OUTSIDE the 28dp pill so they can be real ones. The glyphs belong inside
        // it, but a 16dp clickable is not a control anybody can hit; the row is 48dp and only the
        // pill is short, so each target takes the row's full height with its glyph centred in it.
        // Exactly the split DropdownChip makes: chip-height paint, full-height touch.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = LeadingActionWidth, height = 48.dp)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.home_cd_close_search),
                tint = Muted,
                modifier = Modifier.size(16.dp),
            )
        }
        // Only when there is something to clear. An X on an empty field is a control that either
        // does nothing or does something else — and "something else" is what it used to do.
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = TrailingActionWidth, height = 48.dp)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * The arrow's slot at the head of the search pill, and the X's at its tail.
 *
 * Both 44dp, the width the view toggle's own segments settled on. The trailing one is reserved even
 * while the X is hidden, so the text does not reflow the moment a first character is typed.
 */
private val LeadingActionWidth = 44.dp
private val TrailingActionWidth = 44.dp

/**
 * How an opened collection reads: as its threads, or as one numbered run.
 *
 * Deliberately the DEPTH control's own glyph and its own two words. This is not a new idea to learn
 * — "series or flat" is exactly what the depth chip in the control bar asks of the whole library,
 * and this asks it of one collection. Same question, smaller scope, so it should not look like a
 * different question.
 *
 * A toggle rather than a menu: there are two answers, and a dropdown to choose between two is a menu
 * where a switch would do.
 *
 * Drawn only where it would change something. A collection whose books are in no sub-series has one
 * reading, and offering a choice between it and itself is worse than offering nothing.
 */
@Composable
private fun CollectionOrderChip(flat: Boolean, onChange: (Boolean) -> Unit) {
    // The collection's OWN name for itself, not "flat". Read as one run this shelf is being treated
    // as the collection it is, and "flat" describes what happened to the threads rather than what
    // the reader gets — which is the whole collection, in its own order.
    val asCollection = stringResource(R.string.depth_collection)
    val asSeries = stringResource(R.string.depth_series)
    // One width for both labels, so the chip does not resize under the finger that just tapped it —
    // and so the chevron beside it does not shift every time this is used.
    val labelWidth = rememberTextWidth(
        listOf(asCollection, asSeries),
        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
    )
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp, minWidth = 44.dp)
            .clickable { onChange(!flat) },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(ControlPillHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Filled.Layers,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                if (flat) asCollection else asSeries,
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                modifier = Modifier.width(labelWidth),
            )
        }
    }
}

/** Whether threading this shelf would actually separate anything. */
private fun LibraryEntry.Series.hasThreads(): Boolean =
    isCollection && books.any { it.series != null } && books.mapNotNull { it.series }.distinct().size +
        (if (books.any { it.series == null }) 1 else 0) > 1

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
        modifier = Modifier.controlGroupPill(),
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
                val h = ControlPillHeight.toPx() - 2.dp.toPx()
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
private fun ListeningShelf(
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
private val ListeningPullToExpand = 64.dp

/** The folded cover, at exactly the size [BookListRow] draws its own. */
private val ListeningFoldedCover = 46.dp

/**
 * How much smaller than a grid cover a listening item is.
 *
 * **This is the one number to turn if the panel wants to be taller or shorter.** The collapsed strip
 * used 2.5; this is a little above it, per the brief, and the panel's whole height follows from it
 * because the cover is 2:3 and everything else on the item is a fixed line of text.
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
                    .aspectRatio(1f / 1.5f)
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
    val showProgress = book.hasVisibleProgress()

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
            // Top-left: where this book sits in the thing it belongs to.
            //
            // The SERIES number when it has one, and the collection's otherwise — the sub-series is
            // the more specific claim, and a book in both would say two different numbers about
            // itself in one corner. Which of the two it is goes unsaid: the shelf the book is
            // sitting on is the context that answers it.
            VolumeIndexBadge(
                seriesIndex = if (ctx.collectionNumbered) null else book.seriesIndex,
                collectionIndex = book.collectionIndex,
                modifier = Modifier.align(Alignment.TopStart),
                size = BadgeSize.LARGE,
            )
            if (book.isDownloaded) {
                OfflineBadge(
                    CoverCorner.TOP_END,
                    modifier = Modifier.align(Alignment.TopEnd),
                    size = BadgeSize.LARGE,
                )
            }
            // The length, where the progress ring used to be. A ring said the same thing the bar
            // below the cover now says, and said it in a corner that could hold a fact the card
            // had nowhere else to put.
            // MEDIUM while the two above are LARGE, deliberately: this one is text with no glyph
            // to make legible, and it is the corner a reader consults least.
            book.totalDurationMs?.let {
                DurationBadge(formatCompactDuration(it), modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
        // Under the cover rather than on it, exactly as the listening strip has always drawn it —
        // so "how far in am I" looks the same wherever a book appears.
        if (showProgress) {
            ProgressBar(
                fraction = book.progress ?: 0f,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            )
        }
        Box {
            GridCardText(
                title = book.title,
                meta = bookMeta(book, ctx, LocalContext.current, withDuration = false, withIndex = false),
            ) {
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
    // IntrinsicSize.Min so the overflow button can match the footer instead of guessing at it. A
    // Row is as tall as its tallest child, which makes `fillMaxHeight` on one of them circular;
    // measuring the row's intrinsic height first breaks the cycle and gives the button something
    // to fill.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
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
        // The full height of the footer, not a 48dp square floating at the top of it. Two lines of
        // title and two of meta make this panel taller than the button was, so the dots sat against
        // the title with dead strip beneath them — and the part of the footer that looked like it
        // should open the menu did nothing.
        //
        // The BUTTON spans the footer; the glyph stays a glyph.
        //
        // Drawing the three dots across the full height was a literal reading of "stretch" and it
        // looked like a control with its parts pulled apart — at 58dp the outer two sat a centimetre
        // from the middle one and stopped reading as one mark. What the footer actually needed was a
        // tap target the height of the panel and an icon in proportion to it, so this is Material's
        // own glyph a size up, centred in the full-height target.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .sizeIn(minWidth = 32.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more),
                tint = Faint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** What the arrangement already tells the reader, so a row can say something else instead. */
@Immutable
private data class RowContext(
    val shelving: LibraryShelving,
    val series: LibraryDepth,
    /** Whether the library holds more than one language — see [bookMeta]. */
    val mixedLanguages: Boolean = false,
    /**
     * Whether the shelf these books are sitting on is counting the COLLECTION rather than the series.
     *
     * Set only for the books inside a collection being read as one numbered run. The corner shows
     * one number and the shelf is the context that says which — so when the shelf is the collection,
     * the number has to be the collection's, even for a book that also belongs to a thread.
     */
    val collectionNumbered: Boolean = false,
)

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
    /**
     * False where the cover already carries the length — the grid card, whose bottom-right corner
     * now says it. Repeating it two lines below would spend one of the meta line's two lines
     * restating what is on screen an inch above.
     */
    withDuration: Boolean = true,
    /**
     * True in LIST view, where the cover no longer carries a corner badge for it. False on the grid
     * card, whose cover does — saying it in both places would put the same fact twice on one card.
     */
    withOffline: Boolean = false,
    /**
     * False on the grid card, whose cover corner now carries the number. Same rule as
     * [withDuration]: whatever a corner says, the text beside it stops saying.
     */
    withIndex: Boolean = true,
): String = buildList {
    // The length leads. It is the one number a reader scans a list FOR — "have I got an hour for
    // this" — and it was last, after the author and the genre they can already see from the shelf
    // they are standing on.
    if (withDuration) book.totalDurationMs?.takeIf { it > 0 }?.let { add(formatCompactDuration(it)) }
    // Then whether this device HAS it, which is the other half of "can I listen to this now" and so
    // belongs beside the length rather than after the tags. Same slot in seriesMeta — see there.
    if (withOffline && book.isDownloaded) add(context.getString(R.string.details_offline))
    if (ctx.shelving != LibraryShelving.AUTHOR) {
        add(book.author ?: context.getString(R.string.unknown_author))
    }
    // Every genre it carries, not only the one it shelves under — a book that is Fantasy AND Humour
    // says more about itself in the same amount of space.
    if (ctx.shelving != LibraryShelving.GENRE && book.genres.isNotEmpty()) {
        add(book.genres.joinToString(" · "))
    }
    // Only where it distinguishes something: on a single-language library this is the same two
    // letters on every row, and shelved BY language the heading above already says it.
    if (ctx.mixedLanguages && ctx.shelving != LibraryShelving.LANGUAGE) {
        book.language?.let { add(BookLanguage.shortLabel(it)) }
    }
    if (withIndex && ctx.series == LibraryDepth.FLAT && book.series != null && book.seriesIndex != null) {
        add(context.getString(R.string.home_meta_series_position, book.seriesIndex))
    }
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

// ── the stack ────────────────────────────────────────────────────────────────
//
// One cover anchored to the bottom-left, with the EDGES of two more leaning back and to the right.
//
// It used to fan three full covers diagonally across the cell, which cost the front book a third of
// its width and a third of its height to show two others at an angle you could not read anyway.
// Then it inset the front cover from all four sides and fanned upward from there, which left the
// artwork floating in the middle of the cell with slack on every edge — about 40dp of it along the
// top, because a 2:3 cover inside a 2:3 cell cannot also give up room for a stack.
//
// Anchoring the cover to one corner collects that slack into a single band along the top and right,
// and the sheets and the badges are exactly what goes in it. Nothing is left over.
//
// THE COVER IS THEREFORE NOT 2:3. It fills the cell's height and takes whatever width the stack
// leaves, which comes out near 1:1.79 — so a wide cover loses about 16% off its sides. It was being
// cropped to 2:3 before, so this crops LESS on the square artwork most audiobooks ship with and
// slightly more on tall artwork. That is the trade, made once, here.
//
// The sheets are not cover art. They are blank stock, which is honest (they stand in for no
// particular volume) and cheap (no second and third image load per series cell, on a screen that may
// hold twenty of them). They are filled with `Line` and edged in `Studio` — a pale slab with a
// near-black cut, rather than the dark slab with a pale cut they were, which at this size read as
// texture on the card instead of as separate sheets. The front cover takes the same dark edge, so
// all three are divided by the same line.

/** How far each sheet behind the front cover peeks out to the right. */
private val StackStepX = 13.dp

/** …and how far it rises. Under a third of the horizontal step: a lean, not a diagonal. */
private val StackStepY = 4.dp

/** At most two sheets. A third is a hairline at this step and reads as a rendering artefact. */
private const val STACK_MAX_HINTS = 2

/** Inset, so the stack never touches the card's own outline. */
private val StackPad = 5.dp

/** The cut between sheets, and around the front cover. */
private val StackEdge = 1.5.dp

/**
 * The room the stack takes whether or not it draws anything in it.
 *
 * Reserved unconditionally so every series card in the grid crops its artwork IDENTICALLY: a
 * two-volume series draws one sheet in the same footprint an eight-volume one uses for two. Sizing
 * the cover to the sheets actually drawn would make the crop a function of how many books you happen
 * to own, and a row of series cards would each show its cover at a different width.
 */
private val StackReservedX = StackStepX * STACK_MAX_HINTS
private val StackReservedY = StackStepY * STACK_MAX_HINTS

// The same stack at row scale. Separate constants rather than a scale factor: 46dp is not a scaled
// 255dp, it is the size a book row's cover already is, and the steps have to be legible against a
// cover a fifth of the width rather than proportional to one.

/** A shelf row's whole stack footprint — a book row's cover exactly, so the two line up. */
private val ShelfRowBox = 46.dp
private val RowStackStepX = 4.dp
private val RowStackStepY = 1.5.dp
private val RowStackReservedY = RowStackStepY * STACK_MAX_HINTS
private val RowStackEdge = 1.dp
private val ShelfRowCoverW = ShelfRowBox - RowStackStepX * STACK_MAX_HINTS
private val ShelfRowCoverH = ShelfRowBox - RowStackReservedY

/** A series as a grid cell the same size as a book card: one cover with a pile leaning behind it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesGridCard(
    series: LibraryEntry.Series,
    ctx: RowContext,
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
            // Derived from the declared aspect ratio rather than read from `maxHeight`: the cell IS
            // 1:1.5 by construction, so this is exact and cannot come back unbounded.
            val cellH = maxWidth * 1.5f
            val coverW = maxWidth - StackPad * 2 - StackReservedX
            val coverH = cellH - StackPad * 2 - StackReservedY
            val hints = (series.books.size - 1).coerceIn(0, STACK_MAX_HINTS)
            // The front cover stands on the bottom inset; the reserved rise is all above it.
            val frontY = StackPad + StackReservedY

            // Deepest first, so each sheet is overdrawn by the one in front of it.
            for (i in hints downTo 1) {
                Box(
                    modifier = Modifier
                        .offset(x = StackPad + StackStepX * i, y = frontY - StackStepY * i)
                        .size(width = coverW, height = coverH)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Line)
                        .border(StackEdge, Studio, RoundedCornerShape(9.dp)),
                )
            }
            // The badges sit on the FRONT COVER, not on the cell, so they hug the artwork's own
            // corners rather than floating in the band the stack leans into.
            Box(
                modifier = Modifier
                    .offset(x = StackPad, y = frontY)
                    .size(width = coverW, height = coverH)
                    .clip(RoundedCornerShape(9.dp))
                    .border(StackEdge, Studio, RoundedCornerShape(9.dp)),
            ) {
                CoverArt(model = series.frontCover(), modifier = Modifier.fillMaxSize())
                ShelfBadge(
                    count = series.books.size,
                    modifier = Modifier.align(Alignment.TopStart),
                    size = BadgeSize.LARGE,
                )
                // The count comes along only when it is not all of them: "12 of 12 downloaded" is
                // said better by the icon alone.
                val downloaded = series.books.count { it.isDownloaded }
                if (downloaded > 0) {
                    OfflineBadge(
                        CoverCorner.TOP_END,
                        count = downloaded.takeIf { it < series.books.size }?.toString(),
                        modifier = Modifier.align(Alignment.TopEnd),
                        size = BadgeSize.LARGE,
                    )
                }
                seriesTotalMs(series)?.let {
                    DurationBadge(formatCompactDuration(it), modifier = Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        Box {
            GridCardText(title = series.name, meta = seriesCardMeta(series, ctx, LocalContext.current)) {
                menuOpen = true
            }
            SeriesMenu(series, menuOpen, actions) { menuOpen = false }
        }
    }
}

/**
 * The line under a stacked shelf's cover.
 *
 * The volume count, the downloaded count and now the length are all drawn on the cover itself, so
 * the only thing left for the text is the one fact a corner cannot hold: who wrote it.
 *
 * Which means that shelved BY author there is nothing left to say — and saying it anyway is what
 * [bookMeta] has always refused to do one row below. A shelf carried the author under every cover
 * while the section heading above already named them, so the two views disagreed about the same
 * rule. Empty is correct here: the card's meta line is `minLines = 2` either way, so nothing moves.
 */
private fun seriesCardMeta(
    series: LibraryEntry.Series,
    ctx: RowContext,
    context: android.content.Context,
): String =
    if (ctx.shelving == LibraryShelving.AUTHOR) {
        ""
    } else {
        series.author ?: context.getString(R.string.unknown_author)
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
    ctx: RowContext,
    flat: Boolean,
    onOrderChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .seriesEnclosure(top = true, bottom = false)
            // Clip AFTER the enclosure so the ripple is bounded by the rounded top without also
            // clipping away the bleed the enclosure draws into the gap below.
            .clip(RoundedCornerShape(topStart = SeriesEnclosureRadius, topEnd = SeriesEnclosureRadius))
            .clickable(onClick = onCollapse),
    ) {
        Row(
            modifier = Modifier.padding(
                start = SeriesEnclosurePad,
                end = SeriesEnclosurePad,
                top = SeriesEnclosurePad,
                bottom = SeriesEnclosurePad,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    series.name,
                    // Same face and size as the list view's shelf row, so a series title reads
                    // identically whichever view it is in. The serif stays for the empty states,
                    // which are the only headings on this screen that aren't a row of something.
                    color = Parchment,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                seriesMeta(series, ctx, LocalContext.current).takeIf { it.isNotEmpty() }?.let {
                    Text(it, color = Muted, fontSize = 11.5.sp)
                }
            }
            if (series.hasThreads()) {
                CollectionOrderChip(flat = flat, onChange = onOrderChange)
            }
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.home_cd_collapse_series), tint = Amber)
        }
        // Separates the shelf's own title from its episodes. Inset from the enclosure's side rails
        // so it reads as a rule inside the card rather than a second edge of it.
        HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = SeriesEnclosurePad))
    }
}

/**
 * One row of episodes inside the enclosure. Episodes are emitted a row at a time rather than as
 * individual grid cells because the enclosure has to be drawn by the items themselves — and a row
 * is still lazy, so at most [LibraryGridColumns] cards compose at once instead of the whole series.
 */
/**
 * A sub-series heading inside an opened collection.
 *
 * Draws its own slice of the same enclosure the books around it draw, so the shelf stays one
 * bordered card rather than breaking into pieces at every heading. Quieter and smaller than a shelf
 * heading in the library proper — it labels a thread inside a card that is already titled, and at
 * the same weight the two would compete.
 */
@Composable
private fun ExpandedSubHeader(label: String, last: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .seriesEnclosure(top = false, bottom = last)
            .padding(horizontal = SeriesListEnclosurePad)
            .padding(bottom = if (last) SeriesListEnclosurePad else 0.dp),
    ) {
        Text(
            label.uppercase(),
            style = SectionLabel,
            color = Faint,
            modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 4.dp),
        )
    }
}

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

/**
 * The line beside a shelf row.
 *
 * The volume count and the downloaded count moved onto the cover, so what is left is the author and
 * the length — the two facts that need words. Restating the counts here as well would put the same
 * number twice on one row, two centimetres apart.
 */
private fun seriesMeta(
    series: LibraryEntry.Series,
    ctx: RowContext,
    context: android.content.Context,
): String = buildList {
    // Same running order as a book row — length, then what this device has of it, then who wrote it
    // — so a shelf and a book sitting next to each other in the same list read the same way round.
    // The author used to lead here and trail there, which is the sort of thing nobody can name but
    // everybody has to re-read.
    //
    // The first two came off the cover: at 38x52 the corner badges were a smudge. See SeriesShelfRow.
    seriesTotalMs(series)?.let { add(formatCompactDuration(it)) }
    add(
        context.resources.getQuantityString(
            R.plurals.home_series_book_count,
            series.books.size,
            series.books.size,
        ),
    )
    val downloaded = series.books.count { it.isDownloaded }
    when {
        downloaded == 0 -> Unit
        // "Offline" unqualified only when the WHOLE shelf is here; a partial count has to say so,
        // or a series with one downloaded volume claims to be listenable on a train.
        downloaded == series.books.size -> add(context.getString(R.string.details_offline))
        else -> add(context.getString(R.string.details_offline_some, downloaded, series.books.size))
    }
    // Dropped when the author is the heading overhead, exactly as bookMeta drops it — see
    // seriesCardMeta.
    if (ctx.shelving != LibraryShelving.AUTHOR) {
        add(series.author ?: context.getString(R.string.unknown_author))
    }
}.joinToString(" · ")

/** A series' length, but only once EVERY episode is measured — a partial sum understates it. */
internal fun seriesTotalMs(series: LibraryEntry.Series): Long? {
    val measured = series.books.mapNotNull { it.totalDurationMs?.takeIf { d -> d > 0 } }
    return if (measured.size == series.books.size && measured.isNotEmpty()) measured.sum() else null
}

// ── List row ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
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
            // Long-press opens the menu, the same as the grid card and the series shelf. The 3-dot
            // button stays — this is the shortcut, not a replacement for it — but list view was the
            // one place where holding a row did nothing, so the gesture learned in grid view
            // stopped working on switching.
            .combinedClickable(
                onClick = { onOpen(book.id) },
                onLongClick = { menuOpen = true },
            )
            .padding(if (bordered) SeriesListEnclosurePad else 6.dp)
            .alpha(if (book.hidden) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row's cover keeps the round badge rather than the grid's cut-to-the-edge areas: at
        // 46dp a slanted quadrilateral with a glyph in it is mush, and the corner it would anchor
        // to is a third of the cover.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                CoverArt(
                    model = book.coverModel,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                // Compact, which is what fits a "#12" on a 46dp cover without the badge taking most
                // of the artwork. The corners came off these covers once because a glyph at this
                // size was a smudge; a two-character number is not a glyph, and it is the one fact
                // about a book in a series that the row's single line of text keeps running out of
                // room for.
                VolumeIndexBadge(
                    seriesIndex = if (ctx.collectionNumbered) null else book.seriesIndex,
                    collectionIndex = book.collectionIndex,
                    modifier = Modifier.align(Alignment.TopStart),
                    size = BadgeSize.SMALL,
                )
            }
            // Same bar, same place, whatever view a book appears in.
            if (book.hasVisibleProgress()) {
                ProgressBar(
                    fraction = book.progress ?: 0f,
                    modifier = Modifier.width(46.dp).padding(top = 4.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            // Two reserved lines, because at one line books whose names share a long prefix were
            // indistinguishable — but a fixed-height box rather than minLines, so a title that
            // needs only one line sits in the MIDDLE of the reserved space instead of clinging to
            // its top edge with a gap beneath. The meta line below is unaffected and stays put, so
            // rows are still all the same height.
            Box(
                // heightIn, not height: a Box does not clip, so a hard height would let two lines
                // of a script whose fallback metrics run taller than the 16sp line box spill out
                // and draw over the meta line below. As a minimum it is identical for every title
                // that fits — which is what keeps rows a uniform height — and simply pushes the
                // row down for one that doesn't.
                modifier = Modifier.heightIn(
                    min = with(LocalDensity.current) { (ListRowTitleLineHeight * 2).toDp() },
                ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    book.title,
                    color = Parchment,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = ListRowTitleLineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = bookMeta(book, ctx, LocalContext.current, withStatus = true, withOffline = true),
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

/** Line height of a list row's title; the reserved block is two of these. */
private val ListRowTitleLineHeight = 16.sp

// ── Series shelf row ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesShelfRow(
    series: LibraryEntry.Series,
    ctx: RowContext,
    expanded: Boolean,
    flat: Boolean,
    onOrderChange: (Boolean) -> Unit,
    onToggle: () -> Unit,
    actions: BookActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
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
            .combinedClickable(onClick = onToggle, onLongClick = { menuOpen = true }),
    ) {
        Row(
            modifier = Modifier.padding(SeriesListEnclosurePad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The same stack the grid card draws, at row scale: the front cover on the bottom-left
            // with the edges of two more leaning back and right. It used to fan three real covers
            // with the LAST drawn on top, so the cover a series row showed was its third volume's.
            //
            // 46dp square, which is a BOOK row's cover exactly — so a shelf sitting among those rows
            // lines up with them and does not make its own row taller. It was 52x56.
            Box(modifier = Modifier.size(ShelfRowBox)) {
                val hints = (series.books.size - 1).coerceIn(0, STACK_MAX_HINTS)
                val frontY = RowStackReservedY
                for (i in hints downTo 1) {
                    Box(
                        modifier = Modifier
                            .offset(x = RowStackStepX * i, y = frontY - RowStackStepY * i)
                            .size(width = ShelfRowCoverW, height = ShelfRowCoverH)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Line)
                            .border(RowStackEdge, Studio, RoundedCornerShape(6.dp)),
                    )
                }
                // The badge sits on the FRONT cover rather than the 46dp box, so it hugs the
                // artwork's own corner instead of floating in the band the stack leans into.
                Box(
                    modifier = Modifier
                        .offset(y = frontY)
                        .size(width = ShelfRowCoverW, height = ShelfRowCoverH)
                        .clip(RoundedCornerShape(6.dp))
                        .border(RowStackEdge, Studio, RoundedCornerShape(6.dp)),
                ) {
                    // ONE corner, and it carries no number: the count is in the text beside the
                    // cover anyway ("8 books"), and the same number twice on one row two
                    // centimetres apart is not twice as clear.
                    CoverArt(model = series.frontCover(), modifier = Modifier.fillMaxSize())
                    ShelfBadge(
                        modifier = Modifier.align(Alignment.TopStart),
                        size = BadgeSize.SMALL,
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
                    // The book rows' own face, not the serif: a shelf sits among those rows and
                    // reads as one of them, a size up. The serif at 18sp made it a heading.
                    color = Parchment,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Skipped entirely when empty rather than drawn blank — shelved by author with an
                // unmeasured series there is nothing left for it to say, and an empty Text still
                // takes a line's height.
                seriesMeta(series, ctx, LocalContext.current).takeIf { it.isNotEmpty() }?.let {
                    Text(text = it, color = Muted, fontSize = 11.5.sp)
                }
            }
            // Only while open: folded, the shelf is one card and how its insides are arranged is not
            // yet a question the reader has asked.
            if (expanded && series.hasThreads()) {
                CollectionOrderChip(flat = flat, onChange = onOrderChange)
            }
            // Chevron immediately left of the overflow button, so the two sit together at the trailing
            // edge and the overflow still lines up with the one on every book row. Leading it instead
            // pushed the covers out of line with the book rows above and below.
            Icon(
                // Filled triangles rather than the thin chevrons. At this size a stroked chevron
                // reads as a decoration next to the solid 3-dot button beside it; a filled arrowhead
                // reads as a control, which is what it is.
                imageVector = if (expanded) Icons.Filled.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight,
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
        // Open, a rule separates the shelf's title from its episodes — inset from the enclosure's
        // side rails so it reads as a line inside the card, not another edge of it.
        if (expanded) {
            HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = SeriesListEnclosurePad))
        }
    }
}


// ── Cover art (title-forward placeholder) ─────────────────────────────────────

@Composable
internal fun CoverArt(model: Any?, modifier: Modifier = Modifier) {
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
        //
        // Three groups, hairline-separated: what you can LOOK at, what changes the book's state,
        // and offline. Edit is no longer here — it lives at the foot of Details, where the fields
        // it edits are on screen to be read first.
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_details)) },
            leadingIcon = { Icon(Icons.Filled.Info, null, tint = Muted) },
            onClick = { actions.onDetails(book); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_bookmarks)) },
            leadingIcon = { Icon(Icons.Filled.Bookmarks, null, tint = Muted) },
            onClick = { actions.onBookmarks(book); onDismiss() },
        )

        HorizontalDivider()

        // Greyed rather than absent for an unstarted book. It used to vanish, which moved every
        // item below it up and put a different action under the finger that reached for one.
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.mark_completed),
                    color = if (book.started) Parchment else Faint,
                )
            },
            leadingIcon = { Icon(Icons.Filled.Check, null, tint = if (book.started) Muted else Faint) },
            enabled = book.started,
            onClick = { actions.onMarkCompleted(book.id); onDismiss() },
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
            text = { Text(stringResource(R.string.menu_details)) },
            leadingIcon = { Icon(Icons.Filled.Info, null, tint = Muted) },
            onClick = { actions.onDetailsSeries(series); onDismiss() },
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
private fun LibraryLoading(
    scanState: ScanState,
    indexActivity: IndexActivity,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Amber)
        Spacer(Modifier.height(16.dp))
        val label = when {
            scanState is ScanState.Scanning -> stringResource(
                R.string.home_scanning_progress,
                scanState.directoriesVisited,
                scanState.booksFound,
            )
            indexActivity == IndexActivity.READING -> stringResource(R.string.home_reading_index)
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

/**
 * Edits the shelf-level fields — name, author, genre — and pushes them to every book on the shelf.
 *
 * **It has to know whether it is looking at a series or a collection.** The version before this did
 * not: one field labelled "Series", prefilled with [LibraryEntry.Series.name], which on a collection
 * card is the collection's own name. Saving wrote it into every member's `series`, so editing
 * Discworld overwrote Rincewind, the Watch and the witches in one action and the shelf redrew as a
 * collection holding a single sub-series named after itself.
 *
 * A collection therefore gets ONE name field, labelled Collection, and no series field at all — the
 * sub-series inside it are per-book facts and are not this dialog's business. A plain series gets
 * its name plus the collection it belongs to, as before.
 */
@Composable
private fun ShelfEditDialog(
    series: LibraryEntry.Series,
    onSave: (name: String, author: String, genre: String, collection: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val namesCollection = series.isCollection
    // rememberSaveable, like the book dialog: a rotation used to throw away what was typed.
    var name by rememberSaveable { mutableStateOf(series.name) }
    var author by rememberSaveable { mutableStateOf(series.author.orEmpty()) }
    // Prefilled only when the whole shelf already agrees. Showing one member's genre would make
    // Save quietly impose it on the rest, and blank means "leave it to detection" — so a shelf that
    // disagrees with itself starts empty and the user is choosing, not confirming.
    // The whole LIST, comma-separated, so a two-genre series prefills with both rather than losing
    // the second the moment somebody presses Save.
    var genre by rememberSaveable {
        mutableStateOf(series.books.map { it.genres }.distinct().singleOrNull()?.joinToString(", ").orEmpty())
    }
    // Same rule again, and unused on a collection — there, `name` IS the collection.
    var collection by rememberSaveable {
        mutableStateOf(series.books.map { it.collection }.distinct().singleOrNull().orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (namesCollection) R.string.home_collection_edit_title else R.string.home_series_edit_title,
                ),
            )
        },
        text = {
            // Scrollable: a dialog resizes for the keyboard, and without this the second field
            // gets crushed or clipped once the IME is up (EditBookDialog already does this).
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    pluralStringResource(
                        if (namesCollection) {
                            R.plurals.home_collection_edit_desc
                        } else {
                            R.plurals.home_series_edit_desc
                        },
                        series.books.size,
                        series.books.size,
                    ),
                    color = Muted,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(
                            stringResource(
                                if (namesCollection) R.string.edit_field_collection else R.string.edit_field_series,
                            ),
                        )
                    },
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
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text(stringResource(R.string.edit_field_genre)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                // Only for a plain series. On a collection there is no level above to join, and
                // offering the field would invite exactly the confusion that caused the bug.
                if (!namesCollection) {
                    OutlinedTextField(
                        value = collection,
                        onValueChange = { collection = it },
                        label = { Text(stringResource(R.string.edit_field_collection)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.edit_field_collection_desc),
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            HomerTextButton(onClick = { onSave(name, author, genre, collection) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

