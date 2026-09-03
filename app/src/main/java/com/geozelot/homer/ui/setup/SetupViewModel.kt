package com.geozelot.homer.ui.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.auth.AuthRepository
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.library.DiscoveredLibrary
import com.geozelot.homer.data.library.LibraryDiscovery
import com.geozelot.homer.data.library.LibrarySetupProbe
import com.geozelot.homer.data.library.SetupAction
import com.geozelot.homer.data.library.SetupOutcome
import com.geozelot.homer.data.library.SetupProbe
import com.geozelot.homer.data.library.SetupState
import com.geozelot.homer.data.library.decideSetup
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.sync.facet.LibraryPolicyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One screen of the setup flow. Order is the order they appear in, not a strict sequence. */
enum class SetupStep {
    /** Where the books live: a shared link, or an account. */
    WHERE,

    /** The share-link form. */
    SHARE,

    /** The account sign-in. */
    ACCOUNT,

    /** Which folder in that account holds the books. */
    FOLDER,

    /** What Homer found there, and what it proposes to do about it. */
    FINDINGS,

    /** The rules for a library being created here. */
    CREATE,

    /** Where the reader's place in each book is saved. */
    PROGRESS,

    /** Signing in to an account for progress alone, when the library is somebody's share. */
    SYNC_LOGIN,
}

/**
 * Where the flow is entered.
 *
 * Setup re-run from settings is the same flow, opened at the step that answers the row the user
 * tapped. That is what makes the three migrations in the design cost nothing beyond onboarding:
 * moving the books, switching which index is used, and starting to sync progress are the three
 * screens this flow already has.
 */
enum class SetupEntry {
    /** The whole flow, from "where do your books live". */
    BOOKS,

    /** Straight to the findings for the folder already in use. */
    INDEX,

    /** Straight to the progress question. */
    PROGRESS,
}

/**
 * A decision that would carry this device's existing books somewhere, held for confirmation.
 *
 * Both merge rather than replace — `FacetMerge` cannot delete a book, and only a complete crawl
 * can — so the damage is bounded either way. What the confirmation buys is the counts: *this folder
 * has 313 books, this device knows 12* is the difference between a merge and a scare, and it is not
 * a sentence anybody can reconstruct afterwards.
 *
 * Only ever raised when this device already holds books. On a first run there is nothing to carry
 * and a dialog would be a speed bump in front of the one path everybody takes.
 */
enum class PendingConfirm {
    /** Adopting an index that is already there, with a local library to fold into it. */
    MERGE,

    /** Making this folder's index out of what this device knows. */
    PUBLISH,
}

/**
 * Everything on screen, and nothing that is not.
 *
 * [outcome] is the whole of the findings screen: it came out of [decideSetup] and the screen renders
 * it rather than re-deciding anything. [unreachable] is the honest third state of a probe — not
 * "there is nothing there", but "nothing could be established", which is a different sentence.
 */
data class SetupUiState(
    val step: SetupStep = SetupStep.WHERE,
    /**
     * The step this run of the flow started at.
     *
     * Back stops here rather than at [SetupStep.WHERE]: a re-run entered at the progress question
     * has no earlier screens of its own, and walking back into a findings screen that was never
     * probed would show "nothing could be established" about a library that is working fine.
     */
    val entryStep: SetupStep = SetupStep.WHERE,
    val folder: String = "",
    val candidates: List<DiscoveredLibrary> = emptyList(),
    val discovering: Boolean = false,
    val probing: Boolean = false,
    val unreachable: Boolean = false,
    val probe: SetupProbe? = null,
    val outcome: SetupOutcome? = null,
    /** The rules a library created here would carry. See [SetupViewModel] for the defaults. */
    val requireSharedUse: Boolean = true,
    val editsAllowed: Boolean = false,
    val busy: Boolean = false,
    /** A decision waiting on the counts being read — see [PendingConfirm]. */
    val pending: PendingConfirm? = null,
    val libraryIsShare: Boolean = false,
    /** The signed-in account, for the progress screen's "every Homer of yours" line. */
    val account: String? = null,
    /** True once everything is settled and the flow should hand over to the library. */
    val done: Boolean = false,
) {
    /**
     * Whether Back has anywhere to go inside the flow.
     *
     * Read by the back handler so that on the first screen the press falls through to the system —
     * which on a first run means leaving the app, and from settings means popping the destination.
     * Swallowing it there left the app with no way out but the home button.
     */
    val canGoBack: Boolean get() = step != entryStep
}

