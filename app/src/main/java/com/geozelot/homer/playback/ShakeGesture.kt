package com.geozelot.homer.playback

/**
 * What counts as a shake, as a plain object over accelerometer magnitudes.
 *
 * Pulled out of [ShakeDetector] for the same reason `ListeningFold` was pulled out of a nested-scroll
 * callback: the rule was the hard part and it was unverifiable where it lived. A `SensorEvent` cannot
 * be constructed in a unit test, so the one piece of this feature that had a bug in it was the one
 * piece nothing could assert on — and the bug shipped as "the sleep timer extends itself".
 *
 * [ShakeDetector] now does nothing but convert three axes into a magnitude and hand it here.
 *
 * ## The rule
 *
 * The previous version fired on any single reading above 2.7g, debounced to once a second. At rest
 * the magnitude is 1g, so 2.7 sounds selective — but it is a *peak*, and a peak is exactly what an
 * impact makes. Setting the phone on a table, a knock against a bedframe, rolling over with it on
 * the mattress: each spikes past 2.7g once, and each added fifteen minutes to a running timer.
 *
 * A shake is not an impact, it is an **oscillation**. So:
 *
 *  - the bar is higher ([SHAKE_THRESHOLD_G]), and
 *  - it has to be crossed [REQUIRED_CROSSINGS] times inside [SHAKE_WINDOW_MS], and
 *  - the magnitude has to fall back near rest ([SETTLE_THRESHOLD_G]) between crossings.
 *
 * That last clause is what makes "separate crossings" mean anything. Without it, one hard knock
 * spread over several samples above the bar would count as several crossings and pass — which is the
 * failure being fixed, reintroduced by a weaker test.
 *
 * A single impact cannot satisfy this however hard it lands, because it is one excursion. Nor can a
 * slow sustained movement, because it never crosses the bar at all.
 */
internal class ShakeGesture {

    /**
     * When a shake last fired, or null if none has.
     *
     * Nullable rather than 0, because the debounce compares against it: with 0 as "never", every
     * timestamp inside the first [SHAKE_DEBOUNCE_MS] of the monotonic clock reads as "too soon", so
     * the first shake after a reboot would be swallowed. Found by a test using small timestamps,
     * which is the same defect wearing a smaller hat.
     */
    private var lastShakeMs: Long? = null
    private var firstCrossingMs = 0L
    private var crossings = 0
    private var settled = true

    /**
     * Feeds one magnitude reading in g, at [nowMs] on a monotonic clock.
     *
     * Returns true on the reading that completes a shake — once per gesture, and no more often than
     * [SHAKE_DEBOUNCE_MS], so holding a shake does not extend a timer repeatedly.
     */
    fun onMagnitude(gForce: Float, nowMs: Long): Boolean {
        if (gForce < SETTLE_THRESHOLD_G) {
            settled = true
            // Excursions that stopped arriving are not part of a shake. Discarding them here rather
            // than only on the next crossing means a crossing an hour later starts a fresh gesture
            // instead of pairing with a stale one.
            if (crossings > 0 && nowMs - firstCrossingMs > SHAKE_WINDOW_MS) reset()
            return false
        }
        // Above rest but under the bar, or still inside the excursion we already counted.
        if (gForce < SHAKE_THRESHOLD_G || !settled) return false

        settled = false
        if (crossings == 0 || nowMs - firstCrossingMs > SHAKE_WINDOW_MS) {
            // First of a gesture — or the first of a NEW one, because the old gesture timed out.
            firstCrossingMs = nowMs
            crossings = 1
            return false
        }
        crossings++
        if (crossings < REQUIRED_CROSSINGS) return false
        reset()
        val since = lastShakeMs
        if (since != null && nowMs - since <= SHAKE_DEBOUNCE_MS) return false
        lastShakeMs = nowMs
        return true
    }

    /** Forgets any gesture in progress. Called when the sensor is unregistered and re-registered. */
    fun reset() {
        firstCrossingMs = 0L
        crossings = 0
        settled = true
    }

    internal companion object {
        /** Raised from 2.7: a firm deliberate shake, not a bump. */
        const val SHAKE_THRESHOLD_G = 3.2f

        /** Back near rest (1g), so the next peak is a new excursion rather than the same one. */
        const val SETTLE_THRESHOLD_G = 1.6f

        /** Two excursions. One is an impact; two is somebody moving the phone back and forth. */
        const val REQUIRED_CROSSINGS = 2

        /** Both excursions have to belong to one gesture. */
        const val SHAKE_WINDOW_MS = 700L

        /** And a whole shake cannot repeat faster than this. */
        const val SHAKE_DEBOUNCE_MS = 2_000L
    }
}
