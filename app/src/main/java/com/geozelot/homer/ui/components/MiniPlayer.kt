package com.geozelot.homer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.playback.PlaybackUiState
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface1
import com.geozelot.homer.ui.theme.Surface2

/**
 * Docked mini-player: always one tap from the current book. A live amber hairline across the
 * top shows chapter progress; tapping the row expands to Now Playing. Renders nothing when no
 * book is loaded.
 */
@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onOpenPlayer: (String) -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bookId = state.bookId ?: return
    // Prefer whole-book progress (from measured durations) for the hairline; fall back to the
    // current chapter's progress while durations aren't known yet.
    val fraction = when {
        state.bookTotalMs > 0 -> (state.bookElapsedMs.toFloat() / state.bookTotalMs).coerceIn(0f, 1f)
        state.durationMs > 0 -> (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val bookLeftMs = (state.bookTotalMs - state.bookElapsedMs).takeIf { state.bookTotalMs > 0 && it > 0 }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Surface2, Surface1)))
            .clickable { onOpenPlayer(bookId) },
    ) {
        // Hairline separator along the very top edge of the bar (under the progress line).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Line),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                model = state.coverModel ?: state.artworkData,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 11.dp),
            ) {
                Text(
                    text = state.bookTitle.ifEmpty { state.chapterTitle },
                    color = Parchment,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (state.chapterCount > 0) append("Chapter ${state.chapterIndex + 1}")
                        val speed = formatSpeedShort(state.playbackSpeed)
                        if (speed != null) {
                            if (isNotEmpty()) append(" · ")
                            append(speed)
                        }
                        bookLeftMs?.let {
                            if (isNotEmpty()) append(" · ")
                            append("${formatCompactDuration(it)} left")
                        }
                    },
                    color = Muted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Amber)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = OnAmber,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Live progress hairline, drawn last so it sits on top of the separator.
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .background(Amber),
        )
    }
}

/** "1.25×" etc., or null at normal speed (nothing worth showing). */
private fun formatSpeedShort(speed: Float): String? {
    if (kotlin.math.abs(speed - 1f) < 0.001f) return null
    return "%.2f".format(speed).trimEnd('0').trimEnd('.') + "×"
}
