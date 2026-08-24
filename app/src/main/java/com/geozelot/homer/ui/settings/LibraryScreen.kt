package com.geozelot.homer.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.data.library.IndexPass
import com.geozelot.homer.data.library.LibraryIndexManager
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.data.sync.facet.CrawlSummary
import com.geozelot.homer.data.sync.facet.IndexActivity
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.DiscoveredLibraryCard
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsCard
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsRow
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.components.TagChip
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import kotlinx.coroutines.delay

/**
 * The library, in three sections that mirror the three facets of the shared index: where the books
 * come from, what Homer has managed to read about them, and what is shared with other devices.
 *
 * It replaces two pages — "Library source" and "Sync" — that split the same subject down the wrong
 * seam: the shared index appeared under source while the thing it shares appeared under sync, and
 * scanning was presented as one big button with three "if something looks wrong" repairs beneath it.
 *
 * The middle section is the substance of the change. Reading a library is four separate jobs of
 * wildly different cost, they queue rather than cancel each other, and each one is either complete
 * or it isn't — so each gets a row that says how complete it is and one action to close the gap.
 */
@Composable
fun LibraryScreen(
    viewModel: HomeViewModel,
    onLinkSyncAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val libraryIsShare by viewModel.libraryIsShare.collectAsStateWithLifecycle()
    val libraryWritable by viewModel.libraryWritable.collectAsStateWithLifecycle()
    val libraryRoot by viewModel.libraryRoot.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val sharedIndex by viewModel.sharedCatalogEnabled.collectAsStateWithLifecycle()
    val sharedIndexAvailable by viewModel.sharedCatalogAvailable.collectAsStateWithLifecycle()
    val libraryOwner by viewModel.libraryOwner.collectAsStateWithLifecycle()
    val discovered by viewModel.discovered.collectAsStateWithLifecycle()
    val discovering by viewModel.discovering.collectAsStateWithLifecycle()
    val syncAccount by viewModel.syncAccount.collectAsStateWithLifecycle()
    val progressSync by viewModel.progressSyncEnabled.collectAsStateWithLifecycle()
    val queued by viewModel.indexQueued.collectAsStateWithLifecycle()
    val indexActive by viewModel.indexActive.collectAsStateWithLifecycle()
    val indexProgress by viewModel.indexProgress.collectAsStateWithLifecycle()
    val indexActivity by viewModel.indexActivity.collectAsStateWithLifecycle()
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
    var confirmSignOut by remember { mutableStateOf(false) }

    // Sweep for Homer-bearing folders when the page opens; the sweep itself is throttled, and it
    // also refreshes the shared-index / owner hints shown below.
    LaunchedEffect(Unit) { viewModel.rediscover() }

    // Re-reads the clock so "scanned 12 minutes ago" keeps counting while the screen stays open.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    SettingsScaffold(stringResource(R.string.set_library_title), onBack, modifier) {
        // ── Source ───────────────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.lib_section_source))
        CurrentSourceCard(
            login = account?.loginName,
            host = account?.serverUrl?.substringAfter("://"),
            isShare = libraryIsShare,
            writable = libraryWritable,
        )

        // A share link points at exactly one folder — the share IS the library — so editing the
        // folder would only break it.
        OutlinedTextField(
            value = libraryRoot,
            onValueChange = viewModel::onLibraryRootChange,
            label = { Text(stringResource(R.string.sync_library_folder)) },
            placeholder = { Text(stringResource(R.string.sync_library_folder_placeholder)) },
            singleLine = true,
            enabled = IndexPass.BOOKS !in queued && !libraryIsShare,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        if (libraryIsShare) {
            SettingsNote(
                stringResource(R.string.set_source_share_root_note),
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        DiscoveredLibraries(
            discovered = discovered,
            discovering = discovering,
            // The button is an explicit request: bypass the freshness throttle.
            onRediscover = { viewModel.rediscover(force = true) },
            onUse = viewModel::onLibraryRootChange,
        )

        SettingsDivider()

        // ── Contents ─────────────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.lib_section_contents))
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

        // Queued but not started: without saying so, the Wi-Fi-only rule is indistinguishable from
        // a hang — a row reads "waiting its turn" and nothing ever happens.
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

        // One Stop for the whole queue. Every pass picks up where it stopped when it is next asked
        // for, which is why this is a plain Stop and not a pause.
        if (indexActive) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = viewModel::stopIndexing) {
                    Text(stringResource(R.string.sync_stop), color = Amber, fontSize = 13.sp)
                }
            }
        }

        // The crawl marker sits under the contents rows because it qualifies all of them: it is the
        // reason a book that has left the server may still be on the shelf.
        CrawlLine(lastFullCrawl, now, modifier = Modifier.padding(top = 10.dp))

        SettingsDivider()

        // ── Sharing ──────────────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.lib_section_sharing))
        // What the index is doing right now. The rows above cover the passes; this covers the two
        // steps that are pure network and used to happen in complete silence.
        when (indexActivity) {
            IndexActivity.READING -> SettingsNote(stringResource(R.string.home_reading_index))
            IndexActivity.CONVERTING -> SettingsNote(stringResource(R.string.home_converting_index))
            IndexActivity.PUBLISHING -> SettingsNote(stringResource(R.string.lib_index_publishing))
            IndexActivity.IDLE -> Unit
        }
        SharedIndexSetting(
            enabled = sharedIndex,
            available = sharedIndexAvailable,
            writable = libraryWritable,
            owner = libraryOwner,
            onChange = viewModel::setSharedCatalog,
        )

        SettingsDivider()

        if (libraryIsShare) {
            // A share link is somebody else's storage: progress stays on this device until the
            // user points it at an account of their own.
            SettingsSectionHeader(stringResource(R.string.set_sync_account_header))
            val sync = syncAccount
            SettingsRow(
                label = if (sync != null) {
                    stringResource(
                        R.string.settings_sync_to,
                        "${sync.loginName}@${sync.serverUrl.substringAfter("://")}",
                    )
                } else {
                    stringResource(R.string.settings_sync_device)
                },
            ) {
                if (sync != null) {
                    TextButton(onClick = viewModel::unlinkSyncAccount, contentPadding = SettingsActionPadding) {
                        Text(stringResource(R.string.settings_sync_stop))
                    }
                } else {
                    TextButton(onClick = onLinkSyncAccount, contentPadding = SettingsActionPadding) {
                        Text(stringResource(R.string.settings_sync_link))
                    }
                }
            }
        } else {
            SettingsSwitchRow(
                label = stringResource(R.string.sync_progress_switch),
                checked = progressSync,
                onCheckedChange = viewModel::setProgressSync,
                description = if (progressSync) {
                    stringResource(R.string.sync_progress_on_desc)
                } else {
                    stringResource(R.string.sync_progress_off_desc)
                },
            )
        }

        SettingsDivider()

        SettingsSwitchRow(
            label = stringResource(R.string.sync_show_hidden),
            checked = showHidden,
            onCheckedChange = viewModel::setShowHidden,
        )

        SettingsDivider()

        // ── Start over ───────────────────────────────────────────────────────
        // The two actions that redo work already done. They are not "fixes" — the rows above say
        // what is incomplete and offer to complete it — these throw away a previous answer, which
        // is a different and rarer thing to want.
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
            enabled = IndexPass.LENGTHS !in queued,
            onClick = { confirmRecheck = true },
        )

        SettingsDivider()

        TextButton(onClick = { confirmSignOut = true }, contentPadding = SettingsActionPadding) {
            Text(stringResource(R.string.set_sign_out), color = Danger)
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
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.set_sign_out_confirm_title),
            body = stringResource(R.string.set_sign_out_confirm_body),
            confirmLabel = stringResource(R.string.set_sign_out),
            onConfirm = viewModel::logout,
            onDismiss = { confirmSignOut = false },
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

