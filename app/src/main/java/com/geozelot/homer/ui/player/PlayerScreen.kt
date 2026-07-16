package com.geozelot.homer.ui.player

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.ChapterEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberDeep
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Sage
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface2
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Composable
fun PlayerScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeLeftMs by viewModel.timeLeftMs.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val sleepExtend by viewModel.sleepExtend.collectAsStateWithLifecycle()
    val sleepFade by viewModel.sleepFadeOutSeconds.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val download by viewModel.downloadState.collectAsStateWithLifecycle()

    var showSleepDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        PlayerTopBar(
            skipSilence = skipSilence,
            finished = finished,
            hasChapters = chapters.isNotEmpty(),
            onBack = onBack,
            onToggleSkipSilence = viewModel::setSkipSilence,
            onToggleFinished = viewModel::toggleFinished,
            onChapters = { showChaptersDialog = true },
        )

        // Artwork centered in the flexible space above the controls, sized to the largest
        // 1:1.3 cover that fits both the available width and height (so it never overflows
        // onto the controls on shorter screens).
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val coverWidth = minOf(maxWidth * 0.82f, maxHeight / 1.3f)
            CoverImage(
                model = state.coverModel ?: state.artworkData,
                modifier = Modifier
                    .width(coverWidth)
                    .aspectRatio(1f / 1.3f)
                    .shadow(
                        elevation = 30.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = Amber,
                        spotColor = AmberDeep,
                    )
                    .clip(RoundedCornerShape(14.dp)),
            )
        }

        // Bottom cluster: title, chapter + book-time-left, scrubber, transport, quick-select.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.bookTitle.ifEmpty { state.chapterTitle.ifEmpty { "Loading…" } },
                style = SerifTitle.copy(fontSize = 20.sp),
                color = Parchment,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.chapterCount > 0) {
                Text(
                    text = buildString {
                        append("Chapter ${state.chapterIndex + 1} of ${state.chapterCount}")
                        timeLeftMs?.let {
                            append(" · ")
                            append(if (it <= 0) "finished" else "${formatCompactDuration(it)} left")
                        }
                    },
                    color = Faint,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Scrubber(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            Transport(
                isPlaying = state.isPlaying,
                onPrev = viewModel::previousChapter,
                onPlayPause = viewModel::playPause,
                onNext = viewModel::nextChapter,
                modifier = Modifier.padding(top = 8.dp),
            )

            ToolRow(
                speed = state.playbackSpeed,
                sleepLabel = sleepLabel(state.sleepRemainingMs, state.sleepEndOfChapter),
                sleepActive = state.sleepRemainingMs != null || state.sleepEndOfChapter,
                downloadStatus = download?.status,
                onSpeed = viewModel::setSpeed,
                onSleepMinutes = viewModel::startSleepTimer,
                onSleepEndOfChapter = viewModel::startSleepTimerEndOfChapter,
                onSleepOff = viewModel::cancelSleepTimer,
                onSleepSettings = { showSleepDialog = true },
                onMark = { showBookmarksDialog = true },
                onDownload = viewModel::download,
                onRemoveDownload = viewModel::deleteDownload,
            )
        }
    }

    if (showChaptersDialog) {
        ChaptersDialog(
            chapters = chapters,
            onJump = {
                viewModel.jumpToChapter(it)
                showChaptersDialog = false
            },
            onDismiss = { showChaptersDialog = false },
        )
    }

    if (showBookmarksDialog) {
        BookmarksDialog(
            bookmarks = bookmarks,
            onAdd = viewModel::addBookmark,
            onJump = {
                viewModel.jumpToBookmark(it)
                showBookmarksDialog = false
            },
            onDelete = viewModel::deleteBookmark,
            onDismiss = { showBookmarksDialog = false },
        )
    }

    if (showSleepDialog) {
        SleepSettingsDialog(
            isActive = state.sleepRemainingMs != null || state.sleepEndOfChapter,
            extendMode = sleepExtend,
            fadeSeconds = sleepFade,
            onOff = {
                viewModel.cancelSleepTimer()
                showSleepDialog = false
            },
            onSetExtend = viewModel::setSleepExtend,
            onSetFade = viewModel::setSleepFadeOut,
            onDismiss = { showSleepDialog = false },
        )
    }
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun PlayerTopBar(
    skipSilence: Boolean,
    finished: Boolean,
    hasChapters: Boolean,
    onBack: () -> Unit,
    onToggleSkipSilence: (Boolean) -> Unit,
    onToggleFinished: () -> Unit,
    onChapters: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back", tint = Muted)
        }
        Text("NOW PLAYING", style = SectionLabel, color = Muted)
        Box {
            IconButton(onClick = { overflowOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Muted)
            }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                if (hasChapters) {
                    DropdownMenuItem(
                        text = { Text("Chapters") },
                        onClick = {
                            onChapters()
                            overflowOpen = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Skip silence") },
                    trailingIcon = {
                        Switch(checked = skipSilence, onCheckedChange = { onToggleSkipSilence(it) })
                    },
                    onClick = { onToggleSkipSilence(!skipSilence) },
                )
                DropdownMenuItem(
                    text = { Text(if (finished) "Mark as unfinished" else "Mark as finished") },
                    onClick = {
                        onToggleFinished()
                        overflowOpen = false
                    },
                )
            }
        }
    }
}

// ── Scrubber ─────────────────────────────────────────────────────────────────

@Composable
private fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = if (hasDuration) "-${formatTime(remaining)}" else "--:--",
                color = Muted,
                fontSize = 11.sp,
            )
        }
    }
}

// ── Transport ──────────────────────────────────────────────────────────────

