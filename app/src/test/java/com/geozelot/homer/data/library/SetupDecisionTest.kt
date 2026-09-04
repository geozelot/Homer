package com.geozelot.homer.data.library

import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.sync.facet.LibraryPolicy
import com.geozelot.homer.data.sync.facet.PolicyInForce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The setup decision.
 *
 * One test per situation, then the invariants over every combination. The tree this replaces was
 * twelve nested nodes in a UI, which is where a decision chain goes wrong quietly — here it is six
 * rows and each row is named.
 */
class SetupDecisionTest {

    // ── the six situations ───────────────────────────────────────────────────────────────────

    @Test
    fun `an index whose rules require it, and we can help keep it`() {
        val outcome = decideSetup(
            probe(hasSharedIndex = true, writable = true, policy = LibraryPolicy(sharedIndexRequired = true)),
        )
        assertEquals(SetupSituation.JOIN_AND_MAINTAIN, outcome.situation)
        assertEquals(SetupAction.USE_SHARED_INDEX, outcome.primary)
        // Nothing else is offered: an alternative Homer would then refuse to honour is worse than
        // no alternative at all.
        assertEquals(emptyList<SetupAction>(), outcome.alternatives)
    }

    @Test
    fun `an index whose rules require it, and we can only read`() {
        val outcome = decideSetup(
            probe(hasSharedIndex = true, writable = false, policy = LibraryPolicy(sharedIndexRequired = true)),
        )
        assertEquals(SetupSituation.JOIN_AS_READER, outcome.situation)
        assertEquals(SetupAction.USE_SHARED_INDEX, outcome.primary)
        assertEquals(emptyList<SetupAction>(), outcome.alternatives)
    }

    @Test
    fun `an index and no rule about it - using it is still the default`() {
        val outcome = decideSetup(probe(hasSharedIndex = true, writable = true))
        assertEquals(SetupSituation.ADOPT_OR_OWN, outcome.situation)
        assertEquals(SetupAction.USE_SHARED_INDEX, outcome.primary)
        // The cheapest correct thing for both parties is to use what is there. Building your own is
        // possible and is not the recommendation.
        assertEquals(listOf(SetupAction.KEEP_ON_DEVICE), outcome.alternatives)
    }

    @Test
    fun `no index and write access - offer to make one`() {
        val outcome = decideSetup(probe(hasSharedIndex = false, writable = true))
        assertEquals(SetupSituation.CREATE_HERE, outcome.situation)
        assertEquals(SetupAction.CREATE_LIBRARY, outcome.primary)
        assertEquals(listOf(SetupAction.KEEP_ON_DEVICE), outcome.alternatives)
    }

    @Test
    fun `no index and no write access - expensive, allowed, and said so`() {
        val outcome = decideSetup(probe(hasSharedIndex = false, writable = false))
        assertEquals(SetupSituation.DEVICE_ONLY, outcome.situation)
        assertEquals(SetupAction.KEEP_ON_DEVICE, outcome.primary)
        assertEquals(emptyList<SetupAction>(), outcome.alternatives)
    }

    @Test
    fun `no index, no write access, and the owner requires one`() {
        // The row the request's tree cannot reach, and the one the rules make possible. Crawling
        // anyway would defeat the whole feature, on the owner's server.
        val outcome = decideSetup(
            probe(hasSharedIndex = false, writable = false, policy = LibraryPolicy(sharedIndexRequired = true)),
        )
        assertEquals(SetupSituation.BLOCKED, outcome.situation)
        assertEquals(SetupAction.WAIT_FOR_OWNER, outcome.primary)
        assertEquals(emptyList<SetupAction>(), outcome.alternatives)
    }

    @Test
    fun `no index and write access beats a rule requiring one`() {
        // The owner wants an index here and there is none yet. Making it is honouring the rule,
        // not defying it.
        val outcome = decideSetup(
            probe(hasSharedIndex = false, writable = true, policy = LibraryPolicy(sharedIndexRequired = true)),
        )
        assertEquals(SetupSituation.CREATE_HERE, outcome.situation)
    }

