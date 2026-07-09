package com.geozelot.homer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkMetaEntity
import com.geozelot.homer.data.metadata.DurationEnricher
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.data.sync.HomerSyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Snapshot of playback state for the UI. */
data class PlaybackUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    /** Id (folder relpath) of the loaded book, or null when nothing is playing. */
    val bookId: String? = null,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    /** Milliseconds until the sleep timer pauses playback; null when off. */
    val sleepRemainingMs: Long? = null,
    /** Sleep timer set to pause at the end of the current chapter. */
    val sleepEndOfChapter: Boolean = false,
    /** Book-level cover (WebDAV URL string or a cached File), stable across chapters. */
    val coverModel: Any? = null,
    /** Embedded artwork parsed from the current file — fallback before a cover is cached. */
    val artworkData: ByteArray? = null,
)

/**
 * App-side bridge to [PlaybackService]. Lazily connects a [MediaController], exposes playback
 * as observable [state], and offers transport controls. Lives for the app's lifetime
 * (single-user, single playback session).
 *
 * Owns the controller and the derived UI state; the cross-cutting concerns are delegated to
 * focused collaborators — [SleepTimer] (sleep/shake), [PositionSyncer] (position persistence
 * + `.homer` reconciliation), and [DownloadReloadWatcher] (stream ↔ local re-resolution).
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistResolver: PlaylistResolver,
    private val playbackStateDao: PlaybackStateDao,
    private val durationEnricher: DurationEnricher,
    private val playbackSettings: PlaybackSettings,
    private val bookmarkDao: BookmarkDao,
    private val bookmarkMetaDao: BookmarkMetaDao,
    downloadDao: DownloadDao,
    private val homerSync: HomerSyncRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var currentBookId: String? = null
    private var currentCoverModel: Any? = null
    private var currentOffline = false

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val sleepTimer = SleepTimer(
        context = context,
        scope = scope,
        onPause = ::fadeOutAndPause,
        onChanged = ::pushState,
        onShake = ::extendSleepByPreference,
    )
    private val positionSyncer = PositionSyncer(scope, playbackStateDao, homerSync, ::positionSnapshot)
    private val downloadReloadWatcher = DownloadReloadWatcher(scope, downloadDao)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState()
            positionSyncer.save()
            // Flush to the .homer manifest at natural boundaries (not every tick).
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) positionSyncer.flush()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val auto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            sleepTimer.onChapterTransition(auto)
            // Chapter boundary is a good, cheap moment to sync cross-device position.
            if (auto) positionSyncer.flush()
        }
    }

    /** Loads a book and resumes from its saved position (or the start if none). */
    fun playBook(bookId: String) {
        scope.launch {
            val c = try {
                awaitController()
            } catch (e: Exception) {
                Log.w(TAG, "cannot connect media controller", e)
                return@launch
            }
            // Pull the freshest cross-device state first (time-boxed so a slow/offline server
            // never delays playback). Runs even when reopening the current book, so positions
            // and bookmarks reflect other devices.
            withTimeoutOrNull(RESUME_SYNC_TIMEOUT_MS) { positionSyncer.pull() }
            // Already loaded (e.g. reopening the player) — leave its play/pause state as-is.
            if (currentBookId == bookId && c.mediaItemCount > 0) return@launch
            val playlist = playlistResolver.resolve(bookId) ?: return@launch
            currentBookId = bookId
            currentCoverModel = playlist.coverModel
            currentOffline = playlist.offline
            val saved = playbackStateDao.findByBookId(bookId)
            c.setMediaItems(playlist.items)
            if (saved != null) {
                val index = playlist.items.indexOfFirst { it.mediaId == saved.currentMediaId }
                c.seekTo(if (index >= 0) index else 0, saved.positionMs)
            }
            // Load the queue only — do NOT prepare()/play(), so opening a book streams no
            // audio. Buffering + playback begin when the user hits play (see [playPause]).
            pushState()
            // Watch for download-status flips only after the playlist is loaded, so the reload
            // can't race the initial setMediaItems.
            downloadReloadWatcher.watch(
                bookId = bookId,
                isOffline = { currentOffline },
                onSourceFlip = { reloadCurrentBook() },
            )
        }
    }

    /** Rebuilds the current playlist from the latest source, keeping chapter + position. */
    private suspend fun reloadCurrentBook() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return // nothing loaded yet — playBook is still setting up
        val bookId = currentBookId ?: return
        val playlist = playlistResolver.resolve(bookId) ?: return
        currentOffline = playlist.offline
        currentCoverModel = playlist.coverModel
        val index = c.currentMediaItemIndex
        val position = c.currentPosition.coerceAtLeast(0L)
        val wasPlaying = c.isPlaying
        // If the book was only loaded (never played), keep it that way — a source swap must
        // not start streaming on its own.
        val wasPrepared = c.playbackState != Player.STATE_IDLE
        c.setMediaItems(playlist.items)
        c.seekTo(index, position)
        if (wasPrepared) {
            c.prepare()
            if (wasPlaying) c.play()
        }
        pushState()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            return
        }
        // First play after loading: prepare (which begins buffering/streaming) and kick off
        // the one-time per-file duration measurement now, rather than on open.
        if (c.playbackState == Player.STATE_IDLE) {
            c.prepare()
            currentBookId?.let { durationEnricher.enrich(it) }
        }
        c.play()
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

    /** Sets playback speed and remembers it as the global default. */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        scope.launch { playbackSettings.setSpeed(speed) }
    }

    /** Toggles silence-trimming on the service's player (custom command) and persists it. */
    fun setSkipSilence(enabled: Boolean) {
        controller?.sendCustomCommand(
            PlaybackCommands.SET_SKIP_SILENCE,
            Bundle().apply { putBoolean(PlaybackCommands.KEY_ENABLED, enabled) },
        )
        scope.launch { playbackSettings.setSkipSilence(enabled) }
    }

    fun startSleepTimer(durationMs: Long) {
        scope.launch { playbackSettings.setSleepLastDurationMs(durationMs) }
        sleepTimer.startCountdown(durationMs)
    }

    fun startSleepTimerEndOfChapter() = sleepTimer.startEndOfChapter()

    fun cancelSleepTimer() = sleepTimer.cancel()

    /** Ramps the volume down over the configured seconds, then pauses (0 = pause abruptly). */
    private fun fadeOutAndPause() {
        val c = controller ?: return
        scope.launch {
            val fadeMs = playbackSettings.sleepFadeOutSeconds.first() * 1000L
            if (fadeMs <= 0L) {
                c.pause()
                return@launch
            }
            val steps = 20
            val stepMs = (fadeMs / steps).coerceAtLeast(10L)
            for (i in steps - 1 downTo 0) {
                c.volume = i / steps.toFloat()
                delay(stepMs)
            }
            c.pause()
            c.volume = 1f // restore so the next play starts at full volume
        }
    }

    /** Applies the user's shake-to-extend preference to the running countdown. */
    private fun extendSleepByPreference() {
        if (!sleepTimer.isCountingDown) return
        scope.launch {
            when (val mode = playbackSettings.sleepExtend.first()) {
                "chapter" -> sleepTimer.startEndOfChapter()
                "previous" -> {
                    val last = playbackSettings.sleepLastDurationMs.first()
                    sleepTimer.extendBy(if (last > 0L) last else DEFAULT_EXTEND_MS)
                }
                else -> sleepTimer.extendBy((mode.toLongOrNull() ?: 15L) * 60_000L)
            }
        }
    }

    /** Saves a bookmark at the current chapter + position of the playing book. */
    fun addBookmark() {
        val c = controller ?: return
        val bookId = currentBookId ?: return
        val mediaId = c.currentMediaItem?.mediaId ?: return
        val chapterTitle = c.mediaMetadata.title?.toString().orEmpty()
        val position = c.currentPosition.coerceAtLeast(0L)
        scope.launch {
            val now = System.currentTimeMillis()
            bookmarkDao.insert(
                BookmarkEntity(
                    bookId = bookId,
                    mediaId = mediaId,
                    chapterTitle = chapterTitle,
                    positionMs = position,
                    label = null,
                    createdAt = now,
                ),
            )
            bookmarkMetaDao.upsert(BookmarkMetaEntity(bookId, now))
            homerSync.sync()
        }
    }

    /** Deletes a bookmark and syncs the change out. */
    fun deleteBookmark(id: Long, bookId: String) {
        scope.launch {
            bookmarkDao.deleteById(id)
            bookmarkMetaDao.upsert(BookmarkMetaEntity(bookId, System.currentTimeMillis()))
            homerSync.sync()
        }
    }

    /** Seeks the current playlist to a bookmark's chapter + position. */
    fun jumpToBookmark(mediaId: String, positionMs: Long) {
        val c = controller ?: return
        val index = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == mediaId }
            ?: return
        c.seekTo(index, positionMs)
    }

    private suspend fun awaitController(): MediaController {
        controller?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token)
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        // Session/service went away — drop the stale controller so the position
                        // loop stops and the next playBook reconnects fresh.
                        if (this@PlaybackConnection.controller === controller) {
                            this@PlaybackConnection.controller = null
                        }
                    }
                })
                .buildAsync()
            cont.invokeOnCancellation { future.cancel(true) }
            future.addListener({
                try {
                    val c = future.get()
                    controller = c
                    c.addListener(listener)
                    // Apply the persisted global speed to this session.
                    scope.launch { c.setPlaybackSpeed(playbackSettings.speed.first()) }
                    startPositionUpdates()
                    pushState()
                    cont.resume(c)
                } catch (e: Exception) {
                    Log.w(TAG, "media controller connect failed", e)
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun startPositionUpdates() {
        scope.launch {
            var tick = 0
            while (controller != null) {
                if (controller?.isPlaying == true) {
                    pushState()
                    if (++tick % 10 == 0) positionSyncer.save() // ~ every 5s while playing
                    if (tick % 600 == 0) positionSyncer.flush() // ~ every 5 min, bounds staleness
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    /** The current playback position, or null when nothing is loaded — used by [PositionSyncer]. */
    private fun positionSnapshot(): PositionSnapshot? {
        val c = controller ?: return null
        val bookId = currentBookId ?: return null
        val mediaId = c.currentMediaItem?.mediaId ?: return null
        return PositionSnapshot(bookId, mediaId, c.currentPosition.coerceAtLeast(0L))
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
            bookId = currentBookId,
            bookTitle = metadata.albumTitle?.toString().orEmpty(),
            chapterTitle = metadata.title?.toString().orEmpty(),
            chapterIndex = c.currentMediaItemIndex,
            chapterCount = c.mediaItemCount,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
            playbackSpeed = c.playbackParameters.speed,
            sleepRemainingMs = sleepTimer.remainingMs(),
            sleepEndOfChapter = sleepTimer.endOfChapter,
            coverModel = currentCoverModel,
            artworkData = if (currentCoverModel == null) metadata.artworkData else null,
        )
    }

    private companion object {
        const val TAG = "HomerPlay"
        const val RESUME_SYNC_TIMEOUT_MS = 5_000L
        const val POSITION_POLL_MS = 500L
        const val DEFAULT_EXTEND_MS = 15 * 60_000L
    }
}
