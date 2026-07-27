package com.geozelot.homer.ui.player

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.playback.VolumeMode
import com.geozelot.homer.ui.components.HomerSwitch
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.components.EditBookDialog
import com.geozelot.homer.ui.formatCompactDuration
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberDeep
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
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
    val seekSeconds by viewModel.seekSeconds.collectAsStateWithLifecycle()
    val volumeMode by viewModel.volumeMode.collectAsStateWithLifecycle()
    val sleepExtend by viewModel.sleepExtend.collectAsStateWithLifecycle()
    val sleepFade by viewModel.sleepFadeOutSeconds.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val download by viewModel.downloadState.collectAsStateWithLifecycle()
    val editableBook by viewModel.editableBook.collectAsStateWithLifecycle()

    var showSleepDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

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
        val offline = download?.status == DownloadStatus.DONE
        PlayerTopBar(
            finished = finished,
            offline = offline,
            downloading = DownloadStatus.isActive(download?.status),
            canEdit = editableBook != null,
            onBack = onBack,
            onToggleFinished = viewModel::toggleFinished,
            onToggleOffline = { if (offline) viewModel.deleteDownload() else viewModel.download() },
            onEdit = { showEditDialog = true },
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
            val hasPicker = chapters.isNotEmpty()
            val chapterCount = if (hasPicker) chapters.size else state.chapterCount
            val chapterNumber =
                if (hasPicker) chapters.indexOfFirst { it.isCurrent }.let { if (it >= 0) it + 1 else 1 }
                else state.chapterIndex + 1

            // Chapter picker: a wide pill below the title, above the chapter/progress line.
            if (hasPicker) {
                ChapterButton(onClick = { showChaptersDialog = true }, modifier = Modifier.padding(top = 10.dp))
            }

            // Chapter/progress line (info).
            if (chapterCount > 0) {
                Text(
                    text = buildString {
                        append("Chapter $chapterNumber of $chapterCount")
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

            if (state.hasError) {
                ErrorBanner(onRetry = viewModel::retry, modifier = Modifier.padding(top = 12.dp))
            }

            Scrubber(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            Transport(
                isPlaying = state.isPlaying,
                seekSeconds = seekSeconds,
                onPrev = viewModel::previousChapter,
                onSeekBack = { viewModel.seekBy(-seekSeconds) },
                onPlayPause = viewModel::playPause,
                onSeekForward = { viewModel.seekBy(seekSeconds) },
                onNext = viewModel::nextChapter,
                modifier = Modifier.padding(top = 8.dp),
            )

            ToolRow(
                speed = state.playbackSpeed,
                sleepLabel = sleepLabel(state.sleepRemainingMs, state.sleepEndOfChapter),
                sleepActive = state.sleepRemainingMs != null || state.sleepEndOfChapter,
                volumeMode = volumeMode,
                skipSilence = skipSilence,
                onSpeed = viewModel::setSpeed,
                onSleepMinutes = viewModel::startSleepTimer,
                onSleepEndOfChapter = viewModel::startSleepTimerEndOfChapter,
                onSleepOff = viewModel::cancelSleepTimer,
                onSleepSettings = { showSleepDialog = true },
                onVolumeMode = viewModel::setVolumeMode,
                onToggleSkipSilence = { viewModel.setSkipSilence(!skipSilence) },
                onMark = { showBookmarksDialog = true },
            )
        }
    }

    if (showChaptersDialog) {
        ChapterPickerDialog(
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

    if (showEditDialog) {
        editableBook?.let { editable ->
            EditBookDialog(
                book = editable,
                onSave = { title, author, series, index, genre, tags, hidden, finishedChange ->
                    viewModel.saveOverride(title, author, series, index, genre, tags, hidden, finishedChange)
                    showEditDialog = false
                },
                onReset = {
                    viewModel.clearOverride()
                    showEditDialog = false
                },
                onPickCover = viewModel::setCustomCover,
                onClearCover = viewModel::clearCustomCover,
                onDismiss = { showEditDialog = false },
            )
        }
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
    finished: Boolean,
    offline: Boolean,
    downloading: Boolean,
    canEdit: Boolean,
    onBack: () -> Unit,
    onToggleFinished: () -> Unit,
    onToggleOffline: () -> Unit,
    onEdit: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            // Swipe down on the header to collapse back to the mini-player.
            .pointerInput(Unit) {
                val threshold = 48.dp.toPx()
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragged > threshold) onBack()
                        dragged = 0f
                    },
                    onVerticalDrag = { _, delta -> dragged += delta },
                )
            },
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
                DropdownMenuItem(
                    text = { Text(if (finished) "Mark as unfinished" else "Mark as finished") },
                    onClick = {
                        onToggleFinished()
                        overflowOpen = false
                    },
                )
                if (canEdit) {
                    DropdownMenuItem(
                        text = { Text("Edit book") },
                        onClick = {
                            onEdit()
                            overflowOpen = false
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Offline") },
                    trailingIcon = {
                        if (downloading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Amber, strokeWidth = 2.dp)
                        } else {
                            HomerSwitch(checked = offline, onCheckedChange = { onToggleOffline() })
                        }
                    },
                    onClick = { onToggleOffline() },
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
    seekSeconds: Int,
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter", tint = Parchment, modifier = Modifier.size(38.dp))
        }
        SeekButton(seconds = seekSeconds, forward = false, onClick = onSeekBack)
        Box(
            modifier = Modifier
                .size(84.dp)
                .shadow(12.dp, CircleShape, spotColor = AmberDeep)
                .clip(CircleShape)
                .background(Amber)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = OnAmber,
                modifier = Modifier.size(40.dp),
            )
        }
        SeekButton(seconds = seekSeconds, forward = true, onClick = onSeekForward)
        IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter", tint = Parchment, modifier = Modifier.size(38.dp))
        }
    }
}

