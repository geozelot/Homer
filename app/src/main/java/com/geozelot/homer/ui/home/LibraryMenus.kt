package com.geozelot.homer.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geozelot.homer.R
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.ui.components.HomerSwitch
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Sage

// ── Context menus ────────────────────────────────────────────────────────────
//
// What a long-press offers on a book and on a shelf. Two menus rather than one with disabled rows:
// almost nothing a book does means the same thing to a whole shelf.

@Composable
internal fun BookMenu(
    book: BookListItem,
    expanded: Boolean,
    actions: BookActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // No "Play" item: it was the first and most prominent entry in every menu and did nothing
        // but close the menu. Tapping the card itself opens the book.
        //
        // Three groups, hairline-separated: what you can LOOK at, what changes the book's state,
        // and offline. Edit is no longer here — it lives at the foot of Details, where the fields
        // it edits are on screen to be read first.
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_details)) },
            leadingIcon = { Icon(Icons.Filled.Info, null, tint = Muted) },
            onClick = { actions.onDetails(book); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_bookmarks)) },
            leadingIcon = { Icon(Icons.Filled.Bookmarks, null, tint = Muted) },
            onClick = { actions.onBookmarks(book); onDismiss() },
        )

        HorizontalDivider()

        // Greyed rather than absent for an unstarted book. It used to vanish, which moved every
        // item below it up and put a different action under the finger that reached for one.
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.mark_completed),
                    color = if (book.started) Parchment else Faint,
                )
            },
            leadingIcon = { Icon(Icons.Filled.Check, null, tint = if (book.started) Muted else Faint) },
            enabled = book.started,
            onClick = { actions.onMarkCompleted(book.id); onDismiss() },
        )

        HorizontalDivider()

        // Offline sits at the bottom of every menu: on = keep offline, off = remove/abort. While a
        // download is in flight the trailing control is a spinner so the toggle clearly did something.
        val offlineEnabled = book.downloadStatus != null
        val downloading = DownloadStatus.isActive(book.downloadStatus)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_offline)) },
            leadingIcon = { Icon(Icons.Filled.Download, null, tint = if (book.isDownloaded) Sage else Muted) },
            trailingIcon = {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Amber, strokeWidth = 2.dp)
                } else {
                    HomerSwitch(
                        checked = offlineEnabled,
                        onCheckedChange = {
                            if (offlineEnabled) actions.onRemove(book.id) else actions.onDownload(book.id)
                            onDismiss()
                        },
                    )
                }
            },
            onClick = {
                if (offlineEnabled) actions.onRemove(book.id) else actions.onDownload(book.id)
                onDismiss()
            },
        )
        when (book.downloadStatus) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_pause_download)) },
                onClick = { actions.onPause(book.id); onDismiss() },
            )
            DownloadStatus.PAUSED -> DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_resume_download)) },
                onClick = { actions.onResume(book.id); onDismiss() },
            )
            DownloadStatus.FAILED -> DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_retry_download)) },
                onClick = { actions.onResume(book.id); onDismiss() },
            )
        }
    }
}

/**
 * The series counterpart of [BookMenu]: rename the series, or take the whole thing offline.
 *
 * The offline switch reads as on once ANY episode is downloading or downloaded, matching how a
 * book's own switch reads its single download row — a half-downloaded series is "offline, still
 * working" rather than a third state. Flipping it on downloads every episode that isn't already
 * queued; flipping it off drops all of them.
 */
@Composable
internal fun SeriesMenu(
    series: LibraryEntry.Series,
    expanded: Boolean,
    actions: BookActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_details)) },
            leadingIcon = { Icon(Icons.Filled.Info, null, tint = Muted) },
            onClick = { actions.onDetailsSeries(series); onDismiss() },
        )

        HorizontalDivider()

        val offlineEnabled = series.books.any { it.downloadStatus != null }
        val downloading = series.books.any { DownloadStatus.isActive(it.downloadStatus) }
        val allDownloaded = series.books.all { it.isDownloaded }
        val toggle = {
            if (offlineEnabled) actions.onRemoveSeries(series) else actions.onDownloadSeries(series)
            onDismiss()
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_offline_series)) },
            leadingIcon = {
                Icon(Icons.Filled.Download, null, tint = if (allDownloaded) Sage else Muted)
            },
            trailingIcon = {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Amber, strokeWidth = 2.dp)
                } else {
                    HomerSwitch(checked = offlineEnabled, onCheckedChange = { toggle() })
                }
            },
            onClick = toggle,
        )
        // Downloading one episode of a series makes the switch above read as on, so without this
        // the only thing the menu offered a part-downloaded series was "delete what you have" —
        // which is also what a user reaching for "finish the rest" would have tapped. Covers the
        // failed-episode case too: BookMenu has an explicit Retry item and this is its equivalent.
        if (offlineEnabled && !allDownloaded && !downloading) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_offline_series_rest)) },
                leadingIcon = { Icon(Icons.Filled.Download, null, tint = Muted) },
                onClick = { actions.onDownloadSeries(series); onDismiss() },
            )
        }
    }
}
