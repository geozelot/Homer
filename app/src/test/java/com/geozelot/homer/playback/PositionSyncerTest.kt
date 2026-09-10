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

    private class RecordingDao : PlaybackStateDao {
        val upserts = mutableListOf<PlaybackStateEntity>()
        override suspend fun findByBookId(bookId: String): PlaybackStateEntity? = null
        override suspend fun getAll(): List<PlaybackStateEntity> = emptyList()
        override suspend fun maxUpdatedAt(): Long? = null
        override fun observeProgress(): Flow<List<BookProgress>> = emptyFlow()
        override suspend fun relink(oldId: String, newId: String) = Unit
        override suspend fun updateCurrentMediaId(bookId: String, mediaId: String) = Unit
        override suspend fun deleteOrphans() = Unit
        override suspend fun upsert(state: PlaybackStateEntity) {
            upserts += state
        }
    }

    /** The syncer with its manifest side recorded as an event log ("export", "sync", "sync(forced)"). */
    private class Harness(scope: CoroutineScope) {
        val dao = RecordingDao()
        var snapshot: PositionSnapshot? = PositionSnapshot("book", "book/ch02.mp3", 42_000L)
        val events = mutableListOf<String>()
        val syncer = PositionSyncer(
            scope = scope,
            playbackStateDao = dao,
            snapshot = { snapshot },
            exportMirror = { events += "export" },
            syncManifest = { force -> events += if (force) "sync(forced)" else "sync" },
        )
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
    fun `pull reconciles without forcing`() = runTest {
        val h = Harness(this)
        h.syncer.pull()
        assertEquals(listOf("sync"), h.events)
    }
}
