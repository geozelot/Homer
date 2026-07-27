package com.geozelot.homer.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.BuildConfig
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
            .padding(horizontal = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Muted)
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
    InfoScaffold("Privacy", onBack) {
        Paragraph(
            "Homer is a self-hosted audiobook player. It talks to one server: the Nextcloud you sign " +
                "in to. There are no analytics, no advertising, no third-party tracking, and no Google " +
                "Play Services.",
        )
        SectionHeader("YOUR ACCOUNT")
        Paragraph(
            "Signing in uses Nextcloud's Login Flow, which grants Homer a scoped, revocable app " +
                "password — never your real password. It is stored only in Android's Keystore-backed " +
                "encrypted storage on this device and sent solely to your server to authenticate requests.",
        )
        SectionHeader("YOUR LIBRARY")
        Paragraph(
            "Browsing, streaming, downloads, and cross-device progress all happen against your own " +
                "server over WebDAV. Progress and metadata corrections are written to a .homer file in " +
                "your own storage; nothing is uploaded anywhere else. Offline downloads and cover art are " +
                "kept on this device (or the folder you choose).",
        )
        SectionHeader("OPTIONAL ONLINE COVERS")
        Paragraph(
            "If — and only if — you turn on \"Look up missing covers online,\" Homer sends the title and " +
                "author of books that have no embedded art to Open Library (openlibrary.org) to find a " +
                "cover. This is off by default and sends nothing about your account.",
        )
        SectionHeader("APP LOCK")
        Paragraph(
            "The optional app lock uses your device's biometric/credential prompt directly. Homer never " +
                "sees or stores your biometrics.",
        )
        Text("Homer ${BuildConfig.VERSION_NAME}", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Open-source licenses for Homer's dependencies (all Apache License 2.0). */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    InfoScaffold("Open-source licenses", onBack) {
        Paragraph(
            "Homer is built on open-source software. Every dependency below is distributed under the " +
                "Apache License 2.0.",
        )
        LIBRARIES.forEach { (name, holder) ->
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                Text(name, color = Parchment, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("$holder · Apache License 2.0", color = Muted, fontSize = 12.sp)
            }
        }
        SectionHeader("APACHE LICENSE 2.0")
        Paragraph(
            "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use these files " +
                "except in compliance with the License. You may obtain a copy of the License at " +
                "https://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed " +
                "to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, " +
                "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
        )
        Text("Homer itself", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Paragraph("© geozelot. See the project repository for Homer's own license.")
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