/**
 * Drives setup: two questions to the user, everything else asked of the server.
 *
 * ## Why this is a flow and not a form
 *
 * The dependency chain — is there a library here, may we write, does it have rules, is there
 * progress to sync — reads like a dozen decisions and is mostly a dozen *facts*. So the flow asks
 * where the books are, looks, states what it found with one recommendation, and asks where progress
 * lives. [decideSetup] holds the branching; nothing here re-derives it.
 *
 * ## The defaults for a library being created
 *
 * Shared use required, edits not published. Deliberately the stricter pair: a folder shared with
 * other people is the case that has a problem to prevent, an owner who wants it looser can say so
 * in one tap on the same screen, and a library nobody else can reach is unaffected either way.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val credentialStore: CredentialStore,
    private val authRepository: AuthRepository,
    private val setupProbe: LibrarySetupProbe,
    private val policyRepository: LibraryPolicyRepository,
    private val discovery: LibraryDiscovery,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        // Signing in is a step INSIDE this flow, so the credentials arriving is a transition rather
        // than an exit. Which transition depends on where we were: a share link is itself the
        // library and has nothing to choose, while an account has a folder to pick.
        viewModelScope.launch {
            credentialStore.credentials.collect { credentials ->
                if (credentials == null) return@collect
                _state.update {
                    it.copy(
                        account = credentials.loginName.takeIf { _ -> credentials.kind == WebDavKind.ACCOUNT },
                        libraryIsShare = credentials.kind == WebDavKind.SHARE,
                    )
                }
                when (_state.value.step) {
                    SetupStep.ACCOUNT -> {
                        _state.update { it.copy(step = SetupStep.FOLDER) }
                        discover()
                    }
                    // A share link IS the library: one folder, no choice, straight to what is in it.
                    SetupStep.SHARE -> look("")
                    else -> Unit
                }
            }
        }
    }

    /**
     * Marks the flow as under way, so it survives the process and the credentials arriving.
     *
     * Only for the first run. Entered from settings this must NOT be set: the gate would then keep
     * the user in setup, and abandoning a re-run would strand them there.
     */
    fun beginFirstRun() {
        viewModelScope.launch { librarySettings.setSetupState(SetupState.IN_PROGRESS) }
    }

    /**
     * Opens the flow at the step that answers [entry], for a re-run from settings.
     *
     * Called once; re-entering while already past the entry step would throw away whatever the user
     * had got to on a recomposition.
     */
    fun enter(entry: SetupEntry) {
        if (_state.value.entryStep != SetupStep.WHERE || _state.value.step != SetupStep.WHERE) return
        when (entry) {
            SetupEntry.BOOKS -> Unit
            SetupEntry.INDEX -> viewModelScope.launch {
                val root = librarySettings.libraryRoot.first()
                _state.update { it.copy(entryStep = SetupStep.FINDINGS, folder = root) }
                look(root)
            }
            SetupEntry.PROGRESS -> _state.update {
                it.copy(entryStep = SetupStep.PROGRESS, step = SetupStep.PROGRESS)
            }
        }
    }

    // ── where the books are ──────────────────────────────────────────────────────────────────

    fun chooseShareLink() = _state.update { it.copy(step = SetupStep.SHARE) }

    /** Signing in is skipped when there already is an account — re-running setup is common. */
    fun chooseAccount() {
        if (credentialStore.credentials.value?.kind == WebDavKind.ACCOUNT) {
            _state.update { it.copy(step = SetupStep.FOLDER) }
            discover()
        } else {
            _state.update { it.copy(step = SetupStep.ACCOUNT) }
        }
    }

    fun onFolderChange(value: String) = _state.update { it.copy(folder = value) }

    /**
     * Sweeps for folders that already carry a Homer index, so the commonest case — a second device
     * beside a library somebody has already built — is one tap rather than a typed path.
     */
    fun discover(force: Boolean = false) {
        if (_state.value.discovering) return
        if (!force && _state.value.candidates.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(discovering = true) }
            val found = runCatching { discovery.discover() }.getOrElse { emptyList() }
            _state.update { current ->
                current.copy(
                    discovering = false,
                    candidates = found,
                    // Pre-fill the field with the likeliest answer rather than leaving it blank:
                    // one indexed folder is the overwhelmingly common shape, and typing a path is
                    // the thing this flow exists to avoid.
                    folder = current.folder.ifEmpty {
                        found.firstOrNull { it.hasSharedCatalog }?.relativePath.orEmpty()
                    },
                )
            }
        }
    }

    // ── looking ──────────────────────────────────────────────────────────────────────────────

    /** Probes [folder] and moves to the findings. */
    fun look(folder: String = _state.value.folder) {
        viewModelScope.launch {
            _state.update {
                it.copy(step = SetupStep.FINDINGS, probing = true, unreachable = false, outcome = null)
            }
            val probe = setupProbe.probe(folder)
            if (probe == null) {
                _state.update { it.copy(probing = false, unreachable = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    probing = false,
                    folder = probe.root,
                    probe = probe,
                    outcome = decideSetup(probe),
                    // An owner creating a library gets the strict defaults; a non-owner cannot set
                    // rules at all, so the switches are not shown and their values do not matter.
                    requireSharedUse = true,
                    editsAllowed = false,
                )
            }
        }
    }

    /** Back to the folder question, or to the start for a share link. */
    fun reconsider() {
        _state.update {
            it.copy(
                step = if (it.libraryIsShare) SetupStep.WHERE else SetupStep.FOLDER,
                outcome = null,
                probe = null,
                unreachable = false,
            )
        }
    }

    // ── acting on what was found ─────────────────────────────────────────────────────────────

    fun take(action: SetupAction) {
        val probe = _state.value.probe ?: return
        if (action == SetupAction.CREATE_LIBRARY) {
            _state.update { it.copy(step = SetupStep.CREATE) }
            return
        }
        // Adopting an index while holding books of our own folds the two together. Bounded and
        // reversible — nothing here can delete a book — but not something to do without the counts
        // in front of the user.
        if (action == SetupAction.USE_SHARED_INDEX && probe.localBookCount > 0) {
            _state.update { it.copy(pending = PendingConfirm.MERGE) }
            return
        }
        apply(action)
    }

    /** Goes ahead with whatever [take] or [createLibrary] held back. */
    fun confirmPending() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        when (pending) {
            PendingConfirm.MERGE -> apply(SetupAction.USE_SHARED_INDEX)
            PendingConfirm.PUBLISH -> create()
        }
    }

    fun dismissPending() = _state.update { it.copy(pending = null) }

    private fun apply(action: SetupAction) {
        val probe = _state.value.probe ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            // WAIT_FOR_OWNER adopts the folder with the index switched ON even though there is no
            // index yet: that is precisely the point — when the owner publishes one, this device
            // reads it, and until then it has been told not to go looking for itself.
            adopt(probe, useSharedIndex = action != SetupAction.KEEP_ON_DEVICE)
            _state.update { it.copy(busy = false, step = SetupStep.PROGRESS) }
        }
    }

    fun setRequireSharedUse(value: Boolean) = _state.update { it.copy(requireSharedUse = value) }

    fun setEditsAllowed(value: Boolean) = _state.update { it.copy(editsAllowed = value) }

    /** Creates the library: adopt the folder, then — if we own it — write its rules. */
    fun createLibrary() {
        val probe = _state.value.probe ?: return
        // Same reasoning as the merge: this device's books are about to become the folder's
        // library, and the count is worth saying out loud once.
        if (probe.localBookCount > 0) {
            _state.update { it.copy(pending = PendingConfirm.PUBLISH) }
            return
        }
        create()
    }

    private fun create() {
        val probe = _state.value.probe ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            adopt(probe, useSharedIndex = true)
            if (probe.isOwner) {
                val result = policyRepository.write(
                    sharedIndexRequired = _state.value.requireSharedUse,
                    editsAllowed = _state.value.editsAllowed,
                )
                // Not fatal, and not silent: the library is created either way, and the rules can
                // be set again from settings. Failing the whole creation over a file that is a
                // preference would be the wrong trade.
                if (result != LibraryPolicyRepository.WriteResult.Written) {
                    Log.w(TAG, "library created, but its rules could not be written: $result")
                }
            }
            _state.update { it.copy(busy = false, step = SetupStep.PROGRESS) }
        }
    }

    /**
     * Takes [probe]'s folder as the library.
     *
     * The writability is recorded here because this is the only place that has probed it — a folder
     * shared into an account can be read-only, and assuming otherwise is what made a device promise
     * to publish and then quietly fail. The rules are re-resolved for the new root immediately, so
     * the first pass to be queued is decided under them rather than before them.
     */
    private suspend fun adopt(probe: SetupProbe, useSharedIndex: Boolean) {
        librarySettings.setLibraryRoot(probe.root)
        librarySettings.setLibraryWritable(probe.writable || probe.isOwner)
        librarySettings.setSharedCatalogEnabled(useSharedIndex)
        policyRepository.refresh(force = true)
        Log.i(TAG, "adopted '${probe.root}': shared index ${if (useSharedIndex) "on" else "off"}")
    }

    // ── where progress lives ─────────────────────────────────────────────────────────────────

    /**
     * Sends progress to an account.
     *
     * When the library is an account, that account already IS the progress account —
     * `CredentialStore.syncAccount` derives it — so there is nothing to sign in to and the screen
     * that asked was a confirmation. Only a share link needs a login of its own, because the folder
     * it points at is somebody else's and cannot hold one person's position.
     */
    fun syncProgressToAccount() {
        if (credentialStore.syncAccount.value != null) {
            viewModelScope.launch {
                librarySettings.setProgressSyncEnabled(true)
                finish()
            }
        } else {
            _state.update { it.copy(step = SetupStep.SYNC_LOGIN) }
        }
    }

    fun keepProgressOnDevice() {
        viewModelScope.launch {
            librarySettings.setProgressSyncEnabled(false)
            // A share library may have had an account linked for progress on an earlier run; asking
            // for device-only has to actually unlink it, not merely stop reading it.
            authRepository.unlinkSyncAccount()
            finish()
        }
    }

    /** The sync login landed. Progress has an account now, so the flow is over. */
    fun onSyncAccountLinked() {
        viewModelScope.launch {
            librarySettings.setProgressSyncEnabled(true)
            finish()
        }
    }

    private suspend fun finish() {
        librarySettings.setSetupState(SetupState.DONE)
        val root = librarySettings.libraryRoot.first()
        Log.i(TAG, "setup finished on '$root'")
        _state.update { it.copy(done = true) }
    }

    /** Steps back one screen, or reports that there is nowhere to go. */
    fun back(): Boolean {
        val current = _state.value
        val previous = when (current.step) {
            SetupStep.WHERE -> return false
            SetupStep.SHARE, SetupStep.ACCOUNT -> SetupStep.WHERE
            SetupStep.FOLDER -> SetupStep.WHERE
            SetupStep.FINDINGS -> if (current.libraryIsShare) SetupStep.WHERE else SetupStep.FOLDER
            SetupStep.CREATE -> SetupStep.FINDINGS
            // Going back from the progress question means reopening a decision already applied to
            // settings. Harmless — taking it again simply writes the same three values.
            SetupStep.PROGRESS -> SetupStep.FINDINGS
            SetupStep.SYNC_LOGIN -> SetupStep.PROGRESS
        }
        _state.update { it.copy(step = previous) }
        return true
    }

    private companion object {
        const val TAG = "HomerSetup"
    }
}
