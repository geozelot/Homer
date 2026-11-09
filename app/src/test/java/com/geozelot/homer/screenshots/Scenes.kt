package com.geozelot.homer.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.SerifDisplay
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.playback.PlaybackUiState
import com.geozelot.homer.ui.components.EditableBook
import com.geozelot.homer.ui.components.MiniPlayer
import com.geozelot.homer.ui.home.BookActions
import com.geozelot.homer.ui.home.LibraryControlBar
import com.geozelot.homer.ui.home.LibraryDepth
import com.geozelot.homer.ui.home.LibraryFilter
import com.geozelot.homer.ui.home.LibraryFilterEngine
import com.geozelot.homer.ui.home.LibraryGridPadding
import com.geozelot.homer.ui.home.LibraryGridSpacing
import com.geozelot.homer.ui.home.GridSlot
import com.geozelot.homer.ui.home.LibraryShelving
import com.geozelot.homer.ui.home.LibrarySort
import com.geozelot.homer.ui.home.ListeningShelf
import com.geozelot.homer.ui.home.RowContext
import com.geozelot.homer.ui.home.TopBar
import com.geozelot.homer.ui.home.bookCount
import com.geozelot.homer.ui.home.gridColumnsFor
import com.geozelot.homer.ui.home.libraryContent
import com.geozelot.homer.ui.home.librarySlots
import com.geozelot.homer.ui.home.suggest
import com.geozelot.homer.ui.player.BookHeader
import com.geozelot.homer.ui.player.CLUSTER_MAX_FRACTION
import com.geozelot.homer.ui.player.ChapterButton
import com.geozelot.homer.ui.player.PlayerArtwork
import com.geozelot.homer.ui.player.PlayerTopBar
import com.geozelot.homer.ui.player.PositionLine
import com.geozelot.homer.ui.player.Scrubber
import com.geozelot.homer.ui.player.ToolRow
import com.geozelot.homer.ui.player.Transport
import com.geozelot.homer.ui.player.playerScale
import com.geozelot.homer.ui.player.scaled
import com.geozelot.homer.ui.theme.Ground
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface0
import com.geozelot.homer.ui.theme.Surface1

/**
 * The library and the player as the store shows them, assembled from the very composables the app
 * draws them with. (The settings pages need no assembling: they are drawn whole — see [DemoSettings].)
 *
 * Laid out the way [com.geozelot.homer.ui.home.HomeScreen] and
 * [com.geozelot.homer.ui.player.PlayerScreen] lay them out, minus the ViewModels: those reach a
 * server, a database and a media session, none of which a JVM test has. What is on screen is the
 * real shelf, cards, control bar, mini-player, cover and transport, fed with [DemoLibrary].
 *
 * The one thing that can drift is the arrangement itself. If either screen's layout changes, mirror
 * it here before the next set of screenshots — the components follow automatically.
 */
internal object Scenes {

    private val noActions = BookActions(
        onDownload = {}, onRemove = {}, onEdit = {}, onDetails = {}, onDetailsSeries = {},
        onBookmarks = {}, onSetHidden = { _, _ -> }, onMarkCompleted = {}, onEditSeries = {},
        onDownloadSeries = {}, onRemoveSeries = {}, onPause = {}, onResume = {}, onFilter = {},
    )

    /**
     * What the player reports for [listening]. A book of one file per chapter is a playlist, and
     * the player sits in one item of it; a book of one file is a single item the length of the book,
     * and its chapters are marks the screen finds inside it.
     */
    fun playing(listening: DemoLibrary.Listening = DemoLibrary.odyssey): PlaybackUiState {
        val book = DemoLibrary.book(listening.bookId)
        val chapter = listening.chapters[listening.current]
        return PlaybackUiState(
            isConnected = true,
            isPlaying = true,
            bookId = book.id,
            bookTitle = book.title,
            chapterTitle = if (listening.inFile) book.title else chapter.title,
            chapterIndex = if (listening.inFile) 0 else listening.current,
            chapterCount = if (listening.inFile) 1 else listening.chapters.size,
            positionMs = if (listening.inFile) listening.elapsedMs else listening.intoChapterMs,
            durationMs = if (listening.inFile) listening.totalMs else chapter.lengthMs ?: 0L,
            bookElapsedMs = listening.elapsedMs,
            bookTotalMs = listening.totalMs,
            playbackSpeed = 1.2f,
            coverModel = book.coverModel,
        )
    }

