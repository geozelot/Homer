package com.geozelot.homer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.IndexPass
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.sync.facet.IndexActivity
import com.geozelot.homer.ui.components.LibraryHelpCard
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.components.EditableBook
import com.geozelot.homer.ui.components.MiniPlayer
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifDisplay
import com.geozelot.homer.ui.theme.Surface0
import com.geozelot.homer.ui.theme.Surface1

// ── The library screen ───────────────────────────────────────────────────────
//
// This file is the screen itself: the state it collects, the dialogs and sheets it owns, the
// actions it hands down, and its top bar. Everything it draws with lives beside it in this package
// and is `internal` for that reason:
//
//   LibraryGrid.kt        the lazy grid every view is built from, and its metrics
//   LibraryControlBar.kt  arrange, search and the view toggle
//   ListeningShelf.kt     the pinned Currently-listening shelf
//   LibraryCards.kt       grid cards for a book and a shelf, and the cover stack
//   LibraryRows.kt        the same two, flat, for list view
//   SeriesEnclosure.kt    the bordered block an opened shelf becomes
//   LibraryMenus.kt       what a long-press offers
//   LibraryEmptyStates.kt loading, and no matches
//   ShelfEditDialog.kt    editing a shelf's shared fields
//   CoverArt.kt           cover art, with the title-forward placeholder
//
// They were one 3,500-line file until the split; nothing moved except across file boundaries.

/**
 * The viewport height below which the library stops affording pinned chrome.
 *
 * Every piece of furniture above the grid — the top bar, the listening shelf, the control band —
 * is a fixed number of dp, and rotating a phone halves the height without any of them noticing. On
 * an 800×360 landscape phone they came to about 355dp of a 360dp screen: the library was left with
 * a sliver, and because the shelf folds ON SCROLL and there was nothing left to scroll, the one
 * mechanism that would have given the space back could not be reached.
 *
 * 480dp is chosen to sit above every phone in landscape (the tallest are about 430dp) and below
 * every phone in portrait and every tablet in either orientation. It is a HEIGHT and not an
 * orientation on purpose: a short split-screen window has the same problem and deserves the same
 * answer, and a tall tablet turned sideways has no problem to fix.
 */
private val CompactLibraryHeight = 480.dp

/**
 * The width the merged control row needs before it is an improvement rather than a clipping.
 *
 * Label, search, arrange, the view toggle and two 48dp actions come to about 450dp of things that
 * cannot shrink. Above this they sit comfortably on one row and the 64dp top bar is pure saving;
 * below it — a narrow split-screen window that also happens to be short — the trailing actions
 * would be cut off the end, and a top bar costs less than an unreachable settings button.
 *
 * So the two savings are decided separately: folding the panel needs no width and always applies,
 * merging the chrome needs this.
 */
private val CompactChromeMinWidth = 600.dp

/** What the library's chrome gives up at a given window size. */
internal data class LibraryChrome(
    /** Start the Currently-listening panel folded — there is no room to open it into. */
    val foldListening: Boolean,
    /** Drop the top bar; the control row carries its label and its two actions instead. */
    val mergeTopBar: Boolean,
)

/**
 * The two savings, decided together and separately.
 *
 * Pure and named so the thresholds can be stated against real device sizes in a test rather than
 * only in a comment — and because the asymmetry is the part worth pinning: a short window always
 * folds the panel, but only a short AND wide one merges the chrome.
 */
internal fun libraryChromeFor(width: Dp, height: Dp): LibraryChrome {
    val short = height < CompactLibraryHeight
    return LibraryChrome(
        foldListening = short,
        mergeTopBar = short && width >= CompactChromeMinWidth,
    )
}

