package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.entity.BookEntity
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
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
) {
    val books: Flow<List<BookEntity>> = bookDao.observeAll()
    val bookCount: Flow<Int> = bookDao.observeCount()
    val libraryRoot: Flow<String> = librarySettings.libraryRoot

    /**
     * Drops cached cover art, so a deep artwork pass fetches every book's again.
     *
     * Also the only way back for a book whose art was looked for and not found: once a cover probe
     * is recorded as fruitless it is never retried on its own.
     */
    suspend fun resetCoverArt() {
        bookDao.resetCoverArt()
    }

    /**
     * Re-arms the files whose duration probe came up empty, and the tag read that rides along with
     * it, so a deep length pass tries them again.
     *
     * Stored lengths are deliberately left alone — a duration is a fact about bytes, so there is
     * nothing to correct, only gaps to fill.
     */
    suspend fun rearmDurations() {
        audioFileDao.resetDurationAttempted()
        bookDao.resetMetadataAttempted()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    suspend fun setLibraryRoot(path: String) = librarySettings.setLibraryRoot(path)

    /** Runs a scan, updating [scanState]. No-op if a scan is already running. Cover extraction
     *  is driven separately by [LibraryIndexWorker] after the scan completes. */
    suspend fun scan(incremental: Boolean = false) {
        if (_scanState.value is ScanState.Scanning) return
        _scanState.value = ScanState.Scanning(0, 0)
        try {
            val root = librarySettings.libraryRoot.first()
            val result = scanner.scan(root, incremental, System.currentTimeMillis()) { dirs, books ->
                _scanState.value = ScanState.Scanning(dirs, books)
            }
            // Only a COMPLETE crawl may stamp this — one that skipped no subtree. It is what lets
            // the shared index delete a book: a crawl that did not visit a folder cannot say the
            // book in it is gone, and treating it as gone would erase other devices' libraries.
            //
            // Keyed on what the crawl DID, not on which button asked for it. An incremental scan
            // that skipped nothing saw exactly as much as a full one, and that is the common case:
            // the first crawl of a library has no stored ETags to skip on. Requiring the full-crawl
            // flag meant "Rebuild the library" was the only thing that could ever stamp it, so a
            // user who only ever scanned had deletions that never propagated and a Library page
            // that said "no full crawl yet" for ever.
            if (result.complete) librarySettings.setLastFullCrawlAt(System.currentTimeMillis())
            _scanState.value = ScanState.Done(result.bookCount)
        } catch (e: CancellationException) {
            _scanState.value = ScanState.Idle
            throw e
        } catch (e: Exception) {
            _scanState.value = ScanState.Error(e.message ?: "Scan failed")
        }
    }
}
