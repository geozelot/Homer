package com.geozelot.homer.playback

import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where playback currently is, for persistence — supplied by the host on demand. */
data class PositionSnapshot(val bookId: String, val mediaId: String, val positionMs: Long)

/**
 * Persists the resume position to Room and reconciles it with the central `.homer` manifest.
 * Position writes are cheap and frequent; the manifest [flush] is debounced so a burst of
 * events collapses into one WebDAV round-trip, and it also fires when the app is backgrounded
 * so another device can resume even if the user never explicitly paused.
 *
 * The manifest side is expressed as two suspending actions ([exportMirror], [syncManifest])
 * rather than repository types so the debounce/force behavior stays unit-testable on the JVM.
 */
class PositionSyncer(
    private val scope: CoroutineScope,
    private val playbackStateDao: PlaybackStateDao,
    private val snapshot: () -> PositionSnapshot?,
    /** Writes the local `.homer` mirror — [LocalMirror.export][com.geozelot.homer.data.storage.LocalMirror.export]. */
    private val exportMirror: suspend () -> Unit,
    /** Reconciles with the server manifest — [HomerSyncRepository.sync][com.geozelot.homer.data.sync.HomerSyncRepository.sync]. */
    private val syncManifest: suspend (force: Boolean) -> Unit,
    /**
     * Registers `onBackground` to run when the app goes to the background.
     *
     * A required constructor argument and not a method to call afterwards. Backgrounding is the
     * most important push this class makes — it is what lets another device pick the book up — and
     * a host that forgot to call an `observeAppLifecycle()` would lose it silently, with nothing to
     * fail and nothing to log. Passed in rather than reached for so the rule stays testable on the
     * JVM without an Android process lifecycle.
     */
    observeBackgrounding: (onBackground: () -> Unit) -> Unit,
) {
    /** The debounce wait. Cancellable by design; the push it starts is not. */
    private var debounceJob: Job? = null

    /** Serialises pushes, so an overlapping flush queues behind one instead of aborting it. */
    private val pushLock = Mutex()

    init {
        // Backgrounding (and app close) is the most important push of all — it's what lets another
        // device pick up where this one left off. Always forced.
        observeBackgrounding { if (snapshot() != null) flush(force = true) }
    }

    /** Reconciles with the manifest (pull + merge + push). Suspends until done. */
    suspend fun pull() = syncManifest(false)

    /** Persists the current position to Room, awaiting the write (for use before a swap). */
    suspend fun persist() = saveNow()

    /** Persists the current position to Room (fire-and-forget). */
    fun save() {
        scope.launch { saveNow() }
    }

    /**
     * Persists the position immediately, then reconciles with the manifest (debounced).
     * [force] bypasses the sync throttle *and* the debounce — set it for checkpoints that must
     * not be dropped (pause, backgrounding, app close, explicit user actions). Skipping the
     * debounce matters most on backgrounding: the OS may freeze the process within the very
     * second a debounced job would still be sleeping, silently dropping the push.
     */
    fun flush(force: Boolean = false) {
        // Kept as a handle rather than fired and forgotten, because the push below READS what this
        // writes. The two used to be independent launches, so the push could export the position
        // from before the save — and the push that loses that race most often is the forced one on
        // backgrounding, which is the one another device is waiting for.
        val saving = scope.launch { saveNow() }
        // Only the WAIT is cancellable. Collapsing a burst is what the debounce is for, and
        // cancelling a job still sleeping costs nothing — but a forced flush starts its push at
        // once, so with one cancel covering both a pause followed by a backgrounding aborted a
        // WebDAV round-trip mid-request. The push is launched detached from the debounce, and the
        // mutex collapses overlapping pushes instead of killing them.
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (!force) delay(SYNC_DEBOUNCE_MS)
            scope.launch {
                saving.join()
                pushLock.withLock {
                    // Local mirror first (cheap, offline-safe, all tiers), then the server manifest.
                    exportMirror()
                    syncManifest(force)
                }
            }
        }
    }

    private suspend fun saveNow() {
        val s = snapshot() ?: return
        playbackStateDao.upsert(
            PlaybackStateEntity(
                bookId = s.bookId,
                currentMediaId = s.mediaId,
                positionMs = s.positionMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        const val SYNC_DEBOUNCE_MS = 1_000L
    }
}
