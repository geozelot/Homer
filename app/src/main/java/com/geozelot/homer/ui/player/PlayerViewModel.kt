package com.geozelot.homer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.playback.PlaybackConnection
import com.geozelot.homer.playback.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = connection.state

    private val bookId = MutableStateFlow<String?>(null)

    /** Whole-book length; fills in reactively as durations are measured on first open. */
    val bookDurationMs: StateFlow<Long?> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else bookDao.observeById(id).map { it?.totalDurationMs }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun play(bookId: String) {
        this.bookId.value = bookId
        connection.playBook(bookId)
    }

    fun playPause() = connection.playPause()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun nextChapter() = connection.nextChapter()
    fun previousChapter() = connection.previousChapter()
}
