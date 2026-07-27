package com.geozelot.homer.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Surface2

/**
 * The app's standard toggle. Uses an explicit off-state palette (muted thumb + border on a raised
 * track) because Material's default unchecked colors nearly vanish against the candlelit dark
 * ground; on is the amber accent. Shared so every toggle in the app reads the same.
 */
@Composable
fun HomerSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = OnAmber,
            checkedTrackColor = Amber,
            checkedBorderColor = Amber,
            uncheckedThumbColor = Muted,
            uncheckedTrackColor = Surface2,
            uncheckedBorderColor = Muted,
        ),
    )
}
