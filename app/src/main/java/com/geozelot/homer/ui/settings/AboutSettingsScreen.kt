package com.geozelot.homer.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.SettingsNavRow
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle

/** Which build this is, and the two pages that explain it. */
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
