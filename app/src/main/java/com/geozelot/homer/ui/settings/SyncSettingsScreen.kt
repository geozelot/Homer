package com.geozelot.homer.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Parchment

/**
 * "What follows you between devices". Only one thing does — your place, your bookmarks and your
 * metadata edits — and where it goes depends on how the library was opened: an account library
 * writes to that account, while a share library has no account of its own until one is linked.
 */
@Composable
fun SyncSettingsScreen(
    viewModel: HomeViewModel,
    onLinkSyncAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryIsShare by viewModel.libraryIsShare.collectAsStateWithLifecycle()
    val syncAccount by viewModel.syncAccount.collectAsStateWithLifecycle()
    val progressSync by viewModel.progressSyncEnabled.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.set_sync_title), onBack, modifier) {
        Text(
            stringResource(R.string.set_sync_lead),
            color = Parchment,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (libraryIsShare) {
            // A share link is somebody else's storage: progress stays on this device until the
            // user points it at an account of their own.
            SettingsSectionHeader(stringResource(R.string.set_sync_account_header))
            val sync = syncAccount
            SettingsRow(
                label = if (sync != null) {
                    stringResource(
                        R.string.settings_sync_to,
                        "${sync.loginName}@${sync.serverUrl.substringAfter("://")}",
                    )
                } else {
                    stringResource(R.string.settings_sync_device)
                },
            ) {
                if (sync != null) {
                    TextButton(
                        onClick = viewModel::unlinkSyncAccount,
                        contentPadding = SettingsActionPadding,
                    ) { Text(stringResource(R.string.settings_sync_stop)) }
                } else {
                    TextButton(onClick = onLinkSyncAccount, contentPadding = SettingsActionPadding) {
                        Text(stringResource(R.string.settings_sync_link))
                    }
                }
            }
        } else {
            SettingsSwitchRow(
                label = stringResource(R.string.sync_progress_switch),
                checked = progressSync,
                onCheckedChange = viewModel::setProgressSync,
                description = if (progressSync) {
                    stringResource(R.string.sync_progress_on_desc)
                } else {
                    stringResource(R.string.sync_progress_off_desc)
                },
            )
        }
    }
}
