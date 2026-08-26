package com.geozelot.homer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.Surface1
import com.geozelot.homer.ui.theme.Surface2

/**
 * The app's standard toggle. Uses an explicit off-state palette (muted thumb + border on a raised
 * track) because Material's default unchecked colors nearly vanish against the candlelit dark
 * ground; on is the amber accent. Shared so every toggle in the app reads the same.
 */
@Composable
fun HomerSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = OnAmber,
            checkedTrackColor = Amber,
            checkedBorderColor = Amber,
            uncheckedThumbColor = Muted,
            uncheckedTrackColor = Surface2,
            uncheckedBorderColor = Muted,
        ),
    )
}

// ── Shared settings-row vocabulary ───────────────────────────────────────────
// Every row here puts its leading text in a `weight(1f, fill = false)` child. A Row measures
// unweighted children first at the full available width, so an unweighted label that wraps (long
// text, large font scale, narrow screen) leaves the trailing control measured at zero width —
// invisible and untappable. Weighting the label is the fix, and it must not be undone.

/** An all-caps section header, e.g. "WHERE DOWNLOADS ARE KEPT". */
@Composable
fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = SectionLabel,
        color = Muted,
        modifier = modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

/** A settings row: a weighted leading label (plus optional summary) and a trailing control. */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)) {
            Text(label, color = if (enabled) Parchment else Faint, fontSize = 14.sp)
            summary?.let {
                Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing()
    }
}

/** A labelled switch row, with an optional explanation underneath. */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsRow(label = label, enabled = enabled) {
            HomerSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
        description?.let { SettingsExplanation(it) }
    }
}

/** The small muted paragraph that explains what a setting does. */
@Composable
fun SettingsExplanation(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Muted,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/** A dimmer aside — footnotes, caveats, "why is this disabled" notes. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Faint,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/** A tappable settings row that navigates elsewhere (label + optional summary + chevron). */
@Composable
fun SettingsNavRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        label = label,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        // The chevron dims with the row. Left at full strength it reads as a live affordance on a
        // row that will not respond to it.
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) Muted else Faint,
        )
    }
}

/** A settings row whose trailing control picks one of [options]. */
@Composable
fun <T> SettingsDropdownRow(
    label: String,
    chipLabel: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    onCustom: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsRow(label = label) {
            DropdownChip(
                label = chipLabel,
                options = options,
                selected = selected,
                labelOf = labelOf,
                onSelect = onSelect,
                onCustom = onCustom,
            )
        }
        description?.let { SettingsExplanation(it) }
    }
}

/** A compact bordered pill that opens a menu of [options]. */
@Composable
fun <T> DropdownChip(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The part of [label] that is the current setting, emphasised so the chip weights what changes
     * the same way the open menu bolds the selected row.
     *
     * A substring of the already-formatted label rather than a second piece to append: the
     * separator between the two belongs to the translator, not to this function. If a locale
     * renders the value in a form that isn't in the label verbatim, the span simply isn't applied
     * and the chip reads as plain text.
     */
    value: String? = null,
    /**
     * Appends a "Custom…" item below the presets. Present only where a free value is meaningful —
     * a preset list is a shortcut, not the limit of what the setting can hold, and the presets that
     * suit most people are not the ones that suit everybody.
     */
    onCustom: (() -> Unit)? = null,
    /**
     * Replaces the category words with a glyph — "Shelve: Author" becomes an icon and "Author".
     *
     * The library's control bar carries four of these side by side, and repeating the category in
     * every one of them is what pushed the row onto a second line on a narrower screen. The
     * category is the constant; the value is the part that changes and the part worth reading. With
     * an icon set, the label is the value alone and is emphasised throughout, since there is no
     * longer a prefix to distinguish it from.
     *
     * [iconDescription] carries what the glyph replaces, for anyone who cannot see it.
     */
    icon: ImageVector? = null,
    iconDescription: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        // The pill is ~26dp tall, well under the 48dp minimum touch target, so the tap area is
        // expanded around it — inflating the pill itself would break its alignment with the 11sp
        // label text it sits beside.
        Box(
            modifier = Modifier
                .sizeIn(minHeight = 48.dp)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Line, RoundedCornerShape(8.dp))
                    .background(Surface1)
                    .padding(start = if (icon != null) 7.dp else 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = iconDescription,
                        tint = Muted,
                        modifier = Modifier.size(14.dp).padding(end = 0.dp),
                    )
                    Spacer(Modifier.size(5.dp))
                }
                // Shrinkable, so a chip sharing a row with others (the library control bar) gives
                // way instead of pushing its neighbours off a narrow screen. fill = false keeps it
                // at its natural width whenever there is room, which is everywhere else.
                Text(
                    text = buildAnnotatedString {
                        append(label)
                        // With an icon the whole label IS the value, so it is emphasised as a whole
                        // rather than searched for inside a longer phrase.
                        val start = if (icon != null) 0 else value?.let { label.lastIndexOf(it) } ?: -1
                        if (start >= 0) {
                            addStyle(
                                SpanStyle(color = Parchment, fontWeight = FontWeight.SemiBold),
                                start,
                                if (icon != null) label.length else start + value!!.length,
                            )
                        }
                    },
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Faint, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        // Bold as well as amber: on a dark menu the colour shift alone is easy to
                        // miss, and this is the one row that says what the control is set to.
                        Text(
                            labelOf(option),
                            color = if (option == selected) Amber else Parchment,
                            fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = { onSelect(option); open = false },
                )
            }
            onCustom?.let { custom ->
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_custom), color = Parchment) },
                    onClick = { open = false; custom() },
                )
            }
        }
    }
}

/**
 * Types an exact value for a setting whose presets did not include the one wanted.
 *
 * Clamped rather than rejected: someone who types 900 into a skip interval meant "as much as you
 * allow", and an error message that sends them back to guess again is a worse answer than the
 * nearest legal value. An empty or unparseable entry leaves the setting alone.
 */
@Composable
fun CustomNumberDialog(
    title: String,
    unit: String,
    initial: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { entered -> text = entered.filter { it.isDigit() }.take(4) },
                    label = { Text(unit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.settings_custom_range, range.first, range.last),
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    text.toIntOrNull()?.let { onConfirm(it.coerceIn(range.first, range.last)) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A rounded, outlined surface that groups related settings content. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(14.dp),
        content = content,
    )
}

/** The hairline that separates settings groups. */
@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = 14.dp), color = Line)
}

/** A small pill: genre/tag chips in the library, status chips in settings. */
@Composable
fun TagChip(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = fg,
        fontSize = 9.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Are-you-sure gate for an action that moves data, rebuilds the library, or signs out. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = Muted, fontSize = 13.sp, lineHeight = 19.sp) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Padding for the flat text buttons that carry secondary settings actions. */
val SettingsActionPadding = PaddingValues(horizontal = 4.dp)
