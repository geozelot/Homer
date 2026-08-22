package com.geozelot.homer.ui.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.data.storage.StorageMigrator
import com.geozelot.homer.ui.storage.StorageBrowserScreen
import com.geozelot.homer.ui.components.HomerSwitch
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.ui.components.CoverImage
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
import com.geozelot.homer.ui.theme.OnAmber
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
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onLinkSyncAccount: () -> Unit,
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
    val pendingStorage by viewModel.pendingStorageChange.collectAsStateWithLifecycle()
    val migration by viewModel.migrationProgress.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<BookListItem?>(null) }
    var editingSeries by remember { mutableStateOf<LibraryEntry.Series?>(null) }
    var showAppSettings by remember { mutableStateOf(false) }
    // rememberSaveable so a rotation doesn't drop the user out of these: `searching` in particular
    // used to be lost while the query stayed in the ViewModel, leaving the library filtered with no
    // search field to clear it.
    var showStorageBrowser by rememberSaveable { mutableStateOf(false) }
    var showLibrarySync by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // The storage browser is a full-screen overlay inside this destination, not a nav entry, so
    // without this back would pop the start destination and exit the app mid-folder-pick.
    BackHandler(enabled = showStorageBrowser) { showStorageBrowser = false }
    // Back should leave search rather than leave the app with the library still filtered.
    BackHandler(enabled = searching && !showStorageBrowser) {
        searching = false
        viewModel.setSearchQuery("")
    }

    val actions = remember(viewModel) {
        BookActions(
            onDownload = viewModel::download,
            onRemove = viewModel::deleteDownload,
            onEdit = { editing = it },
            onSetHidden = viewModel::setHidden,
            onMarkCompleted = viewModel::markCompleted,
            onEditSeries = { editingSeries = it },
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
            onOpenLibrarySync = { showLibrarySync = true },
            onSettings = { showAppSettings = true },
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
                        scanning = false,
                        onOpenSettings = { showLibrarySync = true },
                        modifier = Modifier.weight(1f),
                    )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (gridView) 3 else 1),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                libraryContent(
                    entries = entries,
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
            onRetry = viewModel::retry,
            modifier = Modifier.navigationBarsPadding(),
            liveCover = miniPlayerBook?.coverModel,
            liveTitle = miniPlayerBook?.title,
        )
    }

    if (showAppSettings) {
        AppSettingsSheet(
            viewModel = viewModel,
            onOpenLicenses = {
                showAppSettings = false
                onOpenLicenses()
            },
            onOpenPrivacy = {
                showAppSettings = false
                onOpenPrivacy()
            },
            onOpenDiagnostics = {
                showAppSettings = false
                onOpenDiagnostics()
            },
            onOpenStorageBrowser = {
                showAppSettings = false
                showStorageBrowser = true
            },
            onLinkSyncAccount = {
                showAppSettings = false
                onLinkSyncAccount()
            },
            onDismiss = { showAppSettings = false },
        )
    }
    if (showLibrarySync) {
        LibrarySyncSheet(viewModel = viewModel, onDismiss = { showLibrarySync = false })
    }

    if (showStorageBrowser) {
        StorageBrowserScreen(
            onPicked = { path ->
                viewModel.setCustomStoragePath(path)
                showStorageBrowser = false
            },
            onBack = { showStorageBrowser = false },
        )
    }

    editing?.let { book ->
        EditBookDialog(
            book = book.toEditable(),
            onSave = { title, author, series, index, genre, tags, hidden, downloadOnPlay ->
                viewModel.saveOverride(book.id, title, author, series, index, genre, tags, hidden, downloadOnPlay)
                editing = null
            },
            onReset = {
                viewModel.clearOverride(book.id)
                editing = null
            },
            onPickCover = { uri -> viewModel.setCustomCover(book.id, uri) },
            onClearCover = { viewModel.clearCustomCover(book.id) },
            onDismiss = { editing = null },
        )
    }

    editingSeries?.let { series ->
        SeriesEditDialog(
            series = series,
            onSave = { name, author ->
                viewModel.saveSeriesOverride(series.books.map { it.id }, name, author)
                editingSeries = null
            },
            onDismiss = { editingSeries = null },
        )
    }

    pendingStorage?.let {
        StorageConflictDialog(
            onLoad = viewModel::loadPendingStorage,
            onReplace = viewModel::replacePendingStorage,
            onCancel = viewModel::cancelPendingStorage,
        )
    }

    migration?.let { MigrationDialog(it) }
}

