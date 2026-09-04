package com.geozelot.homer.data.library

import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.sync.facet.PolicyInForce

/**
 * What Homer found at a folder, before anything has been adopted.
 *
 * Everything here is an observation, and none of it is a choice. That separation is the whole
 * reason setup can be four screens instead of the twelve-node tree it looks like on paper: the
 * questions a person has to answer are *where are the books* and *where does progress live*, and
 * every other node in that tree is one of these facts.
 */
data class SetupProbe(
    /** How the folder is reached. */
    val kind: WebDavKind,
    /** Files-root-relative folder being considered. */
    val root: String,
    /**
     * Whether this device can write there.
     *
     * Probed for an account too, not only for a share link: a folder somebody shared *into* an
     * account is reached with account credentials and can still be read-only, which is the case the
     * old flow had no way to notice.
     */
    val writable: Boolean,
    /** Whether the signed-in account owns the folder. */
    val isOwner: Boolean,
    /**
     * The folder's owner as the server names them, when it exposed one.
     *
     * Kept apart from [isOwner] because it answers a different question — *whose library is this?*
     * — and it is asked of a share link too, where ownership is somebody else's by definition and
     * "kept by andre" is exactly the caption a reader wants. It is also independent of [policy]: a
     * library with no rules at all still has an owner worth naming.
     */
    val owner: String?,
    /** The rules that would apply here, walked up from [root]. */
    val policy: PolicyInForce,
    /** Whether a shared Homer index is already there. */
    val hasSharedIndex: Boolean,
    /** How many books that index holds, or null when it could not be counted. */
    val remoteBookCount: Int?,
    /** How many books this device already knows — what a migration would be carrying. */
    val localBookCount: Int,
)

/** The one thing Homer proposes, or offers as an alternative. */
enum class SetupAction {
    /** Adopt the index that is already here — as a maintainer, or as a reader. */
    USE_SHARED_INDEX,

    /**
     * Make this folder's index: set the rules, publish whatever this device already knows, scan.
     *
     * There is no separate "upload my library" action, because at one root there is only one index:
     * a device with books of its own creates the index *from* them, and a device adopting an index
     * that already exists merges into it on its next push either way.
     */
    CREATE_LIBRARY,

    /** No shared index. This device works everything out for itself and shares none of it. */
    KEEP_ON_DEVICE,

    /** Nothing to do here yet — the owner requires an index and has not published one. */
    WAIT_FOR_OWNER,
}

/**
 * One observation worth showing on the findings screen.
 *
 * Facts, not sentences: the wording belongs to the screen, and several of these are shown together.
 */
sealed interface SetupFact {
    /** An index is here. [owner] is who keeps it, when the server said. */
    data class SharedIndex(val books: Int?, val owner: String?) : SetupFact

    data object NoSharedIndex : SetupFact

    /** The owner requires devices here to use the index rather than build their own. */
    data class SharedUseRequired(val owner: String?, val atFolder: String?) : SetupFact

    /** The owner allows edits for a reader's own shelf, but not published here. */
    data class EditsLocked(val owner: String?) : SetupFact

    /** Rules are here and could not be read, so the strictest reading applies. */
    data class RulesUnreadable(val atFolder: String?) : SetupFact

    /** This device can write here — so it can publish, and it can set rules if it owns the folder. */
    data object Writable : SetupFact

    data object ReadOnly : SetupFact

    /** This device already knows books of its own, which a decision here would carry or abandon. */
    data class LocalLibrary(val books: Int) : SetupFact
}

/** Which of the six situations a folder turns out to be in. Selects the screen's wording. */
enum class SetupSituation {
    /** An index is here, its rules require using it, and this device can help keep it current. */
    JOIN_AND_MAINTAIN,

    /** An index is here, its rules require using it, and this device can only read it. */
    JOIN_AS_READER,

    /** An index is here and nothing requires using it. Using it is still the right default. */
    ADOPT_OR_OWN,

