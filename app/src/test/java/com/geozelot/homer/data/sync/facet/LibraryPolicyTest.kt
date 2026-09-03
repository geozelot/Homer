package com.geozelot.homer.data.sync.facet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library owner's rules.
 *
 * Two things here are load-bearing and neither fails loudly. The lookup walk is what stops the whole
 * feature being bypassed by pointing the app one folder deeper; and an unreadable policy has to fail
 * *closed*, the opposite of every other facet, or a rule this build cannot parse silently stops
 * applying.
 */
class LibraryPolicyTest {

    // ── the walk up the tree ─────────────────────────────────────────────────────────────────

    @Test
    fun `the walk starts at the library root and ends at the files root`() {
        assertEquals(
            listOf("Media/Books/Audiobooks", "Media/Books", "Media", ""),
            LibraryPolicy.lookupFolders("Media/Books/Audiobooks"),
        )
    }

    @Test
    fun `a library at the files root looks in exactly one place`() {
        // Which is also every share link: the share IS the root, and there is nothing above it.
        assertEquals(listOf(""), LibraryPolicy.lookupFolders(""))
    }

    @Test
    fun `the nearest folder comes first, because the nearest rule wins`() {
        val folders = LibraryPolicy.lookupFolders("a/b/c")
        assertEquals("a/b/c", folders.first())
        assertEquals("", folders.last())
    }

    @Test
    fun `slashes around the root do not add a level`() {
        assertEquals(LibraryPolicy.lookupFolders("Audiobooks"), LibraryPolicy.lookupFolders("/Audiobooks/"))
    }

    @Test
    fun `the walk is capped, and the cap cuts the far end`() {
        val folders = LibraryPolicy.lookupFolders("a/b/c/d/e/f/g/h", maxLevels = 3)
        assertEquals(listOf("a/b/c/d/e/f/g/h", "a/b/c/d/e/f/g", "a/b/c/d/e/f"), folders)
    }

    @Test
    fun `the default cap is deeper than any real nesting and still terminates`() {
        val deep = (1..40).joinToString("/") { "f$it" }
        assertEquals(LibraryPolicy.MAX_LOOKUP_LEVELS, LibraryPolicy.lookupFolders(deep).size)
    }

    // ── what a resolved policy says ──────────────────────────────────────────────────────────

    @Test
    fun `no policy anywhere is exactly today's behaviour`() {
        // Every library that exists predates this file. None of them may become stricter for it.
        assertFalse(PolicyInForce.OPEN.sharedIndexRequired)
        assertTrue(PolicyInForce.OPEN.editsAllowed)
        assertFalse(PolicyInForce.OPEN.present)
        assertTrue(PolicyInForce.OPEN.understood)
    }

    @Test
    fun `a default policy file is as permissive as no file at all`() {
        val open = PolicyInForce.of(LibraryPolicy(), atFolder = "Audiobooks")
        assertFalse(open.sharedIndexRequired)
        assertTrue(open.editsAllowed)
        // But it IS present, which is a different statement: somebody has been here and said yes.
        assertTrue(open.present)
    }

    @Test
    fun `an unreadable policy fails closed`() {
        // The one facet that does. Treating it as absent — which is what ofCurrentSchema does to
        // the other three — would make a rule this build cannot parse stop applying, and the
        // stopping would look exactly like the crawl-everything problem it prevents.
        val strict = PolicyInForce.unreadable(atFolder = "Audiobooks")
        assertTrue(strict.sharedIndexRequired)
        assertFalse(strict.editsAllowed)
        assertTrue(strict.present)
        assertFalse(strict.understood)
    }

    @Test
    fun `the folder the rules came from is remembered`() {
        // With a walk, a rule can come from a folder the reader never chose. Not saying where it
        // came from makes a locked switch look like a bug.
        val policy = PolicyInForce.of(LibraryPolicy(sharedIndexRequired = true), atFolder = "Media/Books")
        assertEquals("Media/Books", policy.atFolder)
    }

    @Test
    fun `a policy at the files root is present, not absent`() {
        // "" is a real folder — the files root, or a share's own root — so presence cannot be
        // inferred from the folder being empty.
        assertTrue(PolicyInForce.of(LibraryPolicy(), atFolder = "").present)
    }

    @Test
    fun `a live owner probe outranks the owner recorded in the file`() {
        val policy = LibraryPolicy(ownerId = "old-owner")
        assertEquals("andre", PolicyInForce.of(policy, atFolder = "", owner = "andre").owner)
        assertEquals("old-owner", PolicyInForce.of(policy, atFolder = "").owner)
    }

    // ── a resolution is about ONE folder ─────────────────────────────────────────────────────

    @Test
    fun `a resolution only describes the root it was made for`() {
        // The gap between changing the root and the next resolve is exactly when a scan is
        // enqueued, so an answer without its subject would go on applying to somewhere else.
        val resolution = resolutionFor("Audiobooks")
        assertTrue(resolution.describes("Audiobooks"))
        assertFalse(resolution.describes("Media/Books"))
        assertFalse(resolution.describes(""))
    }

    @Test
    fun `describing a root ignores surrounding slashes`() {
        assertTrue(resolutionFor("Audiobooks").describes("/Audiobooks/"))
    }

    @Test
    fun `nothing resolved describes nothing, not the files root`() {
        assertFalse(PolicyResolution.NONE.describes(""))
        assertFalse(PolicyResolution.NONE.isOwner)
    }

    @Test
    fun `an unanswerable owner is not somebody else's`() {
        // A WebDAV server that is not Nextcloud exposes no owner. That must not read as "not
        // yours" — it is "unanswerable here", and the account reaching the folder is all there is.
        assertFalse(resolutionFor("x", owned = null).isOwner)
        assertFalse(resolutionFor("x", owned = false).isOwner)
        assertTrue(resolutionFor("x", owned = true).isOwner)
    }

    private fun resolutionFor(root: String, owned: Boolean? = null) = PolicyResolution(
        forRoot = root,
        policy = PolicyInForce.OPEN,
        owned = owned,
        checkedAt = 1_000L,
    )
}
