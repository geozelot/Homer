package com.geozelot.homer.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.SettingsGroup
import com.geozelot.homer.ui.components.SettingsNavRow
import com.geozelot.homer.ui.components.SettingsRowDivider
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Faint

/**
 * The settings hub: five rows, one per area, each summarising its own state so the user can see
 * where a setting lives without opening every page.
 *
 * "Library source" and "Sync" used to be two of them, and they split one subject down the wrong
 * seam — the shared index lived under source while the thing it shares lived under sync. They are
 * now one Library row.
 *
 * The ViewModel is passed in — never resolved here. It is the library destination's [HomeViewModel]
 * instance, scoped by the nav host; building a second one would re-run its `init` (scan, cover
 * fetch, sync) and give this screen state that diverges from the library's.
 */
@Composable
fun SettingsHubScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val libraryIsShare by viewModel.libraryIsShare.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val customStorageUri by viewModel.customStorageUri.collectAsStateWithLifecycle()
    val customStoragePath by viewModel.customStoragePath.collectAsStateWithLifecycle()
    val seekSeconds by viewModel.seekSeconds.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_title), onBack, modifier) {
        // The books, and where they are kept — one group, because they are two halves of the same
        // question: what is in the library and what of it is on this phone.
        SettingsGroup(title = stringResource(R.string.set_hub_books_header)) {
            SettingsNavRow(
                label = stringResource(R.string.set_library_title),
                summary = sourceSummary(account, libraryIsShare, bookCount),
                onClick = onOpenLibrary,
            )
            SettingsRowDivider()
            SettingsNavRow(
                label = stringResource(R.string.set_device_title),
                summary = storageSummary(customStoragePath, customStorageUri),
                onClick = onOpenDevice,
            )
        }

        SettingsGroup(title = stringResource(R.string.set_hub_app_header)) {
            SettingsNavRow(
                label = stringResource(R.string.set_playback_title),
                summary = stringResource(R.string.set_playback_summary, seekSeconds),
                onClick = onOpenPlayback,
            )
            SettingsRowDivider()
            SettingsNavRow(
                label = stringResource(R.string.set_privacy_title),
                summary = stringResource(R.string.set_privacy_summary),
                onClick = onOpenPrivacy,
            )
            SettingsRowDivider()
            SettingsNavRow(
                label = stringResource(R.string.set_about_title),
                summary = stringResource(R.string.set_about_summary),
                onClick = onOpenAbout,
            )
        }

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
        stringResource(
            R.string.set_source_summary_books,
            where,
            pluralStringResource(R.plurals.sync_books_count, bookCount, bookCount),
        )
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