    /** No index, and this device could make one. */
    CREATE_HERE,

    /** No index, no write access, no rule against crawling — possible, and expensive. */
    DEVICE_ONLY,

    /**
     * No index, no write access, and the owner requires one.
     *
     * The situation the request's decision tree cannot reach and the rules make possible. Refusing
     * politely is the point: crawling anyway would defeat the whole feature, and it is the owner's
     * server that would pay.
     */
    BLOCKED,
}

/** What Homer found, what it proposes, and what else is on offer. */
data class SetupOutcome(
    val situation: SetupSituation,
    val primary: SetupAction,
    val alternatives: List<SetupAction>,
    val facts: List<SetupFact>,
)

/**
 * The decision tree, as a function.
 *
 * Setup reads as a dependency chain of decisions — is there a library, may I write, does it have
 * rules, do I want my own — and written as nested UI that chain is where the bugs live. Here it is
 * a fold over [SetupProbe] with one test per row, and the screen renders whatever comes back.
 *
 * The default is always the cheapest correct thing for both parties: use what is there, and create
 * one only where there is nothing to use.
 */
fun decideSetup(probe: SetupProbe): SetupOutcome {
    // A share link is never ownership; an owner is never bound by their own rules.
    val isOwner = probe.kind == WebDavKind.ACCOUNT && probe.isOwner
    val requiresShared = !isOwner && probe.policy.sharedIndexRequired
    // The write probe is authoritative — it is run for an account as well as for a share — but
    // owning the folder settles it regardless, so a probe that failed for its own reasons cannot
    // tell somebody they may not write their own storage.
    val canWrite = probe.writable || isOwner

    val situation = when {
        probe.hasSharedIndex && requiresShared && canWrite -> SetupSituation.JOIN_AND_MAINTAIN
        probe.hasSharedIndex && requiresShared -> SetupSituation.JOIN_AS_READER
        probe.hasSharedIndex -> SetupSituation.ADOPT_OR_OWN
        // Nothing here to adopt. Creating one needs write access; without it, crawling for yourself
        // is the only way to see the folder at all — unless the owner has said not to.
        canWrite -> SetupSituation.CREATE_HERE
        requiresShared -> SetupSituation.BLOCKED
        else -> SetupSituation.DEVICE_ONLY
    }

    val primary = when (situation) {
        SetupSituation.JOIN_AND_MAINTAIN, SetupSituation.JOIN_AS_READER, SetupSituation.ADOPT_OR_OWN ->
            SetupAction.USE_SHARED_INDEX
        SetupSituation.CREATE_HERE -> SetupAction.CREATE_LIBRARY
        SetupSituation.DEVICE_ONLY -> SetupAction.KEEP_ON_DEVICE
        SetupSituation.BLOCKED -> SetupAction.WAIT_FOR_OWNER
    }

    val alternatives = when (situation) {
        // Locked by the rules: offering a way out that Homer would then refuse to take is worse
        // than offering nothing.
        SetupSituation.JOIN_AND_MAINTAIN, SetupSituation.JOIN_AS_READER -> emptyList()
        SetupSituation.ADOPT_OR_OWN -> listOf(SetupAction.KEEP_ON_DEVICE)
        // Creating one here honours a rule requiring shared use; keeping it to yourself is the
        // exact thing that rule forbids, and `LibraryStanding` forces the index back on anyway —
        // so the choice would have been taken away again the moment it was made. A rule can reach
        // this situation from an ancestor folder even though there is no index here yet.
        SetupSituation.CREATE_HERE ->
            if (requiresShared) emptyList() else listOf(SetupAction.KEEP_ON_DEVICE)
        // Nothing else is possible. "Ask the owner to set one up" is a sentence, not an action
        // Homer can take.
        SetupSituation.DEVICE_ONLY, SetupSituation.BLOCKED -> emptyList()
    }

    return SetupOutcome(situation, primary, alternatives, factsOf(probe, requiresShared, canWrite))
}

