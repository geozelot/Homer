package com.geozelot.homer.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsNavRow
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Faint

/**
 * The settings hub: six rows, one per area, each summarising its own state so the user can see
 * where a setting lives without opening every page.
 *
 * The ViewModel is passed in — never resolved here. It is the library destination's [HomeViewModel]
 * instance, scoped by the nav host; building a second one would re-run its `init` (scan, cover
 * fetch, sync) and give this screen state that diverges from the library's.
 */
@Composable
fun SettingsHubScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val libraryIsShare by viewModel.libraryIsShare.collectAsStateWithLifecycle()
    val syncAccount by viewModel.syncAccount.collectAsStateWithLifecycle()
    val progressSync by viewModel.progressSyncEnabled.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val customStorageUri by viewModel.customStorageUri.collectAsStateWithLifecycle()
    val customStoragePath by viewModel.customStoragePath.collectAsStateWithLifecycle()
    val seekSeconds by viewModel.seekSeconds.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_title), onBack, modifier) {
        // "Where your books come from" — the server side of the library.
        SettingsNavRow(
            label = stringResource(R.string.set_source_title),
            summary = sourceSummary(account, libraryIsShare, bookCount),
            onClick = onOpenSource,
        )
        // "What's stored here" — downloads and covers on this phone.
        SettingsNavRow(
            label = stringResource(R.string.set_device_title),
            summary = storageSummary(customStoragePath, customStorageUri),
            onClick = onOpenDevice,
        )
        // "What follows you between devices".
        SettingsNavRow(
            label = stringResource(R.string.set_sync_title),
            summary = if (syncing(libraryIsShare, syncAccount != null, progressSync)) {
                stringResource(R.string.set_sync_state_account)
            } else {
                stringResource(R.string.set_sync_state_device)
            },
            onClick = onOpenSync,
        )

        SettingsDivider()

        SettingsNavRow(
            label = stringResource(R.string.set_playback_title),
            summary = stringResource(R.string.set_playback_summary, seekSeconds),
            onClick = onOpenPlayback,
        )
        SettingsNavRow(
            label = stringResource(R.string.set_privacy_title),
            summary = stringResource(R.string.set_privacy_summary),
            onClick = onOpenPrivacy,
        )
        SettingsNavRow(
            label = stringResource(R.string.set_about_title),
            summary = stringResource(R.string.set_about_summary),
            onClick = onOpenAbout,
        )

        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            color = Faint,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

/** The library source in one line: the share or account it comes from, plus how much is in it. */
@Composable
private fun sourceSummary(
    account: com.geozelot.homer.data.auth.NextcloudCredentials?,
    libraryIsShare: Boolean,
    bookCount: Int,
): String {
    val where = account?.let {
        val host = it.serverUrl.substringAfter("://")
        if (libraryIsShare) {
            stringResource(R.string.settings_library_share, host)
        } else {
            stringResource(R.string.settings_account, it.loginName, host)
        }
    } ?: return stringResource(R.string.set_source_summary)
    return if (bookCount > 0) {
        stringResource(R.string.set_source_summary_books, where, bookCount)
    } else {
        where
    }
}

/** Where downloads live, named the way the "On this device" page names it. */
@Composable
private fun storageSummary(customStoragePath: String?, customStorageUri: String?): String = when {
    customStoragePath != null -> stringResource(R.string.settings_storage_folder, customStoragePath)
    customStorageUri != null ->
        stringResource(R.string.settings_storage_custom_folder, storageFolderName(customStorageUri))
    else -> stringResource(R.string.settings_storage_default)
}

/**
 * Whether anything actually follows the user between devices. A share library needs a linked
 * account (its own storage may be read-only and isn't the user's); an account library needs the
 * progress-sync switch on.
 */
private fun syncing(libraryIsShare: Boolean, hasSyncAccount: Boolean, progressSync: Boolean) =
    if (libraryIsShare) hasSyncAccount else progressSync
