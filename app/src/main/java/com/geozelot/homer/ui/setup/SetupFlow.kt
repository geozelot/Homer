package com.geozelot.homer.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.SetupAction
import com.geozelot.homer.data.library.SetupFact
import com.geozelot.homer.data.library.SetupOutcome
import com.geozelot.homer.data.library.SetupSituation
import com.geozelot.homer.ui.components.ConfirmDialog
import com.geozelot.homer.ui.components.DiscoveredLibraryCard
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.LeadingIconInset
import com.geozelot.homer.ui.components.ScreenInset
import com.geozelot.homer.ui.components.SettingsExplanation
import com.geozelot.homer.ui.components.SettingsNote
import com.geozelot.homer.ui.components.SettingsSectionHeader
import com.geozelot.homer.ui.components.SettingsSwitchRow
import com.geozelot.homer.ui.login.LoginScreen
import com.geozelot.homer.ui.login.LoginViewModel
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.AmberSoft
import com.geozelot.homer.ui.theme.Faint
import com.geozelot.homer.ui.theme.Ground
import com.geozelot.homer.ui.theme.Line
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface1

/**
 * Setup, in four screens: where the books are, a look at the folder, what was found, and where
 * progress lives.
 *
 * The screen that carries the design is the third one. Everything the old flow expressed as nested
 * questions — is there a library, may I write, does it have rules, do I want my own — is stated
 * there as facts, followed by one recommendation and, where there is a genuine choice, one quiet
 * alternative. [com.geozelot.homer.data.library.decideSetup] decides; nothing here does.
 *
 * @param firstRun true when this is the flow a new install cannot skip. It marks setup as under way
 *   so signing in does not eject the user into the library half way through — and it must stay false
 *   when setup is re-run from settings, or abandoning the re-run would strand them here.
 * @param entry which step to open at. A re-run from settings starts at the screen that answers the
 *   row the user tapped, which is what makes the migrations cost nothing beyond onboarding.
 * @param onDone called when everything is settled, and when a re-run is abandoned.
 */