/** Asked when the chosen storage folder already holds a Homer library. */
@Composable
private fun StorageConflictDialog(onLoad: () -> Unit, onReplace: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.home_storage_conflict_title)) },
        text = {
            Text(stringResource(R.string.home_storage_conflict_body))
        },
        confirmButton = { TextButton(onClick = onLoad) { Text(stringResource(R.string.home_storage_load)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onReplace) { Text(stringResource(R.string.home_storage_replace)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Blocking overlay shown while a storage move runs (not cancelable). */
@Composable
private fun MigrationDialog(progress: StorageMigrator.Progress) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.home_migration_title)) },
        text = {
            Column {
                Text(progress.label, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = Amber,
                    )
                    Text(
                        stringResource(R.string.home_migration_files, progress.done, progress.total),
                        color = Faint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Amber)
                }
            }
        },
        confirmButton = {},
    )
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
    onOpenLibrarySync: () -> Unit,
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
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_cd_menu), tint = Muted)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_cd_library_sync)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, tint = Muted) },
                            onClick = { menuOpen = false; onOpenLibrarySync() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_cd_settings)) },
                            leadingIcon = { Icon(Icons.Filled.Settings, null, tint = Muted) },
                            onClick = { menuOpen = false; onSettings() },
                        )
                    }
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

private fun LazyGridScope.libraryContent(
    entries: List<LibraryEntry>,
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
    // The Continue shelf is no longer an item here — it's pinned above the grid by HomeScreen so
    // it can stay put and collapse while the library scrolls under it.
    item(span = { GridItemSpan(maxLineSpan) }, key = "library-head") {
        LibraryHeader(
            label = if (searching) stringResource(R.string.home_section_results) else stringResource(R.string.home_section_library, bookCount),
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
                        // Full-span header banner, then each episode as its own grid cell — so a
                        // large series composes lazily instead of all at once in a single item.
                        item(span = { GridItemSpan(maxLineSpan) }, key = "series-open:${entry.key}") {
                            ExpandedSeriesHeader(
                                series = entry,
                                onCollapse = { expanded[entry.key] = false },
                            )
                        }
                        items(entry.books, key = { "sep:${it.id}" }) { book ->
                            BookGridCard(book, onOpen = onBookClick, actions = actions)
                        }
                    } else {
                        item(key = "series:${entry.key}") {
                            SeriesGridCard(
                                series = entry,
                                onOpen = { expanded[entry.key] = true },
                                onEdit = { actions.onEditSeries(entry) },
                            )
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "series:${entry.key}") {
                        SeriesShelfRow(
                            series = entry,
                            expanded = isOpen,
                            onToggle = { expanded[entry.key] = !isOpen },
                            onEdit = { actions.onEditSeries(entry) },
                        )
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
    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        if (!collapsed) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionLabelRow(stringResource(R.string.home_section_continue))
            }
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // Padding on the row (not the parent) so cards bleed off the edge while scrolling
            // instead of stopping at a hard margin.
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (collapsed) 6.dp else 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(books, key = { "cont:${it.id}" }) { book ->
                if (collapsed) ContinueSlim(book, onOpen) else ContinueCard(book, onOpen, actions)
            }
        }
        // A hairline separates the pinned strip from the library scrolling beneath it.
        if (collapsed) HorizontalDivider(color = Line)
    }
}

