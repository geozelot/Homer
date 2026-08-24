package com.geozelot.homer.ui.home

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.auth.AuthRepository
import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.auth.SignOut
import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.CrawlDirDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.download.DownloadManager
import com.geozelot.homer.data.library.BookCover
import com.geozelot.homer.data.library.BookEditor
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.data.library.IndexPass
import com.geozelot.homer.data.library.LibraryDiscovery
import com.geozelot.homer.data.library.LibraryIndexManager
import com.geozelot.homer.data.library.LibraryRepository
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.data.storage.LocalMirror
import com.geozelot.homer.data.storage.StorageLocation
import com.geozelot.homer.data.storage.StorageMigrationManager
import com.geozelot.homer.data.storage.StorageMigrator
import com.geozelot.homer.data.sync.facet.CrawlSummary
import com.geozelot.homer.data.sync.facet.IndexActivity
import com.geozelot.homer.data.sync.facet.LibraryIndexRepository
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

/**
 * A library row: enough to render without touching the DB entity in the UI.
 *
 * [Immutable] is load-bearing, not decoration. Every saved playback position rebuilds this whole
 * list, so each row arrives as a new instance that is `equals` to the old one for every book but
 * the one playing. Compose skips a card whose arguments are unchanged only if its parameter types
 * are stable — and `coverModel: Any?` plus `tags: List<String>` are both inferred unstable, which
 * dropped that guarantee and recomposed the entire visible library on every position save. The
 * annotation is honest here: all properties are `val`s, and the cover model is only ever an
 * immutable Uri, File or String.
 */
@Immutable
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
    /** When this book was last played, for currently-listening recency; null if never. */
    val lastPlayedAt: Long?,
    /** Whether the book has real listening progress (a saved position past the very start), so
     *  merely opening a book doesn't put it on the Currently-listening shelf. */
    val started: Boolean,
    /** Forced finished flag: null = auto-derive, true/false = user override (legacy; kept for
     *  cross-device compat and to hide already-marked books, but no longer set from the UI). */
    val finishedOverride: Boolean?,
    /** Per-book play mode: null = follow global, true = download on play, false = stream. */
    val downloadOnPlayOverride: Boolean?,
    /** Offline-download status ([com.geozelot.homer.data.db.entity.DownloadStatus]) or null. */
    val downloadStatus: String?,
    val downloadedFiles: Int,
    val hidden: Boolean,
) {
    /**
     * Finished either because the user marked it (legacy flag), or it ran to the end. `timeLeftMs`
     * is null unless the book is fully measured, so an unmeasured book is never auto-finished.
     * The tolerance lets a book that stops a few seconds short still count as done.
     */
    val finished: Boolean get() = finishedOverride ?: (timeLeftMs != null && timeLeftMs <= FINISHED_TOLERANCE_MS)

    /** Fully downloaded for offline playback. */
    val isDownloaded: Boolean get() = downloadStatus == DownloadStatus.DONE
}

/** How close to the end still counts as finished (playback often stops a moment short). */
private const val FINISHED_TOLERANCE_MS = 15_000L

/** Detected book with its override applied, plus the override-only bits (not book fields). */
private data class EffectiveBook(
    val book: BookEntity,
    val hidden: Boolean,
    val tags: List<String>,
    val finishedOverride: Boolean?,
    val downloadOnPlayOverride: Boolean?,
    /** Resolved cover model, computed here (rarely) rather than on every progress tick. */
    val coverModel: Any?,
)

/**
 * A library row: a section header, a standalone book, or a collapsible series shelf.
 *
 * [Immutable] for the same reason as [BookListItem] — `Series.books` is a `List`, so without it
 * an unchanged shelf recomposes whenever the list is rebuilt.
 */
@Immutable
sealed interface LibraryEntry {
    data class Header(val title: String) : LibraryEntry
    data class Standalone(val book: BookListItem) : LibraryEntry

    // Annotated on the class as well as the interface: stability is inferred per concrete type,
    // and this is the one passed to composables (the series banner and card) as itself.
    @Immutable
    data class Series(
        val key: String,
        val name: String,
        val author: String?,
        val books: List<BookListItem>,
    ) : LibraryEntry
}

