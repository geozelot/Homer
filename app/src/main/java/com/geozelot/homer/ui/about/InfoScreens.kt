package com.geozelot.homer.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.R
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.SerifTitle

/** A back-navigable scaffold for a simple scrollable info page, matching the candlelit theme. */
@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Muted)
            }
            Text(title, style = SerifTitle.copy(fontSize = 22.sp), color = Parchment, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 32.dp),
        ) { content() }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, color = Parchment, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = SectionLabel, color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
}

/** What data Homer stores and where it goes — a plain-language privacy statement. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    InfoScaffold(stringResource(R.string.about_privacy_title), onBack) {
        Paragraph(stringResource(R.string.about_privacy_p1))
        SectionHeader(stringResource(R.string.about_privacy_account_header))
        Paragraph(stringResource(R.string.about_privacy_account_body))
        SectionHeader(stringResource(R.string.about_privacy_library_header))
        Paragraph(stringResource(R.string.about_privacy_library_body))
        SectionHeader(stringResource(R.string.about_privacy_covers_header))
        Paragraph(stringResource(R.string.about_privacy_covers_body))
        SectionHeader(stringResource(R.string.about_privacy_lock_header))
        Paragraph(stringResource(R.string.about_privacy_lock_body))
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Open-source licenses for Homer's dependencies (all Apache License 2.0). */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    InfoScaffold(stringResource(R.string.about_licenses_title), onBack) {
        Paragraph(stringResource(R.string.about_licenses_intro))
        LIBRARIES.forEach { (name, holder) ->
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                Text(name, color = Parchment, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.about_license_holder, holder), color = Muted, fontSize = 12.sp)
            }
        }
        SectionHeader(stringResource(R.string.about_apache_header))
        Paragraph(stringResource(R.string.about_apache_body))
        Text(stringResource(R.string.about_homer_itself), color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Paragraph(stringResource(R.string.about_homer_license))
    }
}

private val LIBRARIES = listOf(
    "Jetpack Compose & AndroidX" to "The Android Open Source Project",
    "AndroidX Media3 (ExoPlayer)" to "The Android Open Source Project",
    "AndroidX Room" to "The Android Open Source Project",
    "AndroidX WorkManager" to "The Android Open Source Project",
    "AndroidX Security (Crypto)" to "The Android Open Source Project",
    "AndroidX DataStore" to "The Android Open Source Project",
    "AndroidX Navigation & Browser" to "The Android Open Source Project",
    "Dagger Hilt" to "Google LLC",
    "Kotlin & kotlinx (Coroutines, Serialization)" to "JetBrains s.r.o.",
    "OkHttp" to "Square, Inc.",
    "Coil" to "Coil Contributors",
)
