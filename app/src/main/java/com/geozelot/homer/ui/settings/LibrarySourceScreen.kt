package com.geozelot.homer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.data.library.ScanState
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsCard
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
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
    val scanning = scanState is ScanState.Scanning

    var confirmFullScan by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    // Sweep for Homer-bearing folders when the page opens; the sweep itself is throttled, and it
    // also refreshes the shared-index / owner hints shown below.
    LaunchedEffect(Unit) { viewModel.rediscover() }

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
            enabled = !scanning && !libraryIsShare,
            modifier = Modifier.fillMaxWidth(),
        )
        if (libraryIsShare) {
            SettingsNote(
                stringResource(R.string.set_source_share_root_note),
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = viewModel::scan, enabled = !scanning, modifier = Modifier.weight(1f)) {
                Text(
                    if (scanning) {
                        stringResource(R.string.sync_scanning)
                    } else {
                        stringResource(R.string.sync_scan_library)
                    },
                )
            }
            // Disabled during a scan like Scan itself: repeated taps enqueued repeated passes over
            // the whole library.
            FilledTonalButton(
                onClick = viewModel::refreshCoverArt,
                enabled = !scanning,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.sync_refresh_covers))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanStatus(scanState = scanState, bookCount = bookCount)
            Spacer(Modifier.weight(1f))
            // Rebuilding the whole library is slow and re-fetches every cover, so it asks first.
            TextButton(
                onClick = { confirmFullScan = true },
                enabled = !scanning,
                contentPadding = SettingsActionPadding,
            ) { Text(stringResource(R.string.sync_full_rescan)) }
        }
        SettingsNote(stringResource(R.string.sync_scan_desc))

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
                lib.bookCount?.let { append(context.getString(R.string.sync_detail_book_count, it)) }
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

/** How much is in the library, and whether a crawl is running right now. */
@Composable
private fun ScanStatus(scanState: ScanState, bookCount: Int) {
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
        is ScanState.Done -> Text(stringResource(R.string.sync_books_indexed, bookCount), color = Muted, fontSize = 12.sp)
        is ScanState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        ScanState.Idle -> if (bookCount > 0) {
            Text(stringResource(R.string.sync_books_count, bookCount), color = Muted, fontSize = 12.sp)
        } else {
            Text(stringResource(R.string.set_source_not_scanned), color = Faint, fontSize = 12.sp)
        }
    }
}
