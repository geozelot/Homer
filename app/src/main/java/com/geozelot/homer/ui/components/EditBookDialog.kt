package com.geozelot.homer.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface2

/** A small selectable chip for the tri-state "on play" mode in the edit dialog. */
@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Amber else Parchment,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, if (selected) Amber else Line, RoundedCornerShape(50))
            .background(if (selected) AmberSoft else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * The minimal, screen-agnostic view of a book that the edit dialog needs, so both the library
 * and the player can open the same dialog without depending on either screen's row model. All
 * fields are the *effective* (override-applied) values shown as editable defaults.
 */
data class EditableBook(
    val id: String,
    val title: String,
    val author: String?,
    val series: String?,
    val seriesIndex: Int?,
    val genre: String?,
    val tags: List<String>,
    val hidden: Boolean,
    val finished: Boolean,
    val hasCustomCover: Boolean,
    /** Per-book play mode override: null = follow global, true = download on play, false = stream. */
    val downloadOnPlay: Boolean?,
)

/**
 * Edits a book's metadata override (title/author/series/genre/tags), finished + hidden flags, and
 * custom cover. Blank fields revert to detection; Reset clears the whole override. Shared between
 * the library screen (long-press) and the player overflow.
 */
@Composable
fun EditBookDialog(
    book: EditableBook,
    onSave: (title: String, author: String, series: String, index: String, genre: String, tags: String, hidden: Boolean, finishedChange: Boolean?, downloadOnPlay: Boolean?) -> Unit,
    onReset: () -> Unit,
    onPickCover: (Uri) -> Unit,
    onClearCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPickCover(uri) }
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author.orEmpty()) }
    var series by remember { mutableStateOf(book.series.orEmpty()) }
    var index by remember { mutableStateOf(book.seriesIndex?.toString().orEmpty()) }
    var genre by remember { mutableStateOf(book.genre.orEmpty()) }
    var tags by remember { mutableStateOf(book.tags.joinToString(", ")) }
    var hidden by remember { mutableStateOf(book.hidden) }
    // Tri-state finished: the switch starts at the effective value; only a change from that value
    // forces the flag (null preserves the existing auto/forced state on save).
    val initialFinished = book.finished
    var finished by remember { mutableStateOf(initialFinished) }
    // Per-book play mode: null = follow the global setting, true = download on play, false = stream.
    var playMode by remember { mutableStateOf(book.downloadOnPlay) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit book") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = series,
                    onValueChange = { series = it },
                    label = { Text("Series") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = index,
                    onValueChange = { index = it },
                    label = { Text("Series #") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags") },
                    placeholder = { Text("comma, separated") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mark as finished", fontSize = 14.sp)
                    HomerSwitch(checked = finished, onCheckedChange = { finished = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Hide from library", fontSize = 14.sp)
                    HomerSwitch(checked = hidden, onCheckedChange = { hidden = it })
                }
                Text("On play", fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Default", playMode == null) { playMode = null }
                    ModeChip("Download", playMode == true) { playMode = true }
                    ModeChip("Stream", playMode == false) { playMode = false }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        pickCover.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }) { Text(if (book.hasCustomCover) "Change cover" else "Choose cover") }
                    if (book.hasCustomCover) {
                        TextButton(onClick = onClearCover) { Text("Clear cover") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finishedChange = if (finished != initialFinished) finished else null
                onSave(title, author, series, index, genre, tags, hidden, finishedChange, playMode)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
