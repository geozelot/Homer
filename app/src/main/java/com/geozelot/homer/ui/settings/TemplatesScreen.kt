package com.geozelot.homer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.library.TemplateApplier
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Sage
import com.geozelot.homer.ui.theme.Studio

/**
 * How Homer reads a book's details out of its folder path — and how to tell it something different.
 *
 * The conventional layout is `Author/Series/Book`, and it is not written here because it is not
 * editable: it is what a library with no templates gets, and it stays behind whatever is added, so
 * a pattern only has to describe the folders it is actually about.
 *
 * **The preview is the feature.** Applying rewrites metadata across a whole library in one action,
 * and a template that quietly mis-parses is far worse than no template at all — so nothing is
 * written until Apply, and Apply sits under a list of real books showing exactly what it would do
 * to them. Changed books are shown first, because a preview whose visible rows all say "no change"
 * hides the one row that says the pattern has mangled a title.
 */
@Composable
fun TemplatesScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft by viewModel.templateDraft.collectAsStateWithLifecycle()
    val dirty by viewModel.templateDraftDirty.collectAsStateWithLifecycle()
    val preview by viewModel.templatePreview.collectAsStateWithLifecycle()
    val paths by viewModel.libraryPaths.collectAsStateWithLifecycle()

    // Which row's folder is being picked, by index — null when the picker is closed.
    var picking by remember { mutableStateOf<Int?>(null) }
    val focus by viewModel.templateFocus.collectAsStateWithLifecycle()

    picking?.let { index ->
        val line = draft.getOrNull(index).orEmpty()
        FolderPickerDialog(
            bookIds = paths,
            initialPath = if ('\t' in line) line.substringBefore('\t') else "",
            onPick = { chosen ->
                val pattern = line.substringAfter('\t')
                val joined = if (chosen.isBlank()) pattern else "$chosen\t$pattern"
                viewModel.setTemplateDraft(draft.toMutableList().also { it[index] = joined })
                picking = null
            },
            onDismiss = { picking = null },
        )
    }

    SettingsScaffold(stringResource(R.string.set_templates_title), onBack, modifier) {
        SettingsSectionHeader(stringResource(R.string.set_templates_patterns_header))
        SettingsExplanation(stringResource(R.string.set_templates_lead))

        draft.forEachIndexed { index, line ->
            // The stored line is `scope\tpattern`, or just the pattern when it applies everywhere.
            val scope = if ('\t' in line) line.substringBefore('\t') else ""
            val pattern = line.substringAfter('\t')
            TemplateRow(
                scope = scope,
                pattern = pattern,
                focused = focus == index,
                onFocus = { viewModel.focusTemplateRow(if (it) index else null) },
                onBrowse = { picking = index },
                onEdit = { newScope, newPattern ->
                    val joined = if (newScope.isBlank()) newPattern else "${newScope.trim('/')}\t$newPattern"
                    viewModel.setTemplateDraft(draft.toMutableList().also { it[index] = joined })
                },
                onRemove = { viewModel.setTemplateDraft(draft.toMutableList().also { it.removeAt(index) }) },
            )
        }

        HomerTextButton(onClick = { viewModel.setTemplateDraft(draft + "") }) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.set_templates_add),
                color = Amber,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        SettingsNote(stringResource(R.string.set_templates_fields))
        SettingsNote(stringResource(R.string.set_templates_scope_desc))

        SettingsDivider()

        SettingsSectionHeader(stringResource(R.string.set_templates_preview_header))
        if (preview.isEmpty()) {
            SettingsExplanation(stringResource(R.string.set_templates_preview_empty))
        } else {
            SettingsExplanation(
                if (focus != null && draft.getOrNull(focus!!)?.contains('\t') == true) {
                    stringResource(
                        R.string.set_templates_preview_scoped,
                        draft[focus!!].substringBefore('\t'),
                    )
                } else {
                    stringResource(R.string.set_templates_preview_lead)
                },
            )
            preview.forEach { row -> PreviewRow(row) }
        }

        SettingsDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            HomerTextButton(onClick = viewModel::applyTemplates, enabled = dirty) {
                Text(
                    stringResource(R.string.set_templates_apply),
                    color = if (dirty) Amber else Faint,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (dirty) {
                HomerTextButton(onClick = viewModel::discardTemplateDraft) {
                    Text(stringResource(R.string.action_reset), color = Muted)
                }
            }
        }
        SettingsExplanation(stringResource(R.string.set_templates_apply_desc))
    }
}

