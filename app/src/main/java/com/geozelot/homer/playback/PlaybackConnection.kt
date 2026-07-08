package com.geozelot.homer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import com.geozelot.homer.data.metadata.DurationEnricher
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.data.sync.HomerSyncRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * App-side bridge to [PlaybackService]. Lazily connects a [MediaController], exposes
 * playback as observable [state], and offers transport controls. Lives for the app's
 * lifetime (single-user, single playback session).
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
    private val downloadDao: DownloadDao,
    private val homerSync: HomerSyncRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var currentBookId: String? = null
    private var currentCoverModel: Any? = null
    private var currentOffline = false
    private var downloadWatchJob: Job? = null
    private var syncJob: Job? = null

    private var sleepJob: Job? = null
    private var sleepTargetRealtimeMs = 0L
    private var sleepEndOfChapter = false
    private val shakeDetector = ShakeDetector(context) { extendSleepTimer(SHAKE_EXTEND_MS) }

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    init {
        // Flush position + sync when the app is backgrounded, so switching to another
        // device resumes correctly even if the user never explicitly paused.
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (currentBookId != null) flushSync()
                }
            })
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState()
            persistPosition()
            // Flush to the .homer manifest at natural boundaries (not every tick).
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) flushSync()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (sleepEndOfChapter) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // The chapter finished on its own — pause as armed.
                    controller?.pause()
                } // else: user manually skipped chapters — they've taken control, so disarm.
                clearSleepTimer()
                pushState()
            }
            // Chapter boundary is a good, cheap moment to sync cross-device position.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) flushSync()
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
            // Pull the freshest cross-device state first (time-boxed so a slow/offline
            // server never delays playback). Runs even when reopening the current book,
            // so positions and bookmarks reflect other devices.
            withTimeoutOrNull(RESUME_SYNC_TIMEOUT_MS) { homerSync.sync() }
            // Already playing this book (e.g. reopening the player) — don't restart it.
            if (currentBookId == bookId && c.mediaItemCount > 0) {
                if (!c.isPlaying) c.play()
                return@launch
            }
            val playlist = playlistResolver.resolve(bookId) ?: return@launch
            currentBookId = bookId
            currentCoverModel = playlist.coverModel
            currentOffline = playlist.offline
            // Measure per-file durations for the book total (once per book; cached).
            durationEnricher.enrich(bookId)
            val saved = playbackStateDao.findByBookId(bookId)
            c.setMediaItems(playlist.items)
            if (saved != null) {
                val index = playlist.items.indexOfFirst { it.mediaId == saved.currentMediaId }
                c.seekTo(if (index >= 0) index else 0, saved.positionMs)
            }
            c.prepare()
            c.play()
            pushState()
            // Watch for download-status flips only after the playlist is loaded, so the
            // reload can't race the initial setMediaItems.
            watchDownloadStatus(bookId)
        }
    }

    /**
     * Re-resolves the loaded book (stream ↔ local) whenever its download status flips, so
     * removing a download switches back to streaming instead of hitting deleted files, and
     * a finished download can switch to local — all without interrupting the position.
     */
    private fun watchDownloadStatus(bookId: String) {
        downloadWatchJob?.cancel()
        downloadWatchJob = scope.launch {
            downloadDao.observeByBookId(bookId).collect { download ->
                val nowOffline = download?.status == DownloadStatus.DONE
                if (currentBookId == bookId && nowOffline != currentOffline) {
                    reloadCurrentBook()
                }
            }
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
        c.setMediaItems(playlist.items)
        c.seekTo(index, position)
        c.prepare()
        if (wasPlaying) c.play()
        pushState()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
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

    /** Pauses playback after [durationMs]; shake-to-extend is armed while it counts down. */
    fun startSleepTimer(durationMs: Long) {
        clearSleepTimer()
        sleepTargetRealtimeMs = SystemClock.elapsedRealtime() + durationMs
        shakeDetector.start()
        sleepJob = scope.launch {
            while (true) {
                if (SystemClock.elapsedRealtime() >= sleepTargetRealtimeMs) {
                    controller?.pause()
                    clearSleepTimer()
                    pushState()
                    break
                }
                pushState()
                delay(500)
            }
        }
        pushState()
    }

    /** Pauses when the current chapter finishes (no shake-to-extend in this mode). */
    fun startSleepTimerEndOfChapter() {
        clearSleepTimer()
        sleepEndOfChapter = true
        pushState()
    }

    /** Adds [extraMs] to a running countdown; no-op for end-of-chapter or when off. */
    fun extendSleepTimer(extraMs: Long) {
        if (sleepJob?.isActive != true) return
        // Cap the total remaining so repeated shakes can't push the timer arbitrarily far.
        val cap = SystemClock.elapsedRealtime() + MAX_SLEEP_REMAINING_MS
        sleepTargetRealtimeMs = (sleepTargetRealtimeMs + extraMs).coerceAtMost(cap)
        val remaining = (sleepTargetRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        Log.i(TAG, "shake: sleep timer extended by ${extraMs / 1000}s -> ${remaining / 1000}s left")
        pushState()
    }

    fun cancelSleepTimer() {
        clearSleepTimer()
        pushState()
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

    private fun clearSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndOfChapter = false
        shakeDetector.stop()
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
            val future = MediaController.Builder(context, token)
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        // Session/service went away — drop the stale controller so the
                        // position loop stops and the next playBook reconnects fresh.
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
                    if (++tick % 10 == 0) persistPosition() // ~ every 5s while playing
                    if (tick % 600 == 0) flushSync() // ~ every 5 min, bounds cross-device staleness
                }
                delay(500)
            }
        }
    }

    /** Debounced-ish persistence of the current book's position to Room. */
    private fun persistPosition() {
        scope.launch { savePosition() }
    }

    private suspend fun savePosition() {
        val c = controller ?: return
        val bookId = currentBookId ?: return
        val mediaId = c.currentMediaItem?.mediaId ?: return
        val position = c.currentPosition.coerceAtLeast(0L)
        playbackStateDao.upsert(
            PlaybackStateEntity(
                bookId = bookId,
                currentMediaId = mediaId,
                positionMs = position,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Persists the latest position immediately, then reconciles with the .homer manifest —
     * debounced, so a burst of events (pause + transition + position discontinuity firing
     * together) collapses into a single WebDAV round-trip instead of several queued ones.
     */
    private fun flushSync() {
        scope.launch { savePosition() }
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            homerSync.sync()
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
            playbackSpeed = c.playbackParameters.speed,
            sleepRemainingMs = if (sleepJob?.isActive == true) {
                (sleepTargetRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            } else {
                null
            },
            sleepEndOfChapter = sleepEndOfChapter,
            coverModel = currentCoverModel,
            artworkData = if (currentCoverModel == null) metadata.artworkData else null,
        )
    }

    private companion object {
        const val TAG = "HomerPlay"
        const val SHAKE_EXTEND_MS = 5 * 60 * 1000L
        const val MAX_SLEEP_REMAINING_MS = 2 * 60 * 60 * 1000L
        const val RESUME_SYNC_TIMEOUT_MS = 5_000L
        const val SYNC_DEBOUNCE_MS = 1_000L
    }
}