/** How the library list is ordered (within each shelf). */
enum class LibrarySort(val key: String, val label: String) {
    RECENT("recent", "Recently played"),
    TITLE("title", "Title"),
    AUTHOR("author", "Author"),
    DURATION("duration", "Duration");

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: AUTHOR

        /**
         * The sorts worth offering for a given shelving. Sorting by the very thing the list is
         * already sectioned by orders the shelves and does nothing inside them, so it is dropped
         * rather than left as an option that appears to do nothing.
         */
        fun offeredFor(shelving: LibraryShelving): List<LibrarySort> =
            values().filterNot { it == AUTHOR && shelving == LibraryShelving.AUTHOR }
    }
}

/**
 * How the library list is sectioned into shelves.
 *
 * SERIES used to live here and no longer does: collapsing a series into one card is a way of
 * DRAWING the list, not an axis to section it by, and it was the only value here that produced no
 * headers at all — which is why it read as doing nothing. It is [LibrarySeriesMode] now, an
 * independent control, so a series stays stacked (or doesn't) whichever way the list is shelved.
 *
 * ITEM keeps the stored key "none" so nobody's saved preference resets; a stored "series" from
 * before the split falls through [from] to ITEM.
 */
enum class LibraryShelving(val key: String, val label: String) {
    ITEM("none", "Item"),
    AUTHOR("author", "Author"),
    GENRE("genre", "Genre");

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: ITEM
    }
}

