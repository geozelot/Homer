package com.geozelot.homer.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sleep-timer state machine: a countdown that pauses playback on expiry (with
 * shake-to-extend armed while it runs), or an "end of chapter" mode that pauses when the
 * current chapter finishes.
 *
 * It never touches the media controller — playback is paused via [onPause] and UI refreshes
 * are requested via [onChanged] — so it stays a small, self-contained unit.
 */
class SleepTimer(
    context: Context,
    private val scope: CoroutineScope,
    private val onPause: () -> Unit,
    private val onChanged: () -> Unit,
) {
    private var job: Job? = null
    private var targetRealtimeMs = 0L

    /** True while set to pause at the end of the current chapter. */
    var endOfChapter: Boolean = false
        private set

    private val shakeDetector = ShakeDetector(context) { extend(SHAKE_EXTEND_MS) }

    /** Milliseconds until a running countdown fires, or null when no countdown is active. */
    fun remainingMs(): Long? =
        if (job?.isActive == true) {
            (targetRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            null
        }

    /** Pauses playback after [durationMs]; shake-to-extend is armed while it counts down. */
    fun startCountdown(durationMs: Long) {
        clear()
        targetRealtimeMs = SystemClock.elapsedRealtime() + durationMs
        shakeDetector.start()
        job = scope.launch {
            while (true) {
                if (SystemClock.elapsedRealtime() >= targetRealtimeMs) {
                    onPause()
                    clear()
                    onChanged()
                    break
                }
                onChanged()
                delay(TICK_MS)
            }
        }
        onChanged()
    }

    /** Pauses when the current chapter finishes (no shake-to-extend in this mode). */
    fun startEndOfChapter() {
        clear()
        endOfChapter = true
        onChanged()
    }

    /**
     * Reacts to a chapter change. In end-of-chapter mode, an [auto] advance pauses playback;
     * a manual skip means the user took control, so the timer just disarms.
     */
    fun onChapterTransition(auto: Boolean) {
        if (!endOfChapter) return
        if (auto) onPause()
        clear()
        onChanged()
    }

    /** Adds [extraMs] to a running countdown (shake-to-extend), capped; no-op otherwise. */
    fun extend(extraMs: Long) {
        if (job?.isActive != true) return
        // Cap the total remaining so repeated shakes can't push the timer arbitrarily far.
        val cap = SystemClock.elapsedRealtime() + MAX_REMAINING_MS
        targetRealtimeMs = (targetRealtimeMs + extraMs).coerceAtMost(cap)
        val remaining = (targetRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        Log.i(TAG, "shake: sleep timer extended by ${extraMs / 1000}s -> ${remaining / 1000}s left")
        onChanged()
    }

    /** Cancels any armed timer and refreshes state. */
    fun cancel() {
        clear()
        onChanged()
    }

    private fun clear() {
        job?.cancel()
        job = null
        endOfChapter = false
        shakeDetector.stop()
    }

    private companion object {
        const val TAG = "HomerPlay"
        const val TICK_MS = 500L
        const val SHAKE_EXTEND_MS = 5 * 60 * 1000L
        const val MAX_REMAINING_MS = 2 * 60 * 60 * 1000L
    }
}