/**
 * One editable rule: which folder it applies to, and the pattern.
 *
 * Monospaced, because the braces and the literals between them are the point. The two are stored as
 * one tab-separated line, which is why neither field can contain a tab and why the split happens
 * here rather than being something the user has to type.
 *
 * The folder is optional and blank means the whole library, which is the common case — so it sits
 * above the pattern in smaller type rather than demanding an answer first.
 */
@Composable
private fun TemplateRow(
    scope: String,
    pattern: String,
    focused: Boolean,
    onFocus: (Boolean) -> Unit,
    onBrowse: () -> Unit,
    onEdit: (scope: String, pattern: String) -> Unit,
    onRemove: () -> Unit,
) {
    val mono = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = Parchment,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = scope,
                onValueChange = { onEdit(it, pattern) },
                singleLine = true,
                label = { Text(stringResource(R.string.set_templates_scope), fontSize = 11.sp) },
                placeholder = {
                    Text(
                        stringResource(R.string.set_templates_scope_all),
                        color = Faint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                },
                // Browsable, because a scope has to match the stored path EXACTLY: one guessed
                // capital or stray space and the rule silently covers no books, with nothing on
                // screen to say why.
                trailingIcon = {
                    IconButton(onClick = onBrowse) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = stringResource(R.string.folder_picker_title),
                            tint = Muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                textStyle = mono,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocus(true) },
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = { onEdit(scope, it) },
                singleLine = true,
                label = { Text(stringResource(R.string.set_templates_pattern), fontSize = 11.sp) },
                placeholder = {
                    Text("{author}/{title}", color = Faint, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                },
                textStyle = mono,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .onFocusChanged { if (it.isFocused) onFocus(true) },
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_clear),
                tint = Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * One book, before and after.
 *
 * Only the fields that would CHANGE are listed. A row restating six unchanged fields buries the one
 * that moved, and the reader is checking for damage rather than reading a record.
 */
@Composable
private fun PreviewRow(row: TemplateApplier.Preview) {
    val changes = row.before.differencesFrom(row.after)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Studio)
            .border(1.dp, Line, RoundedCornerShape(6.dp))
            .padding(9.dp),
    ) {
        Text(row.id, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
        if (changes.isEmpty()) {
            Text(
                stringResource(R.string.set_templates_no_change),
                color = Sage,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            changes.forEach { (label, from, to) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(label, color = Faint, fontSize = 10.sp, modifier = Modifier.padding(top = 1.dp))
                    // The old value struck through in the destructive colour, because that is what
                    // Apply is going to do to it.
                    Text(
                        from ?: "—",
                        color = Danger,
                        fontSize = 11.sp,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    )
                    Text("→", color = Faint, fontSize = 11.sp)
                    Text(to ?: "—", color = Parchment, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The fields a template would change, as (label, from, to). */
@Composable
private fun BookEntity.differencesFrom(after: BookEntity): List<Triple<String, String?, String?>> {
    val out = mutableListOf<Triple<String, String?, String?>>()
    fun add(label: String, a: String?, b: String?) {
        if (a != b) out += Triple(label, a, b)
    }
    add(stringResource(R.string.edit_field_title), title, after.title)
    add(stringResource(R.string.edit_field_author), author, after.author)
    add(stringResource(R.string.edit_field_series), series, after.series)
    add(stringResource(R.string.edit_field_series_index), seriesIndex?.toString(), after.seriesIndex?.toString())
    add(stringResource(R.string.edit_field_collection), collection, after.collection)
    add(stringResource(R.string.edit_field_genre), genre, after.genre)
    add(stringResource(R.string.edit_field_language), language, after.language)
    return out
}