/** Whether a multi-book series is drawn as one stacked shelf or as its books, loose in the list. */
enum class LibrarySeriesMode(val key: String, val label: String) {
    STACKED("stacked", "Stacked"),
    FLAT("flat", "Flat");

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: STACKED
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val signOut: SignOut,
    private val libraryRepository: LibraryRepository,
    private val libraryIndexManager: LibraryIndexManager,
    private val librarySettings: LibrarySettings,
    private val webDavClient: WebDavClient,
    private val homerSync: HomerSyncRepository,
    private val downloadManager: DownloadManager,
    private val playbackSettings: PlaybackSettings,
    private val bookOverrideDao: BookOverrideDao,
    private val bookDao: BookDao,
    private val crawlDirDao: CrawlDirDao,
    private val bookEditor: BookEditor,
    private val connection: PlaybackConnection,
    private val libraryIndex: LibraryIndexRepository,
    private val discovery: LibraryDiscovery,
    private val storageLocation: StorageLocation,
    private val storageMigrationManager: StorageMigrationManager,
    private val storageMigrator: StorageMigrator,
    private val localMirror: LocalMirror,
    playbackStateDao: PlaybackStateDao,
    private val downloadDao: DownloadDao,
) : ViewModel() {

    val account: StateFlow<NextcloudCredentials?> = authRepository.credentials

    /** True when the library backend is a public share (vs a signed-in account). */
    val libraryIsShare: StateFlow<Boolean> = account
        .map { it?.kind == WebDavKind.SHARE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The account private progress syncs to (null = device-local). */
    val syncAccount: StateFlow<NextcloudCredentials?> = authRepository.syncAccount

    /** Whether the library backend is writable (a read-write share, or an account). */
    val libraryWritable: StateFlow<Boolean> = librarySettings.libraryWritable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Stops syncing progress to a linked account (share libraries → device-local). */
    fun unlinkSyncAccount() = authRepository.unlinkSyncAccount()

    /** Live playback snapshot for the docked mini-player. */
    val playback: StateFlow<PlaybackUiState> = connection.state

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    // Detection with user overrides applied (D2), hidden books filtered unless shown, plus the
    // resolved cover model. All the inputs here change rarely, so the per-book cover resolution
    // does NOT re-run on the ~5s playback-position ticks that drive `books` below. Ordering is
    // left to `buildEntries` / `listeningShelf`, which always sort, so no sort is needed here.
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
                        downloadOnPlayOverride = override?.downloadOnPlay,
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
                // A trustworthy percentage / time-left needs a total, a saved position AND every
                // file measured. Without the completeness check a partially-measured book reports
                // elapsed > total, which reads as "finished" and hides it from the listening shelf.
                val measured = total != null && total > 0 && elapsed != null &&
                    bookProgress.fullyMeasured
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
                    started = bookProgress?.started == true,
                    finishedOverride = eff.finishedOverride,
                    downloadOnPlayOverride = eff.downloadOnPlayOverride,
                    downloadStatus = download?.status,
                    downloadedFiles = download?.downloadedFiles ?: 0,
                    hidden = eff.hidden,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * The active sort, clamped to what the current shelving actually offers.
     *
     * Clamped on read rather than corrected on write, for two reasons. A stored pair that predates
     * the clamp — shelve=author with sort=author, the natural combination before sorting by the
     * shelved field became pointless — would never pass through a write-side fix, and the chip
     * would show a value its own menu no longer lists. And leaving the STORED sort alone means
     * shelving by author borrows Title for the duration and hands the user's real preference back
     * the moment they shelve some other way.
     */
    val sortMode: StateFlow<LibrarySort> =
        combine(librarySettings.sortMode, librarySettings.shelfMode) { sortKey, shelfKey ->
            val stored = LibrarySort.from(sortKey)
            if (stored in LibrarySort.offeredFor(LibraryShelving.from(shelfKey))) stored else LibrarySort.TITLE
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySort.AUTHOR)

    val shelfMode: StateFlow<LibraryShelving> = librarySettings.shelfMode
        .map(LibraryShelving::from)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryShelving.ITEM)

    val seriesMode: StateFlow<LibrarySeriesMode> = librarySettings.seriesMode
        .map(LibrarySeriesMode::from)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySeriesMode.STACKED)

    /** Library list, filtered by the query, ordered by [sortMode], sectioned by [shelfMode]. */
    val entries: StateFlow<List<LibraryEntry>> =
        combine(books, _searchQuery, sortMode, shelfMode, seriesMode) { list, query, sort, shelving, series ->
            val filtered = if (query.isBlank()) {
                list
            } else {
                val needle = query.trim().lowercase()
                list.filter { it.matchesQuery(needle) }
            }
            buildEntries(filtered, sort, shelving, series)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The currently-playing book as its live library row, so the docked mini-player shows an
     * up-to-date cover/title (the playback snapshot's cover is captured at play time and doesn't
     * reflect a later refresh/edit). Null when nothing is playing or the row isn't loaded yet.
     */
    val miniPlayerBook: StateFlow<BookListItem?> =
        combine(playback, books) { state, list ->
            state.bookId?.let { id -> list.firstOrNull { it.id == id } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** In-progress books: actually started (real progress), not finished/at-end, not hidden;
     *  most-recently-played first. Merely opening a book (position 0) does NOT qualify. */
    val listeningShelf: StateFlow<List<BookListItem>> = books
        .map { list ->
            list.asSequence()
                .filter { it.started && !it.finished && !it.hidden }
                .sortedByDescending { it.lastPlayedAt }
                .take(LISTENING_LIMIT)
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cover grid (true) vs. scannable list (false); persisted. */
    val gridView: StateFlow<Boolean> = librarySettings.gridView
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Sync my listening progress to my account (cross-device for me). */
    val progressSyncEnabled: StateFlow<Boolean> = librarySettings.progressSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Use the shared library catalog + cover cache at the library root. */
    val sharedCatalogEnabled: StateFlow<Boolean> = librarySettings.sharedCatalogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether a shared catalog exists in this library (advisory, for the settings UI). */
    private val _sharedCatalogAvailable = MutableStateFlow(false)
    val sharedCatalogAvailable: StateFlow<Boolean> = _sharedCatalogAvailable.asStateFlow()

    /** Detected Nextcloud owner of the library folder, or null if not discoverable. */
    private val _libraryOwner = MutableStateFlow<String?>(null)
    val libraryOwner: StateFlow<String?> = _libraryOwner.asStateFlow()

    /** Homer-bearing folders found by the discovery sweep (files root, library root, shares). */
    private val _discovered = MutableStateFlow<List<DiscoveredLibrary>>(emptyList())
    val discovered: StateFlow<List<DiscoveredLibrary>> = _discovered.asStateFlow()

    /** When the last discovery sweep completed, so opening the sheet doesn't re-sweep every time. */
    private var lastDiscoveryAtMs = 0L

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

    /** Global default: press Play downloads the book (true) vs streams only (false). */
    val downloadOnPlay: StateFlow<Boolean> = playbackSettings.downloadOnPlay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Configured custom storage folder Uri (null = app-external default). */
    val customStorageUri: StateFlow<String?> = librarySettings.customStorageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Configured all-files storage folder path (null = not using an all-files path). */
    val customStoragePath: StateFlow<String?> = librarySettings.customStoragePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the app currently holds all-files access (for the storage folder browser). */
    fun hasAllFilesAccess(): Boolean = storageLocation.hasAllFilesAccess()

    /** Whether opening/resuming the app requires a biometric / device-credential unlock. */
    val appLockEnabled: StateFlow<Boolean> = librarySettings.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the server's TLS certificate is pinned (trust-on-first-use). */
    val certPinningEnabled: StateFlow<Boolean> = librarySettings.certPinningEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Live progress of a storage move (null when none is running) — drives a blocking overlay. */
    val migrationProgress: StateFlow<StorageMigrator.Progress?> = storageMigrator.progress

    private val _pendingStorageChange = MutableStateFlow<PendingStorageChange?>(null)
    /** Set when a chosen folder already holds a Homer library and the user must pick load vs replace. */
    val pendingStorageChange: StateFlow<PendingStorageChange?> = _pendingStorageChange.asStateFlow()

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
        // Shared index on: pull the catalog so the library is present without scanning. Never
        // touch the network at tier 1 (on-device only). The first-run resolution runs after it, in
        // the same coroutine rather than beside it — it decides on the book count, and racing the
        // pull would make it look at an empty database that is about to fill.
        viewModelScope.launch {
            if (librarySettings.sharedCatalogEnabled.first()) {
                // pull() reports whether a shared index exists, so this needs no second probe.
                _sharedCatalogAvailable.value = libraryIndex.pull()
            }
            resolveSetup()
        }
    }

    /**
     * Runs the discovery sweep and refreshes the shared-catalog/owner hints for the current root
     * from its result. Called when the Library & Sync sheet opens and by the Rediscover action.
     */
    /**
     * Sweeps the server for Homer libraries. [force] is for the explicit Rediscover button; the
     * automatic call when the sheet opens is throttled, because the sweep is dozens of requests
     * and it used to re-run in full every single time the sheet was opened.
     */
    fun rediscover(force: Boolean = false) {
        if (_discovering.value) return
        val now = System.currentTimeMillis()
        if (!force && _discovered.value.isNotEmpty() && now - lastDiscoveryAtMs < DISCOVERY_TTL_MS) return
        viewModelScope.launch {
            _discovering.value = true
            try {
                val libraries = discovery.discover()
                _discovered.value = libraries
                lastDiscoveryAtMs = now
                val current = libraries.firstOrNull { it.isCurrentRoot }
                _sharedCatalogAvailable.value = current?.hasSharedCatalog == true
                _libraryOwner.value = current?.owner
            } finally {
                _discovering.value = false
            }
        }
    }

    private val _librarySetup = MutableStateFlow<LibrarySetup>(LibrarySetup.Unknown)

    /**
     * Whether this install has a library yet, and what to do about it if not.
     *
     * The old answer was an empty shelf and a button to settings, which is the worst possible
     * outcome for the commonest case: a second device, with a finished index sitting on the server
     * beside it, crawling twelve thousand files from scratch because the shared-index switch
     * defaults to off. [LibraryDiscovery] has always been able to find that index; it was only ever
     * used to populate a list in settings.
     */
    val librarySetup: StateFlow<LibrarySetup> = _librarySetup.asStateFlow()

    /**
     * Decides between adopting an existing library, asking which one, and asking where the books
     * are — once, on launch, before an empty shelf is ever shown.
     *
     * Anything that already has books, or has ever crawled, is left alone: an empty library the user
     * has actually scanned is a real answer, not a question.
     */
    private suspend fun resolveSetup() {
        if (bookDao.count() > 0 || crawlDirDao.lastScanned() != null) {
            _librarySetup.value = LibrarySetup.Ready
            return
        }
        _librarySetup.value = LibrarySetup.Looking
        val candidates = runCatching { discovery.discover() }.getOrElse { emptyList() }
        // The Library page shows the same list; priming it (and its freshness stamp) means opening
        // that page straight after setup does not repeat a sweep that is dozens of requests.
        _discovered.value = candidates
        lastDiscoveryAtMs = System.currentTimeMillis()
        val withIndex = candidates.filter { it.hasSharedCatalog }
        _librarySetup.value = when {
            // Exactly one library with an index: there is nothing to ask. Adopting it silently is
            // the entire point — the user should never learn there was a decision to make.
            withIndex.size == 1 -> return adopt(withIndex.single().relativePath)
            candidates.isNotEmpty() -> LibrarySetup.Choose(candidates)
            else -> LibrarySetup.NothingFound
        }
    }

    /**
     * Takes [path] as the library: read the shared index if it has one, crawl if it does not.
     *
     * Sharing is switched on as part of adopting, and the setup screen says so before the tap. It is
     * what makes the next device's adoption work, and leaving it off is how one device ended up
     * doing all the work for ever. A share link is left alone — writing there is not this device's
     * call, and [LibraryIndexRepository.canPublish] would refuse anyway.
     */
    fun adopt(path: String) {
        viewModelScope.launch {
            _librarySetup.value = LibrarySetup.Adopting
            libraryRepository.setLibraryRoot(path)
            _libraryRoot.value = path
            if (account.value?.kind != WebDavKind.SHARE) librarySettings.setSharedCatalogEnabled(true)
            _sharedCatalogAvailable.value = libraryIndex.pull()
            if (bookDao.count() == 0) {
                // No index there, or one that turned out to hold nothing: the folder still has to
                // be read, and a crawl is the only thing that can do it.
                libraryIndexManager.scan()
            } else {
                // The index filled the shelf without a crawl, so nothing has looked at the storage
                // folder — and after a sign-out and back in, or a reinstall over the same folder,
                // the audio is often already sitting there. Claim it rather than re-downloading it.
                localMirror.adoptDownloads()
            }
            _librarySetup.value = LibrarySetup.Ready
        }
    }

    /**
     * Abandons the list of candidates for typing a folder in.
     *
     * Without it the choice is a dead end for the one user whose library is somewhere the sweep
     * cannot see — a folder with no Homer marker in it yet, which is every library before Homer has
     * ever read it.
     */
    fun setupNameFolder() {
        _librarySetup.value = LibrarySetup.NothingFound
    }

    fun onLibraryRootChange(value: String) {
        _libraryRoot.value = value
    }

    fun scan() {
        viewModelScope.launch {
            // Persist the root first; the worker reads it. Scan + covers (+ shared-index publish)
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

    /** Fetch art for the books that have none (no library crawl, nothing already cached refetched). */
    fun fetchCoverArt() = libraryIndexManager.fetchMissingCovers()

    /** Re-fetch cover art for every book (no library crawl). */
    fun refreshCoverArt() = libraryIndexManager.refreshCovers()

    /** Measure the length of every book that doesn't have one yet — see the manager for the cost. */
    fun measureBookLengths() = libraryIndexManager.measureDurations()

    /** As [measureBookLengths], but also re-arms the files whose probe failed before. */
    fun remeasureBookLengths() = libraryIndexManager.remeasureDurations()

    /** Publish outstanding metadata corrections now. */
    fun publishCorrections() = libraryIndexManager.publishCorrections()

    /** Stops everything queued or running. Every pass resumes where it stopped. */
    fun stopIndexing() = libraryIndexManager.cancel()

    /** How many books still have no length, so the settings row can say whether it is worth a tap. */
    val unmeasuredCount: StateFlow<Int> = bookDao.observeCountWithoutDuration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** How many books still have no cached art — the artwork row's completeness. */
    val artlessCount: StateFlow<Int> = bookDao.observeCountWithoutArt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** How many books carry a correction worth sharing (the personal flags are not counted). */
    val correctionCount: StateFlow<Int> = bookOverrideDao.observeCorrectionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The last crawl that saw the whole tree, and whose it was.
     *
     * On the screen because it is what authorises deletion — until one has run, a book removed on
     * the server is kept rather than pruned, and that is otherwise invisible.
     */
    val lastFullCrawl: StateFlow<CrawlSummary?> = libraryIndex.lastFullCrawl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** When the library was last crawled; null before the first scan. */
    val lastScannedAt: StateFlow<Long?> = crawlDirDao.observeLastScanned()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live progress of the pass running now, for the row that asked for it. */
    val indexProgress: StateFlow<LibraryIndexManager.IndexProgress?> = libraryIndexManager.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Which passes are outstanding — running or waiting their turn.
     *
     * Each row reads its own state from this. It is deliberately not a reason to refuse a tap:
     * asking for a second pass queues it now, where it used to cancel the one in flight.
     */
    val indexQueued: StateFlow<Set<IndexPass>> = libraryIndexManager.queued
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Whether anything at all is outstanding — what Stop is offered on. */
    val indexActive: StateFlow<Boolean> = libraryIndexManager.active
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * What the shared index is doing, so a slow read is not silence.
     *
     * Converting a v1 catalog is tens of seconds on the path that fills an empty shelf, and it used
     * to look exactly like an empty library.
     */
    val indexActivity: StateFlow<IndexActivity> = libraryIndex.activity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndexActivity.IDLE)

    /** Queued but not running: constraints (a metered connection, usually) are not met yet. */
    val indexWaiting: StateFlow<Boolean> = libraryIndexManager.waiting
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun download(bookId: String) = downloadManager.download(bookId)
    fun deleteDownload(bookId: String) = downloadManager.delete(bookId)

    /** Take a whole series offline (or drop it) — the series menu's one switch. */
    fun downloadAll(bookIds: List<String>) = bookIds.forEach(downloadManager::download)
    fun deleteDownloads(bookIds: List<String>) = bookIds.forEach(downloadManager::delete)
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

    fun setDownloadOnPlay(value: Boolean) {
        viewModelScope.launch { playbackSettings.setDownloadOnPlay(value) }
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

    /** Saves metadata corrections + hidden flag; blank fields revert to detection. */
    fun saveOverride(
        bookId: String,
        title: String,
        author: String,
        series: String,
        seriesIndex: String,
        genre: String,
        tags: String,
        hidden: Boolean,
        downloadOnPlay: Boolean?,
    ) {
        viewModelScope.launch {
            bookEditor.saveOverride(bookId, title, author, series, seriesIndex, genre, tags, hidden, downloadOnPlay)
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

    /**
     * Points storage at a user-picked SAF folder. If the folder already holds a Homer library the
     * user is asked what to do ([pendingStorageChange]); otherwise everything is moved into it.
     */
    fun setCustomStorageFolder(uri: Uri) {
        viewModelScope.launch { requestStorageChange(uri.toString()) }
    }

    /** Reverts storage to the default app-external location, moving data back (or asking). */
    fun useDefaultStorage() {
        viewModelScope.launch { requestStorageChange(null) }
    }

    /** Points storage at an absolute folder path via all-files access (bypasses SAF). */
    fun setCustomStoragePath(path: String) {
        viewModelScope.launch { requestStorageChange(path) }
    }

    /** A storage-location token is either a SAF `content://` tree or an absolute filesystem path. */
    private fun isSafToken(token: String?) = token?.startsWith("content://") == true

    private suspend fun requestStorageChange(target: String?) {
        val source = storageLocation.currentLocation()
        Log.d(TAG_STORAGE, "requestStorageChange: source=$source target=$target")
        if (source == target) return // already there
        // A SAF folder needs a durable grant taken before we can read/write it (and some pickers
        // refuse certain folders, throwing here). An all-files path needs no per-folder grant.
        if (isSafToken(target)) {
            try {
                storageLocation.takePersistable(target!!)
                Log.d(TAG_STORAGE, "took persistable permission for $target")
            } catch (e: Exception) {
                Log.w(TAG_STORAGE, "could not take a durable permission for the chosen folder", e)
                return
            }
        }
        val hasExisting = runCatching {
            val area = storageLocation.areaFor(target)
            area.exists(MIRROR_MARKER) || area.exists(LEGACY_MIRROR_MARKER)
        }.getOrDefault(false)
        if (hasExisting) {
            // The folder already has Homer data — let the user choose load vs replace.
            Log.d(TAG_STORAGE, "target already has a Homer library; prompting load-vs-replace")
            _pendingStorageChange.value = PendingStorageChange(source, target)
        } else {
            // Empty target: switch the location NOW (synchronous + reliable, independent of the
            // worker), then move any existing local data across in the background.
            commitLocation(source, target)
            storageMigrationManager.migrate(source, target, overwrite = false)
        }
    }

    /** Commits the active storage location immediately and releases the old SAF grant if any. */
    private suspend fun commitLocation(source: String?, target: String?) {
        storageLocation.commit(target)
        if (isSafToken(source) && source != target) storageLocation.releasePersistable(source!!)
        Log.d(TAG_STORAGE, "storage location committed to ${target ?: "default"}")
    }

    /** Adopt the library already in the chosen folder (merge progress, keep its downloads). */
    fun loadPendingStorage() {
        val p = _pendingStorageChange.value ?: return
        _pendingStorageChange.value = null
        viewModelScope.launch {
            // Before commitLocation, which releases the old folder's SAF grant and makes it
            // unreadable. Hand-picked covers are the one part of the local cache that isn't
            // re-derivable, so they're carried across rather than dropped.
            storageMigrator.carryCustomCovers(p.source, p.target)
            commitLocation(p.source, p.target)
            adoptCurrentArea()
        }
    }

    /** Overwrite the chosen folder's Homer data with this device's (switch now, move in background). */
    fun replacePendingStorage() {
        val p = _pendingStorageChange.value ?: return
        _pendingStorageChange.value = null
        viewModelScope.launch {
            commitLocation(p.source, p.target)
            storageMigrationManager.migrate(p.source, p.target, overwrite = true)
        }
    }

    /** Abandon a pending storage change, releasing the SAF grant taken to probe the folder. */
    fun cancelPendingStorage() {
        val p = _pendingStorageChange.value ?: return
        _pendingStorageChange.value = null
        val target = p.target
        if (!isSafToken(target)) return
        viewModelScope.launch {
            if (target != storageLocation.currentLocation()) storageLocation.releasePersistable(target!!)
        }
    }

    /**
     * Adopts whatever the (now active) area already holds: import its progress mirror (LWW),
     * recompute download status against it, and re-fetch covers (their old Uris are stale). Used
     * when the user loads an existing library in the chosen folder rather than moving into it.
     *
     * Only *detected* art is discarded here — it costs one enrichment pass to rebuild. Custom
     * covers are the user's own images and can't be recovered, so `loadPendingStorage` copies them
     * into the new area first (see [StorageMigrator.carryCustomCovers]).
     */
    private suspend fun adoptCurrentArea() {
        bookDao.resetCoverArt()
        localMirror.import()
        localMirror.adoptDownloads()
        libraryIndexManager.fetchMissingCovers()
    }

    /** Toggle syncing my listening progress to my account's `.homer/index.json`. */
    fun setProgressSync(enabled: Boolean) {
        viewModelScope.launch { librarySettings.setProgressSyncEnabled(enabled) }
    }

    /** Toggle the shared library catalog. Turning it on bootstraps from (or creates) the catalog. */
    fun setSharedCatalog(enabled: Boolean) {
        viewModelScope.launch {
            librarySettings.setSharedCatalogEnabled(enabled)
            if (enabled) {
                // pull() first, always: it is the only thing that converts a v1 catalog, and
                // probing for structure.json alone would miss one — publishing this device's view
                // over it instead, and losing every duration the old index had measured.
                if (!libraryIndex.pull()) libraryIndex.push()
                _sharedCatalogAvailable.value = libraryIndex.exists()
            }
        }
    }

    fun setSortMode(sort: LibrarySort) {
        viewModelScope.launch { librarySettings.setSortMode(sort.key) }
    }

    fun setShelfMode(shelving: LibraryShelving) {
        // No sort correction here on purpose — `sortMode` clamps on read, which covers a stored
        // pair this write path would never see, and keeps the user's choice for later.
        viewModelScope.launch { librarySettings.setShelfMode(shelving.key) }
    }

    fun setSeriesMode(mode: LibrarySeriesMode) {
        viewModelScope.launch { librarySettings.setSeriesMode(mode.key) }
    }

    /** Quick hide/show from the context menu, preserving any existing metadata override. */
    fun setHidden(bookId: String, hidden: Boolean) {
        viewModelScope.launch { bookEditor.setHidden(bookId, hidden) }
    }

    /** "Mark as completed": resets the book's progress so it drops off the listening shelf and reopens fresh. */
    fun markCompleted(bookId: String) = connection.resetProgress(bookId)

    /** Toggle play/pause on the currently-loaded book (docked mini-player). */
    fun playPause() = connection.playPause()

    /** Retry a stalled stream from the docked mini-player (after a connection error). */
    fun retry() = connection.retry()

    /**
     * Signs out, and clears the library with it — which is what the confirmation has always said.
     *
     * Delegated to [SignOut] rather than done here because clearing the credentials tears this
     * ViewModel down; work launched in [viewModelScope] would be cancelled part-way through.
     */
    fun logout() = signOut()

    private companion object {
        const val LISTENING_LIMIT = 12

        /** How long a discovery sweep stays fresh enough to reuse (the button always forces). */
        const val DISCOVERY_TTL_MS = 10 * 60_000L
        const val MIRROR_MARKER = "progress.json"
        const val LEGACY_MIRROR_MARKER = ".homer/index.json"
        const val TAG_STORAGE = "HomerStore"
    }
}

/**
 * Whether this install has a library yet, and what has to happen if it does not.
 *
 * It exists so that first install, second device and reinstall are one flow rather than three, and
 * so that none of them starts with an empty shelf and a pointer to settings.
 */
sealed interface LibrarySetup {
    /** Not decided yet — the launch path has not reached the question. */
    data object Unknown : LibrarySetup

    /** There is a library (or the user has scanned and it really is empty). Nothing to ask. */
    data object Ready : LibrarySetup

    /** Sweeping the server for something that looks like a library. */
    data object Looking : LibrarySetup

    /** Reading the library that was found or chosen. */
    data object Adopting : LibrarySetup

    /** Several plausible libraries; the user picks, with each one's book count in front of them. */
    data class Choose(val candidates: List<DiscoveredLibrary>) : LibrarySetup

    /** Nothing on the server carries a Homer index, so the folder has to be named. */
    data object NothingFound : LibrarySetup
}

/** A storage-folder change awaiting the user's decision when the target already holds a library. */
data class PendingStorageChange(val source: String?, val target: String?)

/**
 * Builds the render list. [group] alone decides sectioning and whether series collapse into
 * shelves (decoupled from [sort], which orders the units within): Author/Series group into shelves,
 * a series shelf positioned by [sort] but its episodes always in reading order; None is a flat
 * sorted list; Genre sections flat by genre.
 */
private fun buildEntries(
    books: List<BookListItem>,
    sort: LibrarySort,
    shelving: LibraryShelving,
    series: LibrarySeriesMode,
): List<LibraryEntry> {
    // Collapsing follows the series control alone now. It used to be decided by the shelving, so
    // "by genre" silently flattened every series while "by author" kept them stacked — nobody
    // chose that, it fell out of one expression.
    val units = if (series == LibrarySeriesMode.STACKED) {
        collapseIntoUnits(books)
    } else {
        books.map { SortUnit.Solo(it) }
    }
    val ordered = units.sortedWith(unitComparator(sort))

    return when (shelving) {
        LibraryShelving.ITEM -> ordered.map { it.toEntry() }
        LibraryShelving.AUTHOR -> sectioned(ordered, "Unknown author") { it.author }
        // A stacked series spans genres, so it can't sit under one; it keeps its own section.
        LibraryShelving.GENRE -> sectioned(ordered, "No genre") { (it as? SortUnit.Solo)?.book?.genre }
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