@Composable
private fun Transport(
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter", tint = Parchment, modifier = Modifier.size(34.dp))
        }
        Box(
            modifier = Modifier
                .size(66.dp)
                .shadow(10.dp, CircleShape, spotColor = AmberDeep)
                .clip(CircleShape)
                .background(Amber)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = OnAmber,
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter", tint = Parchment, modifier = Modifier.size(34.dp))
        }
    }
}

// ── Tool row (speed / sleep / mark / save) ────────────────────────────────────

@Composable
private fun ToolRow(
    speed: Float,
    sleepLabel: String,
    sleepActive: Boolean,
    downloadStatus: String?,
    onSpeed: (Float) -> Unit,
    onSleepMinutes: (Long) -> Unit,
    onSleepEndOfChapter: () -> Unit,
    onSleepOff: () -> Unit,
    onSleepSettings: () -> Unit,
    onMark: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Top,
    ) {
        // Speed — quick-select menu.
        Box {
            var open by remember { mutableStateOf(false) }
            ToolButton(
                icon = Icons.Filled.Speed,
                label = "${formatSpeed(speed)}×",
                active = abs(speed - 1f) > 0.001f,
                onClick = { open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f).forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${formatSpeed(preset)}×",
                                color = if (abs(preset - speed) < 0.001f) Amber else Parchment,
                            )
                        },
                        onClick = { onSpeed(preset); open = false },
                    )
                }
            }
        }

        // Sleep — quick-select menu + settings.
        Box {
            var open by remember { mutableStateOf(false) }
            ToolButton(
                icon = Icons.Filled.Bedtime,
                label = sleepLabel,
                active = sleepActive,
                onClick = { open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                listOf(15, 30, 45, 60).forEach { m ->
                    DropdownMenuItem(
                        text = { Text("${m}m") },
                        onClick = { onSleepMinutes(m * 60_000L); open = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("End of chapter") },
                    onClick = { onSleepEndOfChapter(); open = false },
                )
                if (sleepActive) {
                    DropdownMenuItem(
                        text = { Text("Turn off", color = MaterialTheme.colorScheme.error) },
                        onClick = { onSleepOff(); open = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Sleep settings…") },
                    onClick = { onSleepSettings(); open = false },
                )
            }
        }

        // Mark — bookmarks.
        ToolButton(
            icon = Icons.Filled.BookmarkBorder,
            label = "Mark",
            active = false,
            onClick = onMark,
        )

        // Save — offline download.
        val downloaded = downloadStatus == DownloadStatus.DONE
        val downloading = downloadStatus == DownloadStatus.DOWNLOADING
        ToolButton(
            icon = if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
            label = when {
                downloaded -> "Saved"
                downloading -> "Saving…"
                downloadStatus == DownloadStatus.FAILED -> "Retry"
                else -> "Save"
            },
            active = downloaded,
            activeColor = Sage,
            onClick = { if (downloaded) onRemoveDownload() else onDownload() },
        )
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
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 10.5.sp, maxLines = 1)
    }
}

private fun sleepLabel(remainingMs: Long?, endOfChapter: Boolean): String = when {
    endOfChapter -> "Chapter"
    remainingMs != null -> formatTime(remainingMs)
    else -> "Sleep"
}

// ── Sleep settings (advanced: shake-extend + fade) ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSettingsDialog(
    isActive: Boolean,
    extendMode: String,
    fadeSeconds: Int,
    onOff: () -> Unit,
    onSetExtend: (String) -> Unit,
    onSetFade: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val extendOptions = listOf("5" to "+5m", "15" to "+15m", "30" to "+30m", "previous" to "Previous", "chapter" to "Chapter")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Shake to extend", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    extendOptions.forEach { (value, lbl) ->
                        FilterChip(
                            selected = extendMode == value,
                            onClick = { onSetExtend(value) },
                            label = { Text(lbl) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fade out", modifier = Modifier.weight(1f))
                    Stepper(
                        value = if (fadeSeconds == 0) "Off" else "${fadeSeconds}s",
                        onDec = { onSetFade((fadeSeconds - 1).coerceAtLeast(0)) },
                        onInc = { onSetFade((fadeSeconds + 1).coerceAtMost(30)) },
                    )
                }
                if (isActive) {
                    TextButton(onClick = onOff, contentPadding = PaddingValues(0.dp)) {
                        Text("Turn off timer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Stepper(value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDec) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
        IconButton(onClick = onInc) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

// ── Bookmarks ──────────────────────────────────────────────────────────────

@Composable
private fun BookmarksDialog(
    bookmarks: List<BookmarkEntity>,
    onAdd: () -> Unit,
    onJump: (BookmarkEntity) -> Unit,
    onDelete: (BookmarkEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmarks") },
        text = {
            Column {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("Add at current position")
                }
                if (bookmarks.isEmpty()) {
                    Text(
                        "No bookmarks yet.",
                        color = Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJump(bookmark) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        bookmark.chapterTitle.ifEmpty { "Chapter" },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(formatTime(bookmark.positionMs), color = Muted, fontSize = 12.sp)
                                }
                                TextButton(onClick = { onDelete(bookmark) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ── Chapters ─────────────────────────────────────────────────────────────────

@Composable
private fun ChaptersDialog(
    chapters: List<ChapterEntity>,
    onJump: (ChapterEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapters") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(chapters, key = { _, c -> c.id }) { index, chapter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(chapter) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            chapter.title?.ifBlank { null } ?: "Chapter ${index + 1}",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(formatTime(chapter.startMs), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ── Formatting ─────────────────────────────────────────────────────────────

/** Trims trailing zeros: 1.0 -> "1", 1.25 -> "1.25", 1.5 -> "1.5". */
private fun formatSpeed(speed: Float): String =
    "%.2f".format(speed).trimEnd('0').trimEnd('.')

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
