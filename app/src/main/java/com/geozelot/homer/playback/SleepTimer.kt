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
    /** Picks playback back up when a shake lands after the timer fired. */
    private val onResume: () -> Unit,
) {
    private var job: Job? = null
    private var targetRealtimeMs = 0L

    /** Disarms the shake window some minutes after the timer fired — see [startCountdown]. */
    private var disarmJob: Job? = null

    /**
     * True between the timer firing and the shake window closing: playback has been paused, and a
     * shake will pick it up again.
     *
     * The state that did not exist before, and its absence was the whole problem. Shake-to-extend
     * was armed *during* the countdown, which is the one stretch of time when a shake means
     * nothing — the phone is in a hand, or being set down, or in a pocket, and playback is running
     * perfectly well. Every one of those extended the timer, and the reader saw a countdown that
     * refused to end.
     *
     * Armed after it fires, a shake means the one thing it can mean: "I am still awake, keep
     * going." That is also the only moment the gesture is worth its battery.
     */
    val awaitingShake: Boolean get() = disarmJob?.isActive == true

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
     * [armShake] arms shake-to-extend — but only once the countdown has FIRED, for [SHAKE_WINDOW_MS]
     * afterwards. See [awaitingShake] for why it is no longer armed while the timer runs.
     *
     * It is a parameter rather than something read in here because the accelerometer should not be
     * REGISTERED at all when the feature is off.
     */
    fun startCountdown(durationMs: Long, armShake: Boolean) {
        clear()
        targetRealtimeMs = SystemClock.elapsedRealtime() + durationMs
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
            endOfChapter = false
            job = null
            // After `job = null`, so the state this pushes reports no timer rather than a stale one.
            onPause()
            if (armShake) armShakeWindow()
            onChanged()
        }
        onChanged()
    }

    /**
     * Listens for a shake for a few minutes after the timer fired, then stops.
     *
     * Bounded because an accelerometer registered forever is a battery drain nobody asked for, and
     * because a shake an hour later is not somebody catching a timer that just ran out — it is a
     * phone being picked up, and picking your phone up should not restart a book.
     */
    private fun armShakeWindow() {
        shakeDetector.start()
        disarmJob = scope.launch {
            delay(SHAKE_WINDOW_MS)
            shakeDetector.stop()
            Log.i(TAG, "shake window closed")
            onChanged()
        }
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

    /**
     * Gives the reader [extraMs] more.
     *
     * Two situations, and the difference is what the caller means by "more":
     *
     *  - **The timer fired** and the shake window is open. Playback is paused, so this starts a
     *    fresh countdown and asks the host to resume — which is what a shake means at that moment.
     *  - **A countdown is running.** The target moves. No shake reaches this any more, but the
     *    method is still the one way to add time and is kept honest for whatever calls it next.
     *
     * Anything else is a no-op: there is no timer to extend and nothing was asked for.
     */
    fun extendBy(extraMs: Long, resume: Boolean = false) {
        when {
            awaitingShake -> {
                Log.i(TAG, "shake after expiry: another ${extraMs / 1000}s")
                disarmJob?.cancel()
                shakeDetector.stop()
                // A fresh countdown, and its own shake window at the end of it — so a reader who is
                // still awake can do this again.
                startCountdown(extraMs, armShake = true)
                if (resume) onResume()
            }
            job?.isActive == true -> {
                // Cap the total remaining so repeated extensions can't push the timer arbitrarily far.
                val cap = SystemClock.elapsedRealtime() + MAX_REMAINING_MS
                targetRealtimeMs = (targetRealtimeMs + extraMs).coerceAtMost(cap)
                val remaining = (targetRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                Log.i(TAG, "sleep timer extended by ${extraMs / 1000}s -> ${remaining / 1000}s left")
                onChanged()
            }
        }
    }

    /** Cancels any armed timer and refreshes state. */
    fun cancel() {
        clear()
        onChanged()
    }

    private fun clear() {
        job?.cancel()
        job = null
        disarmJob?.cancel()
        disarmJob = null
        endOfChapter = false
        shakeDetector.stop()
    }

    private companion object {
        const val TAG = "HomerPlay"
        const val TICK_MS = 1_000L

        /**
         * How long a shake still means "keep going" after the timer fired.
         *
         * Long enough to cover surfacing from a doze and reaching for the phone; short enough that
         * the accelerometer is not registered for the rest of the night.
         */
        const val SHAKE_WINDOW_MS = 5 * 60 * 1000L
        const val MAX_REMAINING_MS = 2 * 60 * 60 * 1000L
    }
}
