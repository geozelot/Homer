package com.geozelot.homer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberDeep
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment

// ── Transport ──────────────────────────────────────────────────────────────

@Composable
internal fun Transport(
    isPlaying: Boolean,
    seekSeconds: Int,
    scale: Float,
    onPrev: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp.scaled(scale)),
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(52.dp.scaled(scale))) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_cd_previous), tint = Parchment, modifier = Modifier.size(38.dp.scaled(scale)))
        }
        SeekButton(seconds = seekSeconds, forward = false, scale = scale, onClick = onSeekBack)
        Box(
            modifier = Modifier
                .size(84.dp.scaled(scale))
                .shadow(12.dp, CircleShape, spotColor = AmberDeep)
                .clip(CircleShape)
                .background(Amber)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                tint = OnAmber,
                modifier = Modifier.size(40.dp.scaled(scale)),
            )
        }
        SeekButton(seconds = seekSeconds, forward = true, scale = scale, onClick = onSeekForward)
        IconButton(onClick = onNext, modifier = Modifier.size(52.dp.scaled(scale))) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_cd_next), tint = Parchment, modifier = Modifier.size(38.dp.scaled(scale)))
        }
    }
}

/**
 * Skip-back / skip-forward button: a circular arrow with the seconds count inside it.
 *
 * The arc is drawn rather than taken from `Icons.Filled.Replay`, whose arrowhead reaches well into
 * the middle of the glyph — exactly where the number goes, so the two overlapped. Drawing it leaves
 * a deliberate gap at the top for the head and keeps the whole interior clear for the digits.
 */
@Composable
private fun SeekButton(seconds: Int, forward: Boolean, scale: Float, onClick: () -> Unit) {
    val label = if (forward) {
        stringResource(R.string.player_cd_skip_forward, seconds)
    } else {
        stringResource(R.string.player_cd_skip_back, seconds)
    }
    Box(
        modifier = Modifier
            .size(56.dp.scaled(scale))
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(SeekGlyphSize.scaled(scale))) {
            val stroke = 2.dp.toPx()
            // Inset by half the stroke so the arc's outer edge lands on the glyph bounds rather
            // than half a stroke outside them.
            val inset = stroke / 2f
            val d = size.minDimension - stroke
            // The gap the arrowhead occupies, left open at the top. Mirrored for the forward
            // button so both heads sit at the top with the tails running the other way.
            val gap = 74f
            val start = if (forward) -90f + gap / 2f else -90f - gap / 2f
            drawArc(
                color = Parchment,
                startAngle = start,
                sweepAngle = if (forward) 360f - gap else -(360f - gap),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(d, d),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // A filled triangle at the arc's open end, pointing along the direction of travel.
            val r = d / 2f
            val cx = inset + r
            val cy = inset + r
            val headAngle = Math.toRadians(start.toDouble())
            val hx = cx + r * kotlin.math.cos(headAngle).toFloat()
            val hy = cy + r * kotlin.math.sin(headAngle).toFloat()
            val h = 5.dp.toPx()
            val dir = if (forward) 1f else -1f
            drawPath(
                Path().apply {
                    // Tip points across the gap; the base straddles the stroke, so the head reads
                    // as the end of the line rather than a separate mark floating beside it.
                    moveTo(hx + dir * h * 0.9f, hy)
                    lineTo(hx - dir * h * 0.2f, hy - h * 0.75f)
                    lineTo(hx - dir * h * 0.2f, hy + h * 0.75f)
                    close()
                },
                color = Parchment,
            )
        }
        Text("$seconds", color = Parchment, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Outer size of the drawn circular arrow; the digits sit in the clear middle of it. */
private val SeekGlyphSize = 38.dp
