package com.geozelot.homer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

// ── Where a press is shown ───────────────────────────────────────────────────
//
// Homer separates a control's TAP SIZE from its DRAWN SIZE all over the place, and for a good
// reason each time: a chip is 26dp tall because that is how tall a chip should look, and its target
// is 48dp because that is how big a target has to be. The two numbers are deliberate and different.
//
// What kept being forgotten is that the press feedback has to be told which of the two it belongs
// to. Left to itself it takes the TARGET — so tapping the grid/list toggle flashed a 44×48
// rectangle around a 26dp pill, and the mini-player's play button flashed a square around a circle.
// Both look like the app missed and hit the space beside the control.
//
// The rule, in one line: **the target takes the tap, the drawn thing shows the press.**
//
// Two modifiers sharing one interaction source. Where a control's drawn shape IS its target — an
// icon button that fills its own 48dp — none of this applies: `clip()` before `clickable()` already
// gives the ripple the right shape, and that is the simpler thing to reach for.

/** The source a [tapTarget] and its [pressFeedback] share. One per control. */
@Composable
fun rememberTapInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * Takes the tap, and shows nothing.
 *
 * Goes on the full-size target. The feedback is deliberately suppressed here — not forgotten — and
 * belongs on whatever [pressFeedback] is applied to.
 */
fun Modifier.tapTarget(interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    clickable(interactionSource = interaction, indication = null, onClick = onClick)

/**
 * Shows the press, and takes nothing.
 *
 * Goes on the drawn thing, AFTER whatever clips it to its shape — a ripple is bounded by the clip
 * above it, which is what makes it follow a pill or a circle instead of the box around it.
 */
fun Modifier.pressFeedback(interaction: MutableInteractionSource): Modifier =
    indication(interaction, ripple())
