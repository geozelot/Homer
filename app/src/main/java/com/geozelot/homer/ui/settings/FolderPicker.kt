package com.geozelot.homer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Studio

/**
 * Browses the library's folders so a template's scope can be picked rather than typed.
 *
 * **Built from the book paths already in the database**, not from the server. Every book's id IS its
 * library-relative folder path, so the tree is a fold over a list Homer already holds: no requests,
 * no waiting, and it works offline. It also shows exactly the folders that contain books, which is
 * the only thing a scope can usefully name — a folder holding nothing would be a rule about nothing.
 *
 * Typing the path by hand was the alternative, and a scope has to match the stored path exactly:
 * one wrong space or a guessed capital and the rule silently covers no books, with nothing on screen
 * to say why.
 */

/** One level of the tree: the folders directly under [path], and how many books sit beneath each. */
internal data class FolderLevel(val path: String, val children: List<Pair<String, Int>>)

/**
 * The folders directly under [prefix], with a book count for each subtree.
 *
 * Counts are of the whole subtree rather than the immediate folder, because that is the number that
 * answers "is this the level I mean" — a scope covers everything beneath it.
 */
internal fun levelOf(bookIds: List<String>, prefix: String): FolderLevel {
    val clean = prefix.trim('/')
    val depth = if (clean.isEmpty()) 0 else clean.split('/').size
    val counts = LinkedHashMap<String, Int>()
    for (id in bookIds) {
        val segments = id.trim('/').split('/')
        // The book's own folder is everything but the last segment, which is the book itself.
        if (segments.size <= depth + 1) continue
        if (clean.isNotEmpty() && !id.trim('/').startsWith("$clean/", ignoreCase = true)) continue
        val child = segments[depth]
        counts[child] = (counts[child] ?: 0) + 1
    }
    return FolderLevel(clean, counts.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value })
}

/**
 * Picks a folder from the library tree.
 *
 * [onPick] receives a library-relative path, or "" for the whole library — which is a real answer
 * rather than a cancel, since an unscoped pattern is the common case.
 */
@Composable
fun FolderPickerDialog(
    bookIds: List<String>,
    initialPath: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var here by remember { mutableStateOf(initialPath.trim('/')) }
    val level = remember(bookIds, here) { levelOf(bookIds, here) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_picker_title)) },
        text = {
            Column {
                // Where you are, always visible, in the same monospace the pattern field uses so
                // the two read as the same kind of thing.
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
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (here.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { here = here.substringBeforeLast('/', "") }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint = Muted,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(stringResource(R.string.folder_picker_up), color = Muted, fontSize = 12.sp)
                        }
                    }
                    if (level.children.isEmpty()) {
                        Text(
                            stringResource(R.string.folder_picker_leaf),
                            color = Faint,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    level.children.forEach { (name, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { here = if (here.isEmpty()) name else "$here/$name" }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = Faint,
                                modifier = Modifier.size(14.dp).padding(end = 0.dp),
                            )
                            Text(
                                name,
                                color = Parchment,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            Text(
                                pluralStringResource(R.plurals.sync_books_count, count, count),
                                color = Faint,
                                fontSize = 10.sp,
                            )
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
        },
        confirmButton = {
            HomerTextButton(onClick = { onPick(here) }) {
                Text(
                    stringResource(R.string.folder_picker_use),
                    color = Amber,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = Muted) }
        },
    )
}
