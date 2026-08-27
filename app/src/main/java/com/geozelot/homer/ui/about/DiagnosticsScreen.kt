package com.geozelot.homer.ui.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
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
    val log by produceState(initialValue = stringResource(R.string.diag_reading_logs), reloadKey) {
        value = captureLog()
    }

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
            Text(stringResource(R.string.diag_title), style = SerifTitle.copy(fontSize = 22.sp), color = Parchment, modifier = Modifier.padding(start = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HomerTextButton(onClick = { clipboard.setText(AnnotatedString(log)) }) { Text(stringResource(R.string.action_copy)) }
            HomerTextButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log)
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.diag_share_chooser)))
            }) { Text(stringResource(R.string.action_share)) }
            HomerTextButton(onClick = { reloadKey++ }) { Text(stringResource(R.string.action_refresh)) }
        }
        Text(
            stringResource(R.string.diag_warning),
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

/**
 * Reads the app's own recent logcat (no permission needed for own-process logs), filtered to
 * Homer's own tags at every level plus warnings/errors from anything else — so the useful lines
 * (scan/download/sync progress, WorkManager failures, crashes) aren't buried under framework noise.
 *
 * [NOISY_TAGS] is silenced outright. Creating a media codec emits ~15 warning lines that carry no
 * information we can act on, and a metadata sweep creates one per file: a 1200-line window filled
 * up in seconds and held ZERO Homer lines, which is exactly when the log is wanted. Silencing them
 * is what makes this screen usable during a scan or a length pass.
 */
private suspend fun captureLog(): String = withContext(Dispatchers.IO) {
    // Later specs win, so the silences must follow the `*:W` default they override.
    val filterSpec = HOMER_TAGS.map { "$it:V" } + "*:W" + NOISY_TAGS.map { "$it:S" }
    runCatching {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "time", "-t", "2000") + filterSpec.toTypedArray(),
        )
        redact(process.inputStream.bufferedReader().use { it.readText() })
    }.getOrElse { "Could not read logs: ${it.message}" }
        .ifBlank { "No log lines captured. Reproduce the problem, then tap Refresh." }
}

/**
 * Masks the two identifiers that shouldn't leave the device when a log is copied/shared: the
 * Nextcloud account name (in WebDAV paths) and the server scheme+host of any URL. Book/folder
 * names in the path portion are left in place (often needed to diagnose) — the UI warns about them.
 */
private fun redact(raw: String): String = raw
    .replace(Regex("""(/remote\.php/dav/files/)[^/\s]+"""), "$1<account>")
    .replace(Regex("""https?://[^/\s"']+"""), "https://<server>")

private val HOMER_TAGS = listOf(
    "HomerAuth", "HomerScan", "HomerMeta", "HomerDownload",
    "HomerStore", "HomerSync", "HomerPlay", "HomerNet",
)

/**
 * Framework tags that log at W/E per media codec instance and drown everything else out.
 *
 * `.geozelot.homer` is not one of Homer's own tags — it is the native media stack tagging with the
 * truncated process name ("Failed to query component interface..."). Homer's Kotlin logging only
 * ever uses [HOMER_TAGS], so silencing it loses nothing of ours.
 */
private val NOISY_TAGS = listOf(
    "Codec2Client", "CCodec", "CCodecConfig", "CCodecBufferChannel",
    "LoudnessCodecController", "MediaCodec", "MessageQueue", ".geozelot.homer",
)