@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onBookClickAt: (String, Long) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
    /**
     * Whether to dock the mini-player at the foot of the list.
     *
     * False in a two-pane layout, where the player is already on screen beside this one and a
     * second set of transport controls for the same audio is furniture, not a shortcut.
     */
    showMiniPlayer: Boolean = true,
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
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val flatCollections by viewModel.flatCollections.collectAsStateWithLifecycle()
    val filterCount by viewModel.filterCount.collectAsStateWithLifecycle()
    val indexActivity by viewModel.indexActivity.collectAsStateWithLifecycle()
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
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarksId by rememberSaveable { mutableStateOf<String?>(null) }
    // rememberSaveable so a rotation doesn't drop the user out of search: `searching` used to be
    // lost while the query stayed in the ViewModel, leaving the library filtered with no search
    // field to clear it.
    var searching by rememberSaveable { mutableStateOf(false) }
    // The arrange settings, which take the control row the same way search does — so they are the
    // same kind of state, held in the same place, and dismissed by the same gesture. Never both:
    // each opening closes the other, because one row cannot be two things.
    var arranging by rememberSaveable { mutableStateOf(false) }
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
    BackHandler(enabled = searching || arranging) {
        // Arrange first: it has nothing to commit, and if both were somehow open the panel on top
        // is the one a back press is about.
        if (arranging) {
            arranging = false
            return@BackHandler
        }
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
            onFilter = viewModel::addFilterToken,
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
    val arrangingNow by rememberUpdatedState(arranging)
    val dismissSearch = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Neither panel open: leave the event entirely alone, so every ordinary tap behaves.
            if (!searchingNow && !arrangingNow) return@awaitEachGesture
            down.consume()
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                pressed = event.changes.any { it.pressed }
            }
            // Same rule as the back gesture: a tap on the library ends the typing, it does not
            // discard it. See the BackHandler above.
            if (arrangingNow) {
                arranging = false
                return@awaitEachGesture
            }
            viewModel.commitSearchText()
            viewModel.setSearchQuery("")
            searching = false
        }
    }

    // What this window can afford above the first cover — see [libraryChromeFor].
    val chrome = LocalConfiguration.current.let {
        libraryChromeFor(width = it.screenWidthDp.dp, height = it.screenHeightDp.dp)
    }
    // Every rule about when this panel folds lives in ListeningFold, with tests.
    // Keyed on `compact` so a rotation re-decides: the saved value belongs to the layout it was
    // saved in, and restoring an expanded panel into a screen with no room for it is exactly the
    // state this is here to avoid. Turning back restores the expanded default, and a tap or a pull
    // still opens it in either.
    val fold = rememberSaveable(chrome.foldListening, saver = ListeningFold.Saver) {
        ListeningFold(expanded = !chrome.foldListening)
    }
    val pullToExpandPx = with(LocalDensity.current) { ListeningPullToExpand.toPx() }
    // Read here rather than inside the grid: `libraryContent` runs in a LazyGridScope, which is not
    // a composition, so a CompositionLocal is unreachable from it. Reading it in the composition
    // that OWNS the grid is also what makes a language change rebuild the labels.
    val interfaceLocale = LocalConfiguration.current.locales[0]

    /**
     * Folding and unfolding, driven by RAW POINTER TRAVEL rather than by nested scroll.
     *
     * ## Why the fifth attempt changes the input instead of the rules
     *
     * Four versions of this used `NestedScrollConnection` and none of them worked on a device. The
     * fifth was supposed to be instrumentation rather than a fix — and **the instrumentation never
     * shipped**: the edit that added it failed partway, the file was never written, and the commit
     * said otherwise. A build went out claiming to log, logged nothing, and the silence was briefly
     * mistaken for evidence about the deltas.
     *
     * So what a scroll delta's sign means here, and whether that connection was ever in the chain
     * the grid walks, are both still unknown. Three OTHER explanations were eliminated by reading —
     * the source test (`NestedScrollSource.Drag` really is `UserInput` on 1.7.5, confirmed from the
     * bytecode), the stretch overscroll (it hands `applyToScroll` the whole delta and dispatches
     * pre-scroll inside it), and the wiring in the layout tree (the grid IS a descendant of the node
     * that carried the modifier).
     *
     * Rather than settle the remaining question, this stops depending on it. `positionChange().y` is screen
     * coordinates: **positive is the finger moving down the glass**, and there is no second reading.
     * It is the same `awaitEachGesture` / `PointerEventPass.Initial` shape as `dismissSearch` above,
     * which has always worked on this exact node — so the mechanism is now one that is demonstrably
     * delivered here, instead of one that is supposed to be.
     *
     * **Nothing is consumed.** The grid scrolls exactly as before; this only watches.
     *
     * Confirmed working on a device at `2.1.0-BETA.79`, which is why the diagnostic that shipped with
     * it is gone: a log on every 250ms of drag is a cost in a build nobody is debugging, and the rules
     * it was watching are covered by `ListeningFoldTest`.
     *
     * The cost, stated: momentum no longer reaches the fold at all, so the panel does not fold on a
     * fling that follows a lift. It folds on the drag that produced the fling, which is the same
     * moment to a reader. The rule "a fling must never unfold it" is now true by construction rather
     * than by a check, which is why `ListeningFold` no longer takes a flag for it.
     */
    val pullToExpand = Modifier.pointerInput(gridState, fold, pullToExpandPx) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val dy = event.changes.fold(0f) { sum, change -> sum + change.positionChange().y }
                if (dy != 0f) {
                    // `canScrollBackward` rather than the first-item index and offset: it is the
                    // question being asked, answered by the state itself.
                    fold.onScroll(
                        deltaY = dy,
                        atTop = !gridState.canScrollBackward,
                        threshold = pullToExpandPx,
                    )
                }
                pressed = event.changes.any { it.pressed }
            }
        }
    }

    // Every time the box opens, not just the first.
    LaunchedEffect(searching) { if (searching) fold.onSearchOpened() }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Normally [TopBar] carries this. Where there is no top bar, the column has to, or the
            // shelf below rides up under the status bar.
            .then(if (chrome.mergeTopBar) Modifier.statusBarsPadding() else Modifier),
    ) {
        // The wordmark and settings only. Search moved down to the control bar, where the rest of
        // the controls for the list already live — it acts on the library, not on the app.
        //
        // Gone entirely on a short screen: it is 64dp, and what it holds is a wordmark nobody needs
        // told twice plus two buttons the control row has room for. Its help and settings move
        // there; the wordmark does not come back until there is height to spare for it.
        if (!chrome.mergeTopBar) {
            Box(modifier = dismissSearch) {
                TopBar(onHelp = { showHelp = true }, onSettings = onOpenSettings)
            }
        }

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
            // listening shelf ran together as one undifferentiated block. Nothing to close off when
            // there is no top bar, where it would just be a line under the status bar.
            if (!chrome.mergeTopBar) HorizontalDivider(color = Line.copy(alpha = 0.45f))
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
                    arrangeOpen = arranging,
                    onQueryChange = viewModel::setSearchQuery,
                    onOpenSearch = {
                        arranging = false
                        searching = true
                    },
                    onToggleArrange = {
                        searching = false
                        arranging = !arranging
                    },
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
                    compact = chrome.mergeTopBar,
                    onHelp = { showHelp = true },
                    onSettings = onOpenSettings,
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
                // Not "your shelf is empty" whenever it is empty: a crawl that has been asked for
                // and cannot start yet says so instead. Which library this is was settled by the
                // setup flow, before the shelf was ever shown.
                else ->
                    LibrarySetupPanel(
                        scanPending = IndexPass.BOOKS in indexQueued,
                        wifiOnly = wifiOnlyDownloads,
                        readsOnly = readsSharedIndex,
                        modifier = Modifier.weight(1f),
                    )
            }
        } else {
            // The column count comes from the width rather than from a literal, and it is needed in
            // two places — the grid's own cells, and the rows an opened shelf lays out inside a
            // full-span item, which have to match them. One BoxWithConstraints, one answer.
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val columns = if (gridView) gridColumnsFor(maxWidth) else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        // Before dismissSearch, and consuming nothing — see `pullToExpand`.
                        .then(pullToExpand)
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
                        columns = columns,
                        flatCollections = flatCollections,
                        onCollectionOrder = viewModel::setCollectionFlat,
                        ctx = RowContext(
                            shelving = shelfMode,
                            series = seriesMode,
                            gridView = gridView,
                            locale = interfaceLocale,
                        ),
                        expanded = expanded,
                        onBookClick = onBookClick,
                        actions = actions,
                    )
                }
            }
        }

        // The mini-player insets itself (its gradient runs behind the navigation bar). When there's
        // nothing playing it emits nothing at all, so the space has to be reserved here or the last
        // row of books ends up under the navigation bar.
        if (showMiniPlayer && playback.bookId != null) {
            MiniPlayer(
                state = playback,
                onOpenPlayer = onBookClick,
                onPlayPause = viewModel::playPause,
                onPrevChapter = viewModel::previousChapter,
                onNextChapter = viewModel::nextChapter,
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
            onSave = { title, author, series, index, collection, collectionIndex, genres, language, tags, hidden, downloadOnPlay ->
                viewModel.saveOverride(
                    book.id, title, author, series, index, collection, collectionIndex,
                    genres, language, tags, hidden, downloadOnPlay,
                )
                editingId = null
            },
            onReset = {
                viewModel.clearOverride(book.id)
                editingId = null
            },
            onPickCover = { uri -> viewModel.setCustomCover(book.id, uri) },
            onClearCover = { viewModel.clearCustomCover(book.id) },
            // A pattern rewrites what the index says about every book under a folder, so it lives
            // with editing rather than with looking — and only where patterns are this device's to
            // write, which a reader's are not.
            onReadFolderDifferently = if (maintainsLibrary) {
                { editingId = null; viewModel.seedTemplateFor(book.id); onOpenTemplates() }
            } else {
                null
            },
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
            onDismiss = { detailsId = null },
        )
    }
    entries.findSeries(detailsSeriesKey)?.let { series ->
        SeriesDetailsCard(
            series = series,
            onEdit = { detailsSeriesKey = null; editingSeriesKey = series.expandKey },
            onFilter = { detailsSeriesKey = null; searching = false; viewModel.addFilterToken(it) },
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

    if (showHelp) {
        // Answering for the arrangement actually on screen. A card that described every possible
        // arrangement would describe none of them: the reader is looking at ONE, and the marks in
        // front of them are the ones worth decoding.
        LibraryHelpCard(
            gridView = gridView,
            shelved = shelfMode != LibraryShelving.ITEM,
            stacked = seriesMode != LibraryDepth.FLAT,
            numbered = seriesMode != LibraryDepth.FLAT,
            onDismiss = { showHelp = false },
        )
    }

    entries.findSeries(editingSeriesKey)?.let { series ->
        ShelfEditDialog(
            series = series,
            onSave = { name, author, genres, collection ->
                viewModel.saveShelfOverride(
                    bookIds = series.books.map { it.id },
                    name = name,
                    author = author,
                    genres = genres,
                    // The card knows what it drew. A collection card's name field names the
                    // COLLECTION, and writing it to `series` is what used to flatten the threads.
                    namesCollection = series.isCollection,
                    collection = collection,
                )
                editingSeriesKey = null
            },
            onReadFolderDifferently = if (maintainsLibrary) {
                {
                    editingSeriesKey = null
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
internal val LibraryEntry.Series.expandKey: String
    get() = "series:${books.minOf { it.id }}"

/** The live row for an open edit dialog, or null when there's no target (or it's gone). */
internal fun List<LibraryEntry>.findBook(id: String?): BookListItem? {
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
internal fun List<LibraryEntry>.findSeries(key: String?): LibraryEntry.Series? {
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
internal class BookActions(
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
    /** Narrows the library to one genre — what expanding a genre chip is for. */
    val onFilter: (FilterToken) -> Unit,
)

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onHelp: () -> Unit, onSettings: () -> Unit) {
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
                // Before settings, because it explains the screen you are on rather than taking
                // you off it — and because a reader who does not know what a bracket means will
                // not go looking for the answer under a tuning fork.
                IconButton(onClick = onHelp) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(R.string.home_cd_help),
                        tint = Muted,
                    )
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
