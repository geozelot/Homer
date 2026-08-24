package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [planWrites] and [detectMoves] — the rules that decide what a rescan keeps.
 *
 * A crawl sees only names, sizes and ETags. Everything *measured* lives in the row being replaced,
 * so every rescan has to copy it forward, and every way of getting that wrong is silent: a dropped
 * duration re-probes the file (a whole stream) and, until it does, costs the book its time-left,
 * progress ring and auto-finish. This is the logic that has regressed before, which is why it is
 * pulled out of the scanner as a pure function.
 */
class ScanCarryOverTest {

    private fun book(
        id: String,
        contentHash: String? = "hash-$id",
        genre: String? = null,
        localCoverPath: String? = null,
        customCoverPath: String? = null,
        coverAttempted: Boolean = false,
        metadataAttempted: Boolean = false,
        chapterTier: Int = 0,
        coverFilePath: String? = null,
        totalDurationMs: Long? = null,
    ) = BookEntity(
        id = id,
        contentHash = contentHash,
        title = id.substringAfterLast('/'),
        author = id.substringBeforeLast('/', ""),
        series = null,
        seriesIndex = null,
        genre = genre,
        relativePath = id,
        coverFilePath = coverFilePath,
        localCoverPath = localCoverPath,
        customCoverPath = customCoverPath,
        coverAttempted = coverAttempted,
        metadataAttempted = metadataAttempted,
        chapterTier = chapterTier,
        isMultiFile = true,
        fileCount = 1,
        totalDurationMs = totalDurationMs,
        addedAt = 1,
        updatedAt = 1,
    )

    private fun file(
        bookId: String,
        name: String,
        durationMs: Long? = null,
        durationAttempted: Boolean = false,
        index: Int = 0,
        sizeBytes: Long = 4096,
    ) = AudioFileEntity(
        relativePath = "$bookId/$name",
        bookId = bookId,
        fileName = name,
        sortIndex = index,
        sizeBytes = sizeBytes,
        etag = "etag",
        lastModified = 1,
        contentType = "audio/mpeg",
        durationMs = durationMs,
        durationAttempted = durationAttempted,
    )

    private fun detected(id: String, vararg files: AudioFileEntity, coverFilePath: String? = null) =
        BookDetector.Detected(
            book = book(id, coverFilePath = coverFilePath).copy(fileCount = files.size),
            // A crawl never knows a duration — that is the whole point of the carry-over.
            files = files.map { it.copy(durationMs = null, durationAttempted = false) },
        )

    // ── Durations ─────────────────────────────────────────────────────────────

    @Test
    fun `a rescan keeps durations already measured for the same paths`() {
        val planned = planWrites(
            detected = listOf(detected("A/One", file("A/One", "01.mp3"), file("A/One", "02.mp3", index = 1))),
            existingById = mapOf("A/One" to book("A/One")),
            filesByBook = mapOf(
                "A/One" to listOf(
                    file("A/One", "01.mp3", durationMs = 1000),
                    file("A/One", "02.mp3", durationMs = 2000, index = 1),
                ),
            ),
            movedFrom = emptyMap(),
        )
        assertEquals(listOf(1000L, 2000L), planned.files.map { it.durationMs })
        assertEquals("a fully measured book keeps its total", 3000L, planned.books.single().totalDurationMs)
    }

    @Test
    fun `a file replaced in place does not inherit the old file's duration`() {
        // Same path, same name, different bytes — a re-encode, a re-tag, a better rip swapped in.
        // Carrying the measurement forward attaches the old length to new audio, and nothing ever
        // re-measures it: the book's time-left, progress ring and auto-finish are wrong for good.
        val planned = planWrites(
            detected = listOf(detected("A/One", file("A/One", "01.mp3", sizeBytes = 9999))),
            existingById = mapOf("A/One" to book("A/One")),
            filesByBook = mapOf(
                "A/One" to listOf(file("A/One", "01.mp3", durationMs = 1000, durationAttempted = true)),
            ),
            movedFrom = emptyMap(),
        )
        assertNull(planned.files.single().durationMs)
        assertFalse("a changed file earns a fresh probe", planned.files.single().durationAttempted)
        assertNull("and the book cannot claim a total", planned.books.single().totalDurationMs)
    }

    @Test
    fun `a moved book only inherits durations for files that are still the same size`() {
        // The move match is by NAME, since the path changed with the folder — so it needs the same
        // guard, or moving a book is a way of laundering a stale measurement onto new audio.
        val planned = planWrites(
            detected = listOf(
                detected("New/One", file("New/One", "01.mp3"), file("New/One", "02.mp3", index = 1, sizeBytes = 7)),
            ),
            existingById = mapOf("Old/One" to book("Old/One")),
            filesByBook = mapOf(
                "Old/One" to listOf(
                    file("Old/One", "01.mp3", durationMs = 1000),
                    file("Old/One", "02.mp3", durationMs = 2000, index = 1),
                ),
            ),
            movedFrom = mapOf("New/One" to "Old/One"),
        )
        assertEquals(listOf(1000L, null), planned.files.map { it.durationMs })
    }

    @Test
    fun `a rescan keeps the flag marking a file as unmeasurable`() {
        // Without this an ordinary rescan re-streams every file that has already proven it carries
        // no readable duration — the traffic sink that durationAttempted was added to close.
        val planned = planWrites(
            detected = listOf(detected("A/One", file("A/One", "01.mp3"))),
            existingById = mapOf("A/One" to book("A/One")),
            filesByBook = mapOf("A/One" to listOf(file("A/One", "01.mp3", durationAttempted = true))),
            movedFrom = emptyMap(),
        )
        assertTrue(planned.files.single().durationAttempted)
    }

