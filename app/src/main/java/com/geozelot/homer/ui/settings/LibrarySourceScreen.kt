package com.geozelot.homer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.data.library.IndexPass
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.ui.components.ConfirmDialog
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
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.Surface1

/**
 * "Where your books come from": the server the library is read from, which folder on it holds the
 * books, and the actions that re-read it. Signing out lives at the bottom, behind a confirmation.
 */
@Composable
fun LibrarySourceScreen(
    viewModel: HomeViewModel,
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
    // A crawl is only one of the passes the worker runs; a cover or length pass leaves scanState
    // Idle for many minutes. Each row therefore reads its own pass out of `queued` — a pass that is
    // already outstanding is the only thing a row refuses, because asking twice is not two passes.
    val queued by viewModel.indexQueued.collectAsStateWithLifecycle()
    val indexActive by viewModel.indexActive.collectAsStateWithLifecycle()
    val crawling = scanState is ScanState.Scanning || IndexPass.BOOKS in queued
    val unmeasured by viewModel.unmeasuredCount.collectAsStateWithLifecycle()
    val lastScannedAt by viewModel.lastScannedAt.collectAsStateWithLifecycle()
    val indexProgress by viewModel.indexProgress.collectAsStateWithLifecycle()
    val indexWaiting by viewModel.indexWaiting.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()

    var confirmFullScan by remember { mutableStateOf(false) }
    var confirmMeasure by remember { mutableStateOf(false) }
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

    SettingsScaffold(stringResource(R.string.set_source_title), onBack, modifier) {
        CurrentSourceCard(
            login = account?.loginName,
            host = account?.serverUrl?.substringAfter("://"),
            isShare = libraryIsShare,
            writable = libraryWritable,
        )

        Spacer(Modifier.height(16.dp))

        // A share link points at exactly one folder — the share IS the library — so editing the
        // folder would only break it.
        OutlinedTextField(
            value = libraryRoot,
            onValueChange = viewModel::onLibraryRootChange,
            label = { Text(stringResource(R.string.sync_library_folder)) },
            placeholder = { Text(stringResource(R.string.sync_library_folder_placeholder)) },
            singleLine = true,
            enabled = !crawling && !libraryIsShare,
            modifier = Modifier.fillMaxWidth(),
        )
        if (libraryIsShare) {
            SettingsNote(
                stringResource(R.string.set_source_share_root_note),
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // One primary action, because it is the right answer nearly every time; the three
        // recovery actions step down to rows beneath it. This used to be four buttons of four
        // different weights across three rows, which made none of them read as the obvious one,
        // plus a paragraph explaining all four — each row now carries its own explanation instead.
        Button(
            onClick = viewModel::scan,
            enabled = !crawling,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(
                if (crawling) stringResource(R.string.sync_scanning) else stringResource(R.string.sync_scan_library),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            ScanStatus(scanState = scanState, bookCount = bookCount, lastScannedAt = lastScannedAt, now = now)
        }

        // Queued but not started: without saying so, the Wi-Fi-only rule is indistinguishable from
        // a hang — the button reads "Scanning…" and nothing ever happens.
        if (indexWaiting) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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

        // A length pass runs for a long time and resumes itself after the app is killed, so there
        // has to be a way to call it off. Every pass picks up where it stopped, which is why this
        // is a plain Stop and not a pause.
        if (indexActive) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = viewModel::stopIndexing) {
                    Text(stringResource(R.string.sync_stop), color = Amber, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionHeader(stringResource(R.string.sync_fix_header))

        val artworkQueued = IndexPass.ARTWORK in queued
        val covers = indexProgress?.takeIf { it.pass == IndexPass.ARTWORK }
        SettingsRow(
            label = stringResource(R.string.sync_fix_covers),
            summary = when {
                covers != null && covers.total > 0 ->
                    stringResource(R.string.sync_fetching_covers, covers.done, covers.total)
                artworkQueued -> stringResource(R.string.sync_pass_queued)
                else -> stringResource(R.string.sync_fix_covers_desc)
            },
            enabled = !artworkQueued,
            onClick = viewModel::refreshCoverArt,
            trailing = { if (artworkQueued) RowSpinner() },
        )
        SettingsDivider()

        val lengthsQueued = IndexPass.LENGTHS in queued
        val lengths = indexProgress?.takeIf { it.pass == IndexPass.LENGTHS }
        SettingsRow(
            label = stringResource(R.string.sync_fix_lengths),
            summary = when {
                lengths != null && lengths.total > 0 ->
                    stringResource(
                        R.string.sync_measuring,
                        lengths.books, lengths.bookTotal, lengths.done, lengths.total,
                    )
                lengthsQueued -> stringResource(R.string.sync_pass_queued)
                unmeasured == 0 -> stringResource(R.string.sync_fix_lengths_none)
                else -> stringResource(R.string.sync_fix_lengths_desc, unmeasured)
            },
            // Nothing to do when every book already has a length, and the summary says so.
            enabled = !lengthsQueued && unmeasured > 0,
            onClick = { confirmMeasure = true },
            trailing = { if (lengthsQueued) RowSpinner() },
        )
        SettingsDivider()

        SettingsRow(
            label = stringResource(R.string.sync_fix_rebuild),
            summary = stringResource(R.string.sync_fix_rebuild_desc),
            enabled = !crawling,
            onClick = { confirmFullScan = true },
        )

        SettingsDivider()

        SettingsSwitchRow(
            label = stringResource(R.string.sync_show_hidden),
            checked = showHidden,
            onCheckedChange = viewModel::setShowHidden,
        )

        SettingsDivider()

        SharedIndexSetting(
            enabled = sharedIndex,
            available = sharedIndexAvailable,
            writable = libraryWritable,
            owner = libraryOwner,
            onChange = viewModel::setSharedCatalog,
        )

        SettingsDivider()

        DiscoveredLibraries(
            discovered = discovered,
            discovering = discovering,
            // The button is an explicit request: bypass the freshness throttle.
            onRediscover = { viewModel.rediscover(force = true) },
            onUse = viewModel::onLibraryRootChange,
        )

        SettingsDivider()

        TextButton(onClick = { confirmSignOut = true }, contentPadding = SettingsActionPadding) {
            Text(stringResource(R.string.set_sign_out), color = Danger)
        }
    }

    if (confirmFullScan) {
        ConfirmDialog(
            title = stringResource(R.string.set_full_rescan_confirm_title),
            body = stringResource(R.string.set_full_rescan_confirm_body),
            confirmLabel = stringResource(R.string.set_full_rescan_confirm_action),
            onConfirm = viewModel::fullScan,
            onDismiss = { confirmFullScan = false },
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

/** Which server (and which kind of access) the library is being read from right now. */
@Composable
private fun CurrentSourceCard(login: String?, host: String?, isShare: Boolean, writable: Boolean) {
    SettingsSectionHeader(stringResource(R.string.set_source_current_header))
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
 * crawl the library or re-extract cover art. It carries no personal data and never a position, so
 * it is deliberately not presented as part of "Sync".
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
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun DiscoveredLibraryCard(lib: DiscoveredLibrary, onUse: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (lib.isCurrentRoot) AmberSoft else Surface1)
            .border(1.dp, if (lib.isCurrentRoot) Amber else Line, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lib.relativePath.ifEmpty { stringResource(R.string.sync_home_files_root) },
                color = Parchment,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (lib.isCurrentRoot) TagChip(stringResource(R.string.sync_tag_in_use), OnAmber, Amber)
        }
        val detail = buildString {
            append(
                when (lib.kind) {
                    DiscoveredLibrary.Kind.FILES_ROOT -> context.getString(R.string.sync_kind_files_root)
                    DiscoveredLibrary.Kind.LIBRARY_ROOT -> context.getString(R.string.sync_kind_library_root)
                    DiscoveredLibrary.Kind.SHARED_FOLDER -> context.getString(R.string.sync_kind_shared_folder)
                },
            )
            if (lib.hasSharedCatalog) {
                append(context.getString(R.string.set_detail_shared_index))
                lib.bookCount?.let {
                    append(context.resources.getQuantityString(R.plurals.sync_detail_book_count, it, it))
                }
            }
            if (lib.hasPrivateIndex) append(context.getString(R.string.sync_detail_private_progress))
            lib.owner?.let { append(context.getString(R.string.sync_detail_owner, it)) }
        }
        Text(detail, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        if (!lib.isCurrentRoot) {
            TextButton(
                onClick = { onUse(lib.relativePath) },
                contentPadding = SettingsActionPadding,
            ) { Text(stringResource(R.string.sync_use_as_library)) }
        }
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
    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Amber, strokeWidth = 2.dp)
}

/**
 * The line under the Scan button: what is in the library, and when it was last read.
 *
 * "when" is the question that actually brings people to this screen — without it the only way to
 * tell whether a scan is overdue is to run one.
 */
@Composable
private fun ScanStatus(scanState: ScanState, bookCount: Int, lastScannedAt: Long?, now: Long) {
    when (val state = scanState) {
        is ScanState.Scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Amber, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.sync_scan_folders_books, state.directoriesVisited, state.booksFound),
                color = Muted,
                fontSize = 12.sp,
            )
        }
        is ScanState.Done -> Text(statusLine(bookCount, lastScannedAt, now), color = Muted, fontSize = 12.sp)
        is ScanState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        ScanState.Idle -> if (bookCount > 0) {
            Text(statusLine(bookCount, lastScannedAt, now), color = Muted, fontSize = 12.sp)
        } else {
            Text(stringResource(R.string.set_source_not_scanned), color = Faint, fontSize = 12.sp)
        }
    }
}
