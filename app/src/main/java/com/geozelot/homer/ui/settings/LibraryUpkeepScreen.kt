package com.geozelot.homer.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.IndexPass
import com.geozelot.homer.data.library.LibraryIndexManager
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.sync.facet.CrawlSummary
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNavRow
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.components.rememberActionWidth
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.home.WorklistBook
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
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
    onOpenTemplates: () -> Unit,
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
    val unpublished by viewModel.unpublishedCorrections.collectAsStateWithLifecycle()
    val lastScannedAt by viewModel.lastScannedAt.collectAsStateWithLifecycle()
    val lastFullCrawl by viewModel.lastFullCrawl.collectAsStateWithLifecycle()

    val hidden by viewModel.hiddenBooks.collectAsStateWithLifecycle()
    val undetected by viewModel.undetectedBooks.collectAsStateWithLifecycle()

    var showHiddenList by remember { mutableStateOf(false) }
    var showUndetected by remember { mutableStateOf(false) }
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
                unpublished = unpublished,
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
                    HomerTextButton(onClick = viewModel::stopIndexing) {
                        Text(stringResource(R.string.sync_stop), color = Amber, fontSize = 13.sp)
                    }
                }
            }
        }

        // The crawl marker qualifies everything above it, on either side of that branch: it is the
        // reason a book that has left the server may still be on the shelf.
        CrawlLine(lastFullCrawl, now, modifier = Modifier.padding(top = 10.dp))

        // Not a reader's to change: a pattern rewrites what the shared index says about every
        // book, which is the maintainer's half of the split.
        if (!readsOnly) {
            SettingsDivider()
            SettingsSectionHeader(stringResource(R.string.set_upkeep_organising_header))
            SettingsNavRow(
                label = stringResource(R.string.set_templates_title),
                summary = stringResource(R.string.set_templates_summary),
                onClick = onOpenTemplates,
            )
            // Two worklists. Both are "here is what is wrong, in one place" — which beats finding
            // it by scrolling a library of three hundred and noticing.
            SettingsRow(
                label = stringResource(R.string.set_upkeep_undetected),
                summary = if (undetected.isEmpty()) {
                    stringResource(R.string.set_upkeep_undetected_none)
                } else {
                    pluralStringResource(R.plurals.set_upkeep_undetected_some, undetected.size, undetected.size)
                },
                enabled = undetected.isNotEmpty(),
                onClick = { showUndetected = true },
            )
            SettingsRow(
                label = stringResource(R.string.set_upkeep_hidden),
                summary = if (hidden.isEmpty()) {
                    stringResource(R.string.set_upkeep_hidden_none)
                } else {
                    pluralStringResource(R.plurals.set_upkeep_hidden_some, hidden.size, hidden.size)
                },
                enabled = hidden.isNotEmpty(),
                onClick = { showHiddenList = true },
            )
        }

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

    if (showHiddenList) {
        WorklistDialog(
            title = stringResource(R.string.set_upkeep_hidden),
            lead = stringResource(R.string.set_upkeep_hidden_lead),
            books = hidden,
            actionLabel = stringResource(R.string.set_upkeep_unhide),
            onAction = viewModel::unhide,
            onDismiss = { showHiddenList = false },
        )
    }
    if (showUndetected) {
        WorklistDialog(
            title = stringResource(R.string.set_upkeep_undetected),
            lead = stringResource(R.string.set_upkeep_undetected_lead),
            books = undetected,
            // Nothing to do per book here: what fixes these is a pattern or an edit, and both live
            // elsewhere. The list exists so you know WHICH books to go and look at.
            actionLabel = null,
            onAction = {},
            onDismiss = { showUndetected = false },
        )
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
    /** Of those, the ones the shared index has not been told about — what Publish is FOR. */
    unpublished: Int,
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
            // The state that was missing. Corrections publish themselves a few seconds after an
            // edit, so "all shared" is the normal resting state — and while the row could only ever
            // say "N corrections" with a live button, a working publish was indistinguishable from
            // one silently failing.
            unpublished == 0 -> stringResource(R.string.lib_corrections_all_shared, counted)
            else -> stringResource(
                R.string.lib_corrections_pending,
                pluralStringResource(R.plurals.sync_books_count, unpublished, unpublished),
            )
        },
        action = stringResource(R.string.lib_action_publish),
        // Enabled only when there is something to publish. Edits go up on their own a few seconds
        // after they are made, so this is for the case where that attempt happened with no
        // connection — not a button somebody should feel they have to press after every edit.
        actionEnabled = !queued && unpublished > 0 && shared && canPublish,
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

/**
 * One job in the queue: what it knows, and the one thing that would close the gap.
 *
 * The action is drawn to [PassActionWidth] — one width shared by all four passes — with its label
 * CENTRED in it. Sized to its own word, each button was a different width at the trailing edge of
 * its row, so the four of them made a ragged column that had to be read one row at a time. Equal
 * width settles the box edges, which was the whole problem; left-anchoring the label as well made
 * the words line up at the cost of every button looking like text that had slipped.
 *
 * The spinner takes the same slot rather than replacing the button with something narrower, so
 * starting a pass no longer makes the row twitch.
 */
@Composable
private fun PassRow(
    label: String,
    summary: String,
    action: String,
    actionEnabled: Boolean,
    busy: Boolean,
    onAction: () -> Unit,
) {
    val width = PassActionWidth()
    SettingsRow(label = label, summary = summary) {
        Box(modifier = Modifier.width(width), contentAlignment = Alignment.Center) {
            if (busy) {
                RowSpinner()
            } else {
                HomerTextButton(
                    onClick = onAction,
                    enabled = actionEnabled,
                    contentPadding = SettingsActionPadding,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(action)
                }
            }
        }
    }
}

/**
 * The width every pass action shares — the widest of the four, in whatever language is running.
 *
 * Resolved here rather than passed down from the screen so the set stays next to the rows that use
 * it: add a fifth pass and its label goes in this list, or it will be the one button that does not
 * line up.
 */
@Composable
private fun PassActionWidth(): Dp = rememberActionWidth(
    listOf(
        stringResource(R.string.lib_action_scan),
        stringResource(R.string.lib_action_fetch),
        stringResource(R.string.lib_action_measure),
        stringResource(R.string.lib_action_publish),
    ),
)

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

/**
 * One of Upkeep's worklists: the books, their paths, and optionally one action each.
 *
 * The path is shown because it is what identifies a book when its metadata is the problem — for the
 * undetected list it is the only thing Homer knows, and for the hidden list it is how you tell two
 * volumes with the same title apart.
 */
@Composable
private fun WorklistDialog(
    title: String,
    lead: String,
    books: List<WorklistBook>,
    actionLabel: String?,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(lead, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                books.forEach { book ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, color = Parchment, fontSize = 13.sp)
                            Text(
                                book.id,
                                color = Faint,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        actionLabel?.let { label ->
                            HomerTextButton(
                                onClick = { onAction(book.id) },
                                contentPadding = SettingsActionPadding,
                            ) { Text(label, color = Amber, fontSize = 12.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            HomerTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Muted) }
        },
    )
}
