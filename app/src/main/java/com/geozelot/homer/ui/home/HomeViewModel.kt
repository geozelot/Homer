package com.geozelot.homer.ui.home

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
import com.geozelot.homer.data.library.LibraryRepository
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.settings.PlaybackSettings
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
    val totalDurationMs: Long?,
    /** Remaining time from the saved position; null if not started or not yet measured. */
    val timeLeftMs: Long?,
    /** Fraction of the book listened (0f–1f); null if not started or duration unmeasured. */
    val progress: Float?,
    /** When this book was last played, for Continue-shelf recency; null if never. */
    val lastPlayedAt: Long?,
    /** Offline-download status ([com.geozelot.homer.data.db.entity.DownloadStatus]) or null. */
    val downloadStatus: String?,
    val downloadedFiles: Int,
    val hidden: Boolean,
) {
    /** Reached the end (a measured book whose remaining time is zero). */
    val finished: Boolean get() = timeLeftMs != null && timeLeftMs <= 0L

    /** Fully downloaded for offline playback. */
    val isDownloaded: Boolean get() = downloadStatus == DownloadStatus.DONE
}

/** A library row: either a standalone book or a collapsible series shelf. */
sealed interface LibraryEntry {
    data class Standalone(val book: BookListItem) : LibraryEntry
    data class Series(
        val key: String,
        val name: String,
        val author: String?,
        val books: List<BookListItem>,
    ) : LibraryEntry
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val librarySettings: LibrarySettings,
    private val webDavClient: WebDavClient,
    private val homerSync: HomerSyncRepository,
    private val downloadManager: DownloadManager,
    private val playbackSettings: PlaybackSettings,
    private val bookOverrideDao: BookOverrideDao,
    private val connection: PlaybackConnection,
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
    private val effectiveBooks: Flow<List<Pair<BookEntity, Boolean>>> =
        combine(
            libraryRepository.books,
            bookOverrideDao.observeAll(),
            _showHidden,
        ) { books, overrides, showHidden ->
            val overrideByBook = overrides.associateBy { it.bookId }
            books
                .map { book ->
                    val override = overrideByBook[book.id]
                    book.applyOverride(override) to (override?.hidden == true)
                }
                .filter { (_, hidden) -> showHidden || !hidden }
                .sortedWith(
                    // Nulls last (the `== null` keys sort false<true), matching the DB order.
                    compareBy<Pair<BookEntity, Boolean>>(
                        { it.first.author == null },
                        { it.first.author },
                        { it.first.series == null },
                        { it.first.series },
                        { it.first.seriesIndex == null },
                        { it.first.seriesIndex },
                        { it.first.title },
                    ),
                )
        }

    private val books: StateFlow<List<BookListItem>> =
        combine(
            effectiveBooks,
            authRepository.credentials,
            playbackStateDao.observeProgress(),
            downloadDao.observeAll(),
        ) { effective, credentials, progress, downloads ->
            val progressByBook = progress.associateBy { it.bookId }
            val downloadByBook = downloads.associateBy { it.bookId }
            effective.map { (book, hidden) ->
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
                    coverModel = BookCover.model(book, credentials, webDavClient),
                    series = book.series,
                    seriesIndex = book.seriesIndex,
                    totalDurationMs = total,
                    timeLeftMs = if (measured) (total!! - elapsed!!).coerceAtLeast(0) else null,
                    progress = if (measured) (elapsed!!.toFloat() / total!!).coerceIn(0f, 1f) else null,
                    lastPlayedAt = bookProgress?.updatedAt,
                    downloadStatus = download?.status,
                    downloadedFiles = download?.downloadedFiles ?: 0,
                    hidden = hidden,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Library list grouped into collapsible series shelves (2+ books sharing a series). */
    val entries: StateFlow<List<LibraryEntry>> = books
        .map(::groupIntoEntries)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    val wifiOnlyDownloads: StateFlow<Boolean> = playbackSettings.wifiOnlyDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bookCount: StateFlow<Int> = libraryRepository.bookCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val scanState: StateFlow<ScanState> = libraryRepository.scanState

    private val _libraryRoot = MutableStateFlow("")
    val libraryRoot: StateFlow<String> = _libraryRoot.asStateFlow()

    init {
        viewModelScope.launch { _libraryRoot.value = libraryRepository.libraryRoot.first() }
        // Fill in covers for books missing one, without needing a full re-scan.
        libraryRepository.enrichCovers()
        // Pull cross-device resume positions from the .homer manifest on open.
        viewModelScope.launch { homerSync.sync() }
    }

    fun onLibraryRootChange(value: String) {
        _libraryRoot.value = value
    }

    fun scan() {
        viewModelScope.launch {
            libraryRepository.setLibraryRoot(_libraryRoot.value)
            libraryRepository.scan(incremental = false)
        }
    }

    fun download(bookId: String) = downloadManager.download(bookId)
    fun deleteDownload(bookId: String) = downloadManager.delete(bookId)
    fun setWifiOnlyDownloads(value: Boolean) {
        viewModelScope.launch { playbackSettings.setWifiOnlyDownloads(value) }
    }

    fun setShowHidden(value: Boolean) {
        _showHidden.value = value
    }

    /** Saves metadata corrections + hidden flag; blank fields revert to detection. */
    fun saveOverride(
        bookId: String,
        title: String,
        author: String,
        series: String,
        seriesIndex: String,
        hidden: Boolean,
    ) {
        viewModelScope.launch {
            bookOverrideDao.upsert(
                BookOverrideEntity(
                    bookId = bookId,
                    title = title.trim().ifBlank { null },
                    author = author.trim().ifBlank { null },
                    series = series.trim().ifBlank { null },
                    seriesIndex = seriesIndex.trim().toIntOrNull(),
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

    /** Toggle play/pause on the currently-loaded book (docked mini-player). */
    fun playPause() = connection.playPause()

    fun logout() = authRepository.logout()

    private companion object {
        const val CONTINUE_LIMIT = 12
    }
}

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
