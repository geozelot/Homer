package com.geozelot.homer.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsNavRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Amber

/**
 * Everything that governs who can see what: the lock on this device, the trust placed in the
 * server's certificate, and the one feature that talks to a third party.
 */
@Composable
fun PrivacySettingsScreen(
    viewModel: HomeViewModel,
    onOpenPrivacyStatement: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLock by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val certPinning by viewModel.certPinningEnabled.collectAsStateWithLifecycle()
    val pinBlocked by viewModel.pinningBlocked.collectAsStateWithLifecycle()
    val onlineCovers by viewModel.onlineCoverLookup.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.set_privacy_title), onBack, modifier) {
        SettingsSectionHeader(stringResource(R.string.set_privacy_device_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_app_lock),
            checked = appLock,
            onCheckedChange = viewModel::setAppLock,
            description = stringResource(R.string.settings_app_lock_desc),
        )

        SettingsDivider()

        SettingsSectionHeader(stringResource(R.string.set_privacy_server_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_cert_pinning),
            checked = certPinning,
            onCheckedChange = viewModel::setCertPinning,
            description = stringResource(R.string.settings_cert_pinning_desc),
        )
        // While this is set, nothing at all reaches the server — and every screen in the app still
        // looks fine, because everything on them came out of the database. It is stated here, in
        // full, with the one action that resolves it.
        pinBlocked?.let { block ->
            SettingsNote(stringResource(R.string.settings_cert_pinning_blocked, block.host))
            SettingsExplanation(stringResource(R.string.settings_cert_pinning_blocked_desc))
            HomerTextButton(
                onClick = viewModel::trustPresentedCertificate,
                contentPadding = SettingsActionPadding,
            ) {
                Text(stringResource(R.string.settings_cert_pinning_trust), color = Amber)
            }
        }

        SettingsDivider()

        // The only feature that reaches a server other than the user's own — named explicitly.
        SettingsSectionHeader(stringResource(R.string.set_privacy_online_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_online_covers),
            checked = onlineCovers,
            onCheckedChange = viewModel::setOnlineCoverLookup,
            description = stringResource(R.string.settings_online_covers_desc),
        )

        SettingsDivider()

        SettingsNavRow(
            label = stringResource(R.string.about_privacy_title),
            summary = stringResource(R.string.set_privacy_statement_summary),
            onClick = onOpenPrivacyStatement,
        )
    }
}
