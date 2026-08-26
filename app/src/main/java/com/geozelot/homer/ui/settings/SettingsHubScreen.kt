package com.geozelot.homer.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsDropdownRow
import com.geozelot.homer.ui.components.SettingsNavRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
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
    onOpenUpkeep: () -> Unit,
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
    val readsOnly by viewModel.readsSharedIndex.collectAsStateWithLifecycle()
    val unmeasured by viewModel.unmeasuredCount.collectAsStateWithLifecycle()
    val artless by viewModel.artlessCount.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_title), onBack, modifier) {
        // Three categories, because the eight rows underneath answer three different questions:
        // what the library IS, what this phone does with it, and what Homer itself does. Flat, they
        // read as a list to search through; grouped, the right one is usually obvious.
        SettingsSectionHeader(stringResource(R.string.set_cat_library))
        SettingsNavRow(
            label = stringResource(R.string.set_sync_title),
            summary = sourceSummary(account, libraryIsShare, bookCount),
            onClick = onOpenLibrary,
        )
        // Disabled rather than hidden for a reader: the page is not theirs to act on, but the fact
        // that upkeep exists and is somebody else's job is worth being able to see.
        SettingsNavRow(
            label = stringResource(R.string.set_upkeep_title),
            summary = upkeepSummary(readsOnly, artless, unmeasured),
            enabled = !readsOnly,
            onClick = onOpenUpkeep,
        )

        SettingsDivider()

        SettingsSectionHeader(stringResource(R.string.set_cat_device))
        // "What's stored here" — downloads and covers on this phone.
        SettingsNavRow(
            label = stringResource(R.string.set_device_title),
            summary = storageSummary(customStoragePath, customStorageUri),
            onClick = onOpenDevice,
        )
        SettingsNavRow(
            label = stringResource(R.string.set_playback_title),
            summary = stringResource(R.string.set_playback_summary, seekSeconds),
            onClick = onOpenPlayback,
        )

        SettingsDivider()

        SettingsSectionHeader(stringResource(R.string.set_cat_app))
        // A row rather than a page: one setting does not earn a screen of its own, and this is the
        // one place in settings a reader will look for it. Not persisted by Homer — the platform
        // owns the choice, and Android 13+ shows the same setting in system settings.
        LanguageRow()
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

/**
 * Which language Homer's interface is in.
 *
 * Reads its value straight from [AppLanguage.current] rather than from a flow, because the platform
 * recreates the activity when the language changes — by the time this composes again, the strings
 * around it have already re-resolved and so has this row.
 */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val current = AppLanguage.current(context)
    SettingsDropdownRow(
        label = stringResource(R.string.set_language_title),
        chipLabel = languageLabel(context, current),
        options = AppLanguage.entries.toList(),
        selected = current,
        labelOf = { languageLabel(context, it) },
        onSelect = { AppLanguage.apply(context, it) },
        description = stringResource(R.string.set_language_desc),
    )
}

/**
 * "System", "English", "Deutsch".
 *
 * Every language names ITSELF, in its own language, and is therefore not translated — a reader
 * looking for German finds "Deutsch" whatever the interface currently says. Only "System" is
 * translated, because it names a behaviour rather than a language.
 */
private fun languageLabel(context: Context, language: AppLanguage): String =
    if (language == AppLanguage.SYSTEM) context.getString(R.string.set_language_system) else language.label

/**
 * What upkeep has left to do, or whose job it is.
 *
 * A reader is told the state rather than a count of work, because none of it is theirs to start.
 */
@Composable
private fun upkeepSummary(readsOnly: Boolean, artless: Int, unmeasured: Int): String = when {
    readsOnly -> stringResource(R.string.set_upkeep_reader_summary)
    artless == 0 && unmeasured == 0 -> stringResource(R.string.set_upkeep_summary_done)
    else -> stringResource(R.string.set_upkeep_summary_todo, artless, unmeasured)
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
