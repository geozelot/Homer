package com.geozelot.homer.ui.home

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.ui.components.CoverImage
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
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val continueShelf by viewModel.continueShelf.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val gridView by viewModel.gridView.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val groupMode by viewModel.groupMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<BookListItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val actions = remember(viewModel) {
        BookActions(
            onDownload = viewModel::download,
            onRemove = viewModel::deleteDownload,
            onEdit = { editing = it },
            onSetHidden = viewModel::setHidden,
            onSetFinished = viewModel::setFinished,
        )
    }

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
            onSettings = { showSettings = true },
        )

        if (entries.isEmpty()) {
            if (searching && searchQuery.isNotBlank()) {
                EmptyResults(modifier = Modifier.weight(1f))
            } else {
                EmptyLibrary(
                    scanning = scanState is ScanState.Scanning,
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (gridView) 3 else 1),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                libraryContent(
                    entries = entries,
                    continueShelf = if (searching) emptyList() else continueShelf,
                    bookCount = bookCount,
                    gridView = gridView,
                    searching = searching,
                    sortMode = sortMode,
                    groupMode = groupMode,
                    onSortChange = viewModel::setSortMode,
                    onGroupChange = viewModel::setGroupMode,
                    expanded = expanded,
                    onToggleView = viewModel::setGridView,
                    onBookClick = onBookClick,
                    actions = actions,
                )
            }
        }

        MiniPlayer(
            state = playback,
            onOpenPlayer = onBookClick,
            onPlayPause = viewModel::playPause,
            modifier = Modifier.navigationBarsPadding(),
        )
    }

    if (showSettings) {
        SettingsSheet(viewModel = viewModel, onDismiss = { showSettings = false })
    }

    editing?.let { book ->
        EditBookDialog(
            book = book,
            onSave = { title, author, series, index, genre, tags, hidden ->
                viewModel.saveOverride(book.id, title, author, series, index, genre, tags, hidden)
                editing = null
            },
            onReset = {
                viewModel.clearOverride(book.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Callbacks a card/row needs for its context menu, bundled to keep signatures small. */
private class BookActions(
    val onDownload: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onEdit: (BookListItem) -> Unit,
    val onSetHidden: (String, Boolean) -> Unit,
    val onSetFinished: (String, Boolean?) -> Unit,
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
            Wordmark("Homer")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = Muted)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Muted)
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search", tint = Muted)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Title, author, genre, tag…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Faint) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Muted)
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

private fun LazyGridScope.libraryContent(
    entries: List<LibraryEntry>,
    continueShelf: List<BookListItem>,
    bookCount: Int,
    gridView: Boolean,
    searching: Boolean,
    sortMode: LibrarySort,
    groupMode: LibraryGroup,
    onSortChange: (LibrarySort) -> Unit,
    onGroupChange: (LibraryGroup) -> Unit,
    expanded: MutableMap<String, Boolean>,
    onToggleView: (Boolean) -> Unit,
    onBookClick: (String) -> Unit,
    actions: BookActions,
) {
    if (continueShelf.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "continue-head") { SectionLabelRow("Continue") }
        item(span = { GridItemSpan(maxLineSpan) }, key = "continue-shelf") {
            ContinueShelf(books = continueShelf, onOpen = onBookClick)
        }
    }

    item(span = { GridItemSpan(maxLineSpan) }, key = "library-head") {
        LibraryHeader(
            label = if (searching) "RESULTS" else "LIBRARY · $bookCount",
            gridView = gridView,
            onToggleView = onToggleView,
        )
    }

    item(span = { GridItemSpan(maxLineSpan) }, key = "sort-group") {
        SortGroupBar(sortMode, groupMode, onSortChange, onGroupChange)
    }

    entries.forEach { entry ->
        when (entry) {
            is LibraryEntry.Header -> item(
                span = { GridItemSpan(maxLineSpan) },
                key = "header:${entry.title}",
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
                val isOpen = expanded[entry.key] == true
                if (gridView) {
                    if (isOpen) {
                        // One bordered container = the whole opened series card (no Back button).
                        item(span = { GridItemSpan(maxLineSpan) }, key = "series-open:${entry.key}") {
                            ExpandedSeriesContainer(
                                series = entry,
                                onCollapse = { expanded[entry.key] = false },
                                onBookClick = onBookClick,
                                actions = actions,
                            )
                        }
                    } else {
                        item(key = "series:${entry.key}") {
                            SeriesGridCard(entry) { expanded[entry.key] = true }
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "series:${entry.key}") {
                        SeriesShelfRow(entry, isOpen) { expanded[entry.key] = !isOpen }
                    }
                    if (isOpen) {
                        items(
                            entry.books,
                            span = { GridItemSpan(maxLineSpan) },
                            key = { "ep:${it.id}" },
                        ) { book ->
                            BookListRow(book, startPadding = 12.dp, onOpen = onBookClick, actions = actions)
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
            ViewToggleButton(Icons.Filled.GridView, selected = gridView, desc = "Grid view") {
                onToggleView(true)
            }
            ViewToggleButton(Icons.AutoMirrored.Filled.ViewList, selected = !gridView, desc = "List view") {
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
            .size(width = 32.dp, height = 28.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DropdownChip(
            label = "Sort · ${sort.label}",
            options = LibrarySort.values().toList(),
            selected = sort,
            labelOf = { it.label },
            onSelect = onSortChange,
        )
        DropdownChip(
            label = "Group · ${group.label}",
            options = LibraryGroup.values().toList(),
            selected = group,
            labelOf = { it.label },
            onSelect = onGroupChange,
        )
    }
}

@Composable
private fun <T> DropdownChip(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Line, RoundedCornerShape(8.dp))
                .background(Surface1)
                .clickable { open = true }
                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Muted, fontSize = 11.sp)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Faint, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(labelOf(option), color = if (option == selected) Amber else Parchment)
                    },
                    onClick = { onSelect(option); open = false },
                )
            }
        }
    }
}

// ── Continue shelf ─────────────────────────────────────────────────────────

@Composable
private fun ContinueShelf(books: List<BookListItem>, onOpen: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(books, key = { "cont:${it.id}" }) { book ->
            ContinueCard(book, onOpen)
        }
    }
}

@Composable
private fun ContinueCard(book: BookListItem, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable { onOpen(book.id) },
    ) {
        CoverArt(
            model = book.coverModel,
            title = book.title,
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
        Text(
            text = book.timeLeftMs?.let {
                if (it <= 0) "finished" else "${formatCompactDuration(it)} left"
            } ?: (book.author ?: "Unknown author"),
            color = Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
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
    val showCheck = book.finished || book.isDownloaded

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
            CoverArt(model = book.coverModel, title = book.title, modifier = Modifier.fillMaxSize())
            if (showCheck) {
                StatusBadge(modifier = Modifier.align(Alignment.TopEnd).padding(7.dp))
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
            BookMenu(book, menuOpen, actions) { menuOpen = false }
        }
        GridCardText(title = book.title, meta = bookCardMeta(book))
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

private fun bookCardMeta(book: BookListItem): String = buildString {
    append(book.author ?: "Unknown author")
    book.totalDurationMs?.takeIf { it > 0 }?.let { append('\n'); append(formatCompactDuration(it)) }
}

// The stack fans diagonally up-right and is scaled so the whole stack fills the cell — the
// same footprint as a single book card. STACK_SPREAD is the fraction of the cell the fan
// spans; the covers are (1 - STACK_SPREAD) of the cell in each dimension.
private const val STACK_SPREAD = 0.18f

/** A series as a grid cell the same size as a book card: a diagonal stack filling the cell. */
@Composable
private fun SeriesGridCard(series: LibraryEntry.Series, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
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
                    title = book.title,
                    modifier = Modifier
                        .offset(x = pad + dx * depth, y = pad + dy * (steps - depth))
                        .width(coverW)
                        .aspectRatio(1f / 1.5f)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.5.dp, Ground, RoundedCornerShape(9.dp)),
                )
            }
        }
        GridCardText(title = series.name, meta = seriesCardMeta(series))
    }
}

