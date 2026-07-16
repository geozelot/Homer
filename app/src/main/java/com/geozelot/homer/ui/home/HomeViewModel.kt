package com.geozelot.homer.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.auth.AuthRepository
import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.download.DownloadManager
import com.geozelot.homer.data.library.BookCover
import com.geozelot.homer.data.library.LibraryIndexManager
import com.geozelot.homer.data.library.LibraryRepository
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.data.sync.HomerCatalogRepository
import com.geozelot.homer.data.sync.HomerSyncRepository
import com.geozelot.homer.data.webdav.WebDavClient
import com.geozelot.homer.playback.PlaybackConnection
import com.geozelot.homer.playback.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A library row: enough to render without touching the DB entity in the UI. */
data class BookListItem(
    val id: String,
    val title: String,
    val author: String?,
    val isMultiFile: Boolean,
    val fileCount: Int,
    val coverModel: Any?,
    val series: String?,
    val seriesIndex: Int?,
    val genre: String?,
    /** User tags (from the override layer); empty if none. */
    val tags: List<String>,
    val totalDurationMs: Long?,
    /** Remaining time from the saved position; null if not started or not yet measured. */
    val timeLeftMs: Long?,
    /** Fraction of the book listened (0f–1f); null if not started or duration unmeasured. */
    val progress: Float?,
    /** When this book was last played, for Continue-shelf recency; null if never. */
    val lastPlayedAt: Long?,
    /** Forced finished flag: null = auto-derive, true/false = user override. */
    val finishedOverride: Boolean?,
    /** Offline-download status ([com.geozelot.homer.data.db.entity.DownloadStatus]) or null. */
    val downloadStatus: String?,
    val downloadedFiles: Int,
    val hidden: Boolean,
) {
    /** Finished either because the user marked it, or (unforced) it ran to the end. */
    val finished: Boolean get() = finishedOverride ?: (timeLeftMs != null && timeLeftMs <= 0L)

    /** Fully downloaded for offline playback. */
    val isDownloaded: Boolean get() = downloadStatus == DownloadStatus.DONE
}

/** Detected book with its override applied, plus the override-only bits (not book fields). */
private data class EffectiveBook(
    val book: BookEntity,
    val hidden: Boolean,
    val tags: List<String>,
    val finishedOverride: Boolean?,
)

/** A library row: a section header, a standalone book, or a collapsible series shelf. */
sealed interface LibraryEntry {
    data class Header(val title: String) : LibraryEntry
    data class Standalone(val book: BookListItem) : LibraryEntry
    data class Series(
        val key: String,
        val name: String,
        val author: String?,
        val books: List<BookListItem>,
    ) : LibraryEntry
}

/** How the library list is ordered. */
enum class LibrarySort(val key: String, val label: String) {
    AUTHOR("author", "Author"),
    TITLE("title", "Title"),
    RECENT("recent", "Recently played"),
    DURATION("duration", "Duration");

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: AUTHOR
    }
}

