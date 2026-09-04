package com.geozelot.homer.ui.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where Back goes.
 *
 * One rule, [SetupUiState.canGoBack], read by both affordances — the arrow on screen and the system
 * gesture. They used to answer separately and disagree wherever the flow had been entered part-way,
 * which is every re-run from settings.
 */
class SetupUiStateTest {

    // ── a first run: the flow owns the whole stack ───────────────────────────────────────────

    @Test
    fun `the first screen of a first run has nowhere to go`() {
        // The gesture then falls through to the system and leaves the app, and no arrow is drawn.
        assertFalse(state(SetupStep.WHERE, entry = SetupStep.WHERE).canGoBack)
    }

    @Test
    fun `every later screen of a first run steps back inside the flow`() {
        for (step in SetupStep.entries.filterNot { it == SetupStep.WHERE }) {
            assertTrue(step.name, state(step, entry = SetupStep.WHERE).canGoBack)
        }
    }

    // ── a re-run from settings: back out at the step it opened on ────────────────────────────

    @Test
    fun `the step a re-run opened on goes back to settings, not to the previous screen`() {
        // What the flow is opened at is what the settings row asked about. There is nothing behind
        // it belonging to this flow — walking to the findings from the progress question would
        // land on a screen that was never probed.
        assertFalse(state(SetupStep.FINDINGS, entry = SetupStep.FINDINGS).canGoBack)
        assertFalse(state(SetupStep.PROGRESS, entry = SetupStep.PROGRESS).canGoBack)
        assertFalse(state(SetupStep.WHERE, entry = SetupStep.WHERE).canGoBack)
    }

    @Test
    fun `screens reached after the entry step still step back inside the flow`() {
        assertTrue(state(SetupStep.CREATE, entry = SetupStep.FINDINGS).canGoBack)
        assertTrue(state(SetupStep.PROGRESS, entry = SetupStep.FINDINGS).canGoBack)
        assertTrue(state(SetupStep.SYNC_LOGIN, entry = SetupStep.PROGRESS).canGoBack)
    }

    @Test
    fun `walking back to the start of a flow entered midway is still the way out`() {
        // Reachable: "Look again" on an unreachable share link sends the findings back to WHERE.
        // Comparing against the entry step alone said "yes, go back" while `back()` had nowhere to
        // go — so the gesture was enabled, swallowed, and did nothing at all.
        assertFalse(state(SetupStep.WHERE, entry = SetupStep.FINDINGS).canGoBack)
    }

    private fun state(step: SetupStep, entry: SetupStep) = SetupUiState(step = step, entryStep = entry)
}