private fun seriesCardMeta(series: LibraryEntry.Series): String = buildString {
    append(seriesMeta(series))
    // Whole-series length as a second line, once every episode is measured.
    val measured = series.books.mapNotNull { it.totalDurationMs?.takeIf { d -> d > 0 } }
    if (measured.size == series.books.size && measured.isNotEmpty()) {
        append('\n'); append(formatCompactDuration(measured.sum()))
    }
}

/**
 * Expanded series: one faint-bordered container holding a header row (tap to collapse) and
 * the series' books as a 3-column grid inside it — so the whole thing reads as a single
 * opened card with a clear start and end. No Back button, no bottom row.
 */
@Composable
private fun ExpandedSeriesContainer(
    series: LibraryEntry.Series,
    onCollapse: () -> Unit,
    onBookClick: (String) -> Unit,
    actions: BookActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCollapse),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(series.name, style = SerifTitle, color = Parchment, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(seriesMeta(series), color = Muted, fontSize = 11.5.sp)
            }
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Collapse series", tint = Amber)
        }
        // 3-up grid of the series' books, laid out row by row inside the container.
        series.books.chunked(3).forEach { rowBooks ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowBooks.forEach { book ->
                    Box(modifier = Modifier.weight(1f)) {
                        BookGridCard(book, onOpen = onBookClick, actions = actions)
                    }
                }
                repeat(3 - rowBooks.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

private fun seriesMeta(series: LibraryEntry.Series): String = buildString {
    append("${series.books.size} episodes")
    val downloaded = series.books.count { it.isDownloaded }
    if (downloaded > 0) append(" · $downloaded offline")
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
                title = book.title,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (book.finished || book.isDownloaded) {
                StatusBadge(
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listRowMeta(book),
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            if (book.genre != null || book.tags.isNotEmpty() || book.isDownloaded) {
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    book.genre?.let { TagChip(it, Amber, AmberSoft) }
                    book.tags.take(3).forEach { TagChip(it, Muted, Surface2) }
                    if (book.isDownloaded) TagChip("offline", Sage, SageSoft)
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Faint)
            }
            BookMenu(book, menuOpen, actions) { menuOpen = false }
        }
    }
}

private fun listRowMeta(book: BookListItem): String = buildString {
    append(book.author ?: "Unknown author")
    book.totalDurationMs?.takeIf { it > 0 }?.let { append(" · ${formatCompactDuration(it)}") }
    when {
        book.finished -> append(" · finished")
        book.progress != null -> append(" · ${(book.progress * 100).toInt()}%")
    }
}

@Composable
private fun TagChip(text: String, fg: Color, bg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 9.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

// ── Series shelf row ───────────────────────────────────────────────────────

@Composable
private fun SeriesShelfRow(series: LibraryEntry.Series, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(Surface1)
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Stacked-covers glyph (up to three, fanned rightward; last drawn sits on top).
        Box(modifier = Modifier.size(width = 52.dp, height = 56.dp)) {
            series.books.take(3).forEachIndexed { i, book ->
                CoverArt(
                    model = book.coverModel,
                    title = book.title,
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
            val downloaded = series.books.count { it.isDownloaded }
            Text(
                text = buildString {
                    append("${series.books.size} episodes")
                    if (downloaded > 0) append(" · $downloaded offline")
                },
                color = Muted,
                fontSize = 11.5.sp,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = Faint,
        )
    }
}

// ── Status badge, progress ring ───────────────────────────────────────────────

@Composable
private fun StatusBadge(modifier: Modifier = Modifier, iconSize: Dp = 11.dp) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Studio.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = "Finished or offline", tint = Sage, modifier = Modifier.size(iconSize))
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
private fun CoverArt(model: Any?, title: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (model != null) {
            CoverImage(model = model, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Surface2, Surface1))),
            )
            Text(
                title,
                style = SerifTitle.copy(fontSize = 13.sp, lineHeight = 15.sp),
                color = Parchment,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
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
        DropdownMenuItem(
            text = { Text("Play") },
            leadingIcon = { Icon(Icons.Filled.PlayArrow, null, tint = Amber) },
            onClick = onDismiss, // tapping the card already opens the player
        )
        if (book.isDownloaded) {
            DropdownMenuItem(
                text = { Text("Remove download") },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Muted) },
                onClick = { actions.onRemove(book.id); onDismiss() },
            )
        } else {
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = { Icon(Icons.Filled.Download, null, tint = Muted) },
                onClick = { actions.onDownload(book.id); onDismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text(if (book.finished) "Mark unfinished" else "Mark finished") },
            leadingIcon = { Icon(Icons.Filled.Check, null, tint = if (book.finished) Sage else Muted) },
            onClick = { actions.onSetFinished(book.id, !book.finished); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Edit") },
            leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Muted) },
            onClick = { actions.onEdit(book); onDismiss() },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(if (book.hidden) "Unhide" else "Hide") },
            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Muted) },
            onClick = { actions.onSetHidden(book.id, !book.hidden); onDismiss() },
        )
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyResults(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No matches", style = SerifTitle, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text("Try a different title, author, genre or tag.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyLibrary(scanning: Boolean, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (scanning) {
            CircularProgressIndicator(color = Amber)
            Spacer(Modifier.height(16.dp))
            Text("Scanning your library…", color = Muted, fontSize = 14.sp)
        } else {
            Text("Your shelf is empty", style = SerifTitle, color = Parchment)
            Spacer(Modifier.height(8.dp))
            Text(
                "Set your library folder and scan to fill it.",
                color = Muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

// ── Settings sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(viewModel: HomeViewModel, onDismiss: () -> Unit) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val libraryRoot by viewModel.libraryRoot.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val syncTier by viewModel.syncTier.collectAsStateWithLifecycle()
    val tier3Available by viewModel.tier3Available.collectAsStateWithLifecycle()
    val scanning = scanState is ScanState.Scanning

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Settings", style = SerifTitle, color = Parchment)
                    account?.let {
                        Text(
                            "${it.loginName} · ${it.serverUrl.substringAfter("://")}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                TextButton(onClick = viewModel::logout) { Text("Log out") }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = libraryRoot,
                onValueChange = viewModel::onLibraryRootChange,
                label = { Text("Library folder") },
                placeholder = { Text("e.g. Audiobooks (blank = whole drive)") },
                singleLine = true,
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = viewModel::scan, enabled = !scanning) {
                    Text(if (scanning) "Scanning…" else "Scan library")
                }
                ScanStatus(scanState = scanState, bookCount = bookCount)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            SyncTierSelector(
                current = syncTier,
                tier3Available = tier3Available,
                onSelect = viewModel::setSyncTier,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            SettingSwitch("Download on Wi‑Fi only", wifiOnly, viewModel::setWifiOnlyDownloads)
            SettingSwitch("Show hidden books", showHidden, viewModel::setShowHidden)
        }
    }
}

@Composable
private fun SyncTierSelector(current: Int, tier3Available: Boolean, onSelect: (Int) -> Unit) {
    Text("SYNC", style = SectionLabel, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp)),
    ) {
        TierOption(
            selected = current <= 1,
            title = "On this device only",
            subtitle = "Nothing is written to your server.",
            onClick = { onSelect(1) },
        )
        HorizontalDivider(color = Line)
        TierOption(
            selected = current == 2,
            title = "My devices",
            subtitle = "Positions, bookmarks and edits sync privately across your devices.",
            onClick = { onSelect(2) },
        )
        HorizontalDivider(color = Line)
        TierOption(
            selected = current >= 3,
            title = "Shared library",
            subtitle = if (tier3Available) {
                "Uses the shared library catalog — no scan needed on new devices."
            } else {
                "Publishes the full library so others (and new devices) skip scanning."
            },
            onClick = { onSelect(3) },
        )
    }
}

@Composable
private fun TierOption(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) AmberSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (selected) Amber else Parchment, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Amber, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Parchment, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ScanStatus(scanState: ScanState, bookCount: Int) {
    when (val state = scanState) {
        is ScanState.Scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Amber, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("${state.directoriesVisited} folders · ${state.booksFound} books", color = Muted, fontSize = 12.sp)
        }
        is ScanState.Done -> Text("$bookCount books indexed", color = Muted, fontSize = 12.sp)
        is ScanState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        ScanState.Idle -> if (bookCount > 0) Text("$bookCount books", color = Muted, fontSize = 12.sp)
    }
}

// ── Edit dialog (unchanged behaviour) ─────────────────────────────────────────

@Composable
private fun EditBookDialog(
    book: BookListItem,
    onSave: (title: String, author: String, series: String, index: String, genre: String, tags: String, hidden: Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author.orEmpty()) }
    var series by remember { mutableStateOf(book.series.orEmpty()) }
    var index by remember { mutableStateOf(book.seriesIndex?.toString().orEmpty()) }
    var genre by remember { mutableStateOf(book.genre.orEmpty()) }
    var tags by remember { mutableStateOf(book.tags.joinToString(", ")) }
    var hidden by remember { mutableStateOf(book.hidden) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit book") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = series,
                    onValueChange = { series = it },
                    label = { Text("Series") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = index,
                    onValueChange = { index = it },
                    label = { Text("Series #") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags") },
                    placeholder = { Text("comma, separated") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Hide from library", fontSize = 14.sp)
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, author, series, index, genre, tags, hidden) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
