package com.geozelot.homer.ui.player

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.geozelot.homer.ui.components.CoverImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookDurationMs by viewModel.bookDurationMs.collectAsStateWithLifecycle()

    // Media notification needs POST_NOTIFICATIONS on Android 13+.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Start playback when the screen opens for this book.
    LaunchedEffect(bookId) { viewModel.play(bookId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.bookTitle.ifEmpty { "Now playing" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoverImage(
                model = state.coverModel ?: state.artworkData,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .size(240.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Text(
                text = state.chapterTitle.ifEmpty { "Loading…" },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.chapterCount > 0) {
                Text(
                    text = buildString {
                        append("Chapter ${state.chapterIndex + 1} of ${state.chapterCount}")
                        bookDurationMs?.takeIf { it > 0 }?.let { append(" · ${formatTime(it)}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            PositionSlider(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::previousChapter) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = viewModel::playPause, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(64.dp),
                    )
                }
                IconButton(onClick = viewModel::nextChapter) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter", modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun PositionSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging, track a local value so the thumb doesn't fight state updates.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val hasDuration = durationMs > 0
    val sliderValue = dragValue ?: if (hasDuration) positionMs.toFloat() else 0f
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
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(sliderValue.toLong()), style = MaterialTheme.typography.labelMedium)
            Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