/**
 * The observations, in the order they are worth reading: what is here, what the rules say, what this
 * device may do, and what it would be bringing with it.
 */
private fun factsOf(probe: SetupProbe, requiresShared: Boolean, canWrite: Boolean): List<SetupFact> =
    buildList {
        add(
            if (probe.hasSharedIndex) {
                SetupFact.SharedIndex(probe.remoteBookCount, probe.owner ?: probe.policy.owner)
            } else {
                SetupFact.NoSharedIndex
            },
        )
        // Unreadable rules replace the rules rather than joining them: saying "shared use required,
        // edits locked" would report a reading of a file Homer could not read.
        if (probe.policy.present && !probe.policy.understood) {
            add(SetupFact.RulesUnreadable(probe.policy.atFolder))
        } else {
            // The owner recorded IN the rules first: it says who set them, which on a folder that
            // has since been re-shared is not the same as who holds it now.
            val setBy = probe.policy.owner ?: probe.owner
            if (requiresShared) add(SetupFact.SharedUseRequired(setBy, probe.policy.atFolder))
            if (!probe.policy.editsAllowed && !probe.isOwner) add(SetupFact.EditsLocked(setBy))
        }
        add(if (canWrite) SetupFact.Writable else SetupFact.ReadOnly)
        // Only when there is something to lose. On a first run this is zero and mentioning it would
        // be noise.
        if (probe.localBookCount > 0) add(SetupFact.LocalLibrary(probe.localBookCount))
    }

/**
 * How far the first-run flow has got — persisted, because the flow outlives the process.
 *
 * It exists because credentials cannot be the gate. Signing in is a *step* of setup, and the
 * moment it succeeds the auth state flips; gating the wizard on "logged out" would therefore eject
 * the user into the library half way through, before a folder had been chosen or the rules read.
 *
 * [NOT_STARTED] carries the upgrade: an install from before this flow existed has credentials and
 * has never seen a wizard, and it must not be shown one — so "not started" only means "show setup"
 * when there is nothing configured at all.
 */
enum class SetupState {
    /** Nothing has been asked. Setup runs only if there are no credentials either. */
    NOT_STARTED,

    /** Somewhere in the flow. Setup runs regardless of what else is configured. */
    IN_PROGRESS,

    /** Finished, or never needed. */
    DONE,
    ;

    companion object {
        /** Tolerant of a value written by another build: an unknown state is not a reason to
         *  restart somebody's setup. */
        fun of(name: String?): SetupState = entries.firstOrNull { it.name == name } ?: NOT_STARTED
    }
}

/**
 * Whether the setup flow owns the screen.
 *
 * A function rather than three lines inside a ViewModel because getting it wrong is invisible in
 * both directions and has already happened once: signing out reset the marker *after* clearing the
 * credentials, so the flow — which marks itself [SetupState.IN_PROGRESS] the moment it appears —
 * had its mark wiped, and was dismissed the instant new credentials landed. The user was dropped
 * into the library with no folder chosen, every time. Whose bug that was is a question about
 * ordering elsewhere; that this predicate has to be *checkable* is the lesson.
 *
 * The three cases and what each is for:
 *  - [SetupState.DONE] — settled. Never ask again.
 *  - [SetupState.IN_PROGRESS] — somewhere in the flow. Signing in is a *step*, so the credentials
 *    arriving must not end it.
 *  - [SetupState.NOT_STARTED] — nothing asked. Which for an install from before this flow existed
 *    means "and nothing needs asking", because it has a library already.
 */
fun setupIsDue(state: SetupState, hasCredentials: Boolean): Boolean = when (state) {
    SetupState.DONE -> false
    SetupState.IN_PROGRESS -> true
    SetupState.NOT_STARTED -> !hasCredentials
}
