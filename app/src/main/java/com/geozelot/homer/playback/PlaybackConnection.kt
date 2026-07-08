package com.geozelot.homer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Snapshot of playback state for the UI. */
data class PlaybackUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * App-side bridge to [PlaybackService]. Lazily connects a [MediaController], exposes
 * playback as observable [state], and offers transport controls. Lives for the app's
 * lifetime (single-user, single playback session).
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistResolver: PlaylistResolver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()
    }

    /** Loads and starts a book from the beginning. */
    fun playBook(bookId: String) {
        scope.launch {
            val playlist = playlistResolver.resolve(bookId) ?: return@launch
            val c = awaitController()
            c.setMediaItems(playlist.items)
            c.prepare()
            c.play()
            pushState()
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun nextChapter() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun previousChapter() {
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    }

    private suspend fun awaitController(): MediaController {
        controller?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                val c = future.get()
                controller = c
                c.addListener(listener)
                startPositionUpdates()
                pushState()
                cont.resume(c)
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun startPositionUpdates() {
        scope.launch {
            while (controller != null) {
                if (controller?.isPlaying == true) pushState()
                delay(500)
            }
        }
    }

    private fun pushState() {
        val c = controller ?: run {
            _state.value = PlaybackUiState(isConnected = false)
            return
        }
        val metadata = c.mediaMetadata
        _state.value = PlaybackUiState(
            isConnected = true,
            isPlaying = c.isPlaying,
            bookTitle = metadata.albumTitle?.toString().orEmpty(),
            chapterTitle = metadata.title?.toString().orEmpty(),
            chapterIndex = c.currentMediaItemIndex,
            chapterCount = c.mediaItemCount,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
        )
    }
}