@Composable
fun SetupFlow(
    firstRun: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    entry: SetupEntry = SetupEntry.BOOKS,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(firstRun) { if (firstRun) viewModel.beginFirstRun() }
    LaunchedEffect(entry) { viewModel.enter(entry) }
    LaunchedEffect(state.done) { if (state.done) onDone() }

    // One handler for the whole flow: the steps are a stack in the ViewModel rather than in a nav
    // graph, because two of them are entered by the credentials arriving rather than by a tap.
    // Disabled on the entry screen rather than swallowed there: the press then falls through to
    // the system, which is what leaves the app on a first run and pops the destination when setup
    // was re-run from settings.
    BackHandler(enabled = state.canGoBack) { viewModel.back() }

    // The arrow on screen, and it answers the same question the gesture does.
    //
    // It used to call `back()` unconditionally, so the two disagreed wherever the flow had been
    // entered part-way: a re-run opened at the findings walked *into* the wizard's folder question
    // instead of out to settings, and one opened at the progress question walked into a findings
    // screen that had never been probed. On the entry step there is nothing behind us that belongs
    // to this flow, so the way back is out — which is [onDone], the caller's own "pop me".
    //
    // Null only on the first screen of a first run, where there is genuinely nowhere to go and the
    // arrow should not be drawn at all.
    val goBack: (() -> Unit)? = when {
        state.canGoBack -> ({ viewModel.back(); Unit })
        firstRun && state.step == SetupStep.WHERE -> null
        else -> onDone
    }

    state.pending?.let { pending ->
        val remote = state.probe?.remoteBookCount ?: 0
        val local = state.probe?.localBookCount ?: 0
        ConfirmDialog(
            title = stringResource(
                if (pending == PendingConfirm.MERGE) R.string.setup_merge_title else R.string.setup_publish_title,
            ),
            body = if (pending == PendingConfirm.MERGE) {
                stringResource(
                    R.string.setup_merge_body,
                    pluralStringResource(R.plurals.sync_books_count, remote, remote),
                    pluralStringResource(R.plurals.sync_books_count, local, local),
                )
            } else {
                stringResource(
                    R.string.setup_publish_body,
                    pluralStringResource(R.plurals.sync_books_count, local, local),
                )
            },
            confirmLabel = stringResource(R.string.action_continue),
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending,
        )
    }

    when (state.step) {
        SetupStep.WHERE -> WhereStep(
            onShareLink = viewModel::chooseShareLink,
            onAccount = viewModel::chooseAccount,
            onBack = goBack,
            modifier = modifier,
        )

        // The login screen, with the choice already made — the chips it normally shows are this
        // flow's first screen now.
        SetupStep.SHARE -> LoginScreen(
            forcedMode = LoginViewModel.Mode.SHARE,
            onBack = goBack,
            modifier = modifier,
        )

        SetupStep.ACCOUNT -> LoginScreen(
            forcedMode = LoginViewModel.Mode.ACCOUNT,
            onBack = goBack,
            modifier = modifier,
        )

        SetupStep.FOLDER -> FolderStep(
            state = state,
            onFolderChange = viewModel::onFolderChange,
            onLook = { viewModel.look() },
            onUse = { viewModel.look(it) },
            onRediscover = { viewModel.discover(force = true) },
            onBack = goBack,
            modifier = modifier,
        )

        SetupStep.FINDINGS -> FindingsStep(
            state = state,
            onTake = viewModel::take,
            onLookAgain = viewModel::reconsider,
            onBack = goBack,
            modifier = modifier,
        )

        SetupStep.CREATE -> CreateStep(
            state = state,
            onRequireSharedUse = viewModel::setRequireSharedUse,
            onEditsAllowed = viewModel::setEditsAllowed,
            onCreate = viewModel::createLibrary,
            onBack = goBack,
            modifier = modifier,
        )

        SetupStep.PROGRESS -> ProgressStep(
            state = state,
            onUseAccount = viewModel::syncProgressToAccount,
            onKeepOnDevice = viewModel::keepProgressOnDevice,
            onBack = goBack,
            modifier = modifier,
        )

        SetupStep.SYNC_LOGIN -> LoginScreen(
            syncMode = true,
            onLinked = viewModel::onSyncAccountLinked,
            onBack = goBack,
            modifier = modifier,
        )
    }
}

// ── the frame ────────────────────────────────────────────────────────────────────────────────

/**
 * The frame the setup screens share.
 *
 * Close to [com.geozelot.homer.ui.settings.SettingsScaffold] and deliberately not it: a setup screen
 * has no page title to go beside a back arrow — it has a question, set as the first thing in the
 * body — and its back affordance is absent on the first screen of a first run, where there is
 * nowhere to go back to.
 */
@Composable
private fun SetupScaffold(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Reserved whether or not there is a button, so the question below does not jump between
        // screens that have one and screens that do not.
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = LeadingIconInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Muted,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = ScreenInset)
                .padding(bottom = 32.dp),
            content = content,
        )
    }
}

/** The question a screen exists to ask, in the serif face the app titles things with. */
@Composable
private fun Question(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = SerifTitle.copy(fontSize = 24.sp, lineHeight = 30.sp),
        color = Parchment,
        modifier = modifier.padding(top = 8.dp, bottom = 20.dp),
    )
}

/**
 * A choice on its own card: a name, a line saying what it means, and a chevron.
 *
 * Big targets rather than a radio list, because these screens each ask one thing and the answer is
 * the whole content of the screen.
 */
@Composable
private fun ChoiceCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Parchment, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Faint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The one thing Homer recommends: what it would do, and what that means, on the accent.
 *
 * Filled rather than outlined so that on the findings screen — where an alternative sits directly
 * underneath as plain text — which of the two is the recommendation needs no reading.
 */