    // ── ownership ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the owner's own rules do not lock the owner out of choosing`() {
        val outcome = decideSetup(
            probe(
                hasSharedIndex = true,
                writable = true,
                isOwner = true,
                policy = LibraryPolicy(sharedIndexRequired = true),
            ),
        )
        assertEquals(SetupSituation.ADOPT_OR_OWN, outcome.situation)
        assertEquals(listOf(SetupAction.KEEP_ON_DEVICE), outcome.alternatives)
    }

    @Test
    fun `a share link is never the owner`() {
        val outcome = decideSetup(
            probe(
                kind = WebDavKind.SHARE,
                hasSharedIndex = true,
                writable = false,
                isOwner = true,
                policy = LibraryPolicy(sharedIndexRequired = true),
            ),
        )
        assertEquals(SetupSituation.JOIN_AS_READER, outcome.situation)
    }

    @Test
    fun `owning the folder settles write access even if the probe said otherwise`() {
        // An MKCOL can fail for its own reasons. It must not be able to tell somebody they may not
        // write their own storage.
        val outcome = decideSetup(probe(hasSharedIndex = false, writable = false, isOwner = true))
        assertEquals(SetupSituation.CREATE_HERE, outcome.situation)
    }

    // ── the facts the screen shows ───────────────────────────────────────────────────────────

    @Test
    fun `an index is reported with its size and who keeps it`() {
        val facts = decideSetup(
            probe(hasSharedIndex = true, writable = true, remoteBookCount = 313, owner = "andre"),
        ).facts
        assertTrue(SetupFact.SharedIndex(313, "andre") in facts)
        assertTrue(SetupFact.Writable in facts)
    }

    @Test
    fun `an empty folder says so, and says what we may do about it`() {
        val facts = decideSetup(probe(hasSharedIndex = false, writable = false)).facts
        assertTrue(SetupFact.NoSharedIndex in facts)
        assertTrue(SetupFact.ReadOnly in facts)
    }

    @Test
    fun `a rule names the folder it came from`() {
        // With the walk, a rule can come from a folder the reader never chose. Not saying where it
        // came from makes a locked switch look like a bug.
        val facts = decideSetup(
            probe(
                hasSharedIndex = true,
                writable = false,
                policy = LibraryPolicy(sharedIndexRequired = true, ownerId = "andre"),
                policyAtFolder = "Media",
            ),
        ).facts
        assertTrue(SetupFact.SharedUseRequired("andre", "Media") in facts)
    }

    @Test
    fun `locked edits are reported even when nothing else is restricted`() {
        val facts = decideSetup(
            probe(hasSharedIndex = true, writable = true, policy = LibraryPolicy(editsAllowed = false)),
        ).facts
        assertTrue(facts.any { it is SetupFact.EditsLocked })
        assertFalse(facts.any { it is SetupFact.SharedUseRequired })
    }

    @Test
    fun `the owner is not told their own edits are locked`() {
        val facts = decideSetup(
            probe(hasSharedIndex = true, writable = true, isOwner = true, policy = LibraryPolicy(editsAllowed = false)),
        ).facts
        assertFalse(facts.any { it is SetupFact.EditsLocked })
    }

    @Test
    fun `unreadable rules replace the rules rather than joining them`() {
        // Reporting "shared use required, edits locked" would be a reading of a file Homer could
        // not read.
        val facts = decideSetup(probe(hasSharedIndex = true, writable = false, unreadable = true)).facts
        assertTrue(SetupFact.RulesUnreadable("Audiobooks") in facts)
        assertFalse(facts.any { it is SetupFact.SharedUseRequired })
        assertFalse(facts.any { it is SetupFact.EditsLocked })
    }

    @Test
    fun `unreadable rules still make a reader a reader`() {
        val outcome = decideSetup(probe(hasSharedIndex = true, writable = false, unreadable = true))
        assertEquals(SetupSituation.JOIN_AS_READER, outcome.situation)
    }

    @Test
    fun `books already on this device are only mentioned when there are some`() {
        // On a first run this is zero, and mentioning it would be noise.
        assertFalse(
            decideSetup(probe(hasSharedIndex = true, writable = true, localBookCount = 0)).facts
                .any { it is SetupFact.LocalLibrary },
        )
        assertTrue(
            SetupFact.LocalLibrary(12) in
                decideSetup(probe(hasSharedIndex = true, writable = true, localBookCount = 12)).facts,
        )
    }

    @Test
    fun `both counts are on the table when a migration would merge them`() {
        // "This folder has 313 books; this device knows 12" is the difference between a merge and
        // a scare.
        val facts = decideSetup(
            probe(hasSharedIndex = true, writable = true, remoteBookCount = 313, localBookCount = 12),
        ).facts
        assertTrue(SetupFact.SharedIndex(313, null) in facts)
        assertTrue(SetupFact.LocalLibrary(12) in facts)
    }

    // ── invariants over every combination ────────────────────────────────────────────────────

    @Test
    fun `there is always exactly one recommendation, and it is never also an alternative`() {
        forEveryCombination { outcome ->
            assertFalse(outcome.toString(), outcome.primary in outcome.alternatives)
            assertEquals(outcome.toString(), outcome.alternatives.distinct(), outcome.alternatives)
        }
    }

    @Test
    fun `the index is only ever proposed where there is one to use`() {
        forEveryCombination { outcome, probe ->
            if (outcome.primary == SetupAction.USE_SHARED_INDEX) {
                assertTrue(outcome.toString(), probe.hasSharedIndex)
            }
        }
    }

    @Test
    fun `making a library is only ever proposed where it can be written`() {
        forEveryCombination { outcome, probe ->
            if (SetupAction.CREATE_LIBRARY == outcome.primary) {
                assertTrue(outcome.toString(), probe.writable || probe.isOwner)
                assertFalse(outcome.toString(), probe.hasSharedIndex)
            }
        }
    }

    @Test
    fun `keeping it to yourself is never offered where the rules require sharing`() {
        // The one rule the whole feature exists for, and every path to KEEP_ON_DEVICE has to be
        // checked — the alternative as well as the recommendation.
        //
        // This used to allow it wherever the device could write, on the reasoning that publishing
        // the result is not a private crawl. True of what such a device *would* do if it created
        // the library, and not true of the option being offered, which is explicitly not to. And
        // `LibraryStanding` forces the index back on in that case regardless, so the choice was
        // taken away again the moment it was made — a control that does nothing.
        forEveryCombination { outcome, probe ->
            val boundByRules = probe.policy.sharedIndexRequired &&
                !(probe.kind == WebDavKind.ACCOUNT && probe.isOwner)
            if (!boundByRules) return@forEveryCombination
            assertFalse(outcome.toString(), outcome.primary == SetupAction.KEEP_ON_DEVICE)
            assertFalse(outcome.toString(), SetupAction.KEEP_ON_DEVICE in outcome.alternatives)
        }
    }

    @Test
    fun `a rule inherited from a parent folder reaches a folder with no index of its own`() {
        // The path that exposed it: nothing here to adopt, this device can write, and the rule
        // comes from a folder further up. Creating the library is the only thing on offer.
        val outcome = decideSetup(
            probe(
                hasSharedIndex = false,
                writable = true,
                policy = LibraryPolicy(sharedIndexRequired = true),
                policyAtFolder = "Media",
            ),
        )
        assertEquals(SetupSituation.CREATE_HERE, outcome.situation)
        assertEquals(SetupAction.CREATE_LIBRARY, outcome.primary)
        assertEquals(emptyList<SetupAction>(), outcome.alternatives)
    }

    @Test
    fun `every outcome says what is here and what we may do`() {
        forEveryCombination { outcome ->
            assertTrue(
                outcome.toString(),
                outcome.facts.any { it is SetupFact.SharedIndex || it == SetupFact.NoSharedIndex },
            )
            assertTrue(
                outcome.toString(),
                outcome.facts.any { it == SetupFact.Writable || it == SetupFact.ReadOnly },
            )
        }
    }

    private fun forEveryCombination(check: (SetupOutcome, SetupProbe) -> Unit) {
        val policies = listOf(
            LibraryPolicy(),
            LibraryPolicy(sharedIndexRequired = true),
            LibraryPolicy(editsAllowed = false),
            LibraryPolicy(sharedIndexRequired = true, editsAllowed = false),
        )
        for (kind in WebDavKind.entries) {
            for (hasIndex in listOf(true, false)) {
                for (writable in listOf(true, false)) {
                    for (isOwner in listOf(true, false)) {
                        for (policy in policies) {
                            for (unreadable in listOf(false, true)) {
                                for (local in listOf(0, 12)) {
                                    val probe = probe(
                                        kind = kind,
                                        hasSharedIndex = hasIndex,
                                        writable = writable,
                                        isOwner = isOwner,
                                        policy = policy,
                                        unreadable = unreadable,
                                        localBookCount = local,
                                    )
                                    check(decideSetup(probe), probe)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun forEveryCombination(check: (SetupOutcome) -> Unit) =
        forEveryCombination { outcome, _ -> check(outcome) }

    private fun probe(
        kind: WebDavKind = WebDavKind.ACCOUNT,
        hasSharedIndex: Boolean = false,
        writable: Boolean = true,
        isOwner: Boolean = false,
        policy: LibraryPolicy? = null,
        policyAtFolder: String = "Audiobooks",
        unreadable: Boolean = false,
        remoteBookCount: Int? = null,
        localBookCount: Int = 0,
        owner: String? = null,
    ) = SetupProbe(
        kind = kind,
        root = "Audiobooks",
        writable = writable,
        isOwner = isOwner,
        policy = when {
            unreadable -> PolicyInForce.unreadable(atFolder = policyAtFolder)
            policy != null -> PolicyInForce.of(policy, atFolder = policyAtFolder, owner = owner)
            else -> PolicyInForce.OPEN
        },
        owner = owner,
        hasSharedIndex = hasSharedIndex,
        remoteBookCount = remoteBookCount,
        localBookCount = localBookCount,
    )
}

/**
 * Whether the setup flow owns the screen.
 *
 * Four lines of logic, and every one of them has a way of being wrong that nothing shouts about.
 * The middle case is the one that broke: signing in is a step *inside* the flow, so the credentials
 * arriving must not end it — and the marker that says so has to survive sign-out's own teardown.
 */
class SetupIsDueTest {

    @Test
    fun `a fresh install with nothing configured needs setup`() {
        assertTrue(setupIsDue(SetupState.NOT_STARTED, hasCredentials = false))
    }

    @Test
    fun `an install from before the flow existed does not`() {
        // It has credentials, a library and a shelf full of books. Showing it a wizard would be
        // the worst possible upgrade.
        assertFalse(setupIsDue(SetupState.NOT_STARTED, hasCredentials = true))
    }

    @Test
    fun `signing in does not end a flow that is under way`() {
        // The case that broke on a device: sign out, reconnect somewhere else, and the flow was
        // dismissed the instant the credentials landed — before a folder had been chosen.
        assertTrue(setupIsDue(SetupState.IN_PROGRESS, hasCredentials = true))
        assertTrue(setupIsDue(SetupState.IN_PROGRESS, hasCredentials = false))
    }

    @Test
    fun `a finished flow is finished, with or without credentials`() {
        // Credentials can go missing — a revoked app password — and that is a re-authentication,
        // not a reason to ask where the books live again.
        assertFalse(setupIsDue(SetupState.DONE, hasCredentials = true))
        assertFalse(setupIsDue(SetupState.DONE, hasCredentials = false))
    }

    @Test
    fun `a state written by another build does not restart somebody's setup`() {
        assertEquals(SetupState.NOT_STARTED, SetupState.of("SOMETHING_ELSE"))
        assertEquals(SetupState.NOT_STARTED, SetupState.of(null))
        assertEquals(SetupState.IN_PROGRESS, SetupState.of("IN_PROGRESS"))
    }
}
