package com.geozelot.homer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkMetaEntity
import com.geozelot.homer.data.metadata.DurationEnricher
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.data.storage.LocalMirror
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
    /** Whole-book elapsed / total (from measured chapter durations); 0 total when unmeasured. */
    val bookElapsedMs: Long = 0L,
    val bookTotalMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    /** Milliseconds until the sleep timer pauses playback; null when off. */
    val sleepRemainingMs: Long? = null,
    /** Sleep timer set to pause at the end of the current chapter. */
    val sleepEndOfChapter: Boolean = false,
    /** Book-level cover (WebDAV URL string or a cached File), stable across chapters. */
    val coverModel: Any? = null,
    /** Embedded artwork parsed from the current file — fallback before a cover is cached. */
    val artworkData: ByteArray? = null,
    /** A playback error is stalling the stream (usually a lost connection); offer a retry. */
    val hasError: Boolean = false,
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
    private val audioFileDao: AudioFileDao,
    private val durationEnricher: DurationEnricher,
    private val playbackSettings: PlaybackSettings,
    private val bookmarkDao: BookmarkDao,
    private val bookmarkMetaDao: BookmarkMetaDao,
    downloadDao: DownloadDao,
    private val homerSync: HomerSyncRepository,
    localMirror: LocalMirror,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var currentBookId: String? = null
    private var currentCoverModel: Any? = null
    private var currentOffline = false

    /** Measured per-chapter durations (mediaId → ms) for the loaded book, so the scrubber and
     *  time-left reflect the saved position before playback prepares the player. */
    @Volatile
    private var currentDurations: Map<String, Long> = emptyMap()

    /** Sum of [currentDurations], cached so [pushState] doesn't re-sum on every position tick. */
    @Volatile
    private var currentBookTotal = 0L

    /** True while [playBook] is switching to a new book: the state listener must not overwrite
     *  the optimistic new-book state with the still-outgoing controller's state mid-load. */
    @Volatile
    private var loading = false

    /** Seconds to rewind on resume (cached from settings so [playPause] can apply it synchronously). */
    @Volatile
    private var autoRewindMs = 0L

    /** Set when the player reports an error; surfaced in [state] so the UI can offer a retry. */
    @Volatile
    private var playbackError = false

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    init {
        scope.launch {
            playbackSettings.autoRewindSeconds.collect { autoRewindMs = it * 1000L }
        }
    }

    private val sleepTimer = SleepTimer(
        context = context,
        scope = scope,
        onPause = ::fadeOutAndPause,
        onChanged = ::pushState,
        onShake = ::extendSleepByPreference,
    )
    private val positionSyncer = PositionSyncer(scope, playbackStateDao, homerSync, localMirror, ::positionSnapshot)
    private val downloadReloadWatcher = DownloadReloadWatcher(scope, downloadDao)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // During a book switch the controller still holds the outgoing book — ignore its
            // events so they don't clobber the optimistic new-book state.
            if (loading) return
            // Playback made progress again ⇒ clear any stale error banner.
            if (player.isPlaying) playbackError = false
            pushState()
            positionSyncer.save()
            // Flush to the .homer manifest at natural boundaries (not every tick).
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) positionSyncer.flush()
        }

        override fun onPlayerError(error: PlaybackException) {
            // Usually a dropped connection mid-stream. Surface it so the UI can offer Retry
            // instead of silently freezing on the last frame.
            Log.w(TAG, "playback error: ${error.errorCodeName}", error)
            playbackError = true
            pushState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (loading) return
            val auto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            sleepTimer.onChapterTransition(auto)
            // Chapter boundary is a good, cheap moment to sync cross-device position.
            if (auto) positionSyncer.flush()
        }
    }

    /**
     * Eagerly connects to the service so a session already playing (e.g. the service kept running
     * in the background) surfaces in the docked mini-player without the user opening a book first.
     * If the reconnected controller already holds a queue but this process hasn't tracked a book
     * yet, the current book is recovered from its media id. A no-op once a book is loaded.
     */
    fun connect() {
        if (currentBookId != null) return
        scope.launch {
            val c = try {
                awaitController()
            } catch (e: Exception) {
                Log.w(TAG, "eager connect failed", e)
                return@launch
            } ?: return@launch
            if (currentBookId != null || c.mediaItemCount == 0) return@launch
            val mediaId = c.currentMediaItem?.mediaId ?: return@launch
            val bookId = audioFileDao.findBookIdForFile(mediaId) ?: return@launch
            val playlist = playlistResolver.resolve(bookId)
            currentBookId = bookId
            currentCoverModel = playlist?.coverModel
            currentOffline = playlist?.offline ?: false
            currentDurations = audioFileDao.findForBook(bookId).associate { it.relativePath to (it.durationMs ?: 0L) }
            currentBookTotal = currentDurations.values.sum()
            pushState()
            downloadReloadWatcher.watch(
                bookId = bookId,
                isOffline = { currentOffline },
                onSourceFlip = { reloadCurrentBook() },
            )
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
            } ?: run {
                Log.w(TAG, "media controller unavailable (timed out)")
                return@launch
            }
            // Reopening the already-loaded book: just refresh cross-device positions/bookmarks
            // (so they reflect other devices) and leave its queue + play state untouched.
            if (currentBookId == bookId && c.mediaItemCount > 0) {
                withTimeoutOrNull(RESUME_SYNC_TIMEOUT_MS) { positionSyncer.pull() }
                return@launch
            }

            // Persist the outgoing book's position before we leave it (synchronous snapshot,
            // taken while currentBookId still points at it). Skip if a switch is already in
            // flight — that one already persisted the truly-outgoing book, and currentBookId
            // no longer matches the controller's loaded book.
            if (!loading) positionSyncer.persist()

            val playlist = playlistResolver.resolve(bookId) ?: return@launch

            // Switch to the new book: halt the outgoing one and flip the UI immediately —
            // before the (possibly slow) sync + queue load — so the player never lingers on
            // the previous book. `loading` shields the optimistic state from the listener.
            loading = true
            playbackError = false    // a fresh book starts without the previous one's error
            c.stop()                 // halt outgoing audio; leaves the player IDLE so the new
            c.playWhenReady = false  // queue won't auto-buffer or auto-play
            currentBookId = bookId
            currentCoverModel = playlist.coverModel
            currentOffline = playlist.offline
            currentDurations = audioFileDao.findForBook(bookId).associate { it.relativePath to (it.durationMs ?: 0L) }
            currentBookTotal = currentDurations.values.sum()
            val saved = playbackStateDao.findByBookId(bookId)
            val startIndex = saved
                ?.let { s -> playlist.items.indexOfFirst { it.mediaId == s.currentMediaId } }
                ?.takeIf { it >= 0 } ?: 0
            val savedPos = saved?.positionMs ?: 0L
            val before = (0 until startIndex).sumOf { currentDurations[playlist.items[it].mediaId] ?: 0L }
            _state.value = PlaybackUiState(
                isConnected = true,
                bookId = bookId,
                bookTitle = playlist.bookTitle,
                chapterTitle = playlist.items.getOrNull(startIndex)?.mediaMetadata?.title?.toString().orEmpty(),
                chapterIndex = startIndex,
                chapterCount = playlist.items.size,
                positionMs = savedPos,
                durationMs = currentDurations[playlist.items.getOrNull(startIndex)?.mediaId] ?: 0L,
                bookElapsedMs = before + savedPos,
                bookTotalMs = currentBookTotal,
                playbackSpeed = _state.value.playbackSpeed,
                sleepRemainingMs = sleepTimer.remainingMs(),
                sleepEndOfChapter = sleepTimer.endOfChapter,
                coverModel = playlist.coverModel,
            )
            // Per-file duration total (headless probe; reads headers, no main playback).
            durationEnricher.enrich(bookId)

            // Freshest cross-device position (time-boxed), then commit the queue unless a
            // newer open superseded us while we waited.
            withTimeoutOrNull(RESUME_SYNC_TIMEOUT_MS) { positionSyncer.pull() }
            if (currentBookId != bookId) return@launch
            val resumed = playbackStateDao.findByBookId(bookId)
            c.setMediaItems(playlist.items)
            if (resumed != null) {
                val index = playlist.items.indexOfFirst { it.mediaId == resumed.currentMediaId }
                c.seekTo(if (index >= 0) index else 0, resumed.positionMs)
            }
            // Load the queue only — do NOT prepare()/play(), so opening a book streams no
            // audio. Buffering + playback begin when the user hits play (see [playPause]).
            loading = false
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
        // Auto-rewind: on resume, step back a little so the listener re-hears some context.
        // Skipped on a brand-new start (position ~0, clamped by seekBy anyway).
        if (autoRewindMs > 0L && c.currentPosition > 0L) seekBy(-autoRewindMs)
        // First play after loading: prepare (which begins buffering/streaming) then play.
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    /** Re-prepares the current media after a playback error (e.g. a connection was restored). */
    fun retry() {
        val c = controller ?: return
        playbackError = false
        c.prepare()
        c.play()
        pushState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /** Relative seek within the current chapter by [deltaMs] (negative = back), clamped in range. */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = c.duration
        c.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
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

    /** Sets the volume override (reduced/normal/increased) on the service's player and persists it. */
    fun setVolumeMode(mode: String) {
        controller?.sendCustomCommand(
            PlaybackCommands.SET_VOLUME_MODE,
            Bundle().apply { putString(PlaybackCommands.KEY_VOLUME_MODE, mode) },
        )
        scope.launch { playbackSettings.setVolumeMode(mode) }
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

    /** Jumps to the start of a chapter (media item) by index — multi-file chapter navigation. */
    fun jumpToChapterItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.seekTo(index, 0)
    }

    /** Seeks the current playlist to a bookmark's chapter + position. */
    fun jumpToBookmark(mediaId: String, positionMs: Long) {
        val c = controller ?: return
        val index = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == mediaId }
            ?: return
        c.seekTo(index, positionMs)
    }

    /**
     * Connects (or reuses) the [MediaController]. Time-boxed: if [PlaybackService] was killed
     * during a long idle and the async connect never completes, this returns null instead of
     * suspending the caller forever (which would leave transport controls dead). The pending
     * connect future is cancelled on timeout via [suspendCancellableCoroutine]'s cancellation.
     */
    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        return withTimeoutOrNull(CONTROLLER_CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
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
    }

    private fun startPositionUpdates() {
        scope.launch {
            var tick = 0
            while (controller != null) {
                if (!loading && controller?.isPlaying == true) {
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
        val position = c.currentPosition.coerceAtLeast(0L)
        // Whole-book progress from measured chapter durations: sum of chapters before the current
        // one + the current position; total is the sum over all chapters (0 while unmeasured).
        var before = 0L
        for (i in 0 until c.currentMediaItemIndex) {
            before += currentDurations[c.getMediaItemAt(i).mediaId] ?: 0L
        }
        val bookTotal = currentBookTotal
        _state.value = PlaybackUiState(
            isConnected = true,
            isPlaying = c.isPlaying,
            bookId = currentBookId,
            bookTitle = metadata.albumTitle?.toString().orEmpty(),
            chapterTitle = metadata.title?.toString().orEmpty(),
            chapterIndex = c.currentMediaItemIndex,
            chapterCount = c.mediaItemCount,
            positionMs = position,
            // Before playback prepares the player, c.duration is unknown — fall back to the
            // measured chapter duration so the scrubber shows real progress on a resumed book.
            durationMs = c.duration.takeIf { it > 0 } ?: (currentDurations[c.currentMediaItem?.mediaId] ?: 0L),
            bookElapsedMs = before + position,
            bookTotalMs = bookTotal,
            playbackSpeed = c.playbackParameters.speed,
            sleepRemainingMs = sleepTimer.remainingMs(),
            sleepEndOfChapter = sleepTimer.endOfChapter,
            coverModel = currentCoverModel,
            artworkData = if (currentCoverModel == null) metadata.artworkData else null,
            hasError = playbackError,
        )
    }

    private companion object {
        const val TAG = "HomerPlay"
        const val RESUME_SYNC_TIMEOUT_MS = 5_000L
        const val CONTROLLER_CONNECT_TIMEOUT_MS = 10_000L
        const val POSITION_POLL_MS = 500L
        const val DEFAULT_EXTEND_MS = 15 * 60_000L
    }
}
