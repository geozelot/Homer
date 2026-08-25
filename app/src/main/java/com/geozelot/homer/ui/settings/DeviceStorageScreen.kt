package com.geozelot.homer.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.PillButton
import com.geozelot.homer.ui.components.SettingsActionRow
import com.geozelot.homer.ui.components.SettingsGroup
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsRowDivider
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Parchment

/**
 * "What's stored here": the folder this device keeps downloads and cover art in, and when it puts
 * books there. This is the *local* folder — deliberately not called "library folder", which is the
 * folder on the server (see [LibraryScreen]).
 */
@Composable
fun DeviceStorageScreen(
    viewModel: HomeViewModel,
    onOpenStorageBrowser: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val customStorageUri by viewModel.customStorageUri.collectAsStateWithLifecycle()
    val customStoragePath by viewModel.customStoragePath.collectAsStateWithLifecycle()
    val downloadOnPlay by viewModel.downloadOnPlay.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedCount.collectAsStateWithLifecycle()

    var confirmUseAppStorage by remember { mutableStateOf(false) }
    var confirmDeleteDownloads by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            android.util.Log.d("HomerStore", "folder picker returned: $uri") // Log.d: carries a storage path
            viewModel.setCustomStorageFolder(uri)
        } else {
            android.util.Log.w("HomerStore", "folder picker returned null (cancelled or denied by the system)")
        }
    }

    val custom = customStoragePath ?: customStorageUri

    SettingsScaffold(stringResource(R.string.set_device_title), onBack, modifier) {
        SettingsGroup(
            title = stringResource(R.string.set_device_location_header),
            lead = if (custom != null) {
                stringResource(R.string.settings_storage_custom_desc)
            } else {
                stringResource(R.string.settings_storage_default_desc)
            },
        ) {
            SettingsRow(
                label = when {
                    customStoragePath != null ->
                        stringResource(R.string.settings_storage_folder, customStoragePath!!)
                    customStorageUri != null ->
                        stringResource(R.string.settings_storage_custom_folder, storageFolderName(customStorageUri!!))
                    else -> stringResource(R.string.settings_storage_default)
                },
                summary = stringResource(R.string.settings_storage_picker_desc),
            )
            SettingsRowDivider()
            // Three actions on one row of pills rather than three rows: they are alternatives to
            // each other, all answering the same question of where the folder should be.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(
                    text = stringResource(R.string.set_device_choose_folder),
                    onClick = { folderPicker.launch(null) },
                )
                PillButton(
                    text = stringResource(R.string.settings_storage_browse),
                    onClick = onOpenStorageBrowser,
                )
                if (custom != null) {
                    // Reverting moves every downloaded file back into app storage, so it asks first.
                    PillButton(
                        text = stringResource(R.string.settings_storage_use_app),
                        onClick = { confirmUseAppStorage = true },
                    )
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.set_device_downloads_header)) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_download_on_play),
                checked = downloadOnPlay,
                onCheckedChange = viewModel::setDownloadOnPlay,
                description = stringResource(R.string.settings_download_on_play_desc),
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                label = stringResource(R.string.settings_wifi_only),
                checked = wifiOnly,
                onCheckedChange = viewModel::setWifiOnlyDownloads,
            )
            SettingsRowDivider()
            // The only way to reclaim this space, and the only way to be rid of files a library this
            // device no longer has left behind — signing into a different account orphans them, and
            // nothing else on disk knows they are orphans.
            SettingsActionRow(
                label = stringResource(R.string.set_device_delete_downloads),
                summary = if (downloaded > 0) {
                    stringResource(
                        R.string.set_device_delete_downloads_count,
                        pluralStringResource(R.plurals.sync_books_count, downloaded, downloaded),
                    )
                } else {
                    stringResource(R.string.set_device_delete_downloads_none)
                },
                action = stringResource(R.string.set_delete_downloads_confirm_action),
                enabled = downloaded > 0,
                danger = true,
                onClick = { confirmDeleteDownloads = true },
            )
        }
    }

    if (confirmDeleteDownloads) {
        ConfirmDialog(
            title = stringResource(R.string.set_delete_downloads_confirm_title),
            body = stringResource(R.string.set_delete_downloads_confirm_body),
            confirmLabel = stringResource(R.string.set_delete_downloads_confirm_action),
            onConfirm = viewModel::deleteAllDownloads,
            onDismiss = { confirmDeleteDownloads = false },
        )
    }
    if (confirmUseAppStorage) {
        ConfirmDialog(
            title = stringResource(R.string.set_use_app_storage_confirm_title),
            body = stringResource(R.string.set_use_app_storage_confirm_body),
            confirmLabel = stringResource(R.string.set_use_app_storage_confirm_action),
            onConfirm = viewModel::useDefaultStorage,
            onDismiss = { confirmUseAppStorage = false },
        )
    }
}

/** A readable folder name from a SAF tree Uri (e.g. …/tree/primary%3AAudiobooks → "Audiobooks"). */
internal fun storageFolderName(treeUri: String): String =
    Uri.decode(treeUri).substringAfterLast('/').substringAfterLast(':').ifBlank { "selected folder" }
