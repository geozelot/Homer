package com.geozelot.homer.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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

/**
 * A section header, e.g. "WHERE DOWNLOADS ARE KEPT".
 *
 * Two conventions, both written down because both have already been broken:
 *
 *  - **The capitals live in the string resource**, not here. [SectionLabel] sets weight, size and
 *    tracking; Compose has no text-transform, so a header typed in sentence case renders in sentence
 *    case and is the one odd header on the page. Every `*_header` / `*_section_*` string is upper
 *    case in every language.
 *  - **A run of controls between two dividers is a group, and a group among labelled groups gets a
 *    header.** Four screens had grown an unlabelled group — usually the first on the page, or one
 *    marooned between two labelled ones — which reads as an omission rather than as a choice. A
 *    single trailing action at the foot of a page (sign out) is a footer, not a group, and needs
 *    none.
 */
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
    /**
     * Drops the pill's own outline and fill, for a chip that sits INSIDE a bordered field.
     *
     * A box in a box reads as two controls, and three of them in one field reads as three. Without
     * the outline the field is the control and these are its parts — which is also what buys the
     * width for three of them to sit in a segment of a phone's control row.
     */
    bordered: Boolean = true,
    /**
     * Names the axis in front of the value — "Shelve: Author" — instead of leaving it to an icon.
     *
     * Rendered as its OWN text rather than folded into [label] so the two halves can be given
     * different priorities. The category is fixed and the VALUE gives way: three chips whose
     * categories are all fully drawn line up as three settings, where three shortened categories
     * read as three different things. A clipped value is still recognisable — and the chip opens to
     * show it in full, which is what the chip is for.
     */
    category: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        // The pill is well under the 48dp minimum touch target, so the tap area is expanded around
        // it — inflating the pill itself would break its alignment with the 11sp label text it sits
        // beside.
        Box(
            modifier = Modifier
                .sizeIn(minHeight = if (bordered) 48.dp else 0.dp)
                // Borderless, the chip is one share of a field and the whole share should open it:
                // a tap target the width of the words leaves dead space between three controls that
                // look like they divide the field between them.
                .then(if (bordered) Modifier else Modifier.fillMaxWidth())
                .clickable { open = true },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (bordered) {
                            Modifier
                                .border(1.dp, Line, RoundedCornerShape(8.dp))
                                .background(Surface1)
                        } else {
                            Modifier
                        },
                    )
                    // Borderless, the chip IS its share of the field, so it spans it: the text
                    // starts at the share's left edge and the chevron ends at its right.
                    .then(if (bordered) Modifier else Modifier.fillMaxWidth())
                    .padding(
                        start = if (bordered) (if (icon != null) 7.dp else 10.dp) else 4.dp,
                        end = if (bordered) 6.dp else 2.dp,
                        top = 5.dp,
                        bottom = 5.dp,
                    ),
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
                category?.let {
                    Text(
                        "$it:",
                        color = Faint,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(4.dp))
                }
                // Shrinkable, so a chip sharing a row with others (the library control bar) gives
                // way instead of pushing its neighbours off a narrow screen. fill = false keeps it
                // at its natural width whenever there is room, which is everywhere else.
                Text(
                    // Not shrinkable when a category is carrying the give — see [category].
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
                    // Filled weight when a category is present, so the value takes what is left and
                    // the chevron is pushed to the chip's own right edge — which is what puts all
                    // three chevrons on the same three tick marks across the field.
                    modifier = if (category == null) {
                        Modifier.weight(1f, fill = false)
                    } else {
                        Modifier.weight(1f)
                    },
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
            HomerTextButton(
                onClick = {
                    text.toIntOrNull()?.let { onConfirm(it.coerceIn(range.first, range.last)) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
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
            HomerTextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) }
        },
        dismissButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Padding for an action sitting at the trailing edge of a settings row.
 *
 * Tighter than [HomerButtonPadding] because it has a row's own padding around it already — but no
 * longer 4dp-horizontal-and-nothing-vertical, which was fine for a bare word and draws a box
 * clamped against the letters now that these carry a border.
 */
val SettingsActionPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)

/**
 * A text button that looks like a button.
 *
 * Homer's actions were bare tinted words — Save, Publish, Reset, Clear, Use this folder. A word that
 * does something and a word that merely says something looked identical, so every one of them had to
 * be recognised by position and remembered rather than seen. A hairline border and a chip's corner
 * radius are enough to make it read as pressable without turning a settings page into a wall of
 * filled buttons.
 *
 * The same hairline the cards, chips and dividers use, so it reads as one more piece of the same
 * surface rather than a control bolted onto it. Mirrors [TextButton]'s own signature exactly, so
 * every call site was a rename.
 */
@Composable
fun HomerTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = HomerButtonPadding,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        // Faint, and fainter still when disabled: a disabled control should not draw the same
        // outline as a live one and then decline to do anything.
        border = BorderStroke(1.dp, if (enabled) Line else Line.copy(alpha = 0.5f)),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Tighter than Material's default.
 *
 * The stock 24dp horizontal padding was sized for a filled button with a shadow around it; on a
 * bordered one it puts a visible box a thumb's width wider than the word inside it, and two of them
 * side by side in a dialog no longer fit.
 */
val HomerButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
/**
 * The one height every control in the library's control bar is drawn to.
 *
 * Shared rather than repeated so the band cannot drift apart again: the dropdown chips, the search
 * chip and the view toggle each used to carry their own idea of how tall a pill is.
 */
val ControlPillHeight = 28.dp


/**
 * One width for a column of actions that do not share a row.
 *
 * Settings actions sit at the trailing edge of their own rows, so each one was as wide as its own
 * word: four rows of buttons whose left edges stepped in and out with the length of "Scan",
 * "Fetch", "Measure", "Publish". Given the widest of the set, every button can be drawn to it and
 * the column reads as a column.
 *
 * MEASURED, not a constant. In German those same four words are "Scannen", "Holen", "Vermessen" and
 * "Veröffentlichen" — a hardcoded dp would either clip the longest or strand the English ones in
 * whitespace. Recomputed when the labels, the type scale or the font scale change, which is every
 * input the answer depends on.
 *
 * Floored at Material's own minimum so a language with four very short words cannot end up with
 * buttons narrower than an unwidened one would have been.
 */
@Composable
fun rememberActionWidth(
    labels: List<String>,
    contentPadding: PaddingValues = SettingsActionPadding,
): Dp {
    val direction = LocalLayoutDirection.current
    val width = rememberTextWidth(labels, MaterialTheme.typography.labelLarge) +
        contentPadding.calculateLeftPadding(direction) +
        contentPadding.calculateRightPadding(direction) +
        // The hairline, on both sides — it is inside the button's own width.
        2.dp
    return if (width > ButtonDefaults.MinWidth) width else ButtonDefaults.MinWidth
}

/**
 * How wide the widest of [labels] is in [style], and nothing else.
 *
 * The measuring half of [rememberActionWidth], separated because a control that is not a button
 * wants the measurement without a button's padding or its 58dp floor — a chip whose label changes
 * between two words, say, and which should not change width as it does.
 */
@Composable
fun rememberTextWidth(labels: List<String>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, density) {
        val widest = labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        with(density) { widest.toDp() }
    }
}
