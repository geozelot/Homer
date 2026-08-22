package com.geozelot.homer.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.SettingsDropdownRow
import com.geozelot.homer.ui.home.HomeViewModel

/** Skip distance and rewind-on-resume — the two numbers that shape how the player feels. */
@Composable
fun PlaybackSettingsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seekSeconds by viewModel.seekSeconds.collectAsStateWithLifecycle()
    val autoRewind by viewModel.autoRewindSeconds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScaffold(stringResource(R.string.set_playback_title), onBack, modifier) {
        SettingsDropdownRow(
            label = stringResource(R.string.settings_skip_interval),
            chipLabel = stringResource(R.string.settings_seconds, seekSeconds),
            options = SEEK_OPTIONS,
            selected = seekSeconds,
            labelOf = { context.getString(R.string.settings_seconds, it) },
            onSelect = viewModel::setSeekSeconds,
            description = stringResource(R.string.set_playback_skip_desc),
        )
        SettingsDropdownRow(
            label = stringResource(R.string.settings_rewind),
            chipLabel = if (autoRewind == 0) {
                stringResource(R.string.settings_off)
            } else {
                stringResource(R.string.settings_seconds, autoRewind)
            },
            options = REWIND_OPTIONS,
            selected = autoRewind,
            labelOf = {
                if (it == 0) {
                    context.getString(R.string.settings_off)
                } else {
                    context.getString(R.string.settings_seconds, it)
                }
            },
            onSelect = viewModel::setAutoRewindSeconds,
            description = stringResource(R.string.set_playback_rewind_desc),
        )
    }
}

private val SEEK_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60)
private val REWIND_OPTIONS = listOf(0, 5, 10, 15, 20, 30)
