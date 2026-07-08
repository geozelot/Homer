package com.geozelot.homer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.auth.AuthRepository
import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.library.BookCover
import com.geozelot.homer.data.library.LibraryRepository
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.sync.HomerSyncRepository
import com.geozelot.homer.data.webdav.WebDavClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val totalDurationMs: Long?,
    /** Remaining time from the saved position; null if not started or not yet measured. */
    val timeLeftMs: Long?,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val webDavClient: WebDavClient,
    private val homerSync: HomerSyncRepository,
    playbackStateDao: PlaybackStateDao,
) : ViewModel() {

    val account: StateFlow<NextcloudCredentials?> = authRepository.credentials

    val books: StateFlow<List<BookListItem>> =
        combine(
            libraryRepository.books,
            authRepository.credentials,
            playbackStateDao.observeProgress(),
        ) { books, credentials, progress ->
            val elapsedByBook = progress.associate { it.bookId to it.elapsedMs }
            books.map { book ->
                val elapsed = elapsedByBook[book.id]
                BookListItem(
                    id = book.id,
                    title = book.title,
                    author = book.author,
                    isMultiFile = book.isMultiFile,
                    fileCount = book.fileCount,
                    coverModel = BookCover.model(book, credentials, webDavClient),
                    totalDurationMs = book.totalDurationMs,
                    // Only meaningful once both a total and a saved position exist.
                    timeLeftMs = if (book.totalDurationMs != null && elapsed != null) {
                        (book.totalDurationMs - elapsed).coerceAtLeast(0)
                    } else {
                        null
                    },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun logout() = authRepository.logout()
}
