package com.geozelot.homer.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.IndexPass
import com.geozelot.homer.data.library.LibraryIndexManager
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.sync.facet.CrawlSummary
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Muted
import kotlinx.coroutines.delay

/**
 * Upkeep: what Homer has managed to read about the library, and the two ways to make it read again.
 *
 * Split out of the Library page, which had grown to cover two different subjects — where the books
 * come from (now Sync) and what has been read about them (here). The seam is real: the first is
 * something you set once, the second is something you come back to.
 *
 * A device that only READS the shared index gets the stated version instead of the actionable one.
 * Its crawl, measure and correction passes are refused in LibraryIndexManager, so offering the
 * buttons was offering four controls that silently did nothing — and the gaps are still worth
 * naming, because a shelf with 40 art-less books is not broken, it is waiting for whoever maintains
 * the index.
 */
@Composable
fun LibraryUpkeepScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val sharedIndex by viewModel.sharedCatalogEnabled.collectAsStateWithLifecycle()
    val libraryWritable by viewModel.libraryWritable.collectAsStateWithLifecycle()
    val readsOnly by viewModel.readsSharedIndex.collectAsStateWithLifecycle()
    val queued by viewModel.indexQueued.collectAsStateWithLifecycle()
    val indexActive by viewModel.indexActive.collectAsStateWithLifecycle()
    val indexProgress by viewModel.indexProgress.collectAsStateWithLifecycle()
    val indexWaiting by viewModel.indexWaiting.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val unmeasured by viewModel.unmeasuredCount.collectAsStateWithLifecycle()
    val artless by viewModel.artlessCount.collectAsStateWithLifecycle()
    val corrections by viewModel.correctionCount.collectAsStateWithLifecycle()
    val lastScannedAt by viewModel.lastScannedAt.collectAsStateWithLifecycle()
    val lastFullCrawl by viewModel.lastFullCrawl.collectAsStateWithLifecycle()

    var confirmRebuild by remember { mutableStateOf(false) }
    var confirmMeasure by remember { mutableStateOf(false) }
    var confirmRecheck by remember { mutableStateOf(false) }

    // Re-reads the clock so "scanned 12 minutes ago" keeps counting while the screen stays open.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    SettingsScaffold(stringResource(R.string.set_upkeep_title), onBack, modifier) {
        SettingsSectionHeader(stringResource(R.string.lib_section_contents))

        if (readsOnly) {
            SettingsExplanation(stringResource(R.string.lib_contents_reader_lead))
            ReaderContents(bookCount = bookCount, artless = artless, unmeasured = unmeasured)
        } else {
            SettingsExplanation(stringResource(R.string.lib_contents_lead))

            val running = indexProgress
            BooksRow(
                queued = IndexPass.BOOKS in queued,
                scanState = scanState,
                bookCount = bookCount,
                lastScannedAt = lastScannedAt,
                now = now,
                onScan = viewModel::scan,
            )
            SettingsDivider()
            ArtworkRow(
                queued = IndexPass.ARTWORK in queued,
                progress = running?.takeIf { it.pass == IndexPass.ARTWORK },
                artless = artless,
                onFetch = viewModel::fetchCoverArt,
            )
            SettingsDivider()
            LengthsRow(
                queued = IndexPass.LENGTHS in queued,
                progress = running?.takeIf { it.pass == IndexPass.LENGTHS },
                unmeasured = unmeasured,
                onMeasure = { confirmMeasure = true },
            )
            SettingsDivider()
            CorrectionsRow(
                queued = IndexPass.CORRECTIONS in queued,
                count = corrections,
                shared = sharedIndex,
                canPublish = libraryWritable,
                onPublish = viewModel::publishCorrections,
            )

            // Queued but not started: without saying so, the Wi-Fi-only rule is indistinguishable
            // from a hang — a row reads "waiting its turn" and nothing ever happens.
            if (indexWaiting) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            if (wifiOnly) R.string.sync_waiting_wifi else R.string.sync_waiting_network,
                        ),
                        color = Muted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // One Stop for the whole queue. Every pass picks up where it stopped when it is next
            // asked for, which is why this is a plain Stop and not a pause.
            if (indexActive) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = viewModel::stopIndexing) {
                        Text(stringResource(R.string.sync_stop), color = Amber, fontSize = 13.sp)
                    }
                }
            }
        }

        // The crawl marker qualifies everything above it, on either side of that branch: it is the
        // reason a book that has left the server may still be on the shelf.
        CrawlLine(lastFullCrawl, now, modifier = Modifier.padding(top = 10.dp))

        SettingsDivider()

        // A view preference, not an edit — which is why it is here for a reader too.
        SettingsSwitchRow(
            label = stringResource(R.string.sync_show_hidden),
            checked = showHidden,
            onCheckedChange = viewModel::setShowHidden,
        )

        // ── Start over ───────────────────────────────────────────────────────
        // The two actions that redo work already done. They are not "fixes" — the rows above say
        // what is incomplete and offer to complete it — these throw away a previous answer, which
        // is a different and rarer thing to want. Neither is a reader's to ask for.
        if (!readsOnly) {
            SettingsDivider()
            SettingsSectionHeader(stringResource(R.string.lib_section_startover))
            SettingsRow(
                label = stringResource(R.string.lib_rebuild),
                summary = stringResource(R.string.lib_rebuild_desc),
                enabled = IndexPass.BOOKS !in queued,
                onClick = { confirmRebuild = true },
            )
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.lib_recheck_lengths),
                summary = stringResource(R.string.lib_recheck_lengths_desc),
                // A file that failed to measure has no length, so it is counted in `unmeasured`.
                // None outstanding therefore means there is nothing to re-try, and offering it
                // anyway buys a pass that reports "measuring lengths for 0 books" and does nothing.
                enabled = IndexPass.LENGTHS !in queued && unmeasured > 0,
                onClick = { confirmRecheck = true },
            )
        }
    }

    if (confirmRebuild) {
        ConfirmDialog(
            title = stringResource(R.string.set_full_rescan_confirm_title),
            body = stringResource(R.string.set_full_rescan_confirm_body),
            confirmLabel = stringResource(R.string.set_full_rescan_confirm_action),
            onConfirm = viewModel::fullScan,
            onDismiss = { confirmRebuild = false },
        )
    }
    if (confirmMeasure) {
        ConfirmDialog(
            title = stringResource(R.string.set_measure_confirm_title),
            body = stringResource(R.string.set_measure_confirm_body),
            confirmLabel = stringResource(R.string.set_measure_confirm_action),
            onConfirm = viewModel::measureBookLengths,
            onDismiss = { confirmMeasure = false },
        )
    }
    if (confirmRecheck) {
        ConfirmDialog(
            title = stringResource(R.string.lib_recheck_confirm_title),
            body = stringResource(R.string.lib_recheck_confirm_body),
            confirmLabel = stringResource(R.string.lib_recheck_confirm_action),
            onConfirm = viewModel::remeasureBookLengths,
            onDismiss = { confirmRecheck = false },
        )
    }
}

