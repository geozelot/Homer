package com.geozelot.homer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.download.DownloadManager
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.playback.PlaybackConnection
import com.geozelot.homer.playback.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val connection: PlaybackConnection,
    bookDao: BookDao,
    audioFileDao: AudioFileDao,
    playbackSettings: PlaybackSettings,
    private val bookmarkDao: BookmarkDao,
    private val downloadManager: DownloadManager,
    downloadDao: DownloadDao,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = connection.state

    private val bookId = MutableStateFlow<String?>(null)

    val skipSilence: StateFlow<Boolean> = playbackSettings.skipSilence
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else bookmarkDao.observeForBook(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadState: StateFlow<DownloadEntity?> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else downloadDao.observeByBookId(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whole-book length; fills in reactively as durations are measured on first open. */
    val bookDurationMs: StateFlow<Long?> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else bookDao.observeById(id).map { it?.totalDurationMs }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val chapterDurations: StateFlow<List<Long>?> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else audioFileDao.observeForBook(id).map { files ->
                // Null until every chapter is measured, so "time left" is never misleading.
                if (files.isNotEmpty() && files.all { it.durationMs != null }) {
                    files.map { it.durationMs!! }
                } else {
                    null
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Time remaining in the whole book from the current chapter + position; live. */
    val timeLeftMs: StateFlow<Long?> = combine(chapterDurations, state) { durations, s ->
        if (durations == null) return@combine null
        val index = s.chapterIndex.coerceIn(0, durations.size)
        val elapsed = durations.take(index).sum() + s.positionMs.coerceAtLeast(0)
        (durations.sum() - elapsed).coerceAtLeast(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun play(bookId: String) {
        this.bookId.value = bookId
        connection.playBook(bookId)
    }

    fun playPause() = connection.playPause()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun setSpeed(speed: Float) = connection.setSpeed(speed)
    fun setSkipSilence(enabled: Boolean) = connection.setSkipSilence(enabled)
    fun startSleepTimer(durationMs: Long) = connection.startSleepTimer(durationMs)
    fun startSleepTimerEndOfChapter() = connection.startSleepTimerEndOfChapter()
    fun cancelSleepTimer() = connection.cancelSleepTimer()
    fun addBookmark() = connection.addBookmark()
    fun jumpToBookmark(bookmark: BookmarkEntity) =
        connection.jumpToBookmark(bookmark.mediaId, bookmark.positionMs)
    fun deleteBookmark(bookmark: BookmarkEntity) =
        connection.deleteBookmark(bookmark.id, bookmark.bookId)
    fun download() = bookId.value?.let(downloadManager::download)
    fun deleteDownload() = bookId.value?.let(downloadManager::delete)
    fun nextChapter() = connection.nextChapter()
    fun previousChapter() = connection.previousChapter()
}
