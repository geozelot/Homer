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
import com.geozelot.homer.ui.components.CustomNumberDialog
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.data.settings.SLEEP_EXTEND_OFF
import com.geozelot.homer.ui.components.SettingsDropdownRow
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.home.HomeViewModel

/**
 * How the player behaves: the two numbers that shape its feel, what the sleep timer does, and
 * whether the system will let playback continue off screen.
 *
 * The sleep settings used to live in a dialog behind the player's sleep button — two preferences
 * hidden inside a transient control, where they were both hard to find and impossible to change
 * without a book open. The timer itself stays in the player, because starting one is an action
 * about this listening session; how it behaves is a preference and belongs here.
 */
@Composable
fun PlaybackSettingsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seekSeconds by viewModel.seekSeconds.collectAsStateWithLifecycle()
    val autoRewind by viewModel.autoRewindSeconds.collectAsStateWithLifecycle()
    val sleepExtend by viewModel.sleepExtend.collectAsStateWithLifecycle()
    val sleepFade by viewModel.sleepFadeOutSeconds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var customSeek by remember { mutableStateOf(false) }
    var customRewind by remember { mutableStateOf(false) }
    var customFade by remember { mutableStateOf(false) }

    SettingsScaffold(stringResource(R.string.set_playback_title), onBack, modifier) {
        // Labelled like the two groups below it. It was the only unlabelled one, which made the
        // headers further down read as marking those sections out as special.
        SettingsSectionHeader(stringResource(R.string.set_playback_skip_header))
        SettingsDropdownRow(
            label = stringResource(R.string.settings_skip_interval),
            chipLabel = stringResource(R.string.settings_seconds, seekSeconds),
            options = SEEK_OPTIONS,
            selected = seekSeconds,
            labelOf = { context.getString(R.string.settings_seconds, it) },
            onSelect = viewModel::setSeekSeconds,
            description = stringResource(R.string.set_playback_skip_desc),
            onCustom = { customSeek = true },
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
            onCustom = { customRewind = true },
        )

        SettingsDivider()
        SettingsSectionHeader(stringResource(R.string.set_playback_sleep_header))
        SettingsExplanation(stringResource(R.string.set_playback_sleep_lead))
        SettingsDropdownRow(
            label = stringResource(R.string.player_shake_to_extend),
            chipLabel = stringResource(sleepExtendLabel(sleepExtend)),
            options = SLEEP_EXTEND_OPTIONS,
            selected = sleepExtend,
            labelOf = { context.getString(sleepExtendLabel(it)) },
            onSelect = viewModel::setSleepExtend,
            description = stringResource(R.string.set_playback_shake_desc),
        )
        SettingsDropdownRow(
            label = stringResource(R.string.player_fade_out),
            chipLabel = secondsOrOff(context, sleepFade),
            options = SLEEP_FADE_OPTIONS,
            selected = sleepFade,
            labelOf = { secondsOrOff(context, it) },
            onSelect = viewModel::setSleepFadeOutSeconds,
            description = stringResource(R.string.set_playback_fade_desc),
            onCustom = { customFade = true },
        )

        SettingsDivider()
        SettingsSectionHeader(stringResource(R.string.set_playback_background_header))
        BatteryOptimisationRow()
    }

    if (customSeek) {
        CustomNumberDialog(
            title = stringResource(R.string.settings_skip_interval),
            unit = stringResource(R.string.settings_unit_seconds),
            initial = seekSeconds,
            range = 1..300,
            onConfirm = viewModel::setSeekSeconds,
            onDismiss = { customSeek = false },
        )
    }
    if (customRewind) {
        CustomNumberDialog(
            title = stringResource(R.string.settings_rewind),
            unit = stringResource(R.string.settings_unit_seconds),
            initial = autoRewind,
            range = 0..120,
            onConfirm = viewModel::setAutoRewindSeconds,
            onDismiss = { customRewind = false },
        )
    }
    if (customFade) {
        CustomNumberDialog(
            title = stringResource(R.string.player_fade_out),
            unit = stringResource(R.string.settings_unit_seconds),
            initial = sleepFade,
            range = 0..120,
            onConfirm = viewModel::setSleepFadeOutSeconds,
            onDismiss = { customFade = false },
        )
    }
}

/** "off" for zero, "%ds" otherwise — the same phrasing wherever a duration can be nothing. */
private fun secondsOrOff(context: Context, seconds: Int): String =
    if (seconds == 0) {
        context.getString(R.string.settings_off)
    } else {
        context.getString(R.string.settings_seconds, seconds)
    }

private fun sleepExtendLabel(mode: String): Int = when (mode) {
    SLEEP_EXTEND_OFF -> R.string.settings_off
    "5" -> R.string.player_sleep_extend_5
    "15" -> R.string.player_sleep_extend_15
    "30" -> R.string.player_sleep_extend_30
    "chapter" -> R.string.player_sleep_extend_chapter
    else -> R.string.player_sleep_extend_previous
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
private val SLEEP_FADE_OPTIONS = listOf(0, 5, 10, 20, 30, 60)
/** Off first, because it is the default and because the picker had no way to say it at all. */
private val SLEEP_EXTEND_OPTIONS = listOf(SLEEP_EXTEND_OFF, "5", "15", "30", "previous", "chapter")