// ── the four contents rows ───────────────────────────────────────────────────────────────────
//
// Each one answers the same two questions in the same shape — how complete is this, and what would
// close the gap — so they read as a queue rather than four unrelated buttons. None of them is
// disabled because another is running: asking while something else runs is exactly what queues it.

@Composable
private fun BooksRow(
    queued: Boolean,
    scanState: ScanState,
    bookCount: Int,
    lastScannedAt: Long?,
    now: Long,
    onScan: () -> Unit,
) {
    val scanning = scanState as? ScanState.Scanning
    PassRow(
        label = stringResource(R.string.lib_row_books),
        summary = when {
            scanning != null ->
                stringResource(R.string.sync_scan_folders_books, scanning.directoriesVisited, scanning.booksFound)
            queued -> stringResource(R.string.sync_pass_queued)
            bookCount == 0 -> stringResource(R.string.lib_books_none)
            else -> statusLine(bookCount, lastScannedAt, now)
        },
        action = stringResource(R.string.lib_action_scan),
        // A crawl is the one pass with nothing to be complete about — the folder may always have
        // changed — so its action is never withheld.
        actionEnabled = !queued,
        busy = queued,
        onAction = onScan,
    )
}

@Composable
private fun ArtworkRow(
    queued: Boolean,
    progress: LibraryIndexManager.IndexProgress?,
    artless: Int,
    onFetch: () -> Unit,
) {
    PassRow(
        label = stringResource(R.string.lib_row_artwork),
        summary = when {
            progress != null && progress.total > 0 ->
                stringResource(R.string.sync_fetching_covers, progress.done, progress.total)
            queued -> stringResource(R.string.sync_pass_queued)
            artless == 0 -> stringResource(R.string.lib_artwork_complete)
            else -> stringResource(R.string.lib_artwork_missing, artless)
        },
        action = stringResource(R.string.lib_action_fetch),
        actionEnabled = !queued && artless > 0,
        busy = queued,
        onAction = onFetch,
    )
}

