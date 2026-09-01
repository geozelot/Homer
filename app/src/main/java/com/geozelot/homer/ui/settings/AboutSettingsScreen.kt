package com.geozelot.homer.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.R
import com.geozelot.homer.data.update.UpdateChannel
import com.geozelot.homer.data.update.UpdateFailure
import com.geozelot.homer.data.update.UpdateRelease
import com.geozelot.homer.data.update.UpdateState
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsDropdownRow
import com.geozelot.homer.ui.components.SettingsNavRow
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle

/** Which build this is, whether a newer one exists, and the two pages that explain it. */
@Composable
fun AboutSettingsScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(stringResource(R.string.set_about_title), onBack, modifier) {
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = SerifTitle,
            color = Parchment,
        )
        Text(
            stringResource(R.string.set_about_tagline),
            color = Faint,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        UpdateSection()

        SettingsDivider()
        SettingsSectionHeader(stringResource(R.string.set_about_more_header))
        SettingsNavRow(
            label = stringResource(R.string.settings_diagnostics),
            summary = stringResource(R.string.set_about_diagnostics_summary),
            onClick = onOpenDiagnostics,
        )
        SettingsNavRow(
            label = stringResource(R.string.about_licenses_title),
            summary = stringResource(R.string.set_about_licenses_summary),
            onClick = onOpenLicenses,
        )
    }
}

/**
 * Whether a newer Homer exists, and the one button that gets it installed.
 *
 * The scheduled check is opt-in and off by default, matching the online cover lookup: it reaches a
 * third party on a schedule the user did not ask for. Checking by hand is always available, because
 * a tap is its own consent.
 */
