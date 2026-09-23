package com.geozelot.homer.ui.home

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.IndexPass
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.library.authorsToInput
import com.geozelot.homer.data.sync.facet.IndexActivity
import com.geozelot.homer.data.update.pendingRelease
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.components.EditableBook
import com.geozelot.homer.ui.components.LibraryHelpCard
import com.geozelot.homer.ui.components.MiniPlayer
import com.geozelot.homer.ui.components.UpdateDot
import com.geozelot.homer.ui.settings.UpdateViewModel
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
 * A phone, as opposed to a tablet — the smallest of the window's two dimensions.
 *
 * **This is the signal, and an absolute height was not.** The first version of this decided
 * "landscape phone" by height alone, on the reasoning that the tallest phone on its side is about
 * 430dp. That is only true at a device's default density. Android lets the reader choose a display
 * size, and a large phone set to its smallest gives back dp in both directions — a 1440px handset
 * can report something like 1164×523 on its side, which sails past every threshold written for a
 * phone and gets served the tablet layout on a phone.
 *
 * `smallestScreenWidthDp` is the one measurement that does not move: it is the SHORTER edge, so it
 * is the same number in both orientations, and it is what Android itself uses to tell the classes
 * of device apart (the `sw600dp` resource qualifier). A phone stays under 600 at any display size;
 * a 7" tablet starts at 600 and a 10" at 720.
 */
private val TabletSmallestWidth = 600.dp

/**
 * The width the rail and the merged control row both need.
 *
 * The rail is 180dp and the grid beside it wants four columns at least. The merged row is the
 * binding constraint anyway: label, search, arrange, the view toggle and two 48dp actions come to
 * about 450dp of things that cannot shrink, and below that the trailing actions are clipped off the
 * end — a top bar costs less than an unreachable settings button.
 */
private val LandscapeMinWidth = 600.dp

/**
 * The height below which stacked chrome stops fitting, for a window that cannot have the rail.
 *
 * Every piece of furniture above the grid — the top bar, the listening panel, the control band — is
 * a fixed number of dp, and a short window does not make any of them smaller. On an 800×360 screen
 * they came to about 355dp of 360: the library was left with a sliver, and because the panel folds
 * ON SCROLL and there was nothing left to scroll, the one mechanism that would have given the space
 * back could not be reached.
 */
private val CompactLibraryHeight = 480.dp

/**
 * How the library arranges itself for the window it is in.
 *
 * One decision with three outcomes rather than three independent flags, because the outcomes are not
 * independent: a panel that has become a rail has nothing left to fold.
 */
internal enum class LibraryLayout {
    /** Room for everything: top bar, listening panel expanded above the grid. */
    STACKED,

    /**
     * Short, and too narrow to put anything beside anything. The panel starts folded and the top bar
     * stays, because the merged control row needs ~450dp of controls that cannot shrink.
     */
    STACKED_COMPACT,

    /**
     * A phone on its side. The panel goes down the left as a rail, spending width the grid does not
     * need (it caps at six columns either way), and the top bar folds into the control row. Nothing
     * above the grid but the controls.
     */
    RAIL,
    ;

    /** Start the Currently-listening panel folded — there is no room to open it into. */
    val foldListening: Boolean get() = this == STACKED_COMPACT

    /** Drop the top bar; the control row carries its label and its two actions instead. */
    val mergeTopBar: Boolean get() = this == RAIL

    /** The panel is a rail beside the library rather than a strip above it. */
    val listeningRail: Boolean get() = this == RAIL
}

/**
 * The layout for a window.
 *
 * Read the branches as a sentence: a tablet has height to spare whichever way up it is held; a phone
 * turned on its side with room beside the grid gets the rail; anything else short enough to be
 * cramped folds what it can; everything else stacks.
 *
 * Pure and named so the rules can be stated against real device sizes in a test rather than only in
 * a comment. They have already been wrong once — see [TabletSmallestWidth].
 */
internal fun libraryLayoutFor(width: Dp, height: Dp, smallestWidth: Dp): LibraryLayout = when {
    smallestWidth >= TabletSmallestWidth -> LibraryLayout.STACKED
    width > height && width >= LandscapeMinWidth -> LibraryLayout.RAIL
    height < CompactLibraryHeight -> LibraryLayout.STACKED_COMPACT
    else -> LibraryLayout.STACKED
}