/** Collapsed Continue entry: small cover, one-line title, progress hairline. */
@Composable
private fun ContinueSlim(book: BookListItem, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 200.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen(book.id) }
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            model = book.coverModel,
            title = book.title,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(5.dp)),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                book.title,
                color = Parchment,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressBar(
                fraction = book.progress ?: 0f,
                modifier = Modifier
                    .width(96.dp)
                    .padding(top = 4.dp),
            )
        }
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
            CoverArt(model = book.coverModel, title = book.title, modifier = Modifier.fillMaxSize())
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

/**
 * Header banner for an expanded series in grid view (tap to collapse). The episodes follow as
 * their own lazy grid cells, so a large series doesn't compose every card in one pass.
 */
@Composable
private fun ExpandedSeriesHeader(
    series: LibraryEntry.Series,
    onCollapse: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(Surface1)
            .clickable(onClick = onCollapse)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(series.name, style = SerifTitle, color = Parchment, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(seriesMeta(series, LocalContext.current), color = Muted, fontSize = 11.5.sp)
        }
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.home_cd_collapse_series), tint = Amber)
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
                title = book.title,
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
                maxLines = 1,
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

@Composable
private fun TagChip(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = fg,
        fontSize = 9.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
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
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(Surface1)
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
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
        Icon(Icons.Filled.DownloadDone, contentDescription = stringResource(R.string.home_cd_downloaded), tint = Sage, modifier = Modifier.size(iconSize))
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
                    contentDescription = title,
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
            Text(stringResource(R.string.home_scanning), color = Muted, fontSize = 14.sp)
        } else {
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
}

