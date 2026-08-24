package com.geozelot.homer.data.sync.facet

import com.geozelot.homer.data.webdav.DavRead
import com.geozelot.homer.data.webdav.PreconditionFailedException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store's decisions, with the network faked out.
 *
 * Two of these encode failures that already happened in production: an ETag cached before the
 * parse succeeded made one damaged catalog permanent, and a write that loses its race has to
 * re-read rather than clobber.
 */
class FacetStoreTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val file = LibraryFacets.CORRECTIONS_FILE
    private val serializer = CorrectionsFacet.serializer()

    private fun facet(vararg titles: Pair<String, String>) = CorrectionsFacet(
        books = titles.associate { (id, t) -> id to BookCorrection(title = t, editedAt = 1) },
    )

    /** Records what was asked of it and answers from a scripted queue. */
    private class FakeTransport(
        var reads: MutableList<DavRead> = mutableListOf(),
    ) : FacetTransport {
        val readPaths = mutableListOf<String>()
        val ifNoneMatches = mutableListOf<String?>()
        val writes = mutableListOf<Triple<String, String, String?>>()
        var dirsEnsured = 0
        var failWritesWith412 = 0
        var readThrows: Exception? = null
        var writeThrows: Exception? = null
        var nextEtag: String? = "etag-new"

        override suspend fun read(path: String, ifNoneMatch: String?): DavRead {
            readThrows?.let { throw it }
            readPaths += path
            ifNoneMatches += ifNoneMatch
            return if (reads.isEmpty()) DavRead.Absent else reads.removeAt(0)
        }

        val createGuards = mutableListOf<Boolean>()

        override suspend fun write(
            path: String,
            content: String,
            ifMatch: String?,
            onlyIfAbsent: Boolean,
        ): String? {
            writes += Triple(path, content, ifMatch)
            createGuards += onlyIfAbsent
            writeThrows?.let { throw it }
            if (failWritesWith412 > 0) {
                failWritesWith412--
                throw PreconditionFailedException()
            }
            return nextEtag
        }

        override suspend fun ensureDir(path: String) {
            dirsEnsured++
        }
    }

    private fun store(t: FakeTransport, root: String = "Books") =
        FacetStore(t, object : LibraryRootSource { override suspend fun root() = root }, json)

    private fun body(value: CorrectionsFacet, etag: String? = "etag-1") =
        DavRead.Body(json.encodeToString(serializer, value), etag)

    // ── paths ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the facet sits under the library root's homer folder`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet())))
        store(t).load(file, serializer)
        assertEquals("Books/.homer/corrections.json", t.readPaths.single())
    }

    @Test
    fun `an empty library root means the files root`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet())))
        store(t, root = "").load(file, serializer)
        assertEquals(".homer/corrections.json", t.readPaths.single())
    }

    // ── loading ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a parsed body is returned and its etag remembered`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet("a" to "A")), DavRead.NotModified))
        val s = store(t)
        assertEquals(facet("a" to "A"), (s.load(file, serializer) as FacetStore.Load.Present).value)
        assertTrue(s.load(file, serializer) is FacetStore.Load.Unchanged)
        // The second read must be conditional, or every poll re-downloads the whole file.
        assertEquals(listOf(null, "etag-1"), t.ifNoneMatches)
    }

    @Test
    fun `an unparseable body never caches its etag`() = runBlocking {
        // The BETA16 failure: caching before the parse meant every later read got a 304 and
        // reported success for a file that had never been applied.
        val t = FakeTransport(mutableListOf(DavRead.Body("{ not json", "etag-bad"), body(facet("a" to "A"))))
        val s = store(t)
        assertTrue(s.load(file, serializer) is FacetStore.Load.Damaged)
        assertTrue(s.load(file, serializer) is FacetStore.Load.Present)
        assertEquals(listOf(null, null), t.ifNoneMatches)
    }

    @Test
    fun `a missing file clears any remembered etag`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet("a" to "A")), DavRead.Absent, body(facet())))
        val s = store(t)
        s.load(file, serializer)
        assertTrue(s.load(file, serializer) is FacetStore.Load.Missing)
        s.load(file, serializer)
        assertEquals(listOf(null, "etag-1", null), t.ifNoneMatches)
    }

    @Test
    fun `a blank body reads as missing, not as damaged`() = runBlocking {
        val t = FakeTransport(mutableListOf(DavRead.Body("   ", "etag-1")))
        assertTrue(store(t).load(file, serializer) is FacetStore.Load.Missing)
    }

    @Test
    fun `a failed request is unavailable, which is not the same as absent`() = runBlocking {
        val t = FakeTransport().apply { readThrows = java.io.IOException("no route to host") }
        val load = store(t).load(file, serializer)
        assertTrue(load is FacetStore.Load.Unavailable)
        assertEquals("no route to host", (load as FacetStore.Load.Unavailable).message)
    }

    @Test
    fun `forgetting etags forces a full read`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet("a" to "A")), body(facet("a" to "A"))))
        val s = store(t)
        s.load(file, serializer)
        s.forgetEtags()
        s.load(file, serializer)
        assertEquals(listOf(null, null), t.ifNoneMatches)
    }

    // ── saving ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a write is guarded by the etag it read`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet("a" to "A"))))
        val result = store(t).save(file, serializer) { facet("a" to "B") }
        assertEquals(FacetStore.SaveResult.Written, result)
        assertEquals("etag-1", t.writes.single().third)
        assertEquals(1, t.dirsEnsured)
    }

    @Test
    fun `the merge sees what was there`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet("a" to "A"))))
        var seen: FacetStore.Load<CorrectionsFacet>? = null
        store(t).save(file, serializer) { seen = it; facet("a" to "B") }
        assertEquals(facet("a" to "A"), (seen as FacetStore.Load.Present).value)
    }

    @Test
    fun `a damaged remote is distinguishable from a missing one`() = runBlocking {
        // The caller has to be able to refuse to replace a damaged file with an empty view.
        val damaged = FakeTransport(mutableListOf(DavRead.Body("{ not json", "e")))
        var seenDamaged: FacetStore.Load<CorrectionsFacet>? = null
        store(damaged).save(file, serializer) { seenDamaged = it; null }
        assertTrue(seenDamaged is FacetStore.Load.Damaged)

        val missing = FakeTransport(mutableListOf(DavRead.Absent))
        var seenMissing: FacetStore.Load<CorrectionsFacet>? = null
        store(missing).save(file, serializer) { seenMissing = it; null }
        assertTrue(seenMissing is FacetStore.Load.Missing)
    }

    @Test
    fun `declining the merge writes nothing`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet("a" to "A"))))
        assertEquals(FacetStore.SaveResult.Declined, store(t).save(file, serializer) { null })
        assertTrue(t.writes.isEmpty())
    }

    @Test
    fun `a merge that changes nothing writes nothing`() = runBlocking {
        // Otherwise every sync rewrites every facet and bumps every ETag for no reason.
        val t = FakeTransport(mutableListOf(body(facet("a" to "A"))))
        val result = store(t).save(file, serializer) { facet("a" to "A") }
        assertEquals(FacetStore.SaveResult.AlreadyCurrent, result)
        assertTrue(t.writes.isEmpty())
    }

    @Test
    fun `a lost race is retried against what is there now`() = runBlocking {
        val t = FakeTransport(
            mutableListOf(body(facet("a" to "A"), "etag-1"), body(facet("a" to "A", "b" to "B"), "etag-2")),
        ).apply { failWritesWith412 = 1 }
        val seen = mutableListOf<CorrectionsFacet?>()
        val result = store(t).save(file, serializer) { state ->
            seen += (state as? FacetStore.Load.Present)?.value
            facet("a" to "Z")
        }
        assertEquals(FacetStore.SaveResult.Written, result)
        // Second attempt saw the newer remote, not the copy it started from.
        assertEquals(listOf(facet("a" to "A"), facet("a" to "A", "b" to "B")), seen)
        assertEquals(listOf("etag-1", "etag-2"), t.writes.map { it.third })
    }

    @Test
    fun `the last attempt drops the guard rather than losing the change`() = runBlocking {
        val t = FakeTransport(
            mutableListOf(body(facet(), "e1"), body(facet(), "e2"), body(facet(), "e3")),
        ).apply { failWritesWith412 = 2 }
        val result = store(t).save(file, serializer) { facet("a" to "A") }
        assertEquals(FacetStore.SaveResult.Written, result)
        assertEquals(listOf("e1", "e2", null), t.writes.map { it.third })
    }

    @Test
    fun `losing every race reports contention instead of claiming success`() = runBlocking {
        val t = FakeTransport(
            mutableListOf(body(facet(), "e1"), body(facet(), "e2"), body(facet(), "e3")),
        ).apply { failWritesWith412 = 3 }
        assertEquals(FacetStore.SaveResult.Contended, store(t).save(file, serializer) { facet("a" to "A") })
    }

    @Test
    fun `an unreachable server is reported, not retried into contention`() = runBlocking {
        val t = FakeTransport().apply { readThrows = java.io.IOException("offline") }
        val result = store(t).save(file, serializer) { facet("a" to "A") }
        assertTrue(result is FacetStore.SaveResult.Unavailable)
        assertTrue(t.writes.isEmpty())
    }

    @Test
    fun `a write into nothing still creates the folder`() = runBlocking {
        val t = FakeTransport(mutableListOf(DavRead.Absent))
        assertEquals(FacetStore.SaveResult.Written, store(t).save(file, serializer) { facet("a" to "A") })
        assertEquals(1, t.dirsEnsured)
        assertNull(t.writes.single().third)
    }

    @Test
    fun `creating a facet refuses to overwrite one that appeared meanwhile`() = runBlocking {
        // Two devices enabling the shared index at the same moment: without this guard both write
        // unconditionally and the second erases the first.
        val t = FakeTransport(mutableListOf(DavRead.Absent))
        store(t).save(file, serializer) { facet("a" to "A") }
        assertEquals(listOf(true), t.createGuards)
    }

    @Test
    fun `updating an existing facet guards on its etag, not on absence`() = runBlocking {
        val t = FakeTransport(mutableListOf(body(facet("a" to "A"))))
        store(t).save(file, serializer) { facet("a" to "B") }
        assertEquals(listOf(false), t.createGuards)
        assertEquals("etag-1", t.writes.single().third)
    }

    @Test
    fun `the last attempt writes unconditionally rather than losing the change`() = runBlocking {
        val t = FakeTransport(mutableListOf(DavRead.Absent, DavRead.Absent, DavRead.Absent))
            .apply { failWritesWith412 = 2 }
        assertEquals(FacetStore.SaveResult.Written, store(t).save(file, serializer) { facet("a" to "A") })
        // Guarded, guarded, then forced rather than dropped.
        assertEquals(listOf(true, true, false), t.createGuards)
    }

    @Test
    fun `a fresh write remembers the etag the server returned`() = runBlocking {
        val t = FakeTransport(mutableListOf(DavRead.Absent, DavRead.NotModified)).apply { nextEtag = "etag-put" }
        val s = store(t)
        s.save(file, serializer) { facet("a" to "A") }
        assertTrue(s.load(file, serializer) is FacetStore.Load.Unchanged)
        assertEquals("etag-put", t.ifNoneMatches.last())
    }
}
