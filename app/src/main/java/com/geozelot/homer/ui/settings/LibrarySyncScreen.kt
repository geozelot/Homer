package com.geozelot.homer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.library.LibraryRole
import com.geozelot.homer.data.library.LibraryStanding
import com.geozelot.homer.data.library.Restriction
import com.geozelot.homer.data.sync.facet.IndexActivity
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.SettingsActionPadding
import com.geozelot.homer.ui.components.SettingsDivider
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.components.TagChip
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.setup.SetupEntry
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Danger
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment

/**
 * The library, as three facts and one way to change each: where the books are, which index is in
 * use, and where the reader's place is saved.
 *
 * ## Why it is three facts rather than five controls
 *
 * It used to be a source card, an editable root field, a discovery list, a shared-index switch and
 * a sync section — five controls for three things, several of which could contradict each other.
 * Typing a folder into a field did not tell you whether it was writable, whether anything was
 * there, or what its owner allowed; the switch beside it promised something the backend might
 * refuse.
 *
 * Every *change* is now the setup flow, opened at the screen that answers the row: it probes, states
 * what it found, and applies one decision. So there is nothing here that can be set to a value the
 * library will not honour — and the three migrations the design asks for (start syncing progress,
 * move a private library to a server, adopt the shared one instead of your own) cost no code beyond
 * the flow that already exists.
 *
 * The rules panel is the exception, and it appears for the owner alone. Everyone else sees the two
 * chips in the index row and nothing to press, which is what "only the owner can change this" looks
 * like when it is expressed as absence rather than as a disabled switch with an explanation.
 */
