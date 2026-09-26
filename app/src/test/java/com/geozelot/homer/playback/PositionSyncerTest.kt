package com.geozelot.homer.playback

import com.geozelot.homer.data.db.dao.BookProgress
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.delay

/**
 * When the position leaves the device.
 *
 * The first test is the reason this file exists: `flush(force = true)` fires from
 * `ProcessLifecycleOwner.onStop`, the moment the OS may freeze the process — and it used to sit
 * out the same one-second debounce as every routine flush. The push the comment calls "the most
 * important of all" was silently dropped whenever the freeze won that race. Forced flushes now
 * skip the debounce; everything else here guards the coalescing that ordinary flushes rely on.
 */
class PositionSyncerTest {

    private class RecordingDao(private val writeDelayMs: Long = 0) : PlaybackStateDao {
        val upserts = mutableListOf<PlaybackStateEntity>()
        override suspend fun findByBookId(bookId: String): PlaybackStateEntity? = null
        override suspend fun getAll(): List<PlaybackStateEntity> = emptyList()
        override suspend fun maxUpdatedAt(): Long? = null
        override fun observeProgress(): Flow<List<BookProgress>> = emptyFlow()
        override suspend fun relink(oldId: String, newId: String) = Unit
        override suspend fun updateCurrentMediaId(bookId: String, mediaId: String) = Unit
        override suspend fun deleteOrphans() = Unit
        override suspend fun upsert(state: PlaybackStateEntity) {
            if (writeDelayMs > 0) delay(writeDelayMs)
            upserts += state
        }
    }

    /** The syncer with its manifest side recorded as an event log ("export", "sync", "sync(forced)"). */
    private class Harness(scope: CoroutineScope, writeDelayMs: Long = 0) {
        val dao = RecordingDao(writeDelayMs)
        var snapshot: PositionSnapshot? = PositionSnapshot("book", "book/ch02.mp3", 42_000L)
        val events = mutableListOf<String>()
        val savedAtExport = mutableListOf<Int>()

        /** Captured rather than ignored, so the test can BE the process lifecycle. */
        private var background: (() -> Unit)? = null

        val syncer = PositionSyncer(
            scope = scope,
            playbackStateDao = dao,
            snapshot = { snapshot },
            // Records how many writes had LANDED when the export ran — the export reads them.
            exportMirror = { events += "export"; savedAtExport += dao.upserts.size },
            syncManifest = { force -> events += if (force) "sync(forced)" else "sync" },
            observeBackgrounding = { onBackground -> background = onBackground },
        )

        /** The app went to the background. */
        fun background() = background?.invoke()
    }

    // ── the bug ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a forced flush pushes without waiting out the debounce`() = runTest {
        val h = Harness(this)
        h.syncer.flush(force = true)
        runCurrent() // execute what is already scheduled — no virtual time passes
        assertEquals(listOf("export", "sync(forced)"), h.events)
    }

    @Test
    fun `a forced flush overrides a pending debounced one`() = runTest {
        val h = Harness(this)
        h.syncer.flush(force = false)
        advanceTimeBy(PositionSyncer.SYNC_DEBOUNCE_MS / 2)
        h.syncer.flush(force = true)
        runCurrent()
        assertEquals(listOf("export", "sync(forced)"), h.events)
        // The cancelled debounced job must not resurrect once its delay would have elapsed.
        advanceUntilIdle()
        assertEquals(listOf("export", "sync(forced)"), h.events)
    }

    // ── ordinary flushes keep coalescing ─────────────────────────────────────────────────────

    @Test
    fun `a normal flush waits out the debounce`() = runTest {
        val h = Harness(this)
        h.syncer.flush(force = false)
        advanceTimeBy(PositionSyncer.SYNC_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(emptyList<String>(), h.events)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("export", "sync"), h.events)
    }

    @Test
    fun `a burst of flushes collapses into one push`() = runTest {
        val h = Harness(this)
        repeat(5) {
            h.syncer.flush(force = false)
            advanceTimeBy(PositionSyncer.SYNC_DEBOUNCE_MS / 2)
        }
        advanceUntilIdle()
        assertEquals(listOf("export", "sync"), h.events)
    }

    // ── the local write underneath ───────────────────────────────────────────────────────────

    @Test
    fun `flush persists the position before the push`() = runTest {
        val h = Harness(this)
        h.syncer.flush(force = true)
        runCurrent()
        val saved = h.dao.upserts.single()
        assertEquals("book", saved.bookId)
        assertEquals("book/ch02.mp3", saved.currentMediaId)
        assertEquals(42_000L, saved.positionMs)
    }

    @Test
    fun `the push reads the position it was flushed with, even when the write is slow`() = runTest {
        // The race this pins: the save and the push were two independent launches, and the push
        // reads Room. With a write that takes any time at all, the export ran against the position
        // from BEFORE the flush — and the forced flush on backgrounding is the one that loses it.
        val h = Harness(this, writeDelayMs = 500)
        h.syncer.flush(force = true)
        advanceUntilIdle()
        assertEquals(listOf(1), h.savedAtExport)
    }

    @Test
    fun `save persists the snapshot taken at execution time`() = runTest {
        val h = Harness(this)
        h.syncer.save()
        h.snapshot = PositionSnapshot("book", "book/ch03.mp3", 7_000L)
        runCurrent() // the coroutine reads the snapshot when it runs, not when it was launched
        assertEquals("book/ch03.mp3", h.dao.upserts.single().currentMediaId)
    }

    @Test
    fun `nothing is persisted when no book is loaded`() = runTest {
        val h = Harness(this)
        h.snapshot = null
        h.syncer.save()
        advanceUntilIdle()
        assertTrue(h.dao.upserts.isEmpty())
    }

    @Test
    fun `backgrounding pushes, forced and without waiting`() = runTest {
        // The registration is a required constructor argument precisely so this is reachable: it
        // used to be a method the host had to remember to call, and forgetting it would have lost
        // the most important push in the class with nothing failing to say so.
        val h = Harness(this)
        h.background()
        runCurrent()
        assertEquals(listOf("export", "sync(forced)"), h.events)
    }

    @Test
    fun `backgrounding with no book loaded pushes nothing`() = runTest {
        val h = Harness(this)
        h.snapshot = null
        h.background()
        advanceUntilIdle()
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun `a push already in flight is never cancelled by the next flush`() = runTest {
        // A pause starts a forced push at once; backgrounding a moment later used to cancel it
        // mid-request, because one cancel covered both the debounce wait and the push itself.
        val h = Harness(this)
        h.syncer.flush(force = true)
        runCurrent()
        h.syncer.flush(force = true)
        advanceUntilIdle()
        // Both pushes ran to completion, serialised — neither was aborted part-way.
        assertEquals(listOf("export", "sync(forced)", "export", "sync(forced)"), h.events)
    }

    @Test
    fun `pull reconciles without forcing`() = runTest {
        val h = Harness(this)
        h.syncer.pull()
        assertEquals(listOf("sync"), h.events)
    }
}
