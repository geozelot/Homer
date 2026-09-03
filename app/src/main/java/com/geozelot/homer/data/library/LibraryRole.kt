package com.geozelot.homer.data.library

import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.sync.facet.PolicyInForce
import com.geozelot.homer.data.sync.facet.PolicyResolution

/**
 * What this device is, in this library.
 *
 * Ownership is deliberately NOT one of these. It is a fact about the folder, not about the index —
 * an owner who has the shared index switched off is keeping a private one, which is a perfectly
 * coherent thing to be and would have needed a fifth value that means two things at once. It rides
 * alongside as [LibraryStanding.isOwner].
 */
enum class LibraryRole {
    /** Reads the shared index and publishes to it. Pays for the expensive passes. */
    MAINTAINER,

    /** Reads an index it cannot write. Runs no crawl, no measure pass, no cover extraction. */
    READER,

    /** No shared index in use. Works everything out for itself, and shares none of it. */
    PRIVATE,

    /** No library configured yet. */
    NONE,
}

/**
 * Why something is withheld — resolved once, so every screen says the same thing.
 *
 * This type exists because the question "may this device do the expensive work?" used to be derived
 * independently in five places, and the answers could disagree. That is what made *Try missing
 * covers* appear to do nothing: the predicate that enabled the button and the predicate that did
 * the work were not the same predicate. Adding the owner's rules as a sixth input to five separate
 * derivations was asking for it again.
 */
sealed interface Restriction {
    /** Nothing is configured yet. */
    data object NoLibrary : Restriction

    /**
     * The library cannot be written to.
     *
     * Not "a read-only share" any more: a folder shared *into* an account is reached with account
     * credentials and can be just as read-only, which is the case nothing used to probe.
     */
    data object ReadOnlyLibrary : Restriction

    /** The owner allows edits for a reader's own shelf, but not published here. */
    data class EditsLocked(val owner: String?) : Restriction

    /**
     * A policy file is here and could not be read, so the strictest reading applies.
     *
     * Worth its own case rather than being folded into the others: "the owner said no" and "Homer
     * could not tell what the owner said" call for different words, and the second is a bug report.
     */
    data class RulesUnreadable(val atFolder: String?) : Restriction
}

/**
 * Everything that follows from *where* the library is and *what its owner allows* — in one value,
 * asked once.
 *
 * The fields look numerous for what is really one decision, and each one is here because a specific
 * caller asks exactly it: the pass queue asks [maintains], the index asks [mayPublishIndex], the
 * edit sheet asks [mayPublishEdits], the shared-index switch asks [sharedIndexLocked], the rules
 * panel asks [isOwner], and every one of them wants [restriction] for the sentence that explains a
 * "no".
 */