    /**
     * What [com.geozelot.homer.ui.home.HomeScreen] draws for a library with something playing.
     *
     * [query] is what is typed into an open search box, and filters the shelf the way the app does
     * while it is typed. [open] names shelves to draw opened: the grid starts scrolled to the first,
     * with the listening panel folded, which is how the screen stands once somebody has scrolled down
     * to it. Search folds the panel too, as it does in the app.
     */
    @Composable
    fun Library(
        gridView: Boolean,
        shelving: LibraryShelving = LibraryShelving.AUTHOR,
        depth: LibraryDepth = LibraryDepth.COLLECTION,
        // Title, because shelved by author the author sort is not offered — see LibrarySort.offeredFor.
        sort: LibrarySort = LibrarySort.TITLE,
        searchOpen: Boolean = false,
        query: String = "",
        arrangeOpen: Boolean = false,
        open: List<String> = emptyList(),
    ) {
        val books = DemoLibrary.books
        val filter = LibraryFilter(text = query)
        val entries = remember(filter, shelving, depth, sort) {
            LibraryFilterEngine().arrange(books, filter, sort, shelving, depth, bySurname = true)
        }
        val filtering = !filter.isEmpty
        val anchors = books.filter { it.collection in open || it.series in open }.map { it.id }
        val playingBook = DemoLibrary.book(DemoLibrary.PLAYING)
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(updateWaiting = false, onHelp = {}, onSettings = {})
            HorizontalDivider(color = Line.copy(alpha = 0.45f))
            ListeningShelf(
                books = DemoLibrary.listening,
                expanded = !searchOpen && open.isEmpty(),
                onExpand = {},
                rowState = rememberLazyListState(),
                onOpen = {},
                actions = noActions,
            )
            Column(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Surface0, Surface1))),
                ) {
                    HorizontalDivider(color = Line.copy(alpha = 0.45f))
                    LibraryControlBar(
                        count = if (filtering) entries.bookCount() else books.size,
                        searching = filtering,
                        sort = sort,
                        shelving = shelving,
                        series = depth,
                        gridView = gridView,
                        tokens = filter.tokens,
                        shown = entries.bookCount(),
                        total = books.size,
                        query = query,
                        searchOpen = searchOpen,
                        arrangeOpen = arrangeOpen,
                        onQueryChange = {},
                        onOpenSearch = {},
                        onToggleArrange = {},
                        onCloseSearch = {},
                        onRemoveToken = {},
                        onClearFilter = {},
                        onCommitQuery = {},
                        suggestions = if (searchOpen) suggest(books, query, filter.tokens) else emptyList(),
                        onPickSuggestion = {},
                        onSortChange = {},
                        onShelfChange = {},
                        onSeriesChange = {},
                        onToggleView = {},
                        compact = false,
                        modifier = Modifier.padding(horizontal = LibraryGridPadding),
                    )
                    HorizontalDivider(color = Line)
                }
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val columns = if (gridView) gridColumnsFor(maxWidth) else 1
                    val locale = LocalConfiguration.current.locales[0]
                    val expanded = remember { mutableStateListOf<String>().apply { addAll(anchors) } }
                    // The heading above the first opened shelf, so its author is on screen with it.
                    val firstOpen = librarySlots(entries, gridView, columns, emptySet()) { shelf ->
                        shelf.books.any { it.id in anchors }
                    }.indexOfFirst { it is GridSlot.ShelfBanner || (it is GridSlot.Shelf && it.open) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = rememberLazyGridState(initialFirstVisibleItemIndex = (firstOpen - 1).coerceAtLeast(0)),
                        modifier = Modifier.fillMaxSize(),
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
                            flatCollections = emptySet(),
                            onCollectionOrder = { _, _ -> },
                            ctx = RowContext(
                                shelving = shelving,
                                series = depth,
                                gridView = gridView,
                                locale = locale,
                            ),
                            expanded = expanded,
                            onBookClick = {},
                            actions = noActions,
                        )
                    }
                }
            }
            MiniPlayer(
                state = playing(),
                onOpenPlayer = {},
                onPlayPause = {},
                onPrevChapter = {},
                onNextChapter = {},
                onRetry = {},
                liveCover = playingBook.coverModel,
                liveTitle = playingBook.title,
            )
        }
    }

    /** What [com.geozelot.homer.ui.player.PlayerScreen] draws in a tall window. */
    @Composable
    fun Player(
        listening: DemoLibrary.Listening = DemoLibrary.odyssey,
        sleepRemainingMs: Long? = 23 * 60_000L + 41_000L,
    ) {
        val book = DemoLibrary.book(listening.bookId)
        val state = playing(listening)
        val chapters = listening.chapters
        val current = listening.current
        val editable = EditableBook(
            id = book.id,
            title = book.title,
            author = book.authors.firstOrNull(),
            series = book.series,
            seriesIndex = book.seriesIndex,
            collection = null,
            collectionIndex = null,
            genres = book.genres,
            language = book.language,
            tags = emptyList(),
            hidden = false,
            hasCustomCover = false,
            downloadOnPlay = null,
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            val viewportHeight = maxHeight
            val scale = playerScale(viewportHeight, maxWidth)
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerTopBar(
                    started = true,
                    offline = true,
                    downloading = false,
                    canShowDetails = true,
                    onBack = {},
                    onMarkCompleted = {},
                    onToggleOffline = {},
                    onDetails = {},
                    onBookmarks = {},
                    onHelp = {},
                )
                PlayerArtwork(
                    model = book.coverModel,
                    sleepRemainingMs = { sleepRemainingMs },
                    documents = book.documents,
                    onOpenDocument = {},
                    onCollapse = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp.scaled(scale)),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = viewportHeight * CLUSTER_MAX_FRACTION)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BookHeader(book = editable, title = book.title, scale = scale, onFilter = {})
                    ChapterButton(onClick = {}, modifier = Modifier.padding(top = 20.dp))
                    PositionLine(
                        chapterNumber = current + 1,
                        chapterCount = chapters.size,
                        timeLeftMs = { book.timeLeftMs },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Scrubber(
                        positionMs = { state.positionMs },
                        durationMs = state.durationMs,
                        onSeek = {},
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    Transport(
                        isPlaying = true,
                        seekSeconds = 30,
                        scale = scale,
                        onPrev = {},
                        onSeekBack = {},
                        onPlayPause = {},
                        onSeekForward = {},
                        onNext = {},
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ToolRow(
                        speed = state.playbackSpeed,
                        sleepRemainingMs = { sleepRemainingMs },
                        sleepEndOfChapter = false,
                        sleepActive = sleepRemainingMs != null,
                        volumeMode = "normal",
                        skipSilence = true,
                        onSpeed = {},
                        onSleepMinutes = {},
                        onSleepEndOfChapter = {},
                        onSleepOff = {},
                        onCustomSpeed = {},
                        onCustomSleep = {},
                        onVolumeMode = {},
                        onToggleSkipSilence = {},
                        onMark = {},
                    )
                }
            }
        }
    }

    // ── store banner ─────────────────────────────────────────────────────────────────────────

    /**
     * F-Droid's feature graphic: the wordmark as the app sets it, what Homer is in a sentence, and
     * a fan of covers from the shelf — drawn with the app's own cover component.
     */
    @Composable
    fun Banner() {
        val fan = listOf(
            "Bram Stoker/Dracula",
            "Arthur Conan Doyle/Sherlock Holmes/03 The Hound of the Baskervilles",
            DemoLibrary.PLAYING,
            DemoLibrary.MOBY_DICK,
            "L. Frank Baum/Oz/01 The Wonderful Wizard of Oz",
        ).map { DemoLibrary.book(it) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Ground, Surface0, Color(0xFF2A1F15)))),
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 64.dp).width(470.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Amber)) { append("H") }
                        append("omer")
                    },
                    style = SerifDisplay,
                    color = Parchment,
                    fontSize = 104.sp,
                    lineHeight = 108.sp,
                )
                Text(
                    "Your audiobooks, straight from your own Nextcloud.",
                    color = Parchment,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "No extra server · No tracking · No Google services",
                    color = Muted,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp).width(440.dp).height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                val angles = listOf(-14f, -7f, 0f, 7f, 14f)
                val shifts = listOf(-150, -76, 0, 76, 150)
                // The middle cover last, so it sits on top of the fan.
                listOf(0, 4, 1, 3, 2).forEach { i ->
                    CoverImage(
                        model = fan[i].coverModel,
                        modifier = Modifier
                            .size(if (i == 2) 250.dp else 214.dp)
                            .graphicsLayer {
                                rotationZ = angles[i]
                                translationX = shifts[i].dp.toPx()
                                translationY = (if (i == 2) -8 else 14).dp.toPx()
                                shadowElevation = 18.dp.toPx()
                                shape = RoundedCornerShape(10.dp)
                                clip = true
                            },
                    )
                }
            }
        }
    }

    // ── device chrome ────────────────────────────────────────────────────────────────────────

    /**
     * A phone frame around [content]: Homer's ground, then a status bar and the gesture handle drawn
     * into the insets the test hands the window — the content lays itself out around those insets
     * exactly as it does under real system bars.
     */
    @Composable
    fun Phone(statusBar: Dp, navigationBar: Dp, content: @Composable () -> Unit) {
        Surface(color = Ground, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
                StatusBar(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusBar))
                GestureHandle(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navigationBar))
            }
        }
    }

    @Composable
    private fun StatusBar(modifier: Modifier) {
        Row(
            modifier = modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("10:24", color = Parchment, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            val tint = Parchment.copy(alpha = 0.92f)
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.SignalCellular4Bar, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Battery5Bar, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
    }

    @Composable
    private fun GestureHandle(modifier: Modifier) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(108.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Muted.copy(alpha = 0.8f)),
            )
        }
    }
}
