package com.geozelot.homer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.library.IgnoredFolders
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Studio

/**
 * The folders Homer does not read, and the way to add one — see [IgnoredFolders].
 *
 * First on the folder-reading page, ahead of the patterns: what is read at all comes before how it
 * is read. Changes apply at once and are shared, unlike the patterns' draft and Apply — ignoring a
 * folder rewrites nothing, so there is nothing to preview.
 */
@Composable
internal fun IgnoredFoldersSection(
    ignored: List<String>,
    listFolders: suspend (String) -> List<String>?,
    onIgnore: (String) -> Unit,
    onStopIgnoring: (String) -> Unit,
) {
    var browsing by remember { mutableStateOf(false) }

    SettingsSectionHeader(stringResource(R.string.set_ignore_header))
    SettingsExplanation(stringResource(R.string.set_ignore_lead))
    if (ignored.isEmpty()) {
        SettingsNote(stringResource(R.string.set_ignore_none))
    }
    for (folder in ignored) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.FolderOff, contentDescription = null, tint = Faint, modifier = Modifier.size(16.dp))
            Text(
                folder,
                color = Parchment,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            IconButton(onClick = { onStopIgnoring(folder) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.set_ignore_remove_cd, folder),
                    tint = Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    HomerTextButton(onClick = { browsing = true }, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(R.string.set_ignore_add), color = Amber, fontSize = 13.sp)
    }
    SettingsNote(stringResource(R.string.set_ignore_next_scan), modifier = Modifier.padding(top = 6.dp))

    if (browsing) {
        ServerFolderPickerDialog(
            ignored = ignored,
            listFolders = listFolders,
            onPick = { onIgnore(it); browsing = false },
            onDismiss = { browsing = false },
        )
    }
}

/** What one folder listing came back as. */
private sealed interface Listing {
    data object Loading : Listing
    data object Failed : Listing
    data class Ready(val folders: List<String>) : Listing
}

/**
 * Browses the library's folders ON THE SERVER, to pick one to ignore.
 *
 * Not [FolderPickerDialog], which is built from the books already indexed: the folders worth
 * ignoring are exactly the ones the index has never seen. So this asks the server, one level at a
 * time, and needs the network to do it.
 */
@Composable
private fun ServerFolderPickerDialog(
    ignored: List<String>,
    listFolders: suspend (String) -> List<String>?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var here by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    val listing by produceState<Listing>(Listing.Loading, here, attempt) {
        value = Listing.Loading
        value = listFolders(here)?.let { Listing.Ready(it) } ?: Listing.Failed
    }
    fun child(name: String) = if (here.isEmpty()) name else "$here/$name"
    val hereIgnored = IgnoredFolders.covers(ignored, here) && here.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ignore_picker_title)) },
        text = {
            Column {
                Text(
                    here.ifBlank { stringResource(R.string.folder_picker_root) },
                    color = Parchment,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Studio)
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (here.isNotEmpty()) {
                        PickerRow(onClick = { here = here.substringBeforeLast('/', "") }) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                            Text(
                                stringResource(R.string.folder_picker_up),
                                color = Muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    when (val current = listing) {
                        Listing.Loading -> Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Amber, strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.ignore_picker_loading),
                                color = Faint,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                        Listing.Failed -> Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.ignore_picker_failed),
                                color = Muted,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            HomerTextButton(onClick = { attempt++ }) { Text(stringResource(R.string.action_retry)) }
                        }
                        is Listing.Ready -> {
                            if (current.folders.isEmpty()) {
                                Text(
                                    stringResource(R.string.ignore_picker_empty),
                                    color = Faint,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                            for (name in current.folders) {
                                val isIgnored = IgnoredFolders.covers(ignored, child(name))
                                PickerRow(onClick = { here = child(name) }) {
                                    Icon(
                                        if (isIgnored) Icons.Filled.FolderOff else Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = Faint,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        name,
                                        color = if (isIgnored) Muted else Parchment,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    )
                                    if (isIgnored) {
                                        Text(stringResource(R.string.ignore_picker_ignored), color = Faint, fontSize = 10.sp)
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Line,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Not the root: ignoring the whole library would leave nothing to read. And not a folder
            // that is already ignored, or inside one — that says nothing new.
            HomerTextButton(onClick = { onPick(here) }, enabled = here.isNotEmpty() && !hereIgnored) {
                Text(
                    stringResource(R.string.ignore_picker_use),
                    color = if (here.isNotEmpty() && !hereIgnored) Amber else Faint,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = Muted) }
        },
    )
}

@Composable
private fun PickerRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) { content() }
    }
}
