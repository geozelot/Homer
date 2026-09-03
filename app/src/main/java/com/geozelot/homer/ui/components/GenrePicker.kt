package com.geozelot.homer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.data.metadata.BookGenre
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface2

/**
 * Picks a book's genres out of Homer's own vocabulary.
 *
 * Replaces the free-text field, which is what let two people file one book on two shelves by
 * spelling its genre in two languages. There is nothing to type here — [BookGenre] is closed, so the
 * only genres that can be *chosen* are ones with a translated label and a stable key.
 *
 * ## Order is the selection, and the first one is the shelf
 *
 * A book can carry several genres and every one of them filters, but only one can be the heading it
 * shelves under — otherwise the genre shelf stops being a partition. That one is the first, so it has
 * to be changeable without clearing the lot: **tapping a chip promotes it to first**, and its ✕
 * removes it. Dragging to reorder would be the obvious answer and is not worth a drag handler for a
 * list that is rarely more than three long.
 *
 * ## Genres the vocabulary does not know
 *
 * A book can arrive carrying anything its file tags said — `Blues`, `Hörbuchmagazin`. Those show as
 * chips and can be removed, but they are not in the list and cannot be re-added, which is exactly
 * what a closed vocabulary means. They are marked apart rather than silently mixed in, so the chip
 * that behaves differently also looks different.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenrePickerField(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.edit_field_genre),
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        if (selected.isEmpty()) {
            Text(stringResource(R.string.edit_genre_none), color = Faint, fontSize = 13.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                selected.forEachIndexed { index, value ->
                    val known = BookGenre.resolve(value) != null
                    GenreChip(
                        label = BookGenre.display(value, locale),
                        primary = index == 0,
                        known = known,
                        onPromote = { onChange(listOf(value) + (selected - value)) },
                        onRemove = { onChange(selected - value) },
                    )
                }
            }
            if (selected.size > 1) {
                Text(
                    stringResource(R.string.edit_genre_primary_note),
                    color = Faint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (selected.any { BookGenre.resolve(it) == null }) {
                Text(
                    stringResource(R.string.edit_genre_tag_note),
                    color = Faint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (open) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(if (open) R.string.action_done else R.string.edit_genre_add),
                color = Amber,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (!open) return@Column

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.edit_genre_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
        // Matched on the LABEL, in the reader's language, because that is the word they are looking
        // for — and additionally on the key, so somebody who has seen `scifi` in a published file can
        // find it too.
        val folded = BookGenre.fold(query)
        for (shelf in BookGenre.Shelf.entries) {
            val matches = BookGenre.offered(shelf, locale).filter {
                folded.isEmpty() ||
                    BookGenre.fold(it.label(locale)).contains(folded) ||
                    it.key.contains(folded)
            }
            if (matches.isEmpty()) continue
            Text(
                stringResource(
                    if (shelf == BookGenre.Shelf.FICTION) {
                        R.string.genre_shelf_fiction
                    } else {
                        R.string.genre_shelf_nonfiction
                    },
                ),
                color = Faint,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            for (genre in matches) {
                val chosen = genre.key in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Appended rather than inserted: a newly chosen genre is not a claim
                            // about which shelf the book belongs on, and promoting is a separate tap.
                            onChange(if (chosen) selected - genre.key else selected + genre.key)
                        }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (chosen) Amber else Line,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        genre.label(locale),
                        color = if (chosen) Parchment else Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * One chosen genre.
 *
 * [primary] is the one the book shelves under and wears the accent, so "which of these is the
 * heading" is answered by looking rather than by remembering that it is the first. [known] is false
 * for a genre that came out of a file tag and is not in the vocabulary — dimmer, because it can be
 * removed but never chosen again.
 */
@Composable
private fun GenreChip(
    label: String,
    primary: Boolean,
    known: Boolean,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) AmberSoft else Surface2)
            .border(1.dp, if (primary) Amber else Line, RoundedCornerShape(999.dp))
            .clickable(enabled = !primary, onClick = onPromote)
            .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = when {
                primary -> Amber
                known -> Parchment
                else -> Muted
            },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.action_remove),
            tint = if (primary) Amber else Muted,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(16.dp)
                .clickable(onClick = onRemove),
        )
    }
}
