package com.geozelot.homer.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.data.library.IndexPass
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
fun LibrarySyncScreen(
    viewModel: HomeViewModel,
    onLinkSyncAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val libraryIsShare by viewModel.libraryIsShare.collectAsStateWithLifecycle()
    val libraryWritable by viewModel.libraryWritable.collectAsStateWithLifecycle()
    val libraryRoot by viewModel.libraryRoot.collectAsStateWithLifecycle()
    val sharedIndex by viewModel.sharedCatalogEnabled.collectAsStateWithLifecycle()
    val sharedIndexAvailable by viewModel.sharedCatalogAvailable.collectAsStateWithLifecycle()
    val libraryOwner by viewModel.libraryOwner.collectAsStateWithLifecycle()
    val discovered by viewModel.discovered.collectAsStateWithLifecycle()
    val discovering by viewModel.discovering.collectAsStateWithLifecycle()
    val syncAccount by viewModel.syncAccount.collectAsStateWithLifecycle()
    val progressSync by viewModel.progressSyncEnabled.collectAsStateWithLifecycle()
    val queued by viewModel.indexQueued.collectAsStateWithLifecycle()
    val indexActivity by viewModel.indexActivity.collectAsStateWithLifecycle()

    var confirmSignOut by remember { mutableStateOf(false) }

    // Sweep for Homer-bearing folders when the page opens; the sweep itself is throttled, and it
    // also refreshes the shared-index / owner hints shown below.
    LaunchedEffect(Unit) { viewModel.rediscover() }

    SettingsScaffold(stringResource(R.string.set_sync_title), onBack, modifier) {
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

        // ── Sharing ──────────────────────────────────────────────────────────
        SettingsSectionHeader(stringResource(R.string.lib_section_sharing))
        // What the index is doing right now. The rows above cover the passes; this covers the two
        // steps that are pure network and used to happen in complete silence.
        when (indexActivity) {
            IndexActivity.READING -> SettingsNote(stringResource(R.string.home_reading_index))
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

        TextButton(onClick = { confirmSignOut = true }, contentPadding = SettingsActionPadding) {
            Text(stringResource(R.string.set_sign_out), color = Danger)
        }
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
                    // Three situations, and the switch alone cannot tell them apart: reading an
                    // index somebody else keeps is a different thing from keeping one, and both are
                    // different from every device working it out for itself.
                    enabled && !writable -> context.getString(R.string.set_shared_index_state_reader)
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