data class LibraryStanding(
    val role: LibraryRole,
    /** Whether the signed-in account owns the folder — the party the rules do not bind. */
    val isOwner: Boolean,
    val policy: PolicyInForce,
    /**
     * Whether the rules have been resolved for the root in use.
     *
     * False is "nothing asked about here yet", not "no rules here". Nothing is refused for it — the
     * expensive passes all need the network, and so does the resolve, so the pass queue re-resolves
     * before it decides rather than guessing. It exists so the UI can say "checking" instead of
     * claiming a library is open before anybody has looked.
     */
    val policyKnown: Boolean,
    /**
     * Whether the backend can be written to at all, index or no index.
     *
     * Distinct from [mayPublishIndex], which also requires the index to be in use. The UI wants
     * both: "read-only" is a fact about the folder, and saying "read & write" of a folder merely
     * because nothing is being published there would be wrong.
     */
    val backendWritable: Boolean,
    /** Whether the shared index is in use — by choice, or because the owner requires it. */
    val usesSharedIndex: Boolean,
    /**
     * True when the owner requires the shared index, so the switch is not this device's to turn off.
     *
     * Turning it off is the escape hatch the rule exists to close: without this, one tap restores
     * the crawl-everything path on somebody else's server.
     */
    val sharedIndexLocked: Boolean,
    /** May run the expensive passes: the crawl, the measure sweep, cover extraction. */
    val maintains: Boolean,
    /** May write the shared index files at all. */
    val mayPublishIndex: Boolean,
    /**
     * May publish metadata corrections.
     *
     * Separate from [mayPublishIndex] because the owner can allow one and not the other. It never
     * gates the *local* edit: somebody fixing a garbled title for their own shelf costs the owner
     * nothing, and what a locked library takes away is the promise that the fix will travel.
     */
    val mayPublishEdits: Boolean,
    /** May write the rules themselves. The owner, and nobody else. */
    val mayEditRules: Boolean,
    /** Why the expensive work and the publishing are withheld, or null when they are not. */
    val restriction: Restriction?,
    /** Why an edit stays on this device, or null when it will travel. */
    val editRestriction: Restriction?,
) {
    /** Reads an index somebody else keeps — a different statement from "has no index". */
    val readsOnly: Boolean get() = role == LibraryRole.READER

    companion object {
        /** Before anything is known: no library, so nothing is permitted and nothing is claimed. */
        val NONE = LibraryStanding(
            role = LibraryRole.NONE,
            isOwner = false,
            policy = PolicyInForce.OPEN,
            policyKnown = false,
            backendWritable = false,
            usesSharedIndex = false,
            sharedIndexLocked = false,
            maintains = false,
            mayPublishIndex = false,
            mayPublishEdits = false,
            mayEditRules = false,
            restriction = Restriction.NoLibrary,
            editRestriction = Restriction.NoLibrary,
        )

        /**
         * The whole derivation, as a function of facts — no Android, no flows, no network.
         *
         * @param kind how the library is reached, or null when nothing is configured.
         * @param writable whether the backend can be written to, as probed. Defaults to true
         *   everywhere it has not been established, so an unprobed library keeps working.
         * @param sharedIndexEnabled the user's switch.
         * @param resolution the owner's rules as last resolved, with the root they describe.
         * @param libraryRoot the root in use, to check that [resolution] is about it.
         */
        fun of(
            kind: WebDavKind?,
            writable: Boolean,
            sharedIndexEnabled: Boolean,
            resolution: PolicyResolution,
            libraryRoot: String,
        ): LibraryStanding {
            if (kind == null) return NONE

            // Not "an account is always writable". That was true of an account's OWN folder and
            // false of a folder shared into it, which is reached with account credentials and can
            // be read-only — the case nothing used to probe. The flag defaults to true, so an
            // account that has never been probed behaves exactly as it always did.
            val backendWritable = writable
            val policyKnown = resolution.describes(libraryRoot)
            // Unresolved reads as open — see LibraryStanding.policyKnown for why that is safe.
            val policy = if (policyKnown) resolution.policy else PolicyInForce.OPEN
            // A share link is never ownership, whatever a resolution says: the whole nature of a
            // share is that somebody else holds the account. Asserted here as well as where
            // ownership is probed, because this is the function every gate actually reads.
            val isOwner = kind == WebDavKind.ACCOUNT && resolution.isOwner
            // The owner wrote the rules and can change them in one tap, so binding them by their
            // own rules would be theatre.
            val bound = !isOwner
            val requiresShared = bound && policy.sharedIndexRequired

            val usesSharedIndex = sharedIndexEnabled || requiresShared
            val mayPublishIndex = usesSharedIndex && backendWritable
            // With no index in use, nobody else is being served and nobody else is being made to
            // repeat the work — and the device has no other way to learn what is in the library.
            val maintains = !usesSharedIndex || mayPublishIndex
            val mayPublishEdits = mayPublishIndex && (!bound || policy.editsAllowed)

            val role = when {
                !usesSharedIndex -> LibraryRole.PRIVATE
                mayPublishIndex -> LibraryRole.MAINTAINER
                else -> LibraryRole.READER
            }

            // Unreadable rules outrank a read-only share as the explanation: "the owner said no"
            // and "Homer could not tell what the owner said" want different words, and only one of
            // them is a bug report.
            val blocked: Restriction? = when {
                role != LibraryRole.READER -> null
                !policy.understood -> Restriction.RulesUnreadable(policy.atFolder)
                else -> Restriction.ReadOnlyLibrary
            }

            return LibraryStanding(
                role = role,
                isOwner = isOwner,
                policy = policy,
                policyKnown = policyKnown,
                backendWritable = backendWritable,
                usesSharedIndex = usesSharedIndex,
                sharedIndexLocked = requiresShared,
                maintains = maintains,
                mayPublishIndex = mayPublishIndex,
                mayPublishEdits = mayPublishEdits,
                mayEditRules = isOwner,
                restriction = blocked,
                editRestriction = when {
                    mayPublishEdits -> null
                    // Sharing is simply off. Nothing is being withheld — the edit was never going
                    // to travel, and calling that a restriction would put a locked look on the
                    // ordinary case.
                    !usesSharedIndex -> null
                    blocked != null -> blocked
                    !policy.understood -> Restriction.RulesUnreadable(policy.atFolder)
                    else -> Restriction.EditsLocked(policy.owner)
                },
            )
        }
    }
}
