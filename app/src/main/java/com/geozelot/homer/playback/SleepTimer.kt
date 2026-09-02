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
 * shake-to-extend armed while it runs, if the reader wants it), or an "end of chapter" mode that
 * pauses when the current chapter finishes.
 *
 * ## The reported bug, and where it actually was
 *
 * "It either extends itself without any interaction, or the counter just keeps counting" — one
 * defect, seen from two angles. Nothing here was wrong: `ShakeDetector` fired on a single 2.7g peak,
 * which is what an impact produces rather than a shake, so putting the phone down added fifteen
 * minutes. The target kept moving, so the countdown never reached zero and the number went back up.
 * Bounded only by [MAX_REMAINING_MS], which made "never stops" the practical result.
 *
 * Shake-to-extend also could not be turned OFF — the setting offered 5, 15, 30, previous and
 * chapter, and nothing else — so the sensor was registered for every countdown whether the reader
 * wanted the feature or not.
 *
 * It never touches the media controller — playback is paused via [onPause] and UI refreshes
 * are requested via [onChanged] — so it stays a small, self-contained unit.
 */
class SleepTimer(
    context: Context,
    private val scope: CoroutineScope,
    private val onPause: () -> Unit,
    private val onChanged: () -> Unit,
    private val onShake: () -> Unit,
) {
    private var job: Job? = null
    private var targetRealtimeMs = 0L

    /** True while set to pause at the end of the current chapter. */
    var endOfChapter: Boolean = false
        private set

    /** True while a countdown is running (shake-to-extend only applies then). */
    val isCountingDown: Boolean get() = job?.isActive == true

    // The extend amount/mode is a host preference, so a shake just notifies the host.
    private val shakeDetector = ShakeDetector(context) { onShake() }

    /** Milliseconds until a running countdown fires, or null when no countdown is active. */
    fun remainingMs(): Long? =
        if (job?.isActive == true) {
            (targetRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            null
        }

    /**
     * Pauses playback after [durationMs].
     *
     * [armShake] arms shake-to-extend for the life of this countdown. It is a parameter rather than
     * something read in here because the accelerometer should not be REGISTERED at all when the
     * feature is off — the previous version armed it unconditionally, and there was no way to turn
     * the feature off in the first place.
     */
    fun startCountdown(durationMs: Long, armShake: Boolean) {
        clear()
        targetRealtimeMs = SystemClock.elapsedRealtime() + durationMs
        if (armShake) shakeDetector.start()
        job = scope.launch {
            // The target can move under this loop — that is what `extendBy` does — so it is the
            // condition rather than a fixed count of ticks.
            while (SystemClock.elapsedRealtime() < targetRealtimeMs) {
                onChanged()
                delay(TICK_MS)
            }
            // Expired. Everything `clear()` does EXCEPT cancelling the job, because the job is this
            // coroutine and it is about to finish on its own. Calling `clear()` here made the body
            // cancel itself and survived only by not suspending afterwards.
            shakeDetector.stop()
            endOfChapter = false
            job = null
            // After `job = null`, so the state this pushes reports no timer rather than a stale one.
            onPause()
            onChanged()
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
    fun extendBy(extraMs: Long) {
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
        const val TICK_MS = 1_000L
        const val MAX_REMAINING_MS = 2 * 60 * 60 * 1000L
    }
}
