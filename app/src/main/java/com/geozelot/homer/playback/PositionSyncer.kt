package com.geozelot.homer.playback

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import com.geozelot.homer.data.storage.LocalMirror
import com.geozelot.homer.data.sync.HomerSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where playback currently is, for persistence — supplied by the host on demand. */
data class PositionSnapshot(val bookId: String, val mediaId: String, val positionMs: Long)

/**
 * Persists the resume position to Room and reconciles it with the central `.homer` manifest.
 * Position writes are cheap and frequent; the manifest [flush] is debounced so a burst of
 * events collapses into one WebDAV round-trip, and it also fires when the app is backgrounded
 * so another device can resume even if the user never explicitly paused.
 */
class PositionSyncer(
    private val scope: CoroutineScope,
    private val playbackStateDao: PlaybackStateDao,
    private val homerSync: HomerSyncRepository,
    private val localMirror: LocalMirror,
    private val snapshot: () -> PositionSnapshot?,
) {
    private var syncJob: Job? = null

    init {
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    // Backgrounding (and app close) is the most important push of all — it's what
                    // lets another device pick up where this one left off. Always forced.
                    if (snapshot() != null) flush(force = true)
                }
            })
        }
    }

    /** Reconciles with the manifest (pull + merge + push). Suspends until done. */
    suspend fun pull() = homerSync.sync()

    /** Persists the current position to Room, awaiting the write (for use before a swap). */
    suspend fun persist() = saveNow()

    /** Persists the current position to Room (fire-and-forget). */
    fun save() {
        scope.launch { saveNow() }
    }

    /**
     * Persists the position immediately, then reconciles with the manifest (debounced).
     * [force] bypasses the sync throttle — set it for checkpoints that must not be dropped
     * (pause, backgrounding, app close, explicit user actions).
     */
    fun flush(force: Boolean = false) {
        save()
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            // Local mirror first (cheap, offline-safe, all tiers), then the server manifest.
            localMirror.export()
            homerSync.sync(force)
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

    private companion object {
        const val SYNC_DEBOUNCE_MS = 1_000L
    }
}
