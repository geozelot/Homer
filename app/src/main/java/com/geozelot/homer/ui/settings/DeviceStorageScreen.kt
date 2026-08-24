package com.geozelot.homer.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsSectionHeader
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

    var confirmUseAppStorage by remember { mutableStateOf(false) }

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
        SettingsSectionHeader(stringResource(R.string.set_device_location_header))
        Text(
            when {
                customStoragePath != null -> stringResource(R.string.settings_storage_folder, customStoragePath!!)
                customStorageUri != null ->
                    stringResource(R.string.settings_storage_custom_folder, storageFolderName(customStorageUri!!))
                else -> stringResource(R.string.settings_storage_default)
            },
            color = Parchment,
            fontSize = 14.sp,
        )
        SettingsExplanation(
            text = if (custom != null) {
                stringResource(R.string.settings_storage_custom_desc)
            } else {
                stringResource(R.string.settings_storage_default_desc)
            },
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { folderPicker.launch(null) }, contentPadding = SettingsActionPadding) {
                Text(stringResource(R.string.set_device_choose_folder))
            }
            TextButton(onClick = onOpenStorageBrowser, contentPadding = SettingsActionPadding) {
                Text(stringResource(R.string.settings_storage_browse))
            }
            if (custom != null) {
                // Reverting moves every downloaded file back into app storage, so it asks first.
                TextButton(
                    onClick = { confirmUseAppStorage = true },
                    contentPadding = SettingsActionPadding,
                ) { Text(stringResource(R.string.settings_storage_use_app)) }
            }
        }
        SettingsNote(stringResource(R.string.settings_storage_picker_desc))

        SettingsDivider()

        SettingsSectionHeader(stringResource(R.string.set_device_downloads_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_download_on_play),
            checked = downloadOnPlay,
            onCheckedChange = viewModel::setDownloadOnPlay,
            description = stringResource(R.string.settings_download_on_play_desc),
        )
        SettingsSwitchRow(
            label = stringResource(R.string.settings_wifi_only),
            checked = wifiOnly,
            onCheckedChange = viewModel::setWifiOnlyDownloads,
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
