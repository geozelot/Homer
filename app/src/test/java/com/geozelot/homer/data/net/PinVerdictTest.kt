package com.geozelot.homer.data.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that used to expire on a timer.
 *
 * Pinning kept the leaf certificate alone, and a leaf is the one certificate guaranteed to change:
 * a renewal issues a new key, the stored hash stopped matching, and from that moment every request
 * to the server was refused — in both directions, permanently, with a warning in logcat and nothing
 * on screen. These are the cases that must keep holding.
 */
class PinVerdictTest {

    private val leaf = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val renewedLeaf = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
    private val intermediate = "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC="
    private val root = "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD="
    private val stranger = "sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE="

    @Test
    fun `nothing pinned yet captures what is presented`() {
        assertEquals(PinVerdict.Capture, pinVerdict(stored = emptyList(), offered = listOf(leaf, intermediate)))
    }

    @Test
    fun `the same chain verifies`() {
        val chain = listOf(leaf, intermediate, root)
        assertEquals(PinVerdict.Verified, pinVerdict(stored = chain, offered = chain))
    }

    @Test
    fun `a renewed leaf under the same intermediate still verifies`() {
        // THE case. Let's Encrypt renews every sixty to ninety days with a fresh key, so this is
        // what every pinned install eventually meets. Pinning the leaf alone made it a refusal.
        assertEquals(
            PinVerdict.Verified,
            pinVerdict(
                stored = listOf(leaf, intermediate, root),
                offered = listOf(renewedLeaf, intermediate, root),
            ),
        )
    }

    @Test
    fun `a chain sharing nothing is blocked`() {
        assertEquals(
            PinVerdict.Blocked,
            pinVerdict(stored = listOf(leaf, intermediate), offered = listOf(stranger)),
        )
    }

    @Test
    fun `an issuer change is blocked, which is the point of the feature`() {
        // A CA rotating its intermediate lands here too, and that is correct: it is a real change
        // in who vouches for the server, and accepting it is the user's decision to make.
        assertEquals(
            PinVerdict.Blocked,
            pinVerdict(stored = listOf(leaf, intermediate), offered = listOf(renewedLeaf, stranger)),
        )
    }

    @Test
    fun `presenting nothing does not get past a pin`() {
        assertEquals(PinVerdict.Blocked, pinVerdict(stored = listOf(leaf), offered = emptyList()))
    }

    @Test
    fun `a single stored pin from before the chain was kept still verifies itself`() {
        // Installs upgrading into this carry one leaf hash. It keeps working until that leaf is
        // renewed, at which point the block is shown and accepting it stores the whole chain.
        assertEquals(
            PinVerdict.Verified,
            pinVerdict(stored = listOf(leaf), offered = listOf(leaf, intermediate)),
        )
    }
}