@Composable
private fun UpdateSection(viewModel: UpdateViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val lastChecked by viewModel.lastCheckedAtMs.collectAsStateWithLifecycle()

    // Coming back from the system "install unknown apps" screen, pick the install back up. Without
    // this the row still says "Allow" and tapping it opens that screen again — a dead end at the
    // exact moment the user has just done what was asked. Same shape as the battery-optimisation
    // row in Playback settings, which re-reads its permission on resume for the same reason.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            val blocked = (state as? UpdateState.Failed)?.reason == UpdateFailure.INSTALL_NOT_ALLOWED
            if (event == Lifecycle.Event.ON_RESUME && blocked && viewModel.canInstallPackages()) {
                viewModel.retry()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsDivider()
    SettingsSectionHeader(stringResource(R.string.set_update_header))

    SettingsRow(
        label = stringResource(R.string.set_update_check),
        summary = statusSummary(state, lastChecked),
        onClick = actionFor(state)?.let { action -> { runAction(action, context, viewModel, state) } },
        trailing = {
            actionFor(state)?.let { action ->
                Text(stringResource(action.label), color = Amber, fontSize = 13.sp)
            }
        },
    )

    // Only while a download is actually running: a bar sitting at zero reads as stuck.
    (state as? UpdateState.Downloading)?.fraction?.let { fraction ->
        LinearProgressIndicator(
            progress = { fraction },
            color = Amber,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    // Trimmed: a release body can be a whole changelog, and this is a settings row, not a page.
    (state as? UpdateState.Available)?.release?.notes?.let { notes ->
        val shown = if (notes.length > NOTES_MAX_CHARS) notes.take(NOTES_MAX_CHARS).trimEnd() + "\u2026" else notes
        SettingsNote("${stringResource(R.string.set_update_notes_title)}\n$shown")
    }

    SettingsSwitchRow(
        label = stringResource(R.string.set_update_auto),
        checked = autoCheck,
        onCheckedChange = viewModel::setAutoCheck,
        description = stringResource(R.string.set_update_auto_desc),
    )

    // Resolved up front: labelOf is a plain lambda, so it cannot call stringResource itself.
    val stableLabel = stringResource(R.string.set_update_channel_stable)
    val betaLabel = stringResource(R.string.set_update_channel_beta)
    val channelLabel = { c: UpdateChannel -> if (c == UpdateChannel.BETA) betaLabel else stableLabel }

    SettingsDropdownRow(
        label = stringResource(R.string.set_update_channel),
        chipLabel = channelLabel(channel),
        options = UpdateChannel.entries.toList(),
        selected = channel,
        labelOf = channelLabel,
        onSelect = viewModel::setChannel,
        description = stringResource(R.string.set_update_channel_desc),
    )
}

/** The one line under "Check for updates": where the updater is, or when it last looked. */
@Composable
private fun statusSummary(state: UpdateState, lastCheckedAtMs: Long): String = when (state) {
    is UpdateState.Checking -> stringResource(R.string.set_update_checking)
    is UpdateState.UpToDate -> stringResource(R.string.set_update_uptodate)
    is UpdateState.Available -> stringResource(R.string.set_update_available, state.release.version.raw)
    is UpdateState.Downloading ->
        state.fraction
            ?.let { stringResource(R.string.set_update_downloading_pct, (it * 100).toInt()) }
            ?: stringResource(R.string.set_update_downloading)
    is UpdateState.ReadyToInstall -> stringResource(R.string.set_update_ready)
    is UpdateState.Installing -> stringResource(R.string.set_update_installing)
    is UpdateState.Failed -> stringResource(state.reason.messageRes)
    UpdateState.Idle -> {
        val whenText = lastCheckedAtMs.takeIf { it > 0 }?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                .toString()
        } ?: stringResource(R.string.set_update_never)
        stringResource(R.string.set_update_last_checked, whenText)
    }
}

/** What tapping the row does right now. Null while the updater is busy and a tap would be noise. */
private enum class UpdateAction(val label: Int) {
    CHECK(R.string.set_update_action_check),
    INSTALL(R.string.set_update_action_install),
    ALLOW(R.string.set_update_action_allow),
    RETRY(R.string.set_update_action_retry),
    VIEW(R.string.set_update_action_view),
}

private fun actionFor(state: UpdateState): UpdateAction? = when (state) {
    UpdateState.Idle, is UpdateState.UpToDate -> UpdateAction.CHECK
    is UpdateState.Available -> UpdateAction.INSTALL
    is UpdateState.ReadyToInstall -> UpdateAction.INSTALL
    is UpdateState.Failed -> when (state.reason) {
        UpdateFailure.INSTALL_NOT_ALLOWED -> UpdateAction.ALLOW
        // Both mean "GitHub has it but this device can't take it from here" — send them to the page.
        UpdateFailure.SIGNATURE_MISMATCH, UpdateFailure.NO_ASSET -> UpdateAction.VIEW
        else -> UpdateAction.RETRY
    }
    // Checking, Downloading and Installing are all in-flight; the manager ignores a second tap
    // anyway, so offering one would only look broken.
    is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> null
}

private fun runAction(
    action: UpdateAction,
    context: Context,
    viewModel: UpdateViewModel,
    state: UpdateState,
) {
    when (action) {
        UpdateAction.CHECK -> viewModel.check()
        UpdateAction.INSTALL -> releaseIn(state)?.let(viewModel::install)
        UpdateAction.ALLOW -> {
            runCatching { context.startActivity(viewModel.unknownSourcesIntent()) }
                .onFailure { viewModel.dismissFailure() }
        }
        UpdateAction.RETRY -> {
            viewModel.dismissFailure()
            viewModel.retry()
        }
        UpdateAction.VIEW -> {
            val url = releaseIn(state)?.pageUrl ?: RELEASES_URL
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
                viewModel.dismissFailure()
            }
        }
    }
}

private fun releaseIn(state: UpdateState): UpdateRelease? = when (state) {
    is UpdateState.Available -> state.release
    is UpdateState.ReadyToInstall -> state.release
    is UpdateState.Downloading -> state.release
    is UpdateState.Installing -> state.release
    else -> null
}

private val UpdateFailure.messageRes: Int
    get() = when (this) {
        UpdateFailure.NETWORK -> R.string.set_update_err_network
        UpdateFailure.RATE_LIMITED -> R.string.set_update_err_ratelimited
        UpdateFailure.NO_ASSET -> R.string.set_update_err_noasset
        UpdateFailure.DOWNLOAD -> R.string.set_update_err_download
        UpdateFailure.SIGNATURE_MISMATCH -> R.string.set_update_err_signature
        UpdateFailure.INSTALL_NOT_ALLOWED -> R.string.set_update_err_notallowed
        UpdateFailure.INSTALL_FAILED -> R.string.set_update_err_installfailed
    }

private const val RELEASES_URL = "https://github.com/${BuildConfig.UPDATE_REPO}/releases"

/** Enough for a short "what's new"; the release page has the rest. */
private const val NOTES_MAX_CHARS = 400
