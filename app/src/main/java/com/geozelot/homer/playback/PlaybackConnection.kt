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
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkKind
import com.geozelot.homer.data.db.entity.BookmarkMetaEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import com.geozelot.homer.data.download.DownloadManager
import com.geozelot.homer.data.metadata.DurationEnricher
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.data.settings.SLEEP_EXTEND_OFF
import com.geozelot.homer.data.storage.LocalMirror
import com.geozelot.homer.data.sync.HomerSyncRepository
import com.geozelot.homer.data.sync.facet.LibraryIndexRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val artworkData: ArtworkBytes? = null,
    /** A playback error is stalling the stream (usually a lost connection); offer a retry. */
    val hasError: Boolean = false,
)

/**
 * Embedded artwork bytes with **content-based** equality. A bare `ByteArray` in [PlaybackUiState]
 * (a data class backing a `StateFlow`) would compare by reference, defeating the flow's conflation;
 * this makes equal artwork compare equal so identical states don't re-emit.
 */
class ArtworkBytes(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is ArtworkBytes && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
}

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
    private val downloadDao: DownloadDao,
    private val bookOverrideDao: BookOverrideDao,
    private val downloadManager: DownloadManager,
    private val homerSync: HomerSyncRepository,
    private val libraryIndex: LibraryIndexRepository,
    private val localMirror: LocalMirror,
) {
    // A handler so an unhandled error in a fire-and-forget launch (e.g. a DAO write hitting a
    // constraint after a concurrent scan pruned the row) is logged, not propagated to the
    // platform's default handler as a process crash.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "unhandled playback coroutine error", e) },
    )

    /** Serializes book-switching ([connect]/[playBook]) so their shared-state mutations can't
     *  interleave across suspension points, and so the [MediaController] is only ever built once
     *  (a concurrent cold-start connect + first tap must not each build one and leak a controller). */
    private val switchMutex = Mutex()
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

    /** Global "download on play" default (cached from settings); a per-book override can flip it. */
    @Volatile
    private var downloadOnPlayGlobal = true

    /** Set when the player reports an error; surfaced in [state] so the UI can offer a retry. */
    @Volatile
    private var playbackError = false

    /** A play tap that arrived while a book switch was still loading; honoured once the queue is ready. */
    @Volatile
    private var pendingPlay = false

    /** In-flight sleep-timer volume ramp, so a manual play/pause can cancel it. */
    private var fadeJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    init {
        scope.launch {
            playbackSettings.autoRewindSeconds.collect { autoRewindMs = it * 1000L }
        }
        scope.launch {
            playbackSettings.downloadOnPlay.collect { downloadOnPlayGlobal = it }
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

    /** " buffered=12s" — how much audio is in hand, the number that matters during a stall. */
    private fun bufferSuffix(): String {
        val c = controller ?: return ""
        val ahead = (c.bufferedPosition - c.currentPosition).coerceAtLeast(0L)
        return " buffered=${ahead / 1000}s"
    }

    private val listener = object : Player.Listener {
        /**
         * Why playback started or stopped, and how much audio was in hand when it did.
         *
         * Log-only, and here because a background stall is otherwise invisible: the platform's
         * `AudioTrack ... underrun` line says the pipeline ran dry but not why, and a stall that
         * never raises a PlaybackException leaves [onPlayerError] silent. The reason code
         * separates the candidates outright — AUDIO_FOCUS_LOSS means something else took the
         * audio, USER_REQUEST means the pause came from us or the notification, while a drop to
         * BUFFERING with the buffer at zero means the stream simply starved.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.i(TAG, "playWhenReady=$playWhenReady reason=${playWhenReadyReasonName(reason)}${bufferSuffix()}")
        }

        override fun onPlaybackStateChanged(state: Int) {
            Log.i(TAG, "state=${playbackStateName(state)}${bufferSuffix()}")
        }

        override fun onEvents(player: Player, events: Player.Events) {
            // During a book switch the controller still holds the outgoing book — ignore its
            // events so they don't clobber the optimistic new-book state.
            if (loading) return
            // Playback made progress again ⇒ clear any stale error banner.
            if (player.isPlaying) playbackError = false
            // Only react to events that change what the UI shows. onEvents also fires for loading
            // and timeline churn several times a second, and each pushState() re-derives whole-book
            // progress and re-emits to flows that fan out across the library — this was the app's
            // heaviest CPU path. The unconditional positionSyncer.save() that used to sit here is
            // gone too: the poll loop persists on a timer, and pausing is handled just below.
            val visible = events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
            if (visible) pushState()
            // Pausing is the natural checkpoint: persist and push it out. Forced, so the sync
            // throttle can never swallow the one update another device is waiting for.
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
                positionSyncer.flush(force = true)
            }
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
            // Persist the boundary LOCALLY only. This used to push to the server on every chapter
            // change — with 5-minute chapters that was a full manifest round trip every few
            // minutes, and the radio ramp-up cost more battery than the bytes did. Pausing,
            // backgrounding and app close still push.
            if (auto) positionSyncer.save()
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
            switchMutex.withLock {
                if (currentBookId != null) return@withLock
                val c = try {
                    awaitController()
                } catch (e: Exception) {
                    Log.w(TAG, "eager connect failed", e)
                    return@withLock
                } ?: return@withLock
                if (currentBookId != null || c.mediaItemCount == 0) return@withLock
                val mediaId = c.currentMediaItem?.mediaId ?: return@withLock
                val bookId = audioFileDao.findBookIdForFile(mediaId) ?: return@withLock
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
    }

    /** Loads a book and resumes from its saved position (or the start if none). */
    fun playBook(bookId: String) {
        scope.launch {
            switchMutex.withLock {
                val c = try {
                    awaitController()
                } catch (e: Exception) {
                    Log.w(TAG, "cannot connect media controller", e)
                    return@withLock
                } ?: run {
                    Log.w(TAG, "media controller unavailable (timed out)")
                    return@withLock
                }
                // Reopening the already-loaded book: just refresh cross-device positions/bookmarks
                // (so they reflect other devices) and leave its queue + play state untouched.
                if (currentBookId == bookId && c.mediaItemCount > 0) {
                    withTimeoutOrNull(RESUME_SYNC_TIMEOUT_MS) { positionSyncer.pull() }
                    return@withLock
                }

                // Persist the outgoing book's position before we leave it. Switches are serialized
                // by switchMutex, so currentBookId still points at the truly-outgoing book here.
                positionSyncer.persist()

                val playlist = playlistResolver.resolve(bookId) ?: return@withLock

                // Switch to the new book: halt the outgoing one and flip the UI immediately —
                // before the (possibly slow) sync + queue load — so the player never lingers on
                // the previous book. `loading` shields the optimistic state from the listener;
                // try/finally guarantees it clears even if a DAO read in between throws.
                loading = true
                try {
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
                    // Load the new queue NOW (at the locally-saved position), before the possibly-slow
                    // cross-device pull — otherwise the previous book's queue lingers in the controller
                    // and a play tap would resume IT for a few seconds. Not prepared/played.
                    c.setMediaItems(playlist.items)
                    c.seekTo(startIndex, savedPos)
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
                } finally {
                    loading = false
                }
                pushState()
                // A play tap that arrived mid-switch was deferred; honour it now the queue is B's.
                if (pendingPlay) { pendingPlay = false; startPlayback() }
                // Per-file duration total (headless probe; reads headers, no main playback).
                durationEnricher.enrich(bookId)
                // Watch for download-status flips only after the playlist is loaded, so the reload
                // can't race the initial setMediaItems.
                downloadReloadWatcher.watch(
                    bookId = bookId,
                    isOffline = { currentOffline },
                    onSourceFlip = { reloadCurrentBook() },
                )

                // In the background, pull a possibly-newer cross-device position and apply it — but
                // only if the user hasn't started playing yet, so it never yanks an active listen.
                scope.launch {
                    withTimeoutOrNull(RESUME_SYNC_TIMEOUT_MS) { positionSyncer.pull() }
                    if (currentBookId != bookId) return@launch
                    val resumed = playbackStateDao.findByBookId(bookId) ?: return@launch
                    val cc = controller ?: return@launch
                    if (cc.playbackState == Player.STATE_IDLE && !cc.isPlaying) {
                        val index = playlist.items.indexOfFirst { it.mediaId == resumed.currentMediaId }
                        cc.seekTo(if (index >= 0) index else 0, resumed.positionMs)
                        pushState()
                    }
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

    /**
     * Stops playback and forgets the queue.
     *
     * For signing out, where the library the queue points at is about to be deleted. Clearing
     * [currentBookId] is what actually ends it: the mini-player hides on a null book, and
     * [positionSnapshot] returns null, so [PositionSyncer] stops writing positions for a book the
     * database is about to forget.
     *
     * On this class's own scope, not the caller's — sign-out tears down the screen that asked.
     */
    fun stop() {
        scope.launch {
            switchMutex.withLock {
                cancelFade()
                sleepTimer.cancel()
                controller?.let { c ->
                    c.pause()
                    c.clearMediaItems()
                    c.stop()
                }
                currentBookId = null
                currentCoverModel = null
                currentOffline = false
                currentDurations = emptyMap()
                currentBookTotal = 0L
                loading = false
                pendingPlay = false
                pushState()
            }
        }
    }

    fun playPause() {
        val c = controller ?: return
        // Any manual transport input ends a sleep fade: otherwise the ramp keeps running and
        // pauses the user again seconds after they pressed play.
        cancelFade()
        if (c.isPlaying) {
            c.pause()
            return
        }
        // A book switch is still loading the new queue — defer so we don't start the previous
        // book's leftover queue. [playBook] runs the deferred play once the queue is B's.
        if (loading) {
            pendingPlay = true
            return
        }
        startPlayback()
    }

    private fun startPlayback() {
        val c = controller ?: return
        // Auto-rewind: on resume, step back a little so the listener re-hears some context.
        // Skipped on a brand-new start (position ~0, clamped by seekBy anyway).
        if (autoRewindMs > 0L && c.currentPosition > 0L) seekBy(-autoRewindMs)
        // Download-on-play: keep the book offline as it plays (it streams meanwhile; the download
        // watcher swaps to local files once complete). Per-book override wins over the global.
        currentBookId?.let(::maybeAutoDownload)
        // First play after loading: prepare (which begins buffering/streaming) then play.
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    /** Kicks a background download for [bookId] if the effective play-mode wants it and it isn't
     *  already downloaded or in flight. */
    private fun maybeAutoDownload(bookId: String) {
        scope.launch {
            val enabled = bookOverrideDao.findById(bookId)?.downloadOnPlay ?: downloadOnPlayGlobal
            if (!enabled) return@launch
            val status = downloadDao.findByBookId(bookId)?.status
            // Only start if never downloaded or a prior attempt failed; leave done/in-flight/paused.
            if (status == null || status == DownloadStatus.FAILED) downloadManager.download(bookId)
        }
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
        scope.launch {
            playbackSettings.setSleepLastDurationMs(durationMs)
            // Read BEFORE arming, so the accelerometer is never registered for a reader who has the
            // feature off. It used to be registered for every countdown, and there was no off.
            sleepTimer.startCountdown(
                durationMs = durationMs,
                armShake = playbackSettings.sleepExtend.first() != SLEEP_EXTEND_OFF,
            )
        }
    }

    fun startSleepTimerEndOfChapter() = sleepTimer.startEndOfChapter()

    fun cancelSleepTimer() = sleepTimer.cancel()

    /**
     * Ramps the volume down over the configured seconds, then pauses (0 = pause abruptly).
     *
     * Tracked so it can be cancelled: if the user hits play during the fade, an unowned ramp would
     * keep going and force-pause them a moment later — with the volume left part-way down. The
     * `finally` restores full volume however the ramp ends.
     */
    private fun fadeOutAndPause() {
        val c = controller ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val fadeMs = playbackSettings.sleepFadeOutSeconds.first() * 1000L
            if (fadeMs <= 0L) {
                c.pause()
                return@launch
            }
            val steps = 20
            val stepMs = (fadeMs / steps).coerceAtLeast(10L)
            try {
                for (i in steps - 1 downTo 0) {
                    c.volume = i / steps.toFloat()
                    delay(stepMs)
                }
                c.pause()
            } finally {
                c.volume = 1f // restore so the next play starts at full volume
            }
        }
    }

    /** Cancels an in-flight sleep fade and restores the volume — any manual transport input. */
    private fun cancelFade() {
        fadeJob?.cancel()
        fadeJob = null
        controller?.volume = 1f
    }

    /** Applies the user's shake-to-extend preference to the running countdown. */
    private fun extendSleepByPreference() {
        if (!sleepTimer.isCountingDown) return
        scope.launch {
            when (val mode = playbackSettings.sleepExtend.first()) {
                // Checked even though the sensor is not armed when off: `else` below falls back to
                // fifteen minutes for anything it does not recognise, so "off" reaching it would
                // have extended by a quarter of an hour — the exact opposite of what it says.
                SLEEP_EXTEND_OFF -> Unit
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
    /**
     * Marks the current position.
     *
     * @param kind [BookmarkKind.NOTE] for a private mark, [BookmarkKind.CUT] for a chapter boundary.
     *   A cut is a claim about the book rather than about the listener, so it also goes out with the
     *   metadata corrections and becomes everybody's chapter list — which is why it publishes and a
     *   note does not.
     */
    fun addBookmark(kind: String = BookmarkKind.NOTE) {
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
                    kind = kind,
                ),
            )
            bookmarkMetaDao.upsert(BookmarkMetaEntity(bookId, now))
            homerSync.sync(force = true)
            // A cut changes the book's chapter list, so it belongs in the shared index. Coalesced
            // the same way a title edit is — cutting a book is a burst of cuts, not one.
            if (kind == BookmarkKind.CUT) libraryIndex.publishEdits()
        }
    }

    /** Deletes a bookmark and syncs the change out. */
    fun deleteBookmark(id: Long, bookId: String) {
        scope.launch {
            bookmarkDao.deleteById(id)
            bookmarkMetaDao.upsert(BookmarkMetaEntity(bookId, System.currentTimeMillis()))
            homerSync.sync(force = true)
        }
    }

    /**
     * "Mark as completed": resets a book's saved progress to the very start so it drops off the
     * Currently-listening shelf and reopens fresh. Writes position 0 at the first chapter with a fresh
     * timestamp (so the reset wins cross-device via last-write-wins) and syncs. If it's the
     * currently-loaded book, the controller is paused and rewound first so the position poll can't
     * immediately re-save the old spot.
     */
    fun resetProgress(bookId: String) {
        scope.launch {
            val firstMediaId = audioFileDao.findForBook(bookId).firstOrNull()?.relativePath ?: return@launch
            if (currentBookId == bookId) controller?.let { it.pause(); it.seekTo(0, 0L) }
            playbackStateDao.upsert(PlaybackStateEntity(bookId, firstMediaId, 0L, System.currentTimeMillis()))
            if (currentBookId == bookId) pushState()
            localMirror.export()
            homerSync.sync(force = true)
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
    // SessionToken(Context, ComponentName) is @UnstableApi in Media3 — accepted deliberately, as
    // it is the documented way to reach a MediaSessionService that is not yet running.
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        return withTimeoutOrNull(CONTROLLER_CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token)
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        // Session/service went away — drop the stale controller so the position
                        // loop stops and the next playBook reconnects fresh. Release it as well:
                        // dropping the reference alone leaks its binder connection and listener.
                        if (this@PlaybackConnection.controller === controller) {
                            this@PlaybackConnection.controller = null
                        }
                        controller.removeListener(listener)
                        runCatching { controller.release() }
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
                    startPositionUpdates(c)
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

    /**
     * Drives the scrubber and persists the position while playing.
     *
     * Deliberately frugal: it ticks once a second (the UI shows whole seconds), falls back to a
     * slow heartbeat when nothing is playing — it used to wake twice a second for the entire
     * process lifetime, including hours of paused or idle time — and only re-derives UI state when
     * something is actually collecting it, so backgrounded listening with the screen off does no
     * per-tick work at all. The old ~5-minute manifest push from this loop is gone; the network is
     * touched on real events (pause, background, app close, user actions) instead of on a timer.
     */
    private fun startPositionUpdates(c: MediaController) {
        scope.launch {
            var tick = 0
            // Loop only while THIS controller is the active one: if it's replaced by a reconnect,
            // this loop ends instead of running in parallel with the new controller's loop.
            while (controller === c) {
                val playing = !loading && c.isPlaying
                if (playing) {
                    if (_state.subscriptionCount.value > 0) pushState()
                    if (++tick % SAVE_EVERY_TICKS == 0) positionSyncer.save() // local only
                }
                delay(if (playing) POSITION_POLL_MS else IDLE_POLL_MS)
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
            artworkData = if (currentCoverModel == null) metadata.artworkData?.let(::ArtworkBytes) else null,
            hasError = playbackError,
        )
    }

    private companion object {
        const val TAG = "HomerPlay"

        private fun playbackStateName(state: Int) = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "state$state"
        }

        private fun playWhenReadyReasonName(reason: Int) = when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
            else -> "reason$reason"
        }
        const val RESUME_SYNC_TIMEOUT_MS = 5_000L
        const val CONTROLLER_CONNECT_TIMEOUT_MS = 10_000L

        /** Scrubber cadence while playing — the UI only shows whole seconds. */
        const val POSITION_POLL_MS = 1_000L

        /** Heartbeat while paused/idle: just enough to notice the controller went away. */
        const val IDLE_POLL_MS = 5_000L

        /** Persist the position locally about every 15s of playback. */
        const val SAVE_EVERY_TICKS = 15
        const val DEFAULT_EXTEND_MS = 15 * 60_000L
    }
}
