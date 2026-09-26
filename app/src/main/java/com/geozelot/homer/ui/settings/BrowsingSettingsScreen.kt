package com.geozelot.homer.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.home.HomeViewModel

/**
 * How THIS phone shows the library: the jump-to-letter lane, and how authors are filed and written.
 * Never anything the library itself carries — that is the line every page under "On this device"
 * sits on.
 */
@Composable
fun BrowsingSettingsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fastScroll by viewModel.fastScroll.collectAsStateWithLifecycle()
    val authorFiling by viewModel.authorFiling.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.set_browsing_title), onBack, modifier) {
        SettingsSectionHeader(stringResource(R.string.set_browsing_scroll_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_fast_scroll),
            checked = fastScroll,
            onCheckedChange = viewModel::setFastScroll,
            description = stringResource(R.string.settings_fast_scroll_desc),
        )

        SettingsDivider()
        SettingsSectionHeader(stringResource(R.string.set_browsing_authors_header))
        SettingsSwitchRow(
            label = stringResource(R.string.settings_author_by_surname),
            checked = authorFiling.bySurname,
            onCheckedChange = viewModel::setAuthorBySurname,
            description = stringResource(R.string.settings_author_by_surname_desc),
        )
        // Only while the list is actually in that order. Writing names back to front in a list
        // sorted by given name would be showing the index of a different arrangement — so the
        // switch is not merely ignored when it does not apply, it is not offered.
        if (authorFiling.bySurname) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_author_show_filed),
                checked = authorFiling.showFiled,
                onCheckedChange = viewModel::setAuthorShowFiled,
                description = stringResource(R.string.settings_author_show_filed_desc),
            )
        }
    }
}