// ── App settings sheet (general) ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsSheet(
    viewModel: HomeViewModel,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenStorageBrowser: () -> Unit,
    onLinkSyncAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val libraryIsShare by viewModel.libraryIsShare.collectAsStateWithLifecycle()
    val syncAccount by viewModel.syncAccount.collectAsStateWithLifecycle()
    val libraryWritable by viewModel.libraryWritable.collectAsStateWithLifecycle()
    val onlineCovers by viewModel.onlineCoverLookup.collectAsStateWithLifecycle()
    val customStorageUri by viewModel.customStorageUri.collectAsStateWithLifecycle()
    val customStoragePath by viewModel.customStoragePath.collectAsStateWithLifecycle()
    val seekSeconds by viewModel.seekSeconds.collectAsStateWithLifecycle()
    val autoRewind by viewModel.autoRewindSeconds.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val downloadOnPlay by viewModel.downloadOnPlay.collectAsStateWithLifecycle()
    val appLock by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val certPinning by viewModel.certPinningEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            android.util.Log.d("HomerStore", "folder picker returned: $uri") // Log.d: carries a storage path
            viewModel.setCustomStorageFolder(uri)
        } else {
            android.util.Log.w("HomerStore", "folder picker returned null (cancelled or denied by the system)")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // MUST scroll: a ModalBottomSheet measures its content against a bounded height,
                // so without this a Column silently crushes its last children to zero height —
                // which is exactly how the About rows and version line vanished on shorter
                // screens / larger font scales. imePadding keeps fields clear of the keyboard.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.settings_title), style = SerifTitle, color = Parchment)
                    account?.let {
                        val host = it.serverUrl.substringAfter("://")
                        val label = if (libraryIsShare) {
                            stringResource(R.string.settings_library_share, host) +
                                if (!libraryWritable) stringResource(R.string.settings_library_readonly) else ""
                        } else {
                            stringResource(R.string.settings_account, it.loginName, host)
                        }
                        Text(label, color = Muted, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = viewModel::logout) { Text(stringResource(R.string.settings_logout)) }
            }

            // A share library keeps progress on-device unless the user links a personal account.
            if (libraryIsShare) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sync = syncAccount
                    Text(
                        if (sync != null) {
                            stringResource(R.string.settings_sync_to, "${sync.loginName}@${sync.serverUrl.substringAfter("://")}")
                        } else {
                            stringResource(R.string.settings_sync_device)
                        },
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (syncAccount != null) {
                        TextButton(onClick = viewModel::unlinkSyncAccount) { Text(stringResource(R.string.settings_sync_stop)) }
                    } else {
                        TextButton(onClick = onLinkSyncAccount) { Text(stringResource(R.string.settings_sync_link)) }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            val custom = customStoragePath ?: customStorageUri
            Text(stringResource(R.string.settings_storage_header), style = SectionLabel, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
            Text(
                when {
                    customStoragePath != null -> stringResource(R.string.settings_storage_folder, customStoragePath!!)
                    customStorageUri != null -> stringResource(R.string.settings_storage_custom_folder, storageFolderName(customStorageUri!!))
                    else -> stringResource(R.string.settings_storage_default)
                },
                color = Parchment,
                fontSize = 14.sp,
            )
            Text(
                if (custom != null) {
                    stringResource(R.string.settings_storage_custom_desc)
                } else {
                    stringResource(R.string.settings_storage_default_desc)
                },
                color = Faint,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { folderPicker.launch(null) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text(stringResource(R.string.settings_storage_system_picker)) }
                TextButton(
                    onClick = onOpenStorageBrowser,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text(stringResource(R.string.settings_storage_browse)) }
                if (custom != null) {
                    TextButton(
                        onClick = viewModel::useDefaultStorage,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text(stringResource(R.string.settings_storage_use_app)) }
                }
            }
            Text(
                stringResource(R.string.settings_storage_picker_desc),
                color = Faint,
                fontSize = 11.sp,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            SettingSwitch(stringResource(R.string.settings_online_covers), onlineCovers, viewModel::setOnlineCoverLookup)
            Text(
                stringResource(R.string.settings_online_covers_desc),
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_skip_interval), color = Parchment, fontSize = 14.sp)
                DropdownChip(
                    label = stringResource(R.string.settings_seconds, seekSeconds),
                    options = listOf(5, 10, 15, 20, 30, 45, 60),
                    selected = seekSeconds,
                    labelOf = { context.getString(R.string.settings_seconds, it) },
                    onSelect = viewModel::setSeekSeconds,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_rewind), color = Parchment, fontSize = 14.sp)
                DropdownChip(
                    label = if (autoRewind == 0) stringResource(R.string.settings_off) else stringResource(R.string.settings_seconds, autoRewind),
                    options = listOf(0, 5, 10, 15, 20, 30),
                    selected = autoRewind,
                    labelOf = { if (it == 0) context.getString(R.string.settings_off) else context.getString(R.string.settings_seconds, it) },
                    onSelect = viewModel::setAutoRewindSeconds,
                )
            }
            SettingSwitch(stringResource(R.string.settings_download_on_play), downloadOnPlay, viewModel::setDownloadOnPlay)
            Text(
                stringResource(R.string.settings_download_on_play_desc),
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            SettingSwitch(stringResource(R.string.settings_wifi_only), wifiOnly, viewModel::setWifiOnlyDownloads)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            Text(stringResource(R.string.settings_security_header), style = SectionLabel, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
            SettingSwitch(stringResource(R.string.settings_app_lock), appLock, viewModel::setAppLock)
            Text(
                stringResource(R.string.settings_app_lock_desc),
                color = Muted,
                fontSize = 11.sp,
            )
            SettingSwitch(stringResource(R.string.settings_cert_pinning), certPinning, viewModel::setCertPinning)
            Text(
                stringResource(R.string.settings_cert_pinning_desc),
                color = Muted,
                fontSize = 11.sp,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            Text(stringResource(R.string.settings_about_header), style = SectionLabel, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
            NavRow(stringResource(R.string.about_privacy_title), onOpenPrivacy)
            NavRow(stringResource(R.string.about_licenses_title), onOpenLicenses)
            NavRow(stringResource(R.string.settings_diagnostics), onOpenDiagnostics)

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = Faint, fontSize = 11.sp)
        }
    }
}

/** A tappable settings row that navigates to another screen (label + trailing chevron). */
@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Parchment, fontSize = 14.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted)
    }
}