@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onBookClickAt: (String, Long) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
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
    // Held as a State OBJECT and never read at this level. positionMs and bookElapsedMs advance
    // every second while audio plays, and the poll loop runs precisely BECAUSE this screen is
    // collecting — so one read here re-ran the library once a second to move a bar at its foot.
    // What the screen needs is whether anything is loaded at all, which changes when a book does.
    val playbackHolder = viewModel.playback.collectAsStateWithLifecycle()
    val playingBookId by remember { derivedStateOf { playbackHolder.value.bookId } }
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
    // Read here rather than inside each bar: there are two settings buttons and they must not
    // disagree. A bare hiltViewModel() is safe for this one — UpdateViewModel forwards a singleton
    // and says so in its own KDoc.
    // Through derivedStateOf, and not for tidiness: a download reports progress per whole percent,
    // so UpdateManager pushes up to a HUNDRED distinct Downloading states during one. Read directly,
    // each of them re-ran this whole screen to re-derive a boolean that changes at most twice.
    val updateStateHolder = updateViewModel.state.collectAsStateWithLifecycle()
    val updateWaiting by remember {
        derivedStateOf { updateStateHolder.value.pendingRelease != null }
    }

    val config = LocalConfiguration.current
    val layout = libraryLayoutFor(
        width = config.screenWidthDp.dp,
        height = config.screenHeightDp.dp,
        smallestWidth = config.smallestScreenWidthDp.dp,
    )
    // What the system is reserving along the left edge — a side navigation bar, a rotated cutout.
    // Logged because it has already hidden a whole component once: the turned bar took this inset
    // OUT of its 48dp instead of adding it on, and a 48dp inset left nothing to draw.
    val startInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Start)
        .asPaddingValues()
        .calculateStartPadding(LocalLayoutDirection.current)
    // Logged on every change, because the last time this was wrong the report could only say the
    // layout "looked like a tablet" and the numbers behind that had to be guessed at. One line per
    // rotation makes the next one a fact.
    LaunchedEffect(layout, config.screenWidthDp, config.screenHeightDp, startInset) {
        Log.i(
            TAG_UI,
            "library layout=$layout window=${config.screenWidthDp}x${config.screenHeightDp}dp " +
                "sw=${config.smallestScreenWidthDp}dp startInset=$startInset",
        )
    }
    // Every rule about when this panel folds lives in ListeningFold, with tests.
    // Keyed on `compact` so a rotation re-decides: the saved value belongs to the layout it was
    // saved in, and restoring an expanded panel into a screen with no room for it is exactly the
    // state this is here to avoid. Turning back restores the expanded default, and a tap or a pull
    // still opens it in either.
    val fold = rememberSaveable(layout.foldListening, saver = ListeningFold.Saver) {
        ListeningFold(expanded = !layout.foldListening)
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
    // Nothing to fold where the panel is a rail, so the gesture is not installed there: it would
    // sum a delta on every pointer event of every drag and hand the answer to a state nobody reads.
    val pullToExpand = if (layout.listeningRail) Modifier else Modifier.pointerInput(gridState, fold, pullToExpandPx) {
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
            .then(if (layout.mergeTopBar) Modifier.statusBarsPadding() else Modifier),
    ) {
        // The wordmark and settings only. Search moved down to the control bar, where the rest of
        // the controls for the list already live — it acts on the library, not on the app.
        //
        // Gone entirely on a short screen: it is 64dp, and what it holds is a wordmark nobody needs
        // told twice plus two buttons the control row has room for. Its help and settings move
        // there; the wordmark does not come back until there is height to spare for it.
        if (!layout.mergeTopBar) {
            Box(modifier = dismissSearch) {
                TopBar(
                    updateWaiting = updateWaiting,
                    onHelp = { showHelp = true },
                    onSettings = onOpenSettings,
                )
            }
        }

        // The Currently-listening shelf is pinned here — above the scrolling library rather than
        // being its first item — and is now ONE fixed size whatever the library does beneath it; see
        // ListeningShelf for why the collapse went. Its LazyRow state is hoisted so the horizontal
        // scroll position survives scrolling the library and is not reset by the item being disposed.
        val shelfRowState = rememberLazyListState()
        // The rail's own, for the same reason: hoisted so it outlives the item being disposed.
        val railState = rememberLazyListState()
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
        // Only where the shelf is a strip ABOVE the library. Where it is a rail it lives inside
        // the Row below, beside the grid rather than on top of it.
        if (!layout.listeningRail && listeningShelf.isNotEmpty() && libraryPresent) {
            // Closes the top bar off from the panel below it — without it the wordmark row and the
            // listening shelf ran together as one undifferentiated block. Nothing to close off when
            // there is no top bar, where it would just be a line under the status bar.
            if (!layout.mergeTopBar) HorizontalDivider(color = Line.copy(alpha = 0.45f))
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

        // Beside the library rather than above it, where the window is short and wide.
        //
        // The grid caps at six columns however wide the screen is, so a phone on its side has width
        // it cannot spend and no height at all — which is the whole trade: the rail costs 180dp of
        // the one Homer has plenty of, and nothing of the one it is short of. See [LibraryLayout].
        Row(modifier = Modifier.weight(1f)) {
            // The top bar, turned. Not merged away and not dropped: it is the same bar a quarter
            // turn anticlockwise, which is why the buttons end up at the top and the wordmark at
            // the bottom — the left end of a row becomes the bottom of a column.
            //
            // It also puts help and settings somewhere that does not depend on there being a
            // library. They used to ride on the control row, and the control row does not render
            // for an empty shelf — so a reader whose first scan found nothing, in landscape, had
            // no way into the settings that would have let them fix it.
            val railShown = layout.listeningRail && listeningShelf.isNotEmpty() && libraryPresent
            if (layout.mergeTopBar) {
                LibrarySideBar(
                    updateWaiting = updateWaiting,
                    onHelp = { showHelp = true },
                    onSettings = onOpenSettings,
                    modifier = dismissSearch,
                )
                // The same rule the upright stack draws between the top bar and the listening
                // panel, in the same place, turned with everything else. Only where there is a
                // panel for it to close the bar off from.
                if (railShown) VerticalHairline(Line.copy(alpha = 0.45f))
            }
            if (railShown) {
                ListeningRail(
                    books = listeningShelf,
                    railState = railState,
                    onOpen = onBookClick,
                    actions = actions,
                    modifier = dismissSearch,
                )
            }
            // And the solid one at the edge of the whole left group — keyed on the BAR, not the
            // rail, because the bar is always there and the rail is not: a reader with nothing in
            // progress would otherwise have the turned bar bleeding straight into the grid.
            if (layout.mergeTopBar) VerticalHairline(Line)
            Column(modifier = Modifier.weight(1f)) {
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
                            compact = layout.mergeTopBar,
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
            }
        }

        // The mini-player insets itself (its gradient runs behind the navigation bar). When there's
        // nothing playing it emits nothing at all, so the space has to be reserved here or the last
        // row of books ends up under the navigation bar.
        if (playingBookId != null) {
            MiniPlayer(
                // The ticking value reaches the one composable built to tick, and no further.
                state = playbackHolder.value,
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
    // The edit field's form, semicolon-separated — BookEditor turns it back. See AuthorList.kt.
    author = authorsToInput(authors),
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
private fun TopBar(updateWaiting: Boolean, onHelp: () -> Unit, onSettings: () -> Unit) {
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
                UpdateDot(updateWaiting) {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = settingsDescription(updateWaiting),
                            tint = Muted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the settings button is called, which depends on whether it is also carrying the dot.
 *
 * One node saying both things rather than two nodes saying one each: the dot beside it is marked
 * decorative, so a screen reader is not handed "An update is available" as a separate stop with
 * nothing to activate.
 */
@Composable
private fun settingsDescription(updateWaiting: Boolean): String = stringResource(
    if (updateWaiting) R.string.home_cd_settings_update else R.string.home_cd_settings,
)

/** How wide the turned bar is: one icon button, and nothing else has to fit across it. */
private val SideBarWidth = 48.dp

/**
 * The top bar, on its side.
 *
 * A quarter turn anticlockwise and nothing else. That is the whole design and it is worth saying
 * plainly, because it is what makes the result feel placed rather than rearranged: rotating a row
 * anticlockwise sends its left end to the bottom and its right end to the top, so the wordmark
 * lands at the foot of the column and the two buttons at the head — exactly where they would be if
 * the bar had physically turned with the screen.
 *
 * The wordmark turns with it, reading bottom-to-top. That is the continental convention for a book
 * spine, which is both where this app's first language is spoken and what the thing beside it is:
 * a shelf.
 */
@Composable
private fun LibrarySideBar(
    updateWaiting: Boolean,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            // NO background of its own, which is the point: upright, the top bar sits on the same
            // ground the library does, and a rule separates it from the listening panel. Painting
            // this Surface0 made it the panel's colour instead and the two ran together — the bar
            // stopped reading as the bar and started reading as more panel.
            //
            // A landscape navigation bar or a cutout sits along this very edge — this is the one
            // place in the app pinned to it. OUTSIDE the width, and that is the whole point: inside
            // it, the inset ate the 48dp rather than being added to it, and a 48dp cutout left zero
            // content width with the icons measuring to nothing.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            .width(SideBarWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Settings ABOVE help, which is not the reading order of the row this came from and is
        // exactly right: a quarter turn anticlockwise sends a row's RIGHT end to the TOP, and
        // settings is the rightmost thing on the upright bar. Ordering them by how the row reads
        // instead would mean the bar had been rearranged rather than turned — and the wordmark,
        // which lands at the foot by the same rule, would be the only part that had really moved.
        UpdateDot(updateWaiting) {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = settingsDescription(updateWaiting),
                    tint = Muted,
                )
            }
        }
        IconButton(onClick = onHelp) {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = stringResource(R.string.home_cd_help),
                tint = Muted,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TurnedWordmark(stringResource(R.string.app_name), Modifier.padding(bottom = 16.dp))
    }
}

/** A 1dp rule down the full height — the turned counterpart of a [HorizontalDivider]. */
@Composable
private fun VerticalHairline(color: Color) {
    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(color))
}

/** Anticlockwise, so the first letter sits at the foot of the bar and the word runs upward. */
private const val WordmarkTurn = -90f

/**
 * The wordmark, running up the bar.
 *
 * Measured, then drawn: the canvas is sized to the result — the bar's width across, the text's own
 * length down — and the draw scope turns about the centre the two share. Every number here is one
 * this function computed, which is why it is written this way; `Modifier.rotate` on a laid-out Text
 * was tried twice and turned nothing on a device, and it is used nowhere else in the app to compare
 * against.
 *
 * A canvas carries no semantics, so the word is stated for a screen reader by hand. A Text did that
 * for free; it is the one thing this costs.
 */
@Composable
private fun TurnedWordmark(text: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val laid = measurer.measure(wordmarkText(text), style = SerifDisplay.copy(color = Parchment), maxLines = 1)
    val length = with(LocalDensity.current) { laid.size.width.toDp() }
    Canvas(
        modifier = modifier
            .width(SideBarWidth)
            .height(length)
            .semantics { contentDescription = text },
    ) {
        rotate(degrees = WordmarkTurn, pivot = center) {
            drawText(
                textLayoutResult = laid,
                topLeft = Offset(center.x - laid.size.width / 2f, center.y - laid.size.height / 2f),
            )
        }
    }
}

/** "Homer" with an amber initial — the one place the two wordmarks agree on what they say. */
private fun wordmarkText(text: String): AnnotatedString = buildAnnotatedString {
    if (text.isNotEmpty()) {
        withAmber(text.first().toString())
        append(text.drop(1))
    }
}

/** "Homer" with an amber initial, in the serif voice. */
@Composable
private fun Wordmark(text: String) {
    Text(
        text = wordmarkText(text),
        style = SerifDisplay,
        color = Parchment,
        maxLines = 1,
    )
}

private fun AnnotatedString.Builder.withAmber(s: String) {
    pushStyle(SpanStyle(color = Amber))
    append(s)
    pop()
}

/** Log tag for what the window is and what the library did about it. */
private const val TAG_UI = "HomerUI"
