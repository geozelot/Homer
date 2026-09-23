package com.geozelot.homer.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.library.authorsToInput
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.components.GenrePickerField
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted

/**
 * Edits the shelf-level fields — name, author, genre — and pushes them to every book on the shelf.
 *
 * **It has to know whether it is looking at a series or a collection.** The version before this did
 * not: one field labelled "Series", prefilled with [LibraryEntry.Series.name], which on a collection
 * card is the collection's own name. Saving wrote it into every member's `series`, so editing
 * Discworld overwrote Rincewind, the Watch and the witches in one action and the shelf redrew as a
 * collection holding a single sub-series named after itself.
 *
 * A collection therefore gets ONE name field, labelled Collection, and no series field at all — the
 * sub-series inside it are per-book facts and are not this dialog's business. A plain series gets
 * its name plus the collection it belongs to, as before.
 */
@Composable
internal fun ShelfEditDialog(
    series: LibraryEntry.Series,
    onSave: (name: String, author: String, genres: List<String>, collection: String) -> Unit,
    /** Opens the template editor scoped to the folder this shelf's books share. Null for a reader. */
    onReadFolderDifferently: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val namesCollection = series.isCollection
    // rememberSaveable, like the book dialog: a rotation used to throw away what was typed.
    var name by rememberSaveable { mutableStateOf(series.name) }
    var author by rememberSaveable { mutableStateOf(authorsToInput(series.authors)) }
    // Prefilled only when the whole shelf already agrees. Showing one member's genre would make
    // Save quietly impose it on the rest, and blank means "leave it to detection" — so a shelf that
    // disagrees with itself starts empty and the user is choosing, not confirming.
    // The whole LIST, comma-separated, so a two-genre series prefills with both rather than losing
    // the second the moment somebody presses Save.
    // Prefilled only when the whole shelf already agrees, on the same rule as the author above and
    // for the same reason: a shelf that disagrees with itself starts empty, so Save is a choice
    // rather than an accidental vote for whichever member happened to be first.
    var genres by rememberSaveable {
        mutableStateOf(series.books.map { it.genres }.distinct().singleOrNull().orEmpty())
    }
    // Same rule again, and unused on a collection — there, `name` IS the collection.
    var collection by rememberSaveable {
        mutableStateOf(series.books.map { it.collection }.distinct().singleOrNull().orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (namesCollection) R.string.home_collection_edit_title else R.string.home_series_edit_title,
                ),
            )
        },
        text = {
            // Scrollable: a dialog resizes for the keyboard, and without this the second field
            // gets crushed or clipped once the IME is up (EditBookDialog already does this).
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    pluralStringResource(
                        if (namesCollection) {
                            R.plurals.home_collection_edit_desc
                        } else {
                            R.plurals.home_series_edit_desc
                        },
                        series.books.size,
                        series.books.size,
                    ),
                    color = Muted,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(
                            stringResource(
                                if (namesCollection) R.string.edit_field_collection else R.string.edit_field_series,
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.edit_field_author)) },
                    // A semicolon separates people; a comma does not, because a name may contain
                    // one. See AuthorList.kt.
                    placeholder = { Text(stringResource(R.string.edit_authors_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                GenrePickerField(
                    selected = genres,
                    onChange = { genres = it },
                    modifier = Modifier.padding(top = 12.dp),
                )
                // Only for a plain series. On a collection there is no level above to join, and
                // offering the field would invite exactly the confusion that caused the bug.
                if (!namesCollection) {
                    OutlinedTextField(
                        value = collection,
                        onValueChange = { collection = it },
                        label = { Text(stringResource(R.string.edit_field_collection)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.edit_field_collection_desc),
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                // The shelf's own way into the template editor, scoped to the folder its books
                // share. It used to live on the shelf's DETAILS card; moving the book one to Edit
                // and not this one would have left a whole series or collection — the case a
                // pattern is most worth writing for — with no way to reach it at all.
                onReadFolderDifferently?.let { open ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Line)
                    HomerTextButton(onClick = open, contentPadding = SettingsActionPadding) {
                        Icon(
                            Icons.Filled.Rule,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.size(7.dp))
                        Text(stringResource(R.string.details_read_folder), color = Amber, fontSize = 12.sp)
                    }
                    SettingsExplanation(stringResource(R.string.edit_read_folder_desc))
                }
            }
        },
        confirmButton = {
            HomerTextButton(onClick = { onSave(name, author, genres, collection) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
