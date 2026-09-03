package com.geozelot.homer.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.data.library.genresToInput
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface2

/** A small selectable chip for the tri-state "on play" mode in the edit dialog. */
@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // The pill stays compact; the tap area around it is raised to the 48dp minimum.
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Amber else Parchment,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, if (selected) Amber else Line, RoundedCornerShape(50))
                .background(if (selected) AmberSoft else Surface2)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
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
    /** Parent grouping above the series. Editable per book, so a STANDALONE can join one. */
    val collection: String?,
    /** Position within that collection — Discworld #12, independent of the sub-series' own count. */
    val collectionIndex: Int?,
    /** Every genre, primary first. Shown and typed as a comma-separated list, like the tags. */
    val genres: List<String>,
    val language: String?,
    val tags: List<String>,
    val hidden: Boolean,
    val hasCustomCover: Boolean,
    /** Per-book play mode override: null = follow global, true = download on play, false = stream. */
    val downloadOnPlay: Boolean?,
)

/**
 * Edits a book's metadata override (title/author/series/genre/tags), the hidden flag, and the
 * custom cover. Blank fields revert to detection; Reset clears the whole override. Shared between
 * the library screen (long-press) and the player overflow.
 */
@Composable
fun EditBookDialog(
    book: EditableBook,
    onSave: (
        title: String,
        author: String,
        series: String,
        index: String,
        collection: String,
        collectionIndex: String,
        genre: String,
        language: String,
        tags: String,
        hidden: Boolean,
        downloadOnPlay: Boolean?,
    ) -> Unit,
    onReset: () -> Unit,
    onPickCover: (Uri) -> Unit,
    onClearCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPickCover(uri) }
    // rememberSaveable throughout: these are drafts the user is typing, and plain remember threw
    // them away (along with the dialog) on every rotation. They deliberately have no key on [book]
    // either — the caller re-derives that row from the live library on each recomposition, and a
    // key would reset the drafts whenever anything about the book changed underneath.
    var title by rememberSaveable { mutableStateOf(book.title) }
    var author by rememberSaveable { mutableStateOf(book.author.orEmpty()) }
    var series by rememberSaveable { mutableStateOf(book.series.orEmpty()) }
    var collection by rememberSaveable { mutableStateOf(book.collection.orEmpty()) }
    var index by rememberSaveable { mutableStateOf(book.seriesIndex?.toString().orEmpty()) }
    var collectionIndex by rememberSaveable { mutableStateOf(book.collectionIndex?.toString().orEmpty()) }
    // Labels, not the stored keys — see `genresToInput`. Typing any recognised spelling in any
    // language canonicalises back on save, so what is shown here is safe to edit freely.
    val configuration = LocalConfiguration.current
    var genre by rememberSaveable {
        mutableStateOf(genresToInput(book.genres, configuration.locales[0]))
    }
    var language by rememberSaveable { mutableStateOf(book.language.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(book.tags.joinToString(", ")) }
    var hidden by rememberSaveable { mutableStateOf(book.hidden) }
    // Per-book play mode: null = follow the global setting, true = download on play, false = stream.
    var playMode by rememberSaveable { mutableStateOf(book.downloadOnPlay) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.edit_field_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = series,
                    onValueChange = { series = it },
                    label = { Text(stringResource(R.string.edit_field_series)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = index,
                    onValueChange = { index = it },
                    label = { Text(stringResource(R.string.edit_field_series_index)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                // Per book as well as per series, because a book with no series at all can still
                // belong to a collection — a Discworld novel in none of its threads is exactly that,
                // and the series dialog cannot reach it.
                OutlinedTextField(
                    value = collection,
                    onValueChange = { collection = it },
                    label = { Text(stringResource(R.string.edit_field_collection)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                // Directly under its collection, mirroring how the series index sits under the
                // series. A book's place in the parent grouping is a different number from its place
                // in the sub-series — Discworld counts every novel, "Die Hexen" counts only its own
                // — and without this field the second of the two could be stated and the first
                // could not.
                OutlinedTextField(
                    value = collectionIndex,
                    onValueChange = { collectionIndex = it },
                    label = { Text(stringResource(R.string.edit_field_collection_index)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = language,
                    // A free field rather than a picker: the codes are short, the set is open, and
                    // a picker would have to enumerate every language somebody might own a book in.
                    onValueChange = { language = it },
                    label = { Text(stringResource(R.string.edit_field_language)) },
                    placeholder = { Text(stringResource(R.string.edit_language_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text(stringResource(R.string.edit_field_genre)) },
                    placeholder = { Text(stringResource(R.string.edit_genre_placeholder)) },
                    supportingText = { Text(stringResource(R.string.edit_field_genre_desc)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.edit_field_tags)) },
                    placeholder = { Text(stringResource(R.string.edit_tags_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.edit_hide_from_library), fontSize = 14.sp)
                    HomerSwitch(checked = hidden, onCheckedChange = { hidden = it })
                }
                Text(stringResource(R.string.edit_on_play), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(stringResource(R.string.edit_mode_default), playMode == null) { playMode = null }
                    ModeChip(stringResource(R.string.edit_mode_download), playMode == true) { playMode = true }
                    ModeChip(stringResource(R.string.edit_mode_stream), playMode == false) { playMode = false }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomerTextButton(onClick = {
                        pickCover.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }) { Text(if (book.hasCustomCover) stringResource(R.string.edit_change_cover) else stringResource(R.string.edit_choose_cover)) }
                    if (book.hasCustomCover) {
                        HomerTextButton(onClick = onClearCover) { Text(stringResource(R.string.edit_clear_cover)) }
                    }
                }
            }
        },
        confirmButton = {
            HomerTextButton(onClick = {
                onSave(
                    title, author, series, index, collection, collectionIndex,
                    genre, language, tags, hidden, playMode,
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                HomerTextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
                HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