    @Test
    fun `a part-measured book gets no total`() {
        // A total covering only some files makes elapsed exceed it, which reads as "finished" and
        // hides the book from the library entirely.
        val planned = planWrites(
            detected = listOf(detected("A/One", file("A/One", "01.mp3"), file("A/One", "02.mp3", index = 1))),
            existingById = mapOf("A/One" to book("A/One", totalDurationMs = 9999)),
            filesByBook = mapOf("A/One" to listOf(file("A/One", "01.mp3", durationMs = 1000))),
            movedFrom = emptyMap(),
        )
        assertNull(planned.books.single().totalDurationMs)
    }

    @Test
    fun `a removed file drops out of the recomputed total`() {
        val planned = planWrites(
            detected = listOf(detected("A/One", file("A/One", "01.mp3"))),
            existingById = mapOf("A/One" to book("A/One", totalDurationMs = 3000)),
            filesByBook = mapOf(
                "A/One" to listOf(
                    file("A/One", "01.mp3", durationMs = 1000),
                    file("A/One", "02.mp3", durationMs = 2000, index = 1),
                ),
            ),
            movedFrom = emptyMap(),
        )
        assertEquals(1, planned.files.size)
        assertEquals(1000L, planned.books.single().totalDurationMs)
    }

    // ── Moved books ───────────────────────────────────────────────────────────

    @Test
    fun `a book that moved is matched by fingerprint, not by path`() {
        val existing = listOf(book("Author/Old Title", contentHash = "abc"))
        val moved = detectMoves(
            detected = listOf(detected("Author/New Title").let { it.copy(book = it.book.copy(contentHash = "abc")) }),
            existingBooks = existing,
            keepIds = setOf("Author/New Title"),
        )
        assertEquals(mapOf("Author/New Title" to "Author/Old Title"), moved)
    }

    @Test
    fun `a book still at its own path is never treated as a move target`() {
        // Two books can share a fingerprint (identical file names and sizes). One that still exists
        // where it always did has not moved, and claiming otherwise would re-link the other's data.
        val existing = listOf(book("A/One", contentHash = "same"), book("A/Two", contentHash = "same"))
        val moved = detectMoves(
            detected = listOf(detected("A/One").let { it.copy(book = it.book.copy(contentHash = "same")) }),
            existingBooks = existing,
            keepIds = setOf("A/One"),
        )
        assertTrue(moved.isEmpty())
    }

    @Test
    fun `a book the scan still accounts for is not a move source`() {
        val existing = listOf(book("A/One", contentHash = "abc"))
        val moved = detectMoves(
            detected = listOf(detected("A/Two").let { it.copy(book = it.book.copy(contentHash = "abc")) }),
            existingBooks = existing,
            // A/One survives under a skipped subtree, so it did not move — A/Two is a real copy.
            keepIds = setOf("A/One", "A/Two"),
        )
        assertTrue(moved.isEmpty())
    }

    @Test
    fun `a moved book carries its durations across by file name`() {
        // The relative path changed with the folder, so matching on it would find nothing and the
        // whole book would be re-probed.
        val planned = planWrites(
            detected = listOf(detected("Author/New", file("Author/New", "01.mp3"))),
            existingById = mapOf("Author/Old" to book("Author/Old", genre = "Fantasy", localCoverPath = "/covers/x.img")),
            filesByBook = mapOf("Author/Old" to listOf(file("Author/Old", "01.mp3", durationMs = 5000))),
            movedFrom = mapOf("Author/New" to "Author/Old"),
        )
        assertEquals(5000L, planned.files.single().durationMs)
        assertEquals(5000L, planned.books.single().totalDurationMs)
        assertEquals("Fantasy", planned.books.single().genre)
        assertEquals("/covers/x.img", planned.books.single().localCoverPath)
    }

    // ── Covers and probe flags ────────────────────────────────────────────────

    @Test
    fun `a hand-picked cover survives a rescan`() {
        val planned = planWrites(
            detected = listOf(detected("A/One")),
            existingById = mapOf("A/One" to book("A/One", customCoverPath = "/covers/mine.img", coverAttempted = true)),
            filesByBook = emptyMap(),
            movedFrom = emptyMap(),
        )
        assertEquals("/covers/mine.img", planned.books.single().customCoverPath)
    }

    @Test
    fun `finding an uncached folder cover re-arms a book an earlier pass gave up on`() {
        val planned = planWrites(
            detected = listOf(detected("A/One", coverFilePath = "A/One/cover.jpg")),
            existingById = mapOf("A/One" to book("A/One", coverAttempted = true, localCoverPath = null)),
            filesByBook = emptyMap(),
            movedFrom = emptyMap(),
        )
        assertFalse("a visible but uncached cover is worth one cheap GET", planned.books.single().coverAttempted)
    }

    @Test
    fun `an already cached cover is not re-fetched`() {
        val planned = planWrites(
            detected = listOf(detected("A/One", coverFilePath = "A/One/cover.jpg")),
            existingById = mapOf("A/One" to book("A/One", coverAttempted = true, localCoverPath = "/covers/x.img")),
            filesByBook = emptyMap(),
            movedFrom = emptyMap(),
        )
        assertTrue(planned.books.single().coverAttempted)
    }

    @Test
    fun `a book new to the index carries nothing and is left fully re-armed`() {
        val planned = planWrites(
            detected = listOf(detected("A/New", file("A/New", "01.mp3"))),
            existingById = emptyMap(),
            filesByBook = emptyMap(),
            movedFrom = emptyMap(),
        )
        val result = planned.books.single()
        assertFalse(result.coverAttempted)
        assertFalse(result.metadataAttempted)
        assertNull(result.genre)
        assertNull(result.totalDurationMs)
        assertFalse(planned.files.single().durationAttempted)
    }
}