/** A readable folder name from a SAF tree Uri (e.g. …/tree/primary%3AAudiobooks → "Audiobooks"). */
private fun storageFolderName(treeUri: String): String =
    Uri.decode(treeUri).substringAfterLast('/').substringAfterLast(':').ifBlank { "selected folder" }

// ── Library & Sync sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySyncSheet(viewModel: HomeViewModel, onDismiss: () -> Unit) {
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val libraryRoot by viewModel.libraryRoot.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val progressSync by viewModel.progressSyncEnabled.collectAsStateWithLifecycle()
    val sharedCatalog by viewModel.sharedCatalogEnabled.collectAsStateWithLifecycle()
    val sharedCatalogAvailable by viewModel.sharedCatalogAvailable.collectAsStateWithLifecycle()
    val libraryOwner by viewModel.libraryOwner.collectAsStateWithLifecycle()
    val discovered by viewModel.discovered.collectAsStateWithLifecycle()
    val discovering by viewModel.discovering.collectAsStateWithLifecycle()
    val scanning = scanState is ScanState.Scanning

    // Sweep for Homer-bearing folders when the sheet opens.
    LaunchedEffect(Unit) { viewModel.rediscover() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.sync_title), style = SerifTitle, color = Parchment)

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = libraryRoot,
                onValueChange = viewModel::onLibraryRootChange,
                label = { Text(stringResource(R.string.sync_library_folder)) },
                placeholder = { Text(stringResource(R.string.sync_library_folder_placeholder)) },
                singleLine = true,
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
            )

            // Primary actions as top-level pills.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = viewModel::scan, enabled = !scanning, modifier = Modifier.weight(1f)) {
                    Text(if (scanning) stringResource(R.string.sync_scanning) else stringResource(R.string.sync_scan_library))
                }
                FilledTonalButton(onClick = viewModel::refreshCoverArt, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sync_refresh_covers))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanStatus(scanState = scanState, bookCount = bookCount)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::fullScan, enabled = !scanning, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(stringResource(R.string.sync_full_rescan))
                }
            }
            Text(
                stringResource(R.string.sync_scan_desc),
                color = Faint,
                fontSize = 11.sp,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            DiscoveredLibraries(
                discovered = discovered,
                discovering = discovering,
                // The button is an explicit request: bypass the freshness throttle.
                onRediscover = { viewModel.rediscover(force = true) },
                onUse = viewModel::onLibraryRootChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            SyncSettings(
                progressSync = progressSync,
                sharedCatalog = sharedCatalog,
                sharedCatalogAvailable = sharedCatalogAvailable,
                owner = libraryOwner,
                onProgressSync = viewModel::setProgressSync,
                onSharedCatalog = viewModel::setSharedCatalog,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Line)

            SettingSwitch(stringResource(R.string.sync_show_hidden), showHidden, viewModel::setShowHidden)
        }
    }
}