// ── source ───────────────────────────────────────────────────────────────────────────────────

/** Which server (and which kind of access) the library is being read from right now. */
@Composable
private fun CurrentSourceCard(login: String?, host: String?, isShare: Boolean, writable: Boolean) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    host == null -> stringResource(R.string.set_source_none)
                    isShare -> stringResource(R.string.settings_library_share, host)
                    else -> stringResource(R.string.settings_account, login.orEmpty(), host)
                },
                color = Parchment,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            if (!writable) {
                TagChip(stringResource(R.string.set_source_readonly), OnAmber, Amber)
            }
        }
        Text(
            text = if (isShare) {
                stringResource(R.string.set_source_share_desc)
            } else {
                stringResource(R.string.set_source_account_desc)
            },
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The shared library index, framed as what it actually buys the user: devices that don't have to
 * crawl the library or re-extract cover art. It carries no personal data and never a position.
 */
@Composable
private fun SharedIndexSetting(
    enabled: Boolean,
    available: Boolean,
    writable: Boolean,
    owner: String?,
    onChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // A read-only share can read an index that's already there but can never write one, so the
    // toggle would be a promise the backend can't keep.
    val canToggle = writable || available
    SettingsSwitchRow(
        label = stringResource(R.string.set_shared_index_title),
        checked = enabled,
        onCheckedChange = onChange,
        enabled = canToggle,
        description = stringResource(R.string.set_shared_index_desc),
    )
    SettingsExplanation(
        buildString {
            append(
                when {
                    enabled && available -> context.getString(R.string.set_shared_index_state_reading)
                    enabled -> context.getString(R.string.set_shared_index_state_publishing)
                    else -> context.getString(R.string.set_shared_index_state_off)
                },
            )
            if (enabled && owner != null) append(context.getString(R.string.set_shared_index_owner, owner))
        },
    )
    if (!writable) SettingsNote(stringResource(R.string.set_shared_index_readonly_note))
}

/** Other Homer libraries the discovery sweep found on the server, each adoptable as the root. */
@Composable
private fun DiscoveredLibraries(
    discovered: List<DiscoveredLibrary>,
    discovering: Boolean,
    onRediscover: () -> Unit,
    onUse: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsSectionHeader(
            stringResource(R.string.set_source_other_header),
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        if (discovering) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onRediscover, contentPadding = SettingsActionPadding) {
                Text(stringResource(R.string.sync_rediscover))
            }
        }
    }
    if (discovered.isEmpty()) {
        SettingsNote(
            if (discovering) {
                stringResource(R.string.sync_discovering)
            } else {
                stringResource(R.string.sync_no_libraries)
            },
        )
    } else {
        discovered.forEach { lib -> DiscoveredLibraryCard(lib, onUse) }
    }
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