/** How the library list is sectioned. */
enum class LibraryGroup(val key: String, val label: String) {
    NONE("none", "No grouping"),
    AUTHOR("author", "Author"),
    GENRE("genre", "Genre");

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: NONE
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val libraryIndexManager: LibraryIndexManager,
    private val librarySettings: LibrarySettings,
    private val webDavClient: WebDavClient,
    private val homerSync: HomerSyncRepository,
    private val downloadManager: DownloadManager,
    private val playbackSettings: PlaybackSettings,
    private val bookOverrideDao: BookOverrideDao,
    private val connection: PlaybackConnection,
    private val catalog: HomerCatalogRepository,
    playbackStateDao: PlaybackStateDao,
    downloadDao: DownloadDao,
) : ViewModel() {

    val account: StateFlow<NextcloudCredentials?> = authRepository.credentials

    /** Live playback snapshot for the docked mini-player. */
    val playback: StateFlow<PlaybackUiState> = connection.state

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    // Detection with user overrides applied (D2), hidden books filtered unless shown, then
    // re-sorted on the effective author/series so manual fixes group correctly.
    private val effectiveBooks: Flow<List<EffectiveBook>> =
        combine(
            libraryRepository.books,
            bookOverrideDao.observeAll(),
            _showHidden,
        ) { books, overrides, showHidden ->
            val overrideByBook = overrides.associateBy { it.bookId }
            books
                .map { book ->
                    val override = overrideByBook[book.id]
                    EffectiveBook(
                        book = book.applyOverride(override),
                        hidden = override?.hidden == true,
                        tags = override?.tags?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
                        finishedOverride = override?.finished,
                    )
                }
                .filter { showHidden || !it.hidden }
                .sortedWith(
                    // Nulls last (the `== null` keys sort false<true), matching the DB order.
                    compareBy<EffectiveBook>(
                        { it.book.author == null },
                        { it.book.author },
                        { it.book.series == null },
                        { it.book.series },
                        { it.book.seriesIndex == null },
                        { it.book.seriesIndex },
                        { it.book.title },
                    ),
                )
        }

    private val books: StateFlow<List<BookListItem>> =
        combine(
            effectiveBooks,
            authRepository.credentials,
            playbackStateDao.observeProgress(),
            downloadDao.observeAll(),
            libraryRepository.libraryRoot,
        ) { effective, credentials, progress, downloads, libraryRoot ->
            val progressByBook = progress.associateBy { it.bookId }
            val downloadByBook = downloads.associateBy { it.bookId }
            effective.map { eff ->
                val book = eff.book
                val bookProgress = progressByBook[book.id]
                val elapsed = bookProgress?.elapsedMs
                val total = book.totalDurationMs
                val download = downloadByBook[book.id]
                // Both a total and a saved position are needed for progress/time-left.
                val measured = total != null && total > 0 && elapsed != null
                BookListItem(
                    id = book.id,
                    title = book.title,
                    author = book.author,
                    isMultiFile = book.isMultiFile,
                    fileCount = book.fileCount,
                    coverModel = BookCover.model(book, credentials, webDavClient, libraryRoot),
                    series = book.series,
                    seriesIndex = book.seriesIndex,
                    genre = book.genre,
                    tags = eff.tags,
                    totalDurationMs = total,
                    timeLeftMs = if (measured) (total!! - elapsed!!).coerceAtLeast(0) else null,
                    progress = if (measured) (elapsed!!.toFloat() / total!!).coerceIn(0f, 1f) else null,
                    lastPlayedAt = bookProgress?.updatedAt,
                    finishedOverride = eff.finishedOverride,
                    downloadStatus = download?.status,
                    downloadedFiles = download?.downloadedFiles ?: 0,
                    hidden = eff.hidden,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val sortMode: StateFlow<LibrarySort> = librarySettings.sortMode
        .map(LibrarySort::from)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySort.AUTHOR)

    val groupMode: StateFlow<LibraryGroup> = librarySettings.groupMode
        .map(LibraryGroup::from)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryGroup.NONE)

    /** Library list, filtered by the query, ordered by [sortMode], sectioned by [groupMode]. */
    val entries: StateFlow<List<LibraryEntry>> =
        combine(books, _searchQuery, sortMode, groupMode) { list, query, sort, group ->
            val filtered = if (query.isBlank()) {
                list
            } else {
                val needle = query.trim().lowercase()
                list.filter { it.matchesQuery(needle) }
            }
            buildEntries(filtered, sort, group)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** In-progress books (started, not finished), most-recently-played first. */
    val continueShelf: StateFlow<List<BookListItem>> = books
        .map { list ->
            list.asSequence()
                .filter { it.lastPlayedAt != null && !it.finished && !it.hidden }
                .sortedByDescending { it.lastPlayedAt }
                .take(CONTINUE_LIMIT)
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cover grid (true) vs. scannable list (false); persisted. */
    val gridView: StateFlow<Boolean> = librarySettings.gridView
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Sync level: 1 = on-device only, 2 = cross-device progress sync, 3 = shared library cache. */
    val syncTier: StateFlow<Int> = librarySettings.syncTier
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    /** Whether a Tier-3 shared catalog exists in this library (advisory, for the settings UI). */
    private val _tier3Available = MutableStateFlow(false)
    val tier3Available: StateFlow<Boolean> = _tier3Available.asStateFlow()

    /** Detected Nextcloud owner of the library folder, or null if not discoverable. */
    private val _libraryOwner = MutableStateFlow<String?>(null)
    val libraryOwner: StateFlow<String?> = _libraryOwner.asStateFlow()

    val wifiOnlyDownloads: StateFlow<Boolean> = playbackSettings.wifiOnlyDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bookCount: StateFlow<Int> = libraryRepository.bookCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val scanState: StateFlow<ScanState> = libraryRepository.scanState

    private val _libraryRoot = MutableStateFlow("")
    val libraryRoot: StateFlow<String> = _libraryRoot.asStateFlow()

    init {
        viewModelScope.launch { _libraryRoot.value = libraryRepository.libraryRoot.first() }
        // Surface an already-running playback session in the mini-player on cold start, before the
        // user opens a book (recovers the current book from the restored queue if present).
        connection.connect()
        // Fill in covers for books missing one, in a foreground worker (survives backgrounding).
        libraryIndexManager.fetchMissingCovers()
        // Pull cross-device resume positions from the .homer manifest on open.
        viewModelScope.launch { homerSync.sync() }
        // Tier 3: pull the shared catalog so the library is present without scanning. Never
        // touch the network at tier 1 (on-device only).
        viewModelScope.launch {
            if (librarySettings.syncTier.first() >= 3) {
                catalog.consume()
                _tier3Available.value = catalog.exists()
            }
        }
    }

    /**
     * Probes the library folder's Nextcloud owner and whether a shared catalog exists, for the
     * settings UI. Called when the settings sheet opens (a user-initiated read).
     */
    fun probeSharedLibrary() {
        viewModelScope.launch {
            val owner = webDavClient.fetchOwnerId(libraryRepository.libraryRoot.first())
            Log.i("HomerCatalog", "library owner = ${owner ?: "(unknown — claim-based)"}")
            _libraryOwner.value = owner
            _tier3Available.value = catalog.exists()
        }
    }

    fun onLibraryRootChange(value: String) {
        _libraryRoot.value = value
    }

    fun scan() {
        viewModelScope.launch {
            // Persist the root first; the worker reads it. Scan + covers (+ Tier-3 publish)
            // then run in the foreground worker so they survive the app being backgrounded.
            libraryRepository.setLibraryRoot(_libraryRoot.value)
            libraryIndexManager.scan()
        }
    }

    /** Deep re-scan: rebuild the library and re-fetch all cover art. */
    fun fullScan() {
        viewModelScope.launch {
            libraryRepository.setLibraryRoot(_libraryRoot.value)
            libraryIndexManager.fullScan()
        }
    }

    /** Re-fetch cover art for every book (no library crawl). */
    fun refreshCoverArt() = libraryIndexManager.refreshCovers()

    fun download(bookId: String) = downloadManager.download(bookId)
    fun deleteDownload(bookId: String) = downloadManager.delete(bookId)
    fun setWifiOnlyDownloads(value: Boolean) {
        viewModelScope.launch { playbackSettings.setWifiOnlyDownloads(value) }
    }

    fun setShowHidden(value: Boolean) {
        _showHidden.value = value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Saves metadata corrections + hidden flag; blank fields revert to detection.
     * [finishedChange] carries the finished toggle: null leaves the existing flag untouched (the
     * dialog didn't change it), true/false forces that value.
     */
    fun saveOverride(
        bookId: String,
        title: String,
        author: String,
        series: String,
        seriesIndex: String,
        genre: String,
        tags: String,
        hidden: Boolean,
        finishedChange: Boolean?,
    ) {
        viewModelScope.launch {
            val finished = finishedChange ?: bookOverrideDao.findById(bookId)?.finished
            val tagList = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }
            bookOverrideDao.upsert(
                BookOverrideEntity(
                    bookId = bookId,
                    title = title.trim().ifBlank { null },
                    author = author.trim().ifBlank { null },
                    series = series.trim().ifBlank { null },
                    seriesIndex = seriesIndex.trim().toIntOrNull(),
                    genre = genre.trim().ifBlank { null },
                    tags = tagList.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    finished = finished,
                    hidden = hidden,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            homerSync.sync()
        }
    }

    /**
     * Reverts a book to pure detection. Stored as an all-null "cleared" override (not a
     * row delete) with a fresh timestamp, so the reset propagates to other devices via
     * last-write-wins instead of being resurrected on the next pull.
     */
    fun clearOverride(bookId: String) {
        viewModelScope.launch {
            bookOverrideDao.upsert(
                BookOverrideEntity(
                    bookId = bookId,
                    title = null,
                    author = null,
                    series = null,
                    seriesIndex = null,
                    hidden = false,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            homerSync.sync()
        }
    }

    fun setGridView(grid: Boolean) {
        viewModelScope.launch { librarySettings.setGridView(grid) }
    }

    fun setSyncTier(tier: Int) {
        viewModelScope.launch {
            librarySettings.setSyncTier(tier)
            if (tier >= 3) {
                if (catalog.exists()) catalog.consume() // bootstrap from the shared library
                else catalog.publishIfAllowed()         // create it (owner, or claim-based)
                _tier3Available.value = catalog.exists()
            }
        }
    }

    fun setSortMode(sort: LibrarySort) {
        viewModelScope.launch { librarySettings.setSortMode(sort.key) }
    }

    fun setGroupMode(group: LibraryGroup) {
        viewModelScope.launch { librarySettings.setGroupMode(group.key) }
    }

    /** Quick hide/show from the context menu, preserving any existing metadata override. */
    fun setHidden(bookId: String, hidden: Boolean) {
        viewModelScope.launch {
            val existing = bookOverrideDao.findById(bookId)
            bookOverrideDao.upsert(
                existing?.copy(hidden = hidden, updatedAt = System.currentTimeMillis())
                    ?: BookOverrideEntity(
                        bookId = bookId,
                        title = null,
                        author = null,
                        series = null,
                        seriesIndex = null,
                        hidden = hidden,
                        updatedAt = System.currentTimeMillis(),
                    ),
            )
            homerSync.sync()
        }
    }

    /**
     * Mark/unmark finished from the context menu (preserving other override fields).
     * [finished] = true forces finished, false forces not-finished, null reverts to auto.
     */
    fun setFinished(bookId: String, finished: Boolean?) {
        viewModelScope.launch {
            val existing = bookOverrideDao.findById(bookId)
            bookOverrideDao.upsert(
                existing?.copy(finished = finished, updatedAt = System.currentTimeMillis())
                    ?: BookOverrideEntity(
                        bookId = bookId,
                        title = null,
                        author = null,
                        series = null,
                        seriesIndex = null,
                        finished = finished,
                        hidden = false,
                        updatedAt = System.currentTimeMillis(),
                    ),
            )
            homerSync.sync()
        }
    }

    /** Toggle play/pause on the currently-loaded book (docked mini-player). */
    fun playPause() = connection.playPause()

    fun logout() = authRepository.logout()

    private companion object {
        const val CONTINUE_LIMIT = 12
    }
}

/** Orders [books] by [sort] and sections them by [group], producing the render list. */
private fun buildEntries(
    books: List<BookListItem>,
    sort: LibrarySort,
    group: LibraryGroup,
): List<LibraryEntry> {
    val ordered = books.sortedWith(sort.comparator())
    // Series shelves only make sense when books are in author/series order.
    val shelves = sort == LibrarySort.AUTHOR

    if (group == LibraryGroup.NONE) {
        return if (shelves) groupIntoEntries(ordered) else ordered.map(LibraryEntry::Standalone)
    }

    val keyOf: (BookListItem) -> String? = when (group) {
        LibraryGroup.AUTHOR -> { b -> b.author }
        LibraryGroup.GENRE -> { b -> b.genre }
        LibraryGroup.NONE -> { _ -> null }
    }
    val fallback = if (group == LibraryGroup.AUTHOR) "Unknown author" else "No genre"
    val byKey = ordered.groupBy(keyOf)
    val orderedKeys = byKey.keys.sortedWith(compareBy({ it == null }, { it?.lowercase() }))

    return buildList {
        for (key in orderedKeys) {
            add(LibraryEntry.Header(key ?: fallback))
            val groupBooks = byKey.getValue(key)
            // Keep series shelves within an author section when sorted by author.
            if (shelves && group == LibraryGroup.AUTHOR) {
                addAll(groupIntoEntries(groupBooks))
            } else {
                groupBooks.forEach { add(LibraryEntry.Standalone(it)) }
            }
        }
    }
}

private fun LibrarySort.comparator(): Comparator<BookListItem> = when (this) {
    LibrarySort.AUTHOR -> compareBy(
        { it.author == null },
        { it.author?.lowercase() },
        { it.series == null },
        { it.series?.lowercase() },
        { it.seriesIndex == null },
        { it.seriesIndex },
        { it.title.lowercase() },
    )
    LibrarySort.TITLE -> compareBy { it.title.lowercase() }
    // Never-played / unmeasured sort last under the descending orders.
    LibrarySort.RECENT -> compareByDescending { it.lastPlayedAt ?: Long.MIN_VALUE }
    LibrarySort.DURATION -> compareByDescending { it.totalDurationMs ?: -1L }
}

/** Case-insensitive match across title, author, series, genre and tags. */
private fun BookListItem.matchesQuery(needle: String): Boolean =
    title.contains(needle, ignoreCase = true) ||
        author?.contains(needle, ignoreCase = true) == true ||
        series?.contains(needle, ignoreCase = true) == true ||
        genre?.contains(needle, ignoreCase = true) == true ||
        tags.any { it.contains(needle, ignoreCase = true) }

/**
 * Collapses runs of books sharing an author + series (2+) into a [LibraryEntry.Series];
 * everything else stays a [LibraryEntry.Standalone]. Input is already sorted by
 * author/series/index/title, so grouped books are contiguous.
 */
private fun groupIntoEntries(items: List<BookListItem>): List<LibraryEntry> {
    val entries = mutableListOf<LibraryEntry>()
    var i = 0
    while (i < items.size) {
        val book = items[i]
        val series = book.series
        if (series != null) {
            var j = i + 1
            while (j < items.size && items[j].series == series && items[j].author == book.author) j++
            if (j - i >= 2) {
                entries += LibraryEntry.Series(
                    key = "${book.author.orEmpty()}|$series",
                    name = series,
                    author = book.author,
                    books = items.subList(i, j).toList(),
                )
                i = j
                continue
            }
        }
        entries += LibraryEntry.Standalone(book)
        i++
    }
    return entries
}
