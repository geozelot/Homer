package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.metadata.CoverEnricher
import com.geozelot.homer.data.settings.LibrarySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Library-facing surface for the UI: the observable book list and scan lifecycle.
 * Owns the transient [ScanState]; the persisted index lives in Room.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val scanner: LibraryScanner,
    private val librarySettings: LibrarySettings,
    private val coverEnricher: CoverEnricher,
    bookDao: BookDao,
) {
    val books: Flow<List<BookEntity>> = bookDao.observeAll()
    val bookCount: Flow<Int> = bookDao.observeCount()
    val libraryRoot: Flow<String> = librarySettings.libraryRoot

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    suspend fun setLibraryRoot(path: String) = librarySettings.setLibraryRoot(path)

    /** Kick off background cover extraction for any books still missing a cover. */
    fun enrichCovers() = coverEnricher.enrichMissingCovers()

    /** Runs a scan, updating [scanState]. No-op if a scan is already running. */
    suspend fun scan(incremental: Boolean = false) {
        if (_scanState.value is ScanState.Scanning) return
        _scanState.value = ScanState.Scanning(0, 0)
        try {
            val root = librarySettings.libraryRoot.first()
            val result = scanner.scan(root, incremental, System.currentTimeMillis()) { dirs, books ->
                _scanState.value = ScanState.Scanning(dirs, books)
            }
            _scanState.value = ScanState.Done(result.bookCount)
            // Fill in embedded covers for books without a folder image (background).
            coverEnricher.enrichMissingCovers()
        } catch (e: CancellationException) {
            _scanState.value = ScanState.Idle
            throw e
        } catch (e: Exception) {
            _scanState.value = ScanState.Error(e.message ?: "Scan failed")
        }
    }
}
