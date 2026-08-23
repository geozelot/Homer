package com.geozelot.homer.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsDropdownRow
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.theme.Amber
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

        SettingsDivider()
        SettingsSectionHeader(stringResource(R.string.set_playback_background_header))
        BatteryOptimisationRow()
    }
}

/**
 * Whether the system will let Homer keep playing once the app is off screen, and a way in to
 * change it.
 *
 * Homer already does everything the platform asks: a mediaPlayback foreground service, a wake lock
 * held while playing, a media session. None of it binds an aggressive power manager, which on many
 * devices freezes a backgrounded app's threads regardless — the audio starves a minute or two
 * later and resumes only when the app is reopened. There is no API that fixes this from inside the
 * app; the exemption is the fix, so the app's job is to show the state plainly and open the right
 * screen.
 *
 * This is not hypothetical: it is the confirmed cause of the background-playback stalls chased
 * through several betas, after the foreground service, the wake lock and the buffer size had each
 * been ruled out. If a report of playback stopping in the background arrives again, check this
 * row's state before looking at any code.
 *
 * Re-read on every resume, because the user changes it in Settings and comes back.
 */
@Composable
private fun BatteryOptimisationRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var exempt by remember { mutableStateOf(context.isIgnoringBatteryOptimisations()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exempt = context.isIgnoringBatteryOptimisations()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsRow(
        label = stringResource(R.string.set_playback_background_label),
        summary = stringResource(
            if (exempt) {
                R.string.set_playback_background_on
            } else {
                R.string.set_playback_background_off
            },
        ),
        onClick = if (exempt) null else ({ context.openBatteryOptimisationSettings() }),
        trailing = {
            if (!exempt) {
                Text(stringResource(R.string.set_playback_background_action), color = Amber, fontSize = 13.sp)
            }
        },
    )
    SettingsExplanation(stringResource(R.string.set_playback_background_desc))
}

private fun Context.isIgnoringBatteryOptimisations(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true

/**
 * Opens the system's battery-optimisation list. Deliberately the LIST rather than the per-app
 * allow dialog: that one needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS declared, and several OEM
 * builds simply refuse the direct intent. Falls back to Homer's own app-info page, which every
 * build has and which reaches the same setting.
 */
private fun Context.openBatteryOptimisationSettings() {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
    for (intent in intents) {
        if (runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

private val SEEK_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60)
private val REWIND_OPTIONS = listOf(0, 5, 10, 15, 20, 30)
