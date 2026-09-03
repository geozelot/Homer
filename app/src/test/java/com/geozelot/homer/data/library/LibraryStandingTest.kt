package com.geozelot.homer.data.library

import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.sync.facet.LibraryPolicy
import com.geozelot.homer.data.sync.facet.PolicyInForce
import com.geozelot.homer.data.sync.facet.PolicyResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a device may do in a library.
 *
 * This is the test that matters most in the whole feature, because none of it fails loudly. Five
 * places used to derive "may this device do the expensive work?" independently, they could disagree,
 * and the disagreement is what made *Try missing covers* silently do nothing. The owner's rules are
 * a sixth input, so the derivation is one function and this is every combination of its inputs.
 *
 * The rows to read first are [a reader cannot escape the rules by switching the index off] — the
 * bypass the whole policy exists to close — and [the owner is not bound by their own rules].
 */
class LibraryStandingTest {

    private val root = "Audiobooks"

    // ── nothing configured ───────────────────────────────────────────────────────────────────

    @Test
    fun `no library means nothing is permitted and nothing is claimed`() {
        val standing = standing(kind = null)
        assertEquals(LibraryRole.NONE, standing.role)
        assertFalse(standing.maintains)
        assertFalse(standing.mayPublishIndex)
        assertFalse(standing.mayPublishEdits)
        assertFalse(standing.mayEditRules)
        assertEquals(Restriction.NoLibrary, standing.restriction)
    }

    // ── the ordinary cases, unchanged by any of this ─────────────────────────────────────────

    @Test
    fun `an account with the index off works for itself`() {
        val standing = standing(WebDavKind.ACCOUNT, sharedIndexEnabled = false)
        assertEquals(LibraryRole.PRIVATE, standing.role)
        // Nobody else is being served, so nobody else is being made to repeat the work — and this
        // device has no other way to learn what is in the library.
        assertTrue(standing.maintains)
        assertFalse(standing.mayPublishIndex)
        assertNull(standing.restriction)
        // Sharing is off, so an edit was never going to travel. That is not a restriction, and
        // dressing it as one would put a locked look on the ordinary case.
        assertNull(standing.editRestriction)
    }

    @Test
    fun `an account with the index on maintains and publishes`() {
        val standing = standing(WebDavKind.ACCOUNT, sharedIndexEnabled = true)
        assertEquals(LibraryRole.MAINTAINER, standing.role)
        assertTrue(standing.maintains)
        assertTrue(standing.mayPublishIndex)
        assertTrue(standing.mayPublishEdits)
        assertNull(standing.restriction)
    }

    @Test
    fun `an account is never a reader, whatever the writable flag says`() {
        // The flag is only set from a share's write probe. An account's own folder is writable by
        // the account that owns it, and reading the stale flag would make a whole library read-only.
        val standing = standing(WebDavKind.ACCOUNT, writable = false, sharedIndexEnabled = true)
        assertEquals(LibraryRole.MAINTAINER, standing.role)
        assertTrue(standing.mayPublishIndex)
    }

    @Test
    fun `a read-only share with the index on is a reader`() {
        val standing = standing(WebDavKind.SHARE, writable = false, sharedIndexEnabled = true)
        assertEquals(LibraryRole.READER, standing.role)
        assertTrue(standing.readsOnly)
        assertFalse(standing.maintains)
        assertFalse(standing.mayPublishIndex)
        assertEquals(Restriction.ReadOnlyShare, standing.restriction)
    }

    @Test
    fun `a writable share maintains like an account`() {
        val standing = standing(WebDavKind.SHARE, writable = true, sharedIndexEnabled = true)
        assertEquals(LibraryRole.MAINTAINER, standing.role)
        assertTrue(standing.mayPublishIndex)
    }

    @Test
    fun `a read-only share with the index off is private, when no rule says otherwise`() {
        // Today's behaviour, and it has to survive: somebody with read access to a folder and no
        // shared index has no other way to see what is in it.
        val standing = standing(WebDavKind.SHARE, writable = false, sharedIndexEnabled = false)
        assertEquals(LibraryRole.PRIVATE, standing.role)
        assertTrue(standing.maintains)
        assertFalse(standing.sharedIndexLocked)
    }

    // ── the bypass the policy exists to close ────────────────────────────────────────────────

    @Test
    fun `a reader cannot escape the rules by switching the index off`() {
        // Switching it off is the escape hatch: without this, one tap restores the crawl-everything
        // path on somebody else's server, which is the entire failure the rule prevents.
        val standing = standing(
            WebDavKind.SHARE,
            writable = false,
            sharedIndexEnabled = false,
            policy = LibraryPolicy(sharedIndexRequired = true),
        )
        assertEquals(LibraryRole.READER, standing.role)
        assertTrue(standing.usesSharedIndex)
        assertTrue(standing.sharedIndexLocked)
        assertFalse(standing.maintains)
    }

