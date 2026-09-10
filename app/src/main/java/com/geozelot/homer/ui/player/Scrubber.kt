package com.geozelot.homer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Surface2
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Scrubber ─────────────────────────────────────────────────────────────────
//
// Two of the four composables that tick. Playback position advances every second, and these are
// the only places that read it — [Scrubber] takes the position as a LAMBDA and [PositionLine] the
// time left the same way, so the per-second recomposition stops here instead of running the whole
// screen. (The other two are `SleepCountdown` and `SleepTool`, which tick only while a sleep timer
// is running and for the same reason: they are what shows it.)

/**
 * How far the finger has to leave the bar before the drag stops being a normal one, and what a
 * pixel is worth once it has.
 *
 * A chapter of a long book is an hour of audio behind a few hundred pixels of bar, so a pixel is
 * roughly ten seconds and there is no way to land on a sentence. Dragging away from the bar trades
 * reach for resolution, the way a podcast player does: the same finger travel covers half, then a
 * quarter, of the time.
 *
 * The first band is the important one. Below it NOTHING changes — the Material slider handles the
 * gesture exactly as it always has, including a press that jumps the thumb — because a drag that
 * strays twenty pixels while crossing the screen is a plain scrub with a shaky hand, not a request
 * for precision.
 */
private val PrecisionBands = listOf(
    40.dp to 1f,
    100.dp to 0.5f,
)
private const val FinestRate = 0.25f

/** What a rate is called. A symbol rather than a string resource: it reads the same in every language. */
private fun rateLabel(rate: Float): String = when {
    rate >= 1f -> "1×"
    rate >= 0.5f -> "½×"
    else -> "¼×"
}

@Composable
internal fun Scrubber(
    /** The playback position, read inside this composable ONLY — see the file header. */
    positionMs: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    // Non-null only while a precision drag is in progress; it holds that drag's current rate, which
    // is both what the label shows and the flag saying the slider's own gesture is no longer the one
    // in charge.
    var precisionRate by remember { mutableStateOf<Float?>(null) }
    var trackWidth by remember { mutableIntStateOf(0) }
    var labelWidth by remember { mutableIntStateOf(0) }

    val hasDuration = durationMs > 0
    val sliderValue = dragValue ?: if (hasDuration) positionMs().toFloat() else 0f
    val range = if (hasDuration) 0f..durationMs.toFloat() else 0f..1f

    val commit = {
        dragValue?.let { onSeek(it.toLong()) }
        dragValue = null
    }

    Column(modifier = modifier) {
        // Reserved whether or not a precision drag is running, so engaging one does not shove the
        // transport down the screen at the moment the reader is aiming at something.
        RateLabel(
            rate = precisionRate,
            fraction = if (hasDuration) sliderValue / durationMs.toFloat() else 0f,
            trackWidth = trackWidth,
            labelWidth = labelWidth,
            onLabelWidth = { labelWidth = it },
        )
        Slider(
            value = sliderValue.coerceIn(range.start, range.endInclusive),
            onValueChange = { if (precisionRate == null) dragValue = it },
            onValueChangeFinished = {
                // Taking the gesture over cancels the slider's own drag, which lands here as well.
                // The precision gesture commits its own release, so this must not commit a second
                // time — nor clear the value it is still moving.
                if (precisionRate == null) commit()
            },
            valueRange = range,
            enabled = hasDuration,
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Surface2,
            ),
            modifier = Modifier
                .onSizeChanged { trackWidth = it.width }
                .precisionScrub(
                    enabled = hasDuration,
                    durationMs = durationMs,
                    valueAt = { dragValue ?: positionMs().toFloat() },
                    onRate = { precisionRate = it },
                    onValue = { dragValue = it },
                    onRelease = { precisionRate = null; commit() },
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
 * The rate readout, riding above the thumb it belongs to.
 *
 * It follows the thumb rather than sitting in a fixed corner because the thumb is where the reader
 * is looking; a rate stated somewhere else is a number they have to go and find while aiming.
 */
@Composable
private fun RateLabel(
    rate: Float?,
    fraction: Float,
    trackWidth: Int,
    labelWidth: Int,
    onLabelWidth: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(RateLabelHeight)) {
        if (rate != null) {
            val travel = (trackWidth - labelWidth).coerceAtLeast(0)
            Text(
                text = rateLabel(rate),
                color = Amber,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .offset { IntOffset((fraction.coerceIn(0f, 1f) * travel).roundToInt(), 0) }
                    .clip(RoundedCornerShape(6.dp))
                    .background(Surface2)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .onSizeChanged { onLabelWidth(it.width) },
            )
        }
    }
}

private val RateLabelHeight = 18.dp

/**
 * Watches a drag on the slider and takes it over once the finger leaves the bar.
 *
 * Works on the INITIAL pass, which is the only place this can sit: initial runs outside-in, so this
 * sees a move before the slider's own drag detector does and can decide whether to let it through.
 * While the finger is still near the bar nothing is consumed and the slider behaves exactly as it
 * always has. Past the first band this consumes every move for the rest of the gesture — the
 * slider's drag is cancelled at that point and cannot be resumed mid-stream, so handing control
 * back is not on offer; what happens instead is that the rate returns to 1× as the finger comes
 * back, which is the same thing to the reader and predictable to implement.
 *
 * Deliberately a modifier on the slider rather than a Box over it: an overlay would have to
 * re-implement the press-to-position tap, and it would sit between the slider and the reader for
 * accessibility. The slider keeps its own semantics node, so TalkBack still sees one slider with
 * one value.
 */
private fun Modifier.precisionScrub(
    enabled: Boolean,
    durationMs: Long,
    valueAt: () -> Float,
    onRate: (Float?) -> Unit,
    onValue: (Float) -> Unit,
    onRelease: () -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(durationMs) {
    val bands = PrecisionBands.map { (distance, rate) -> distance.toPx() to rate }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var engaged = false
        var value = 0f
        var lastX = down.position.x
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial)
                .changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val rate = scrubRateFor(abs(change.position.y - down.position.y), bands)
            if (!engaged && rate < 1f) {
                engaged = true
                // Where the slider had got to before this became a precision drag, so the fine
                // part carries on from where the coarse part left off.
                value = valueAt()
            }
            if (engaged) {
                val perPixel = if (size.width > 0) durationMs.toFloat() / size.width else 0f
                value = (value + (change.position.x - lastX) * perPixel * rate)
                    .coerceIn(0f, durationMs.toFloat())
                onRate(rate)
                onValue(value)
                change.consume()
            }
            lastX = change.position.x
        }
        if (engaged) onRelease()
    }
}

/**
 * The rate for a vertical distance: the first band it falls inside, or the finest past them all.
 *
 * Bands are inclusive at the top so a band's own distance still belongs to it, and the list is
 * assumed ordered nearest-first — [PrecisionBands] is the only caller that supplies one.
 */
internal fun scrubRateFor(dy: Float, bands: List<Pair<Float, Float>>): Float =
    bands.firstOrNull { dy <= it.first }?.second ?: FinestRate

/** The finest rate, past every band — exposed for the same reason [scrubRateFor] is. */
internal const val ScrubFinestRate = FinestRate

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
