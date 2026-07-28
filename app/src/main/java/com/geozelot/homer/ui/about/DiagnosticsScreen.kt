package com.geozelot.homer.ui.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device log viewer, so diagnostics can be captured without adb. An app can read its **own**
 * process logs via `logcat -d` with no special permission; this grabs the recent lines and offers
 * Copy / Share so the user can paste them into a bug report.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var reloadKey by remember { mutableStateOf(0) }
    val log by produceState(initialValue = "Reading logs…", reloadKey) {
        value = captureLog()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Muted)
            }
            Text("Diagnostics", style = SerifTitle.copy(fontSize = 22.sp), color = Parchment, modifier = Modifier.padding(start = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(log)) }) { Text("Copy") }
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log)
                context.startActivity(Intent.createChooser(intent, "Share logs"))
            }) { Text("Share") }
            TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
        }
        Text(
            "Homer's own recent log. Reproduce the problem, then Refresh and Copy or Share this.",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = log,
            color = Parchment,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1)
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        )
    }
}

/** Reads the app's own recent logcat (no permission needed for own-process logs). */
private suspend fun captureLog(): String = withContext(Dispatchers.IO) {
    runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "1500"))
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrElse { "Could not read logs: ${it.message}" }
        .ifBlank { "No log lines captured." }
}