@Composable
fun LibrarySyncScreen(
    viewModel: HomeViewModel,
    onChange: (SetupEntry) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val libraryRoot by viewModel.libraryRoot.collectAsStateWithLifecycle()
    val libraryIsShare by viewModel.libraryIsShare.collectAsStateWithLifecycle()
    val standing by viewModel.standing.collectAsStateWithLifecycle()
    val syncAccount by viewModel.syncAccount.collectAsStateWithLifecycle()
    val progressSync by viewModel.progressSyncEnabled.collectAsStateWithLifecycle()
    val bookCount by viewModel.bookCount.collectAsStateWithLifecycle()
    val indexActivity by viewModel.indexActivity.collectAsStateWithLifecycle()

    var confirmSignOut by remember { mutableStateOf(false) }

    // The rules are what every row below is qualified by, so they are worth being current when the
    // page is opened rather than up to six hours old.
    LaunchedEffect(Unit) { viewModel.refreshLibraryRules() }

    // No status line under the title: what qualifies this page is the rules, and they are stated
    // as chips on the row they qualify. "Last full crawl" belongs to Upkeep, beside the passes it
    // authorises.
    SettingsScaffold(stringResource(R.string.set_sync_title), onBack, modifier) {
        // ── books ────────────────────────────────────────────────────────────
        StateRow(
            header = stringResource(R.string.lib_fact_books),
            value = libraryRoot.ifEmpty { stringResource(R.string.sync_home_files_root) },
            detail = booksDetail(account, libraryIsShare, standing, bookCount),
            onChange = { onChange(SetupEntry.BOOKS) },
        )

        SettingsDivider()

        // ── the index ────────────────────────────────────────────────────────
        StateRow(
            header = stringResource(R.string.lib_fact_index),
            value = stringResource(
                when {
                    standing.role == LibraryRole.READER -> R.string.lib_index_reader
                    standing.usesSharedIndex -> R.string.lib_index_shared
                    bookCount > 0 -> R.string.lib_index_private
                    else -> R.string.lib_index_none
                },
            ),
            detail = indexDetail(standing, bookCount),
            chips = ruleChips(standing),
            onChange = { onChange(SetupEntry.INDEX) },
        )
        // What the index is doing right now. Both steps are pure network and used to happen in
        // complete silence.
        when (indexActivity) {
            IndexActivity.READING -> SettingsNote(stringResource(R.string.home_reading_index))
            IndexActivity.PUBLISHING -> SettingsNote(stringResource(R.string.lib_index_publishing))
            IndexActivity.IDLE -> Unit
        }
        // The one sentence that says why nothing is happening — the answer to every "X does
        // nothing" report, resolved once rather than re-derived per screen.
        whyText(standing)?.let { SettingsExplanation(it) }

        SettingsDivider()

        // ── progress ─────────────────────────────────────────────────────────
        val progressAccount = syncAccount?.takeIf { progressSync }
        StateRow(
            header = stringResource(R.string.lib_fact_progress),
            value = progressAccount?.let {
                stringResource(
                    R.string.lib_progress_account,
                    "${it.loginName}@${it.serverUrl.substringAfter("://")}",
                )
            } ?: stringResource(R.string.lib_progress_device),
            detail = stringResource(
                if (progressAccount != null) {
                    R.string.lib_progress_desc_account
                } else {
                    R.string.lib_progress_desc_device
                },
            ),
            onChange = { onChange(SetupEntry.PROGRESS) },
        )

        // ── the rules, for the owner ─────────────────────────────────────────
        if (standing.mayEditRules) {
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SettingsSectionHeader(stringResource(R.string.lib_rules_header))
                TagChip(stringResource(R.string.lib_rules_owner), OnAmber, Amber)
            }
            SettingsSwitchRow(
                label = stringResource(R.string.setup_rule_require),
                checked = standing.policy.sharedIndexRequired,
                onCheckedChange = {
                    viewModel.setLibraryRules(it, standing.policy.editsAllowed)
                },
                description = stringResource(R.string.setup_rule_require_desc),
            )
            SettingsSwitchRow(
                label = stringResource(R.string.setup_rule_edits),
                checked = standing.policy.editsAllowed,
                onCheckedChange = {
                    viewModel.setLibraryRules(standing.policy.sharedIndexRequired, it)
                },
                description = stringResource(R.string.setup_rule_edits_desc),
            )
            SettingsExplanation(stringResource(R.string.setup_rules_honoured))
        }

        SettingsDivider()

        HomerTextButton(onClick = { confirmSignOut = true }, contentPadding = SettingsActionPadding) {
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

// ── the row ──────────────────────────────────────────────────────────────────────────────────

/**
 * One fact about the library: what it is, a line qualifying it, any rules that apply, and the one
 * way to change it.
 *
 * The action is a text button rather than the whole row being tappable. These rows are read far more
 * often than they are acted on — the page exists to answer "what is this library?" — and a tappable
 * row invites a tap on the way to reading it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StateRow(
    header: String,
    value: String,
    detail: String?,
    onChange: () -> Unit,
    chips: List<String> = emptyList(),
) {
    SettingsSectionHeader(header)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp, bottom = 4.dp)) {
            Text(value, color = Parchment, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            detail?.let {
                Text(
                    it,
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (chips.isNotEmpty()) {
                FloatingChips(chips)
            }
        }
        HomerTextButton(onClick = onChange, contentPadding = SettingsActionPadding) {
            Text(stringResource(R.string.lib_change))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingChips(chips: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        chips.forEach { TagChip(it, Amber, AmberSoft) }
    }
}

// ── the lines under each fact ────────────────────────────────────────────────────────────────

/** Which server the books come from, how they are reached, and how many there are. */
@Composable
private fun booksDetail(
    account: NextcloudCredentials?,
    isShare: Boolean,
    standing: LibraryStanding,
    bookCount: Int,
): String? {
    val credentials = account ?: return null
    val host = credentials.serverUrl.substringAfter("://")
    val where = if (isShare) {
        stringResource(R.string.lib_books_share, host)
    } else {
        stringResource(R.string.lib_books_account, credentials.loginName, host)
    }
    // The folder's own writability, not "are we publishing": a read-only share with the index
    // switched off publishes nothing and is still read-only, and saying otherwise here would be
    // the one line on the page that lies.
    val access = stringResource(
        if (standing.backendWritable) R.string.lib_access_write else R.string.lib_access_read,
    )
    val books = if (bookCount > 0) {
        pluralStringResource(R.plurals.sync_books_count, bookCount, bookCount)
    } else {
        null
    }
    return listOfNotNull(where, access, books).joinToString(" · ")
}

/** Who keeps the index, and how much is in it. */
@Composable
private fun indexDetail(standing: LibraryStanding, bookCount: Int): String? {
    if (!standing.usesSharedIndex) return null
    val owner = standing.policy.owner
    val keeper = when {
        standing.mayPublishIndex -> stringResource(R.string.lib_index_kept_by_you)
        owner != null -> stringResource(R.string.lib_index_kept_by, owner)
        else -> null
    }
    val books = if (bookCount > 0) {
        pluralStringResource(R.plurals.sync_books_count, bookCount, bookCount)
    } else {
        null
    }
    return listOfNotNull(keeper, books).joinToString(" · ").ifEmpty { null }
}

/**
 * The rules in force, as chips.
 *
 * Shown to everyone, including the owner — the owner's switches are further down the page, and a
 * rule that only appears where it can be edited would leave the reader's locked index row
 * unexplained.
 */
@Composable
private fun ruleChips(standing: LibraryStanding): List<String> = buildList {
    if (!standing.policy.understood) {
        add(stringResource(R.string.lib_chip_rules_unreadable))
        return@buildList
    }
    if (standing.policy.sharedIndexRequired) add(stringResource(R.string.lib_chip_shared_required))
    if (!standing.policy.editsAllowed) add(stringResource(R.string.lib_chip_edits_locked))
}

/** Why the expensive work, or a published edit, is not happening. Null when it is. */
@Composable
private fun whyText(standing: LibraryStanding): String? =
    when (standing.restriction ?: standing.editRestriction) {
        is Restriction.RulesUnreadable -> stringResource(R.string.lib_why_rules_unreadable)
        Restriction.ReadOnlyLibrary -> stringResource(R.string.lib_why_reader)
        is Restriction.EditsLocked -> stringResource(R.string.lib_why_edits_locked)
        Restriction.NoLibrary, null -> null
    }
