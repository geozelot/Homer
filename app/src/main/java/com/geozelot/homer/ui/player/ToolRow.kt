package com.geozelot.homer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geozelot.homer.R
import com.geozelot.homer.playback.VolumeMode
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import kotlin.math.abs

// ── The bottom row: a timer, how it plays, and a mark ─────────────────────────────────────────

/**
 * Five controls, sorted into the three kinds of thing they actually are.
 *
 * They used to sit in one undifferentiated row of five: speed, sleep, volume, silence, mark. Read
 * left to right that is a timer, then two sound settings, then a third sound setting, then an
 * action — three different kinds interleaved, and five equally-spaced targets with nothing to say
 * which of them belong together.
 *
 * So: **sleep** at the leading edge, because when playback stops is a property of this sitting
 * rather than of the book. **Mark** at the trailing edge, because it is the only one that MAKES
 * something. And the three at-play settings — speed, volume, cut silence — banded together in the
 * middle, each still its own button, one tap deep, exactly as they were.
 *
 * ## Why a [Box] and not a [Row]
 *
 * The group is centred on the SCREEN, not on what is left after its neighbours. Sleep's label is a
 * live countdown, so it changes width every minute; in a row with weights, a centred middle would
 * drift sideways as the timer ticked down. Anchoring the sides and centring the group independently
 * is the only arrangement where the three settings hold still.
 */
@Composable
internal fun ToolRow(
    speed: Float,
    /**
     * The sleep timer's remaining time — read inside [SleepTool] ONLY. It counts down every second,
     * and this row holds five controls that do not.
     */
    sleepRemainingMs: () -> Long?,
    sleepEndOfChapter: Boolean,
    sleepActive: Boolean,
    volumeMode: String,
    skipSilence: Boolean,
    onSpeed: (Float) -> Unit,
    onSleepMinutes: (Long) -> Unit,
    onSleepEndOfChapter: () -> Unit,
    onSleepOff: () -> Unit,
    onCustomSpeed: () -> Unit,
    onCustomSleep: () -> Unit,
    onVolumeMode: (String) -> Unit,
    onToggleSkipSilence: () -> Unit,
    onMark: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
        SleepTool(
            remainingMs = sleepRemainingMs,
            endOfChapter = sleepEndOfChapter,
            active = sleepActive,
            onMinutes = onSleepMinutes,
            onEndOfChapter = onSleepEndOfChapter,
            onOff = onSleepOff,
            onCustom = onCustomSleep,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        // The three at-play settings, held together by proximity alone.
        //
        // The box they sat in was one more line on a screen that already has a scrubber, a
        // transport row and a chapter pill drawing horizontals. Three glyphs set close with a gap
        // either side of the group say "these belong together" without adding an edge — which is
        // the same thing the enclosure was for, and quieter.
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Speed — quick-select menu.
            Box {
                var open by remember { mutableStateOf(false) }
                ToolButton(
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.player_speed_value, formatSpeed(speed)),
                    active = abs(speed - 1f) > 0.001f,
                    onClick = { open = true },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    MenuHeader(stringResource(R.string.player_speed))
                    SPEED_PRESETS.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.player_speed_value, formatSpeed(preset)),
                                    color = if (abs(preset - speed) < 0.001f) Amber else Parchment,
                                )
                            },
                            onClick = { onSpeed(preset); open = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_custom), color = Parchment) },
                        onClick = { open = false; onCustomSpeed() },
                    )
                }
            }

            // Volume override — quick-select menu.
            Box {
                var open by remember { mutableStateOf(false) }
                ToolButton(
                    icon = if (volumeMode == VolumeMode.REDUCED) {
                        Icons.AutoMirrored.Filled.VolumeDown
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    label = when (volumeMode) {
                        VolumeMode.REDUCED -> stringResource(R.string.player_volume_quiet)
                        VolumeMode.INCREASED -> stringResource(R.string.player_volume_boost)
                        else -> stringResource(R.string.player_volume_normal)
                    },
                    active = volumeMode != VolumeMode.NORMAL,
                    onClick = { open = true },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    MenuHeader(stringResource(R.string.player_volume))
                    listOf(
                        VolumeMode.REDUCED to stringResource(R.string.player_volume_reduced),
                        VolumeMode.NORMAL to stringResource(R.string.player_volume_normal),
                        VolumeMode.INCREASED to stringResource(R.string.player_volume_increased),
                    ).forEach { (mode, lbl) ->
                        DropdownMenuItem(
                            text = { Text(lbl, color = if (mode == volumeMode) Amber else Parchment) },
                            onClick = { onVolumeMode(mode); open = false },
                        )
                    }
                }
            }

            // Skip silence — toggle. The label says which way it is set rather than what it is
            // called: "Cut" and "Keep" are the two answers, and the button is the question.
            ToolButton(
                icon = Icons.Filled.ContentCut,
                label = stringResource(
                    if (skipSilence) R.string.player_silence_on else R.string.player_silence_off,
                ),
                active = skipSilence,
                onClick = onToggleSkipSilence,
            )
        }

        // Mark — bookmarks.
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            ToolButton(
                icon = Icons.Filled.BookmarkBorder,
                label = stringResource(R.string.player_mark),
                active = false,
                onClick = onMark,
            )
        }
    }
}

