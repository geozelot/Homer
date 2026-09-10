package com.geozelot.homer.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One save per event, no more and no less.
 *
 * The rule is split across two player callbacks because the player reports a jump through both, and
 * that split is the whole hazard: every real event fires at least one of them, a manual chapter skip
 * fires both, and the two rules only make sense read as a pair. Written as a table of the events
 * that actually happen, asserting the SUM — which is the invariant, and the thing that broke in each
 * direction once already (a chapter change saving twice, a seek not saving at all).
 */
class PlaybackSaveRulesTest {

    /** How many saves one real event produces. Null = that callback does not fire for this event. */
    private fun saves(discontinuity: Int?, transition: Int?): Int =
        (if (discontinuity != null && savesOnDiscontinuity(discontinuity)) 1 else 0) +
            (if (transition != null && savesOnTransition(transition)) 1 else 0)

    @Test
    fun `a seek inside a chapter saves once`() {
        assertEquals(1, saves(Player.DISCONTINUITY_REASON_SEEK, null))
    }

    @Test
    fun `a manual chapter skip saves once, though it fires both callbacks`() {
        // The one that used to save twice: jumping to a chapter is a seek AND a media-item change.
        assertEquals(
            1,
            saves(Player.DISCONTINUITY_REASON_SEEK, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK),
        )
    }

    @Test
    fun `a chapter running out saves once`() {
        assertEquals(
            1,
            saves(
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            ),
        )
    }

    @Test
    fun `opening a book saves nothing here`() {
        // A fresh queue is a PLAYLIST_CHANGED transition; the position it starts at was just read
        // from the database, and writing it straight back is a round trip that says nothing. The
        // `loading` guard in the listener stops it too — this is the second lock on the same door.
        assertEquals(
            0,
            saves(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED),
        )
    }

    @Test
    fun `an item removed from under the player saves nothing`() {
        assertEquals(0, saves(Player.DISCONTINUITY_REASON_REMOVE, null))
    }
}
