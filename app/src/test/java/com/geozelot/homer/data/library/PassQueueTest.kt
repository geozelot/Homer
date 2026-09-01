package com.geozelot.homer.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the arithmetic that replaced "one unique work name, enqueued with REPLACE".
 *
 * The defect it fixes is not subtle — asking for lengths cancelled a cover pass half way through —
 * but the properties that make the queue a safe replacement are. Three of them carry the design:
 * a request is absorbed rather than duplicated, a pass that ran is removed by the *exact* token it
 * ran under (so an upgrade to deep survives the shallow pass finishing), and a token this build
 * cannot interpret is never guessed at.
 */
class PassQueueTest {

    @Test
    fun `a token round-trips`() {
        for (pass in IndexPass.entries) {
            for (deep in listOf(false, true)) {
                val request = PassRequest(pass, deep && pass.hasDeep)
                assertEquals(request, PassQueue.decode(PassQueue.encode(request)))
            }
        }
    }

    @Test
    fun `deep is dropped from a pass that has no deep variant`() {
        // Publishing corrections thoroughly is not a thing, and a token that claimed it would name
        // a pass the drain has no branch for.
        assertEquals("ARTWORK", PassQueue.encode(PassRequest(IndexPass.ARTWORK)))
    }

    @Test
    fun `a token this build does not understand is not guessed at`() {
        assertNull("a pass from a newer build", PassQueue.decode("SHELVES"))
        // Also covers a pass that has LEFT: `CORRECTIONS` was removed, and a token an older
        // build persisted has to be dropped rather than mishandled.
        assertNull("a pass this build does not have", PassQueue.decode("CORRECTIONS"))
        assertNull(PassQueue.decode(""))
        assertNull(PassQueue.decode("BOOKS:thorough"))
    }

    @Test
    fun `asking twice is one pass`() {
        val once = PassQueue.request(emptySet(), PassRequest(IndexPass.ARTWORK))
        val twice = PassQueue.request(once, PassRequest(IndexPass.ARTWORK))
        // Several places ask for artwork on their own initiative, app open among them.
        assertEquals(once, twice)
        assertEquals(1, PassQueue.pending(twice).size)
    }

    @Test
    fun `a deep request replaces the shallow one it subsumes`() {
        var q = PassQueue.request(emptySet(), PassRequest(IndexPass.BOOKS))
        q = PassQueue.request(q, PassRequest(IndexPass.BOOKS, deep = true))
        assertEquals(listOf(PassRequest(IndexPass.BOOKS, deep = true)), PassQueue.pending(q))
    }

    @Test
    fun `a shallow request is absorbed by a deep one already waiting`() {
        var q = PassQueue.request(emptySet(), PassRequest(IndexPass.BOOKS, deep = true))
        q = PassQueue.request(q, PassRequest(IndexPass.BOOKS))
        // A full crawl does everything an incremental one would, so queueing both is one crawl too
        // many — and the cheap one must not be the one that survives.
        assertEquals(listOf(PassRequest(IndexPass.BOOKS, deep = true)), PassQueue.pending(q))
    }

    @Test
    fun `corrections run first and lengths last`() {
        // Asked for in exactly the wrong order, because the set preserves insertion order — asking
        // in run order would pass whether the queue sorted or not.
        val q = IndexPass.entries.reversed()
            .fold(emptySet<String>()) { acc, pass -> PassQueue.request(acc, PassRequest(pass)) }
        assertEquals(
            // A correction is kilobytes and someone is waiting to see it on another device; a length
            // sweep is the longest thing the app does. Neither order is arbitrary.
            listOf(IndexPass.BOOKS, IndexPass.ARTWORK, IndexPass.LENGTHS),
            PassQueue.pending(q).map { it.pass },
        )
        assertEquals(PassRequest(IndexPass.BOOKS), PassQueue.next(q))
    }

    @Test
    fun `an empty queue has nothing to run`() {
        assertNull(PassQueue.next(emptySet()))
        assertEquals(emptyList<PassRequest>(), PassQueue.pending(emptySet()))
    }

    @Test
    fun `a finished pass leaves the rest of the queue alone`() {
        var q = PassQueue.request(emptySet(), PassRequest(IndexPass.BOOKS))
        q = PassQueue.request(q, PassRequest(IndexPass.LENGTHS))
        q = PassQueue.done(q, PassRequest(IndexPass.BOOKS))
        assertEquals(listOf(PassRequest(IndexPass.LENGTHS)), PassQueue.pending(q))
    }

    @Test
    fun `finishing a shallow pass does not cancel the deep one asked for while it ran`() {
        // The worker took the shallow request, then the user hit "Rebuild the library". Removing
        // every token for the pass would silently swallow the thorough crawl they asked for.
        var q = PassQueue.request(emptySet(), PassRequest(IndexPass.BOOKS))
        q = PassQueue.request(q, PassRequest(IndexPass.BOOKS, deep = true))
        q = PassQueue.done(q, PassRequest(IndexPass.BOOKS))
        assertEquals(listOf(PassRequest(IndexPass.BOOKS, deep = true)), PassQueue.pending(q))
    }

    @Test
    fun `finishing a deep pass clears the pass`() {
        val q = PassQueue.done(
            PassQueue.request(emptySet(), PassRequest(IndexPass.LENGTHS, deep = true)),
            PassRequest(IndexPass.LENGTHS, deep = true),
        )
        assertTrue(PassQueue.pending(q).isEmpty())
    }

    @Test
    fun `finishing a pass sweeps up tokens nothing can run`() {
        // A downgrade leaves tokens from the newer build behind. They can never be drained, so they
        // would sit in the store for ever and keep it looking non-empty.
        val q = PassQueue.done(setOf("SHELVES", "BOOKS"), PassRequest(IndexPass.BOOKS))
        assertTrue(q.isEmpty())
    }

    @Test
    fun `an undecodable token is not mistaken for outstanding work`() {
        // Whatever else happens, garbage in the store must not make the UI claim a pass is running
        // or keep the reconciler starting workers with nothing to do.
        assertTrue(PassQueue.pending(setOf("SHELVES", "BOOKS:thorough")).isEmpty())
        assertNull(PassQueue.next(setOf("SHELVES")))
    }
}