/**
 * One control on the bottom row: a glyph, and nothing else.
 *
 * The label under it is gone. Five of them made the row a paragraph read at arm's length while
 * something is playing, and every one of those words is said again — in full, and in the reader's
 * own language — by the header of the menu the button opens. What the glyph still has to carry is
 * whether the setting is doing anything, and that is the tint: [Amber] when it is, [Muted] when it
 * sits at its default.
 *
 * [label] therefore survives as the content description. It is the same word the menu is headed
 * with, so what a screen reader announces and what a sighted reader sees on opening agree.
 */
/**
 * Sleep: the glyph, and the menu of durations behind it.
 *
 * Its own composable because it is the one control on this row that TICKS. The remaining time
 * reaches the button's description and the menu's header, and both of those are inside here — so a
 * running timer redraws a glyph once a second instead of redrawing the player.
 *
 * The description is worth keeping live even though nothing on screen shows the number: to a
 * screen reader it is the only place the remaining time is said at all.
 */
@Composable
private fun SleepTool(
    remainingMs: () -> Long?,
    endOfChapter: Boolean,
    active: Boolean,
    onMinutes: (Long) -> Unit,
    onEndOfChapter: () -> Unit,
    onOff: () -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = sleepLabel(remainingMs(), endOfChapter, context)
    Box(modifier = modifier) {
        var open by remember { mutableStateOf(false) }
        ToolButton(
            icon = Icons.Filled.Bedtime,
            label = label,
            active = active,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // The one header that is not just a noun. A running timer used to be readable on
            // the button itself; with the words gone, the moment it is opened is the moment to
            // say how long is left.
            MenuHeader(if (active) "${stringResource(R.string.player_sleep)} · $label" else stringResource(R.string.player_sleep))
            listOf(15, 30, 45, 60).forEach { m ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_sleep_minutes, m)) },
                    onClick = { onMinutes(m * 60_000L); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_sleep_end_of_chapter)) },
                onClick = { onEndOfChapter(); open = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_custom), color = Parchment) },
                onClick = { open = false; onCustom() },
            )
            if (active) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_sleep_turn_off), color = MaterialTheme.colorScheme.error) },
                    onClick = { onOff(); open = false },
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    activeColor: androidx.compose.ui.graphics.Color = Amber,
) {
    val tint = if (active) activeColor else Muted
    Box(
        // A glyph is a small target, and this one lost the words that used to make it a big one.
        // The 48dp is claimed here rather than by inflating the icon.
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * What the menu that just opened is about — the word the button under it no longer says.
 *
 * Set in [SectionLabel] and indented to the items' own text, the same way
 * [com.geozelot.homer.ui.components.DropdownChip] heads its
 * menus, so the two places in Homer where a small control opens a list of values name that list
 * identically.
 */
@Composable
internal fun MenuHeader(text: String) {
    Text(
        text,
        style = SectionLabel,
        color = Faint,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
    )
}

internal fun sleepLabel(remainingMs: Long?, endOfChapter: Boolean, context: android.content.Context): String = when {
    endOfChapter -> context.getString(R.string.player_sleep_chapter)
    remainingMs != null -> formatTime(remainingMs)
    else -> context.getString(R.string.player_sleep)
}

private val SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