/** Skip-back / skip-forward button: a circular-arrow icon with the seconds count centered in it. */
@Composable
private fun SeekButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Replay,
            contentDescription = if (forward) "Skip forward $seconds seconds" else "Skip back $seconds seconds",
            tint = Parchment,
            // The Replay glyph is a counter-clockwise arrow; mirror it for the forward button.
            modifier = Modifier.size(40.dp).then(if (forward) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
        )
        Text("$seconds", color = Parchment, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Wide pill opening the chapter picker; sits below the title. */
@Composable
private fun ChapterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Parchment, modifier = Modifier.size(17.dp))
        Text("Chapters", color = Parchment, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Shown when the stream stalls on an error (typically a lost connection); tap re-prepares. */
@Composable
private fun ErrorBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Surface2)
            .clickable(onClick = onRetry)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Danger, modifier = Modifier.size(17.dp))
        Text("Can't reach the server — Retry", color = Danger, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Tool row (speed / sleep / mark) ───────────────────────────────────────────

@Composable
private fun ToolRow(
    speed: Float,
    sleepLabel: String,
    sleepActive: Boolean,
    volumeMode: String,
    skipSilence: Boolean,
    onSpeed: (Float) -> Unit,
    onSleepMinutes: (Long) -> Unit,
    onSleepEndOfChapter: () -> Unit,
    onSleepOff: () -> Unit,
    onSleepSettings: () -> Unit,
    onVolumeMode: (String) -> Unit,
    onToggleSkipSilence: () -> Unit,
    onMark: () -> Unit,
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

        // Volume override — quick-select menu.
        Box {
            var open by remember { mutableStateOf(false) }
            ToolButton(
                icon = when (volumeMode) {
                    VolumeMode.REDUCED -> Icons.AutoMirrored.Filled.VolumeDown
                    VolumeMode.INCREASED -> Icons.AutoMirrored.Filled.VolumeUp
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                label = when (volumeMode) {
                    VolumeMode.REDUCED -> "Quiet"
                    VolumeMode.INCREASED -> "Boost"
                    else -> "Volume"
                },
                active = volumeMode != VolumeMode.NORMAL,
                onClick = { open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                listOf(
                    VolumeMode.REDUCED to "Reduced",
                    VolumeMode.NORMAL to "Normal",
                    VolumeMode.INCREASED to "Increased",
                ).forEach { (mode, lbl) ->
                    DropdownMenuItem(
                        text = { Text(lbl, color = if (mode == volumeMode) Amber else Parchment) },
                        onClick = { onVolumeMode(mode); open = false },
                    )
                }
            }
        }

        // Skip silence — toggle.
        ToolButton(
            icon = Icons.Filled.ContentCut,
            label = "Silence",
            active = skipSilence,
            onClick = onToggleSkipSilence,
        )

        // Mark — bookmarks.
        ToolButton(
            icon = Icons.Filled.BookmarkBorder,
            label = "Mark",
            active = false,
            onClick = onMark,
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
private fun ChapterPickerDialog(
    chapters: List<PlayerChapter>,
    onJump: (PlayerChapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Open scrolled to the current chapter so it's visible in a long list.
    LaunchedEffect(Unit) {
        val current = chapters.indexOfFirst { it.isCurrent }
        if (current > 0) listState.scrollToItem(current)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapters") },
        text = {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(chapters) { index, chapter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(chapter) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.width(28.dp),
                            color = if (chapter.isCurrent) Amber else Muted,
                            fontSize = 12.sp,
                        )
                        Text(
                            chapter.title.ifBlank { "Chapter ${index + 1}" },
                            modifier = Modifier.weight(1f),
                            color = if (chapter.isCurrent) Amber else Parchment,
                            fontWeight = if (chapter.isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        chapter.startMs?.let {
                            Text(formatTime(it), color = Muted, fontSize = 12.sp)
                        }
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
