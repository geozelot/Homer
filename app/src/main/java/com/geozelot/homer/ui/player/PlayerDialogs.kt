package com.geozelot.homer.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.theme.Muted

// ── Dialogs ──────────────────────────────────────────────────────────────────

/**
 * Types an exact playback speed.
 *
 * Its own dialog rather than [com.geozelot.homer.ui.components.CustomNumberDialog] because a
 * speed is fractional, and the one thing
 * a listener must not be able to do by accident is set it to zero — which is silence that looks
 * like a stall. Anything unparseable leaves the speed alone; anything out of range is clamped.
 */
@Composable
internal fun CustomSpeedDialog(initial: Float, onConfirm: (Float) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(formatSpeed(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_speed_custom_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    // One separator, digits only — and both separators accepted, because the
                    // keyboard offers whichever the locale uses and the parser below wants a dot.
                    onValueChange = { entered ->
                        text = entered.filter { it.isDigit() || it == '.' || it == ',' }.take(4)
                    },
                    label = { Text(stringResource(R.string.player_speed_unit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.player_speed_custom_range),
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            HomerTextButton(
                onClick = {
                    text.replace(',', '.').toFloatOrNull()
                        ?.let { onConfirm(it.coerceIn(MIN_SPEED, MAX_SPEED)) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Bounds for a typed speed. Zero is silence that looks like a stall, so it is not reachable. */
private const val MIN_SPEED = 0.25f
private const val MAX_SPEED = 4.0f