@Composable
private fun DiscoveredLibraries(
    discovered: List<DiscoveredLibrary>,
    discovering: Boolean,
    onRediscover: () -> Unit,
    onUse: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.sync_discovered_header), style = SectionLabel, color = Muted)
        if (discovering) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onRediscover, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(stringResource(R.string.sync_rediscover))
            }
        }
    }
    if (discovered.isEmpty()) {
        Text(
            if (discovering) stringResource(R.string.sync_discovering) else stringResource(R.string.sync_no_libraries),
            color = Faint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else {
        discovered.forEach { lib -> DiscoveredLibraryCard(lib, onUse) }
    }
}

@Composable
private fun DiscoveredLibraryCard(lib: DiscoveredLibrary, onUse: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (lib.isCurrentRoot) AmberSoft else Surface1)
            .border(1.dp, if (lib.isCurrentRoot) Amber else Line, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lib.relativePath.ifEmpty { stringResource(R.string.sync_home_files_root) },
                color = Parchment,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (lib.isCurrentRoot) TagChip(stringResource(R.string.sync_tag_in_use), OnAmber, Amber)
        }
        val detail = buildString {
            append(
                when (lib.kind) {
                    DiscoveredLibrary.Kind.FILES_ROOT -> context.getString(R.string.sync_kind_files_root)
                    DiscoveredLibrary.Kind.LIBRARY_ROOT -> context.getString(R.string.sync_kind_library_root)
                    DiscoveredLibrary.Kind.SHARED_FOLDER -> context.getString(R.string.sync_kind_shared_folder)
                },
            )
            if (lib.hasSharedCatalog) {
                append(context.getString(R.string.sync_detail_shared_catalog))
                lib.bookCount?.let { append(context.getString(R.string.sync_detail_book_count, it)) }
            }
            if (lib.hasPrivateIndex) append(context.getString(R.string.sync_detail_private_progress))
            lib.owner?.let { append(context.getString(R.string.sync_detail_owner, it)) }
        }
        Text(detail, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        if (!lib.isCurrentRoot) {
            TextButton(
                onClick = { onUse(lib.relativePath) },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text(stringResource(R.string.sync_use_as_library)) }
        }
    }
}

/**
 * Two independent switches (they replaced a linear 1/2/3 tier): syncing my own progress, and
 * using the shared library catalog. They're orthogonal — either can be on without the other.
 */
@Composable
private fun SyncSettings(
    progressSync: Boolean,
    sharedCatalog: Boolean,
    sharedCatalogAvailable: Boolean,
    owner: String?,
    onProgressSync: (Boolean) -> Unit,
    onSharedCatalog: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Text(stringResource(R.string.sync_sync_header), style = SectionLabel, color = Muted, modifier = Modifier.padding(bottom = 6.dp))

    SettingSwitch(stringResource(R.string.sync_progress_switch), progressSync, onProgressSync)
    Text(
        if (progressSync) {
            stringResource(R.string.sync_progress_on_desc)
        } else {
            stringResource(R.string.sync_progress_off_desc)
        },
        color = Muted,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )

    SettingSwitch(stringResource(R.string.sync_shared_switch), sharedCatalog, onSharedCatalog)
    Text(
        buildString {
            append(
                when {
                    sharedCatalog && sharedCatalogAvailable ->
                        context.getString(R.string.sync_shared_reads)
                    sharedCatalog ->
                        context.getString(R.string.sync_shared_publishes)
                    else ->
                        context.getString(R.string.sync_shared_each)
                },
            )
            if (sharedCatalog) append(if (owner != null) context.getString(R.string.sync_shared_owner, owner) else context.getString(R.string.sync_shared_owner_unknown))
        },
        color = Muted,
        fontSize = 11.sp,
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label MUST be the weighted child: a Row measures unweighted children first at the
        // full available width, so an unweighted label that wraps (long text, large font scale,
        // narrow screen) left the switch measured at zero width — invisible and untappable.
        Text(
            label,
            color = Parchment,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
        )
        HomerSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ScanStatus(scanState: ScanState, bookCount: Int) {
    when (val state = scanState) {
        is ScanState.Scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Amber, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sync_scan_folders_books, state.directoriesVisited, state.booksFound), color = Muted, fontSize = 12.sp)
        }
        is ScanState.Done -> Text(stringResource(R.string.sync_books_indexed, bookCount), color = Muted, fontSize = 12.sp)
        is ScanState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        ScanState.Idle -> if (bookCount > 0) Text(stringResource(R.string.sync_books_count, bookCount), color = Muted, fontSize = 12.sp)
    }
}

/** Edits the series-level fields (name + author) and pushes them to every book in the series. */
@Composable
private fun SeriesEditDialog(
    series: LibraryEntry.Series,
    onSave: (name: String, author: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(series.name) }
    var author by remember { mutableStateOf(series.author.orEmpty()) }
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
