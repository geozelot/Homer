package com.geozelot.homer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Surface2

// ── Scrubber ─────────────────────────────────────────────────────────────────
//
// The two composables that tick. Playback position advances every second, and these are the only
// places on the player that read it — [Scrubber] takes the position as a LAMBDA and [PositionLine]
// the time left the same way, so the per-second recomposition stops here instead of running the
// whole screen: the header, the transport, the top bar and the artwork never see a tick.

@Composable
internal fun Scrubber(
    /** The playback position, read inside this composable ONLY — see the file header. */
    positionMs: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val hasDuration = durationMs > 0
    val sliderValue = dragValue ?: if (hasDuration) positionMs().toFloat() else 0f
    val range = if (hasDuration) 0f..durationMs.toFloat() else 0f..1f

    Column(modifier = modifier) {
        Slider(
            value = sliderValue.coerceIn(range.start, range.endInclusive),
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(it.toLong()) }
                dragValue = null
            },
            valueRange = range,
            enabled = hasDuration,
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Surface2,
            ),
        )
        // Chapter-relative: elapsed on the left, time-to-end-of-chapter on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(sliderValue.toLong()), color = Muted, fontSize = 11.sp)
            val remaining = (durationMs - sliderValue.toLong()).coerceAtLeast(0)
            Text(
                text = if (hasDuration) stringResource(R.string.player_time_remaining, formatTime(remaining)) else stringResource(R.string.player_time_unknown),
                color = Muted,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Where you are: which chapter, and how much of the book is left. Named for what it says rather
 * than "info", which is the block of facts ABOUT the book two regions up — and calling both of
 * them info is how a gap meant for one of them landed on the other.
 *
 * The chapter part is conditional (a single-file book with no embedded chapters has none), the
 * time-left part is NOT — it used to live inside the chapter branch, which lost the
 * remaining-time readout for exactly those books.
 */
@Composable
internal fun PositionLine(
    chapterNumber: Int,
    chapterCount: Int,
    /** The book's remaining time, read inside this composable ONLY — see the file header. */
    timeLeftMs: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val line = buildString {
        if (chapterCount > 0) {
            // The number as a string, unpadded: the picker pads its numbers so twenty rows line
            // up in columns, and this is one line under a title where "Chapter 07" would just
            // look like a typo.
            append(context.getString(R.string.player_chapter_of, "$chapterNumber", chapterCount))
        }
        timeLeftMs()?.let {
            if (isNotEmpty()) append(" · ")
            append(
                if (it <= 0) {
                    context.getString(R.string.status_finished)
                } else {
                    context.getString(R.string.time_left, formatCompactDuration(it))
                },
            )
        }
    }
    if (line.isNotEmpty()) {
        Text(
            text = line,
            color = Faint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )
    }
}