    @Test
    fun `requiring the shared index does not demote a device that can publish to it`() {
        val standing = standing(
            WebDavKind.SHARE,
            writable = true,
            sharedIndexEnabled = false,
            policy = LibraryPolicy(sharedIndexRequired = true),
        )
        assertEquals(LibraryRole.MAINTAINER, standing.role)
        assertTrue(standing.usesSharedIndex)
        assertTrue(standing.sharedIndexLocked)
        assertTrue(standing.maintains)
    }

    // ── edits ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `locked edits stop the publish and nothing else`() {
        // The local edit survives on purpose: somebody fixing a garbled title for their own shelf
        // costs the owner nothing. What a locked library takes away is the promise it will travel.
        val standing = standing(
            WebDavKind.SHARE,
            writable = true,
            sharedIndexEnabled = true,
            policy = LibraryPolicy(editsAllowed = false, ownerId = "andre"),
        )
        assertEquals(LibraryRole.MAINTAINER, standing.role)
        assertTrue(standing.maintains)
        assertTrue(standing.mayPublishIndex)
        assertFalse(standing.mayPublishEdits)
        assertEquals(Restriction.EditsLocked("andre"), standing.editRestriction)
        // The index itself is not restricted — only the edits are.
        assertNull(standing.restriction)
    }

    @Test
    fun `a reader's edit restriction is the reason it cannot write at all`() {
        val standing = standing(
            WebDavKind.SHARE,
            writable = false,
            sharedIndexEnabled = true,
            policy = LibraryPolicy(editsAllowed = false),
        )
        // Not EditsLocked: the rule is beside the point when nothing here can write anything.
        assertEquals(Restriction.ReadOnlyShare, standing.editRestriction)
    }

    // ── the owner ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the owner is not bound by their own rules`() {
        // They wrote them and can change them in one tap, so applying them here would be theatre.
        val standing = standing(
            WebDavKind.ACCOUNT,
            sharedIndexEnabled = false,
            policy = LibraryPolicy(sharedIndexRequired = true, editsAllowed = false),
            owned = true,
        )
        assertEquals(LibraryRole.PRIVATE, standing.role)
        assertFalse(standing.sharedIndexLocked)
        assertTrue(standing.maintains)
    }

    @Test
    fun `the owner publishes edits their own rules forbid others`() {
        val standing = standing(
            WebDavKind.ACCOUNT,
            sharedIndexEnabled = true,
            policy = LibraryPolicy(editsAllowed = false),
            owned = true,
        )
        assertTrue(standing.mayPublishEdits)
        assertNull(standing.editRestriction)
    }

    @Test
    fun `only the owner may write the rules`() {
        assertTrue(standing(WebDavKind.ACCOUNT, owned = true).mayEditRules)
        assertFalse(standing(WebDavKind.ACCOUNT, owned = false).mayEditRules)
        // Null is "the server could not say", which is not permission.
        assertFalse(standing(WebDavKind.ACCOUNT, owned = null).mayEditRules)
        assertFalse(standing(WebDavKind.SHARE, writable = true, owned = true).mayEditRules)
    }

    // ── rules that are not established, or not readable ──────────────────────────────────────

    @Test
    fun `rules resolved for another folder do not apply here`() {
        // A resolution is about one folder. Carrying it across a root change would have it quietly
        // go on applying to somewhere else.
        val elsewhere = PolicyResolution(
            forRoot = "Media/Books",
            policy = PolicyInForce.of(LibraryPolicy(sharedIndexRequired = true), atFolder = "Media/Books"),
            owned = false,
            checkedAt = 1L,
        )
        val standing = LibraryStanding.of(
            kind = WebDavKind.SHARE,
            writable = false,
            sharedIndexEnabled = false,
            resolution = elsewhere,
            libraryRoot = root,
        )
        assertFalse(standing.policyKnown)
        assertFalse(standing.sharedIndexLocked)
        // Fail-open, and safe: the passes all need the network, and so does the resolve — the pass
        // queue re-resolves before it decides rather than guessing.
        assertEquals(LibraryRole.PRIVATE, standing.role)
    }

    @Test
    fun `nothing resolved yet is not the same as no rules`() {
        val standing = LibraryStanding.of(
            kind = WebDavKind.ACCOUNT,
            writable = true,
            sharedIndexEnabled = true,
            resolution = PolicyResolution.NONE,
            libraryRoot = root,
        )
        assertFalse(standing.policyKnown)
        assertEquals(LibraryRole.MAINTAINER, standing.role)
    }

    @Test
    fun `unreadable rules are read as the strictest ones`() {
        val standing = LibraryStanding.of(
            kind = WebDavKind.SHARE,
            writable = false,
            sharedIndexEnabled = false,
            resolution = PolicyResolution(
                forRoot = root,
                policy = PolicyInForce.unreadable(atFolder = root),
                owned = false,
                checkedAt = 1L,
            ),
            libraryRoot = root,
        )
        assertEquals(LibraryRole.READER, standing.role)
        assertTrue(standing.sharedIndexLocked)
        assertFalse(standing.maintains)
        // Said differently from a read-only share on purpose: "the owner said no" and "Homer could
        // not tell what the owner said" want different words, and only one of them is a bug report.
        assertEquals(Restriction.RulesUnreadable(root), standing.restriction)
    }

    @Test
    fun `unreadable rules lock a writable share out of publishing edits`() {
        val standing = LibraryStanding.of(
            kind = WebDavKind.SHARE,
            writable = true,
            sharedIndexEnabled = true,
            resolution = PolicyResolution(
                forRoot = root,
                policy = PolicyInForce.unreadable(atFolder = root),
                owned = false,
                checkedAt = 1L,
            ),
            libraryRoot = root,
        )
        assertEquals(LibraryRole.MAINTAINER, standing.role)
        assertFalse(standing.mayPublishEdits)
        assertEquals(Restriction.RulesUnreadable(root), standing.editRestriction)
    }

    // ── the invariants, over every combination ───────────────────────────────────────────────

    @Test
    fun `publishing anything always implies the shared index is in use`() {
        forEveryCombination { standing ->
            if (standing.mayPublishIndex) assertTrue(standing.toString(), standing.usesSharedIndex)
            if (standing.mayPublishEdits) assertTrue(standing.toString(), standing.mayPublishIndex)
        }
    }

    @Test
    fun `a device that may publish always maintains`() {
        // The reverse does not hold: a private device maintains and publishes nothing.
        forEveryCombination { standing ->
            if (standing.mayPublishIndex) assertTrue(standing.toString(), standing.maintains)
        }
    }

    @Test
    fun `a withheld pass always has a reason, and a permitted one never does`() {
        // The whole point of resolving this once: "nothing is happening" must never be silent.
        forEveryCombination { standing ->
            assertEquals(standing.toString(), standing.maintains, standing.restriction == null)
        }
    }

    @Test
    fun `only a reader is ever refused the expensive work`() {
        forEveryCombination { standing ->
            if (!standing.maintains && standing.role != LibraryRole.NONE) {
                assertEquals(standing.toString(), LibraryRole.READER, standing.role)
            }
        }
    }

    @Test
    fun `the index is only ever locked for a device the rules bind`() {
        forEveryCombination { standing ->
            if (standing.sharedIndexLocked) {
                assertFalse(standing.toString(), standing.isOwner)
                assertTrue(standing.toString(), standing.usesSharedIndex)
            }
        }
    }

    private fun forEveryCombination(check: (LibraryStanding) -> Unit) {
        val policies = listOf(
            null,
            LibraryPolicy(),
            LibraryPolicy(sharedIndexRequired = true),
            LibraryPolicy(editsAllowed = false),
            LibraryPolicy(sharedIndexRequired = true, editsAllowed = false),
        )
        for (kind in listOf(null, WebDavKind.ACCOUNT, WebDavKind.SHARE)) {
            for (writable in listOf(true, false)) {
                for (enabled in listOf(true, false)) {
                    for (policy in policies) {
                        for (owned in listOf(null, true, false)) {
                            for (unreadable in listOf(false, true)) {
                                check(
                                    standing(
                                        kind = kind,
                                        writable = writable,
                                        sharedIndexEnabled = enabled,
                                        policy = policy,
                                        owned = owned,
                                        unreadable = unreadable,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun standing(
        kind: WebDavKind?,
        writable: Boolean = true,
        sharedIndexEnabled: Boolean = true,
        policy: LibraryPolicy? = null,
        owned: Boolean? = false,
        unreadable: Boolean = false,
    ): LibraryStanding = LibraryStanding.of(
        kind = kind,
        writable = writable,
        sharedIndexEnabled = sharedIndexEnabled,
        resolution = PolicyResolution(
            forRoot = root,
            policy = when {
                unreadable -> PolicyInForce.unreadable(atFolder = root)
                policy != null -> PolicyInForce.of(policy, atFolder = root)
                else -> PolicyInForce.OPEN
            },
            owned = owned,
            checkedAt = 1L,
        ),
        libraryRoot = root,
    )
}
