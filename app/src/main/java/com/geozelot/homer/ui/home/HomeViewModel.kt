package com.geozelot.homer.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.auth.AuthRepository
import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.download.DownloadManager
import com.geozelot.homer.data.library.BookCover
import com.geozelot.homer.data.library.BookEditor
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.data.library.LibraryDiscovery
import com.geozelot.homer.data.library.LibraryIndexManager
import com.geozelot.homer.data.library.LibraryRepository
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.data.storage.LocalMirror
import com.geozelot.homer.data.storage.StorageLocation
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
    /** Whether a user-chosen custom cover is set (so the edit dialog can offer to clear it). */
    val hasCustomCover: Boolean,
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
    /** Resolved cover model, computed here (rarely) rather than on every progress tick. */
    val coverModel: Any?,
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

/** How the library list is ordered (within each group). */
enum class LibrarySort(val key: String, val label: String) {
    RECENT("recent", "Recently played"),
    TITLE("title", "Title"),
    AUTHOR("author", "Author"),
    DURATION("duration", "Duration");

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: AUTHOR
    }
}

/** How the library list is sectioned into shelves/headers. */
enum class LibraryGroup(val key: String, val label: String) {
    NONE("none", "None"),
    AUTHOR("author", "Author"),
    SERIES("series", "Series"),
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
    private val bookDao: BookDao,
    private val bookEditor: BookEditor,
    private val connection: PlaybackConnection,
    private val catalog: HomerCatalogRepository,
    private val discovery: LibraryDiscovery,
    private val storageLocation: StorageLocation,
    private val localMirror: LocalMirror,
    playbackStateDao: PlaybackStateDao,
    private val downloadDao: DownloadDao,
) : ViewModel() {

    val account: StateFlow<NextcloudCredentials?> = authRepository.credentials

    /** Live playback snapshot for the docked mini-player. */
    val playback: StateFlow<PlaybackUiState> = connection.state

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    // Detection with user overrides applied (D2), hidden books filtered unless shown, plus the
    // resolved cover model. All the inputs here change rarely, so the per-book cover resolution
    // does NOT re-run on the ~5s playback-position ticks that drive `books` below. Ordering is
    // left to `buildEntries` / `continueShelf`, which always sort, so no sort is needed here.
    private val effectiveBooks: Flow<List<EffectiveBook>> =
        combine(
            libraryRepository.books,
            bookOverrideDao.observeAll(),
            _showHidden,
            authRepository.credentials,
            libraryRepository.libraryRoot,
        ) { books, overrides, showHidden, credentials, libraryRoot ->
            val overrideByBook = overrides.associateBy { it.bookId }
            books
                .map { book ->
                    val override = overrideByBook[book.id]
                    val effective = book.applyOverride(override)
                    EffectiveBook(
                        book = effective,
                        hidden = override?.hidden == true,
                        tags = override?.tags?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
                        finishedOverride = override?.finished,
                        coverModel = BookCover.model(effective, credentials, webDavClient, libraryRoot),
                    )
                }
                .filter { showHidden || !it.hidden }
        }

    private val books: StateFlow<List<BookListItem>> =
        combine(
            effectiveBooks,
            playbackStateDao.observeProgress(),
            downloadDao.observeAll(),
        ) { effective, progress, downloads ->
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
                    coverModel = eff.coverModel,
                    hasCustomCover = book.customCoverPath != null,
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

    /** Homer-bearing folders found by the discovery sweep (files root, library root, shares). */
    private val _discovered = MutableStateFlow<List<DiscoveredLibrary>>(emptyList())
    val discovered: StateFlow<List<DiscoveredLibrary>> = _discovered.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    val wifiOnlyDownloads: StateFlow<Boolean> = playbackSettings.wifiOnlyDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Opt-in Open Library cover lookup for art-less books. */
    val onlineCoverLookup: StateFlow<Boolean> = librarySettings.onlineCoverLookup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Seconds the player's skip buttons jump (default 15). */
    val seekSeconds: StateFlow<Int> = playbackSettings.seekSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 15)

    /** Seconds to rewind on resume; 0 = off (default). */
    val autoRewindSeconds: StateFlow<Int> = playbackSettings.autoRewindSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Configured custom storage folder Uri (null = app-external default). */
    val customStorageUri: StateFlow<String?> = librarySettings.customStorageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether opening/resuming the app requires a biometric / device-credential unlock. */
    val appLockEnabled: StateFlow<Boolean> = librarySettings.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the server's TLS certificate is pinned (trust-on-first-use). */
    val certPinningEnabled: StateFlow<Boolean> = librarySettings.certPinningEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bookCount: StateFlow<Int> = libraryRepository.bookCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * False until Room has delivered the book list at least once. Derived from the raw Room flow
     * (not the seeded [books] StateFlow, whose initial empty value is indistinguishable from a
     * genuinely empty library), so the UI can show a brief "opening library" phase instead of
     * flashing the empty-shelf screen on every launch.
     */
    val libraryLoaded: StateFlow<Boolean> = libraryRepository.books
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val scanState: StateFlow<ScanState> = libraryRepository.scanState

    private val _libraryRoot = MutableStateFlow("")
    val libraryRoot: StateFlow<String> = _libraryRoot.asStateFlow()

    init {
        viewModelScope.launch { _libraryRoot.value = libraryRepository.libraryRoot.first() }
        // One-time relocation to the siloed Homer/ storage root. Offline downloads lived in
        // internal storage; drop them (start fresh, per design) so they re-download into the new
        // location, and reclaim the old files. Covers relocate lazily as new ones are extracted.
        viewModelScope.launch {
            if (!librarySettings.storageRelocated.first()) {
                downloadDao.deleteAll()
                storageLocation.deleteLegacyDownloads()
                librarySettings.setStorageRelocated(true)
            }
        }
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
     * Runs the discovery sweep and refreshes the shared-catalog/owner hints for the current root
     * from its result. Called when the Library & Sync sheet opens and by the Rediscover action.
     */
    fun rediscover() {
        if (_discovering.value) return
        viewModelScope.launch {
            _discovering.value = true
            try {
                val libraries = discovery.discover()
                _discovered.value = libraries
                val current = libraries.firstOrNull { it.isCurrentRoot }
                _tier3Available.value = current?.hasSharedCatalog == true
                _libraryOwner.value = current?.owner
            } finally {
                _discovering.value = false
            }
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
    fun pauseDownload(bookId: String) = downloadManager.pause(bookId)
    fun resumeDownload(bookId: String) = downloadManager.resume(bookId)
    fun setWifiOnlyDownloads(value: Boolean) {
        viewModelScope.launch { playbackSettings.setWifiOnlyDownloads(value) }
    }

    fun setSeekSeconds(value: Int) {
        viewModelScope.launch { playbackSettings.setSeekSeconds(value) }
    }

    fun setAutoRewindSeconds(value: Int) {
        viewModelScope.launch { playbackSettings.setAutoRewindSeconds(value) }
    }

    fun setAppLock(value: Boolean) {
        viewModelScope.launch { librarySettings.setAppLockEnabled(value) }
    }

    fun setCertPinning(value: Boolean) {
        viewModelScope.launch { librarySettings.setCertPinningEnabled(value) }
    }

    /** Toggles online cover lookup; enabling it re-arms art-less books and kicks a cover pass. */
    fun setOnlineCoverLookup(value: Boolean) {
        viewModelScope.launch {
            librarySettings.setOnlineCoverLookup(value)
            if (value) {
                bookDao.retryCoversWithoutArt()
                libraryIndexManager.fetchMissingCovers()
            }
        }
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
            bookEditor.saveOverride(bookId, title, author, series, seriesIndex, genre, tags, hidden, finishedChange)
        }
    }

    /**
     * Applies a series-level edit (name + author) to every member book (see [BookEditor]).
     * Members re-group under the new series name; the change syncs like any override.
     */
    fun saveSeriesOverride(bookIds: List<String>, series: String, author: String) {
        viewModelScope.launch { bookEditor.saveSeriesOverride(bookIds, series, author) }
    }

    /** Reverts a book to pure detection (see [BookEditor.clearOverride]). */
    fun clearOverride(bookId: String) {
        viewModelScope.launch { bookEditor.clearOverride(bookId) }
    }

    fun setGridView(grid: Boolean) {
        viewModelScope.launch { librarySettings.setGridView(grid) }
    }

    /** Copies a user-picked image into the cover cache and sets it as the book's custom cover. */
    fun setCustomCover(bookId: String, uri: Uri) {
        viewModelScope.launch { bookEditor.setCustomCover(bookId, uri) }
    }

    /** Clears a custom cover, reverting to detected/extracted/online art. */
    fun clearCustomCover(bookId: String) {
        viewModelScope.launch { bookEditor.clearCustomCover(bookId) }
    }

    /** Points storage at a user-picked SAF folder (survives uninstall) and rebuilds data there. */
    fun setCustomStorageFolder(uri: Uri) {
        viewModelScope.launch {
            storageLocation.setCustomFolder(uri)
            relocateStorage()
        }
    }

    /** Reverts storage to the default app-external location and rebuilds data there. */
    fun useDefaultStorage() {
        viewModelScope.launch {
            storageLocation.useDefault()
            relocateStorage()
        }
    }

    /**
     * After a storage-location change the old area's downloads/covers no longer apply: drop them
     * (re-download / re-extract into the new area) and kick a cover pass. Custom covers are cleared
     * since their Uris pointed at the old area.
     */
    private suspend fun relocateStorage() {
        bookDao.resetCoverArt()
        bookDao.clearCustomCovers()
        // Adopt whatever the new folder already holds: import its progress mirror, and recompute
        // download status against it (present → done without re-downloading, absent → cleared).
        localMirror.import()
        localMirror.adoptDownloads()
        libraryIndexManager.fetchMissingCovers()
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
        viewModelScope.launch { bookEditor.setHidden(bookId, hidden) }
    }

    /**
     * Mark/unmark finished from the context menu (preserving other override fields).
     * [finished] = true forces finished, false forces not-finished, null reverts to auto.
     */
    fun setFinished(bookId: String, finished: Boolean?) {
        viewModelScope.launch { bookEditor.setFinished(bookId, finished) }
    }

    /** Toggle play/pause on the currently-loaded book (docked mini-player). */
    fun playPause() = connection.playPause()

    /** Retry a stalled stream from the docked mini-player (after a connection error). */
    fun retry() = connection.retry()

    fun logout() = authRepository.logout()

    private companion object {
        const val CONTINUE_LIMIT = 12
    }
}

/**
 * Builds the render list. [group] alone decides sectioning and whether series collapse into
 * shelves (decoupled from [sort], which orders the units within): Author/Series group into shelves,
 * a series shelf positioned by [sort] but its episodes always in reading order; None is a flat
 * sorted list; Genre sections flat by genre.
 */
private fun buildEntries(
    books: List<BookListItem>,
    sort: LibrarySort,
    group: LibraryGroup,
): List<LibraryEntry> {
    val collapse = group == LibraryGroup.AUTHOR || group == LibraryGroup.SERIES
    val units = if (collapse) collapseIntoUnits(books) else books.map { SortUnit.Solo(it) }
    val ordered = units.sortedWith(unitComparator(sort))

    return when (group) {
        LibraryGroup.NONE, LibraryGroup.SERIES -> ordered.map { it.toEntry() }
        LibraryGroup.AUTHOR -> sectioned(ordered, "Unknown author") { it.author }
        LibraryGroup.GENRE -> sectioned(ordered, "No genre") { (it as? SortUnit.Solo)?.book?.genre }
    }
}

/** A list unit awaiting placement: a standalone book or a collapsed series shelf. */
private sealed interface SortUnit {
    data class Solo(val book: BookListItem) : SortUnit
    data class Ser(val series: LibraryEntry.Series) : SortUnit

    val author: String?
        get() = when (this) {
            is Solo -> book.author
            is Ser -> series.author
        }

    fun toEntry(): LibraryEntry = when (this) {
        is Solo -> LibraryEntry.Standalone(book)
        is Ser -> series
    }
}

/** Reading order within a series: by series index when known, then title. */
private val inSeriesOrder: Comparator<BookListItem> =
    compareBy({ it.seriesIndex == null }, { it.seriesIndex }, { it.title.lowercase() })

/** Collapses author+series sets (2+) into ordered series units; everything else stays solo. */
private fun collapseIntoUnits(books: List<BookListItem>): List<SortUnit> {
    val bySeries = books.filter { it.series != null }.groupBy { "${it.author.orEmpty()}|${it.series}" }
    val consumed = HashSet<String>()
    val units = mutableListOf<SortUnit>()
    for ((key, members) in bySeries) {
        if (members.size < 2) continue
        units += SortUnit.Ser(
            LibraryEntry.Series(
                key = key,
                name = members.first().series!!,
                author = members.first().author,
                books = members.sortedWith(inSeriesOrder),
            ),
        )
        members.forEach { consumed += it.id }
    }
    books.forEach { if (it.id !in consumed) units += SortUnit.Solo(it) }
    return units
}

private fun unitComparator(sort: LibrarySort): Comparator<SortUnit> = when (sort) {
    LibrarySort.TITLE -> compareBy { unitTitle(it).lowercase() }
    LibrarySort.AUTHOR -> compareBy(
        { it.author == null }, { it.author?.lowercase() }, { unitTitle(it).lowercase() },
    )
    // Never-played / unmeasured sort last under the descending orders.
    LibrarySort.RECENT -> compareByDescending { unitRecency(it) }
    LibrarySort.DURATION -> compareByDescending { unitDuration(it) }
}

private fun unitTitle(u: SortUnit): String = when (u) {
    is SortUnit.Solo -> u.book.title
    is SortUnit.Ser -> u.series.name
}

private fun unitRecency(u: SortUnit): Long = when (u) {
    is SortUnit.Solo -> u.book.lastPlayedAt ?: Long.MIN_VALUE
    is SortUnit.Ser -> u.series.books.maxOf { it.lastPlayedAt ?: Long.MIN_VALUE }
}

private fun unitDuration(u: SortUnit): Long = when (u) {
    is SortUnit.Solo -> u.book.totalDurationMs ?: -1L
    is SortUnit.Ser -> u.series.books.sumOf { it.totalDurationMs ?: 0L }
}

/** Groups [units] into sections keyed by [keyOf] (nulls last under [fallback]) with headers. */
private fun sectioned(
    units: List<SortUnit>,
    fallback: String,
    keyOf: (SortUnit) -> String?,
): List<LibraryEntry> {
    val byKey = units.groupBy(keyOf)
    val keys = byKey.keys.sortedWith(compareBy({ it == null }, { it?.lowercase() }))
    return buildList {
        for (key in keys) {
            add(LibraryEntry.Header(key ?: fallback))
            byKey.getValue(key).forEach { add(it.toEntry()) }
        }
    }
}

/** Case-insensitive match across title, author, series, genre and tags. */
private fun BookListItem.matchesQuery(needle: String): Boolean =
    title.contains(needle, ignoreCase = true) ||
        author?.contains(needle, ignoreCase = true) == true ||
        series?.contains(needle, ignoreCase = true) == true ||
        genre?.contains(needle, ignoreCase = true) == true ||
        tags.any { it.contains(needle, ignoreCase = true) }

