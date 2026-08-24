package com.geozelot.homer.data.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the rule that keeps the decoder-free duration probe safe to try.
 *
 * Whether a renderer-less player reports a duration depends on the device's extractors, so the
 * fast path cannot be validated up front — it is used optimistically. What has to hold is that a
 * device where it silently returns nothing pays a *bounded* price and never a wrong answer. These
 * tests pin the two halves of that: the gate withdraws after a fixed number of rescues, and it
 * does not withdraw for the one reason that looks like failure but isn't.
 */
class FastProbeGateTest {

    @Test
    fun `the fast path is tried by default`() {
        assertTrue(FastProbeGate().shouldTryFast())
    }

    @Test
    fun `it withdraws after the configured run of rescues`() {
        val gate = FastProbeGate(fallbackLimit = 3)
        assertFalse("no announcement before the limit", gate.onFullProbeRescue())
        assertFalse(gate.onFullProbeRescue())
        assertTrue("the transition is announced once, so it can be logged", gate.onFullProbeRescue())
        assertFalse(gate.shouldTryFast())
    }

    @Test
    fun `withdrawal is announced once and only once`() {
        val gate = FastProbeGate(fallbackLimit = 1)
        assertTrue(gate.onFullProbeRescue())
        // A caller that keeps reporting must not produce a log line per file for the rest of the run.
        assertFalse(gate.onFullProbeRescue())
        assertFalse(gate.onFullProbeRescue())
    }

    @Test
    fun `a success resets the run`() {
        // Occasional rescues are normal — a file whose header the fast path can't read says nothing
        // about the next one. Only an unbroken run means the fast path is structurally useless here.
        val gate = FastProbeGate(fallbackLimit = 3)
        gate.onFullProbeRescue()
        gate.onFullProbeRescue()
        gate.onFastSuccess()
        assertFalse(gate.onFullProbeRescue())
        assertFalse(gate.onFullProbeRescue())
        assertTrue(gate.shouldTryFast())
    }

    @Test
    fun `both probes failing never withdraws the fast path`() {
        // The case that matters most. A library of files with no readable duration — or a dropped
        // connection — makes every probe come back empty. Counting those would disable a perfectly
        // good fast path on exactly the libraries that most need it to be quick.
        val gate = FastProbeGate(fallbackLimit = 3)
        repeat(50) { gate.onBothFailed() }
        assertTrue(gate.shouldTryFast())
        // And they must not be banked toward the limit either: one rescue after fifty unreadable
        // files is still one rescue, not the fifty-first strike.
        assertFalse(gate.onFullProbeRescue())
        assertTrue(gate.shouldTryFast())
    }

    @Test
    fun `a working fast path is never withdrawn`() {
        val gate = FastProbeGate(fallbackLimit = 3)
        repeat(500) { gate.onFastSuccess() }
        assertTrue(gate.shouldTryFast())
    }

    @Test
    fun `the default limit is small enough to be cheap and large enough to tolerate noise`() {
        // Worst case on a device where the fast path never works: FALLBACK_LIMIT files pay the fast
        // timeout on top of the full probe, then nothing does. Keep that a handful, not a hundred.
        assertTrue(FastProbeGate.FALLBACK_LIMIT in 2..5)
    }
}