@Composable
private fun LengthsRow(
    queued: Boolean,
    progress: LibraryIndexManager.IndexProgress?,
    unmeasured: Int,
    onMeasure: () -> Unit,
) {
    PassRow(
        label = stringResource(R.string.lib_row_lengths),
        summary = when {
            progress != null && progress.total > 0 ->
                stringResource(
                    R.string.sync_measuring,
                    progress.books, progress.bookTotal, progress.done, progress.total,
                )
            queued -> stringResource(R.string.sync_pass_queued)
            unmeasured == 0 -> stringResource(R.string.lib_lengths_complete)
            else -> stringResource(R.string.lib_lengths_missing, unmeasured)
        },
        action = stringResource(R.string.lib_action_measure),
        actionEnabled = !queued && unmeasured > 0,
        busy = queued,
        onAction = onMeasure,
    )
}

@Composable
private fun CorrectionsRow(
    queued: Boolean,
    count: Int,
    shared: Boolean,
    canPublish: Boolean,
    onPublish: () -> Unit,
) {
    val counted = pluralStringResource(R.plurals.sync_books_count, count, count)
    PassRow(
        label = stringResource(R.string.lib_row_corrections),
        summary = when {
            queued -> stringResource(R.string.sync_pass_queued)
            count == 0 -> stringResource(R.string.lib_corrections_none)
            !shared || !canPublish -> stringResource(R.string.lib_corrections_local, counted)
            else -> stringResource(R.string.lib_corrections_shared, counted)
        },
        action = stringResource(R.string.lib_action_publish),
        // Corrections are published on their own a moment after an edit; this row is for saying so,
        // and for the case where that attempt was made with no connection.
        actionEnabled = !queued && count > 0 && shared && canPublish,
        busy = queued,
        onAction = onPublish,
    )
}

/**
 * What a reader device shows instead of the Contents actions: what the index says is here, and how
 * complete it is.
 *
 * Stated rather than actionable, because none of it is this device's to change. The gaps are worth
 * naming anyway — a shelf with 40 art-less books is not broken, it is waiting for whoever maintains
 * the index, and saying so is the difference between patience and a bug report.
 */
@Composable
private fun ReaderContents(bookCount: Int, artless: Int, unmeasured: Int) {
    val lines = listOfNotNull(
        pluralStringResource(R.plurals.sync_books_count, bookCount, bookCount),
        stringResource(R.string.lib_artwork_missing, artless).takeIf { artless > 0 },
        stringResource(R.string.lib_reader_unmeasured, unmeasured).takeIf { unmeasured > 0 },
    )
    Text(
        lines.joinToString(" · "),
        color = Muted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** One job in the queue: what it knows, and the one thing that would close the gap. */
@Composable
private fun PassRow(
    label: String,
    summary: String,
    action: String,
    actionEnabled: Boolean,
    busy: Boolean,
    onAction: () -> Unit,
) {
    SettingsRow(label = label, summary = summary) {
        if (busy) {
            RowSpinner()
        } else {
            TextButton(onClick = onAction, enabled = actionEnabled, contentPadding = SettingsActionPadding) {
                Text(action)
            }
        }
    }
}

/**
 * "Last full crawl 2 days ago, from Pixel 7" — or the fact that none has run.
 *
 * Worth a line of its own because it is the only thing that authorises deletion: books absent from
 * a complete crawl that post-dates them are pruned, and nothing else can prune them. Before the
 * first one, a book deleted on the server stays on the shelf for ever, which looks like a bug.
 */
@Composable
private fun CrawlLine(crawl: CrawlSummary?, now: Long, modifier: Modifier = Modifier) {
    val text = if (crawl == null) {
        stringResource(R.string.lib_crawl_never)
    } else {
        val ago = DateUtils.getRelativeTimeSpanString(crawl.at, now, DateUtils.MINUTE_IN_MILLIS).toString()
        when {
            crawl.byThisDevice -> stringResource(R.string.lib_crawl_this_device, ago)
            crawl.deviceName != null -> stringResource(R.string.lib_crawl_named_device, ago, crawl.deviceName)
            else -> stringResource(R.string.lib_crawl_other_device, ago)
        }
    }
    Text(text, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = modifier)
}

/** "309 books · scanned 12 minutes ago" — the relative part left to the platform to localise. */
@Composable
private fun statusLine(bookCount: Int, lastScannedAt: Long?, now: Long): String {
    val books = pluralStringResource(R.plurals.sync_books_count, bookCount, bookCount)
    // `now` is ticked by the caller rather than read here: composing it would freeze the phrase at
    // whatever it said when the screen opened, which is a poor showing for the one line whose whole
    // job is saying how stale the index is.
    val when_ = lastScannedAt?.takeIf { it > 0 }?.let {
        DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString()
    } ?: stringResource(R.string.sync_scanned_never)
    return stringResource(R.string.sync_status_line, books, when_)
}

@Composable
private fun RowSpinner() {
    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Amber, strokeWidth = 2.dp)
    }
}
