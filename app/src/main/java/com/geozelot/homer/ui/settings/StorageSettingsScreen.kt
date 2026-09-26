package com.geozelot.homer.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment

/**
 * "What's stored here": the folder this device keeps downloads and cover art in, what may fetch
 * books into it, and how to be rid of them. This is the *local* folder — deliberately not called
 * "library folder", which is the folder on the server (see [LibraryScreen]).
 *
 * One of three pages under "On this device", alongside [BrowsingSettingsScreen] and
 * [PlaybackSettingsScreen]. They were once three sections of one page, which put a display setting
 * and a lock-screen permission under a title that promised storage.
 */
@Composable
fun StorageSettingsScreen(
    viewModel: HomeViewModel,
    onOpenStorageBrowser: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val customStorageUri by viewModel.customStorageUri.collectAsStateWithLifecycle()
    val customStoragePath by viewModel.customStoragePath.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedCount.collectAsStateWithLifecycle()
    val storageLost by viewModel.storageAccessLost.collectAsStateWithLifecycle()

    // Re-asked on every resume, not once: the way a folder grant is lost is that the user leaves
    // for system settings (or a file manager, or the card slot) and comes back — so the moment this
    // page becomes visible again is exactly the moment the answer may have changed.
    val storageLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(storageLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStorageAccess()
        }
        storageLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { storageLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(customStoragePath, customStorageUri) { viewModel.refreshStorageAccess() }

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

    SettingsScaffold(stringResource(R.string.set_storage_title), onBack, modifier) {
        // Three groups, one question asked three ways: where the bytes are kept, what may fetch
        // them, and how to be rid of them.
        SettingsSectionHeader(stringResource(R.string.set_device_location_header))
        Text(
            when {
                customStoragePath != null -> stringResource(R.string.settings_storage_folder, customStoragePath!!)
                customStorageUri != null ->
                    stringResource(R.string.settings_storage_custom_folder, storageFolderName(customStorageUri!!))
                else -> stringResource(R.string.settings_storage_default)
            },
            // Dimmed while it is unreachable: the folder is still what was chosen, but nothing is
            // being written there, and stating it in the same voice as a working one is a lie.
            color = if (storageLost) Muted else Parchment,
            fontSize = 14.sp,
        )
        if (storageLost) {
            // Said here rather than left to be inferred from books that quietly stopped being
            // downloaded. The two buttons below are already the whole remedy — pick the folder
            // again, or move to app storage — so this states the problem and points at them.
            Text(
                stringResource(R.string.settings_storage_lost),
                color = Danger,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            SettingsExplanation(stringResource(R.string.settings_storage_lost_desc))
        } else {
            SettingsExplanation(
                text = if (custom != null) {
                    stringResource(R.string.settings_storage_custom_desc)
                } else {
                    stringResource(R.string.settings_storage_default_desc)
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomerTextButton(onClick = { folderPicker.launch(null) }, contentPadding = SettingsActionPadding) {
                Text(stringResource(R.string.set_device_choose_folder))
            }
            HomerTextButton(onClick = onOpenStorageBrowser, contentPadding = SettingsActionPadding) {
                Text(stringResource(R.string.settings_storage_browse))
            }
            if (custom != null) {
                // Reverting moves every downloaded file back into app storage, so it asks first.
                HomerTextButton(
                    onClick = { confirmUseAppStorage = true },
                    contentPadding = SettingsActionPadding,
                ) { Text(stringResource(R.string.settings_storage_use_app)) }
            }
        }
        SettingsNote(stringResource(R.string.settings_storage_picker_desc))

        SettingsDivider()
        // Whether a download may use mobile data is a fact about the bytes, so it is here.
        // "Download it while I listen" is triggered by PLAYING, and lives with playback.
        SettingsSectionHeader(stringResource(R.string.set_device_downloads_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_wifi_only),
            checked = wifiOnly,
            onCheckedChange = viewModel::setWifiOnlyDownloads,
            description = stringResource(R.string.settings_wifi_only_desc),
        )

        SettingsDivider()
        // Last on the page, because it is the one row here that destroys something. It is also
        // the only way to be rid of files a library this device no longer has left behind —
        // signing into a different account orphans them, and nothing else on disk knows they are.
        SettingsSectionHeader(stringResource(R.string.set_device_reclaim_header))
        SettingsRow(
            label = stringResource(R.string.set_device_delete_downloads),
            summary = if (downloaded > 0) {
                stringResource(
                    R.string.set_device_delete_downloads_count,
                    pluralStringResource(R.plurals.sync_books_count, downloaded, downloaded),
                )
            } else {
                stringResource(R.string.set_device_delete_downloads_none)
            },
            onClick = { confirmDeleteDownloads = true },
        )
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