@Composable
private fun RecommendedAction(
    label: String,
    body: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AmberSoft)
            .border(1.dp, Amber, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = Amber,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!enabled) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
        }
        body?.let {
            Text(it, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ── 1 · where ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun WhereStep(
    onShareLink: () -> Unit,
    onAccount: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SetupScaffold(onBack, modifier) {
        Text(
            stringResource(R.string.app_name),
            style = SerifTitle.copy(fontSize = 30.sp),
            color = Amber,
            modifier = Modifier.padding(top = 12.dp),
        )
        Question(stringResource(R.string.setup_where_title), modifier = Modifier.padding(top = 12.dp))
        ChoiceCard(
            title = stringResource(R.string.setup_where_share),
            description = stringResource(R.string.setup_where_share_desc),
            onClick = onShareLink,
        )
        ChoiceCard(
            title = stringResource(R.string.setup_where_account),
            description = stringResource(R.string.setup_where_account_desc),
            onClick = onAccount,
        )
        SettingsNote(stringResource(R.string.setup_where_note), modifier = Modifier.padding(top = 10.dp))
    }
}

// ── 2 · which folder ─────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderStep(
    state: SetupUiState,
    onFolderChange: (String) -> Unit,
    onLook: () -> Unit,
    onUse: (String) -> Unit,
    onRediscover: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SetupScaffold(onBack, modifier) {
        Question(stringResource(R.string.setup_folder_title))

        // The sweep first, and the field under it. A folder that already carries a library is the
        // answer nearly every time — the field is for the one library Homer cannot see yet, which
        // is every library before it has ever been read.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionHeader(
                stringResource(R.string.set_source_other_header),
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            if (state.discovering) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
            } else {
                HomerTextButton(onClick = onRediscover) { Text(stringResource(R.string.sync_rediscover)) }
            }
        }
        if (state.candidates.isEmpty()) {
            SettingsNote(
                stringResource(
                    if (state.discovering) R.string.sync_discovering else R.string.sync_no_libraries,
                ),
            )
        } else {
            state.candidates.forEach { candidate ->
                DiscoveredLibraryCard(candidate, onUse, actionLabel = stringResource(R.string.setup_folder_look))
            }
        }

        OutlinedTextField(
            value = state.folder,
            onValueChange = onFolderChange,
            label = { Text(stringResource(R.string.sync_library_folder)) },
            placeholder = { Text(stringResource(R.string.sync_library_folder_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        SettingsNote(stringResource(R.string.setup_folder_hint), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        RecommendedAction(
            label = stringResource(R.string.setup_folder_look),
            body = null,
            enabled = true,
            onClick = onLook,
        )
    }
}

// ── 3 · what's here ──────────────────────────────────────────────────────────────────────────

@Composable
private fun FindingsStep(
    state: SetupUiState,
    onTake: (SetupAction) -> Unit,
    onLookAgain: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SetupScaffold(onBack, modifier) {
        Question(stringResource(R.string.setup_findings_title))
        Text(
            state.folder.ifEmpty { stringResource(R.string.sync_home_files_root) },
            color = Parchment,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )

        val outcome = state.outcome
        when {
            state.probing -> Looking()
            // Not "there is nothing here": nothing was established, which is a different sentence
            // and a different remedy.
            state.unreachable || outcome == null -> {
                SettingsNote(stringResource(R.string.setup_unreachable), modifier = Modifier.padding(top = 16.dp))
                Spacer(Modifier.height(12.dp))
                RecommendedAction(
                    label = stringResource(R.string.setup_look_again),
                    body = null,
                    enabled = true,
                    onClick = onLookAgain,
                )
            }
            else -> Findings(outcome, state.busy, onTake)
        }
    }
}

@Composable
private fun Looking() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
        Text(
            stringResource(R.string.setup_looking),
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ColumnScope.Findings(
    outcome: SetupOutcome,
    busy: Boolean,
    onTake: (SetupAction) -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    outcome.facts.forEach { Fact(it) }
    Spacer(Modifier.height(20.dp))
    RecommendedAction(
        label = actionLabel(outcome.primary),
        body = stringResource(
            when (outcome.situation) {
                SetupSituation.JOIN_AND_MAINTAIN -> R.string.setup_body_join_maintain
                SetupSituation.JOIN_AS_READER -> R.string.setup_body_join_reader
                SetupSituation.ADOPT_OR_OWN -> R.string.setup_body_adopt
                SetupSituation.CREATE_HERE -> R.string.setup_body_create
                SetupSituation.DEVICE_ONLY -> R.string.setup_body_device_only
                SetupSituation.BLOCKED -> R.string.setup_body_blocked
            },
        ),
        enabled = !busy,
        onClick = { onTake(outcome.primary) },
    )
    // Alternatives are plain text under the recommendation, never a second button: the point of the
    // screen is that Homer has an answer, and two equal buttons would put the decision back.
    outcome.alternatives.forEach { alternative ->
        HomerTextButton(
            onClick = { onTake(alternative) },
            // Dead while the recommendation is being applied. Both write the same settings, and
            // tapping the alternative under a spinner ran a second decision over the first.
            enabled = !busy,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(
                // The alternative's OWN label. Only KEEP_ON_DEVICE is offered as one today, and it
                // is phrased differently down here than it is as a recommendation — but the branch
                // that gave every other action that same phrasing would have mislabelled the first
                // alternative anyone adds, silently and in the one place a decision is made.
                if (alternative == SetupAction.KEEP_ON_DEVICE) {
                    stringResource(R.string.setup_alt_keep_on_device)
                } else {
                    actionLabel(alternative)
                },
            )
        }
    }
}

@Composable
private fun actionLabel(action: SetupAction): String = stringResource(
    when (action) {
        SetupAction.USE_SHARED_INDEX -> R.string.setup_use_library
        SetupAction.CREATE_LIBRARY -> R.string.setup_create_action
        SetupAction.KEEP_ON_DEVICE -> R.string.setup_keep_on_device
        SetupAction.WAIT_FOR_OWNER -> R.string.setup_wait_for_owner
    },
)

/**
 * One observation, as a marked line.
 *
 * The marker carries the reading — accent for what is here and what may be done, plain for an
 * absence, so the shape of the list is legible before any of it is read.
 */
@Composable
private fun Fact(fact: SetupFact) {
    val (mark, accent) = when (fact) {
        is SetupFact.SharedIndex -> "✓" to true
        SetupFact.NoSharedIndex -> "○" to false
        is SetupFact.SharedUseRequired -> "⚑" to true
        is SetupFact.EditsLocked -> "⚑" to true
        is SetupFact.RulesUnreadable -> "⚑" to true
        SetupFact.Writable -> "✓" to false
        SetupFact.ReadOnly -> "ⓘ" to false
        is SetupFact.LocalLibrary -> "ⓘ" to false
    }
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            mark,
            color = if (accent) Amber else Faint,
            fontSize = 12.sp,
            modifier = Modifier.width(20.dp).padding(top = 1.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(factText(fact), color = Parchment, fontSize = 13.sp, lineHeight = 18.sp)
            factDetail(fact)?.let {
                Text(it, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun factText(fact: SetupFact): String = when (fact) {
    is SetupFact.SharedIndex -> stringResource(R.string.setup_fact_index)
    SetupFact.NoSharedIndex -> stringResource(R.string.setup_fact_no_index)
    is SetupFact.SharedUseRequired -> stringResource(R.string.setup_fact_required)
    is SetupFact.EditsLocked -> stringResource(R.string.setup_fact_edits_locked)
    is SetupFact.RulesUnreadable -> stringResource(R.string.setup_fact_rules_unreadable)
    SetupFact.Writable -> stringResource(R.string.setup_fact_writable)
    SetupFact.ReadOnly -> stringResource(R.string.setup_fact_readonly)
    is SetupFact.LocalLibrary -> stringResource(
        R.string.setup_fact_local,
        pluralStringResource(R.plurals.sync_books_count, fact.books, fact.books),
    )
}

/** The second line, where a fact has one: a count, an owner, the folder a rule came from. */
@Composable
private fun factDetail(fact: SetupFact): String? = when (fact) {
    is SetupFact.SharedIndex -> listOfNotNull(
        fact.books?.let { pluralStringResource(R.plurals.sync_books_count, it, it) },
        fact.owner?.let { stringResource(R.string.setup_fact_index_owner, it) },
    ).joinToString(" · ").ifEmpty { null }

    is SetupFact.SharedUseRequired -> listOfNotNull(
        fact.owner?.let { stringResource(R.string.setup_fact_required_by, it) },
        // Named because, with the walk up the tree, a rule can come from a folder the reader never
        // chose — and not saying where it came from makes a locked switch look like a bug.
        fact.atFolder?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.setup_fact_required_at, it) },
    ).joinToString(" · ").ifEmpty { null }

    is SetupFact.EditsLocked -> fact.owner?.let { stringResource(R.string.setup_fact_required_by, it) }
    is SetupFact.RulesUnreadable -> stringResource(R.string.setup_fact_rules_unreadable_desc)
    else -> null
}

// ── 3a · creating one ────────────────────────────────────────────────────────────────────────

@Composable
private fun CreateStep(
    state: SetupUiState,
    onRequireSharedUse: (Boolean) -> Unit,
    onEditsAllowed: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SetupScaffold(onBack, modifier) {
        Question(stringResource(R.string.setup_create_title))
        Text(
            stringResource(
                R.string.setup_create_in,
                state.folder.ifEmpty { stringResource(R.string.sync_home_files_root) },
            ),
            color = Muted,
            fontSize = 12.sp,
        )

        // Rules are the owner's to set. For anybody else the library is still theirs to create —
        // they can write the folder — so the switches are absent rather than disabled, and one line
        // says why.
        if (state.probe?.isOwner == true) {
            SettingsSectionHeader(
                stringResource(R.string.setup_create_rules_header),
                modifier = Modifier.padding(top = 20.dp),
            )
            SettingsSwitchRow(
                label = stringResource(R.string.setup_rule_require),
                checked = state.requireSharedUse,
                onCheckedChange = onRequireSharedUse,
                description = stringResource(R.string.setup_rule_require_desc),
            )
            SettingsSwitchRow(
                label = stringResource(R.string.setup_rule_edits),
                checked = state.editsAllowed,
                onCheckedChange = onEditsAllowed,
                description = stringResource(R.string.setup_rule_edits_desc),
            )
            SettingsExplanation(
                stringResource(R.string.setup_rules_honoured),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            SettingsNote(stringResource(R.string.setup_rules_not_owner), modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.height(12.dp))
        RecommendedAction(
            label = stringResource(R.string.setup_create_confirm),
            body = stringResource(R.string.setup_body_create),
            enabled = !state.busy,
            onClick = onCreate,
        )
    }
}

// ── 4 · where progress lives ─────────────────────────────────────────────────────────────────

@Composable
private fun ProgressStep(
    state: SetupUiState,
    onUseAccount: () -> Unit,
    onKeepOnDevice: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SetupScaffold(onBack, modifier) {
        Question(stringResource(R.string.setup_progress_title))

        // A share link's folder is somebody else's and cannot hold one person's position, so that
        // is the only case with a genuine choice. An account library already IS the progress
        // account, which makes this a confirmation.
        if (!state.libraryIsShare && state.account != null) {
            // Not a question at all. The library is an account, and that account already IS the
            // progress account — so this is a confirmation with a way out, not a fork.
            RecommendedAction(
                label = stringResource(R.string.setup_progress_finish),
                body = stringResource(R.string.setup_progress_settled, state.account),
                enabled = !state.busy,
                onClick = onUseAccount,
            )
            HomerTextButton(
                onClick = onKeepOnDevice,
                enabled = !state.busy,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.setup_progress_settled_alt))
            }
        } else {
            // Inert while the answer is being applied. Either card finishes setup, and finishing
            // pulls progress — long enough on a slow link for a second tap to land on the other one.
            ChoiceCard(
                title = stringResource(R.string.setup_progress_account),
                description = stringResource(R.string.setup_progress_account_desc),
                enabled = !state.busy,
                onClick = onUseAccount,
            )
            ChoiceCard(
                title = stringResource(R.string.setup_progress_device),
                description = stringResource(R.string.setup_progress_device_desc),
                enabled = !state.busy,
                onClick = onKeepOnDevice,
            )
        }
        SettingsNote(stringResource(R.string.setup_progress_note), modifier = Modifier.padding(top = 10.dp))
    }
}
