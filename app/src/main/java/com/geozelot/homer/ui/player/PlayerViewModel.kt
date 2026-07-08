package com.geozelot.homer.ui.player

import androidx.lifecycle.ViewModel
import com.geozelot.homer.playback.PlaybackConnection
import com.geozelot.homer.playback.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val connection: PlaybackConnection,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = connection.state

    fun play(bookId: String) = connection.playBook(bookId)
    fun playPause() = connection.playPause()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun nextChapter() = connection.nextChapter()
    fun previousChapter() = connection.previousChapter()
}
