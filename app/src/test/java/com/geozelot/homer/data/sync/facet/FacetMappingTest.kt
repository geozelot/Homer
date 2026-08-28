package com.geozelot.homer.data.sync.facet

import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookmarkKind
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.db.entity.ChapterEntity
import com.geozelot.homer.data.db.entity.ChapterTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Database to facets and back.
 *
 * Most of these pin something that must NOT travel: a reader's private flags, another device's
 * bookkeeping, or a correction dressed up as a detection.
 */
class FacetMappingTest {

    private fun book(
        id: String = "Author/Book",
        title: String = "Book",
        genre: String? = null,
        contentHash: String? = "hash",
        localCoverPath: String? = null,
        chapterTier: Int = ChapterTier.UNDETERMINED,
        totalDurationMs: Long? = null,
        updatedAt: Long = 100,
    ) = BookEntity(
        id = id,
        contentHash = contentHash,
        title = title,
        author = "Author",
        series = null,
        seriesIndex = null,
        genre = genre,
        relativePath = id,
        coverFilePath = "Author/Book/cover.jpg",
        localCoverPath = localCoverPath,
        customCoverPath = null,
        coverAttempted = false,
        metadataAttempted = false,
        chapterTier = chapterTier,
        isMultiFile = true,
        fileCount = 2,
        totalDurationMs = totalDurationMs,
        addedAt = 1,
        updatedAt = updatedAt,
    )

    private fun file(path: String, index: Int, duration: Long? = null, attempted: Boolean = false) =
        AudioFileEntity(
            relativePath = path,
            bookId = "Author/Book",
            fileName = path.substringAfterLast('/'),
            sortIndex = index,
            sizeBytes = 1024,
            etag = "e-$path",
            lastModified = 999,
            contentType = "audio/mpeg",
            durationMs = duration,
            durationAttempted = attempted,
        )

    private fun override(
        title: String? = null,
        genre: String? = null,
        finished: Boolean? = null,
        hidden: Boolean = false,
        downloadOnPlay: Boolean? = null,
        updatedAt: Long = 500,
    ) = BookOverrideEntity(
        bookId = "Author/Book",
        title = title,
        author = null,
        series = null,
        seriesIndex = null,
        genre = genre,
        tags = null,
        finished = finished,
        downloadOnPlay = downloadOnPlay,
        hidden = hidden,
        updatedAt = updatedAt,
    )

    // ── publishing ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `structure carries what the crawl saw, in sort order`() {
        val s = FacetMapping.structureOf(
            book(),
            listOf(file("Author/Book/b.mp3", 1), file("Author/Book/a.mp3", 0)),
        )
        assertEquals("Book", s.title)
        assertEquals("hash", s.contentHash)
        assertEquals(listOf("a.mp3", "b.mp3"), s.files.map { it.path })
        assertEquals(listOf("e-Author/Book/a.mp3", "e-Author/Book/b.mp3"), s.files.map { it.etag })
        assertEquals(100L, s.updatedAt)
    }

    @Test
    fun `a file path is stored relative to its book`() {
        // The single largest thing in the index was every file repeating its book's folder.
        val s = FacetMapping.structureOf(book(), listOf(file("Author/Book/CD1/01.mp3", 0)))
        assertEquals("CD1/01.mp3", s.files.single().path)
    }

    @Test
    fun `the path round-trips exactly`() {
        val original = "Author/Book/CD1/01.mp3"
        val s = FacetMapping.structureOf(book(), listOf(file(original, 0)))
        val back = FacetMapping.fileEntities("Author/Book", s, null, emptyList())
        assertEquals(original, back.single().relativePath)
    }

    @Test
    fun `a file outside its own book keeps a whole path rather than a corrupted one`() {
        // Nothing produces this; the marker exists so the encoding cannot silently invent a path.
        val stored = FacetMapping.bookRelative("Author/Book", "Elsewhere/x.mp3")
        assertEquals("/Elsewhere/x.mp3", stored)
        assertEquals("Elsewhere/x.mp3", FacetMapping.libraryRelative("Author/Book", stored))
    }

    @Test
    fun `a book at the library root round-trips too`() {
        assertEquals("01.mp3", FacetMapping.bookRelative("", "01.mp3"))
        assertEquals("01.mp3", FacetMapping.libraryRelative("", "01.mp3"))
    }

    @Test
    fun `structure carries neither durations nor fields nothing reads`() {
        // Durations are computed, not crawled, and would merge by the wrong rule. contentType and
        // lastModified are written by the scanner and read by no one, at 12k entries apiece.
        val fields = StructureFile::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("duration", true) })
        assertFalse(fields.any { it.contains("contentType", true) })
        assertFalse(fields.any { it.contains("lastModified", true) })
        assertFalse(fields.any { it.contains("fileName", true) })
        assertFalse(fields.any { it.contains("sortIndex", true) })
    }

    @Test
    fun `derived carries what reading the files taught us`() {
        val d = FacetMapping.derivedOf(
            book(genre = "Fantasy", localCoverPath = "/covers/x", chapterTier = ChapterTier.EMBEDDED, totalDurationMs = 9_000),
            listOf(file("Author/Book/a.mp3", 0, 4_000), file("Author/Book/b.mp3", 1, 5_000)),
            listOf(ChapterEntity(bookId = "Author/Book", sortIndex = 0, title = "One", startMs = 0)),
        )
        assertNotNull(d)
        assertEquals("Fantasy", d!!.genre)
        assertEquals(9_000L, d.totalDurationMs)
        assertTrue(d.hasCachedCover)
        assertEquals("EMBEDDED", d.chapterTier)
        assertEquals(listOf(DerivedChapter(0, "One")), d.chapters)
        assertEquals(mapOf("a.mp3" to 4_000L, "b.mp3" to 5_000L), d.fileDurationsMs)
    }

    @Test
    fun `a book that taught us nothing produces no derived entry`() {
        // Silence, not a claim that there is nothing to find.
        assertNull(FacetMapping.derivedOf(book(), listOf(file("Author/Book/a.mp3", 0)), emptyList()))
    }

    @Test
    fun `an established absence of chapters is worth publishing`() {
        val d = FacetMapping.derivedOf(book(chapterTier = ChapterTier.NONE), emptyList(), emptyList())
        assertEquals("NONE", d?.chapterTier)
    }

    @Test
    fun `a correction publishes only claims about the book`() {
        val c = FacetMapping.correctionOf(
            override(title = "Proper Title", finished = true, hidden = true, downloadOnPlay = false),
            cuts = emptyList(),
            deviceId = "phone",
        )
        assertNotNull(c)
        assertEquals("Proper Title", c!!.title)
        assertEquals("phone", c.editedBy)
        assertEquals(500L, c.editedAt)
        // The reader's business stays on this device: a shared folder would otherwise tell
        // everyone else what its owner has finished or hidden.
        val fields = BookCorrection::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it in setOf("finished", "hidden", "downloadOnPlay") })
    }

    @Test
    fun `a purely personal override publishes nothing at all`() {
        assertNull(FacetMapping.correctionOf(override(finished = true, hidden = true), emptyList(), "phone"))
    }

    @Test
    fun `chapter cuts alone are worth publishing`() {
        // A single-file book can carry a hand-made chapter list and no corrected field anywhere —
        // and that chapter list is the thing other readers most want out of the index.
        val c = FacetMapping.correctionOf(
            override = null,
            cuts = listOf(cut(positionMs = 900L, label = "Two"), cut(positionMs = 0L, label = "One")),
            deviceId = "phone",
        )
        assertNotNull(c)
        assertNull("nothing was corrected, only cut", c!!.title)
        assertEquals(listOf(900L, 0L), c.chapters.map { it.startMs })
    }

    @Test
    fun `a cut's own timestamp can be the reason to republish`() {
        // `editedAt` drives the corrections merge, so a cut made after the last field edit has to
        // move it forward or the newer chapter list loses to the older correction.
        val c = FacetMapping.correctionOf(override(title = "T"), listOf(cut(0L, "One", createdAt = 9_000L)), "phone")
        assertEquals(9_000L, c!!.editedAt)
    }

    @Test
    fun `a person's cuts outrank the chapters a tag supplied`() {
        val fromTag = DerivedBook(chapterTier = "EMBEDDED", chapters = listOf(DerivedChapter(0L, "Tag")))
        val chapters = FacetMapping.chapterEntities(
            bookId = "A/One",
            derived = fromTag,
            correction = BookCorrection(chapters = listOf(DerivedChapter(0L, "Mine"), DerivedChapter(60L, "Also"))),
        )
        assertEquals(listOf("Mine", "Also"), chapters.map { it.title })
    }

    @Test
    fun `cuts are stored in playing order however they were made`() {
        // A cut made later can belong earlier in the book, and sortIndex is read as playing order.
        val chapters = FacetMapping.chapterEntities(
            bookId = "A/One",
            derived = null,
            correction = BookCorrection(
                chapters = listOf(DerivedChapter(120L, "Third"), DerivedChapter(0L, "First"), DerivedChapter(60L, "Second")),
            ),
        )
        assertEquals(listOf("First", "Second", "Third"), chapters.map { it.title })
        assertEquals(listOf(0, 1, 2), chapters.map { it.sortIndex })
    }

    @Test
    fun `no cuts falls back to what the tag said`() {
        val fromTag = DerivedBook(chapterTier = "EMBEDDED", chapters = listOf(DerivedChapter(0L, "Tag")))
        val chapters = FacetMapping.chapterEntities("A/One", fromTag, BookCorrection(title = "T"))
        assertEquals(listOf("Tag"), chapters.map { it.title })
    }

    private fun cut(positionMs: Long, label: String?, createdAt: Long = 500L) = BookmarkEntity(
        bookId = "A/One",
        mediaId = "A/One/01.mp3",
        chapterTitle = "",
        positionMs = positionMs,
        label = label,
        createdAt = createdAt,
        kind = BookmarkKind.CUT,
    )

    // ── consuming ────────────────────────────────────────────────────────────────────────────

    private val structure = StructureBook(
        title = "From Facet",
        author = "Author",
        contentHash = "facet-hash",
        coverFilePath = "Author/Book/cover.jpg",
        isMultiFile = true,
        files = listOf(
            StructureFile("a.mp3", 1024, "e-a"),
            StructureFile("b.mp3", 1024, "e-b"),
        ),
        updatedAt = 700,
    )

    @Test
    fun `a book row is rebuilt from the facets`() {
        val e = FacetMapping.bookEntity("Author/Book", structure, DerivedBook(genre = "Fantasy", totalDurationMs = 9_000), null, now = 42)
        assertEquals("From Facet", e.title)
        assertEquals("Fantasy", e.genre)
        assertEquals(9_000L, e.totalDurationMs)
        assertEquals(2, e.fileCount)
        assertEquals(42L, e.addedAt)
        assertEquals(700L, e.updatedAt)
    }

    @Test
    fun `local bookkeeping survives a consume`() {
        // Another device's view must not re-run work this one has already done, nor claim the
        // book has been here since somebody else first saw it.
        val existing = book(localCoverPath = "/covers/mine").copy(
            customCoverPath = "/covers/custom",
            coverAttempted = true,
            metadataAttempted = true,
            addedAt = 5,
        )
        val e = FacetMapping.bookEntity("Author/Book", structure, null, existing, now = 999)
        assertEquals("/covers/mine", e.localCoverPath)
        assertEquals("/covers/custom", e.customCoverPath)
        assertTrue(e.coverAttempted)
        assertTrue(e.metadataAttempted)
        assertEquals(5L, e.addedAt)
    }

    @Test
    fun `a content hash is never nulled out by a facet that lacks one`() {
        val e = FacetMapping.bookEntity(
            "Author/Book",
            structure.copy(contentHash = null),
            null,
            book(contentHash = "local-hash"),
            now = 1,
        )
        assertEquals("local-hash", e.contentHash)
    }

    @Test
    fun `a local genre survives a facet that has none`() {
        val e = FacetMapping.bookEntity("Author/Book", structure, DerivedBook(genre = null), book(genre = "Fantasy"), now = 1)
        assertEquals("Fantasy", e.genre)
    }

    @Test
    fun `a local total duration survives a facet that has none`() {
        val e = FacetMapping.bookEntity(
            "Author/Book", structure, DerivedBook(totalDurationMs = null), book(totalDurationMs = 8_000), now = 1,
        )
        assertEquals(8_000L, e.totalDurationMs)
    }

    @Test
    fun `the facet wins where it has a value`() {
        val e = FacetMapping.bookEntity(
            "Author/Book",
            structure,
            DerivedBook(genre = "Fantasy", totalDurationMs = 9_000),
            book(genre = "Other", totalDurationMs = 1, contentHash = "local-hash"),
            now = 1,
        )
        assertEquals("Fantasy", e.genre)
        assertEquals(9_000L, e.totalDurationMs)
        assertEquals("facet-hash", e.contentHash)
    }

    @Test
    fun `a local measurement survives a facet that lacks it`() {
        val files = FacetMapping.fileEntities(
            "Author/Book",
            structure,
            DerivedBook(fileDurationsMs = mapOf("a.mp3" to 4_000)),
            listOf(file("Author/Book/a.mp3", 0), file("Author/Book/b.mp3", 1, duration = 5_000, attempted = true)),
        )
        assertEquals(4_000L, files.first { it.relativePath == "Author/Book/a.mp3" }.durationMs)
        assertEquals(5_000L, files.first { it.relativePath == "Author/Book/b.mp3" }.durationMs)
        // "I tried and got nothing" is about this device's attempt, not about the file.
        assertTrue(files.first { it.relativePath == "Author/Book/b.mp3" }.durationAttempted)
    }

    @Test
    fun `a file that left the library is not carried over`() {
        val files = FacetMapping.fileEntities("Author/Book", structure, null, listOf(file("Author/Book/gone.mp3", 9, 1)))
        assertEquals(listOf("Author/Book/a.mp3", "Author/Book/b.mp3"), files.map { it.relativePath })
    }

    @Test
    fun `an incoming correction never touches the reader's own flags`() {
        val e = FacetMapping.overrideEntity(
            "Author/Book",
            BookCorrection(title = "Shared Title", editedAt = 900),
            override(title = "Mine", finished = true, hidden = true, downloadOnPlay = false),
        )
        assertNotNull(e)
        assertEquals("Shared Title", e!!.title)
        assertEquals(true, e.finished)
        assertTrue(e.hidden)
        assertEquals(false, e.downloadOnPlay)
        assertEquals(900L, e.updatedAt)
    }

    @Test
    fun `an absent correction leaves a local edit completely alone`() {
        // The data-loss bug: absence was read as "clear it", so a correction made offline was
        // destroyed by the next pull, before it had ever been published.
        val mine = override(title = "Mine", finished = true)
        assertEquals(mine, FacetMapping.overrideEntity("Author/Book", null, mine))

        val unpublished = override(title = "Fixed offline")
        assertEquals(unpublished, FacetMapping.overrideEntity("Author/Book", null, unpublished))
    }

    @Test
    fun `an absent correction on a book with no override stays absent`() {
        assertNull(FacetMapping.overrideEntity("Author/Book", null, null))
    }

    @Test
    fun `clearing one field among several still propagates`() {
        // The entry survives with the remaining fields, so the merge carries the clear.
        val e = FacetMapping.overrideEntity(
            "Author/Book",
            BookCorrection(genre = "Fantasy", editedAt = 900),
            override(title = "Wrong", genre = "Fantasy"),
        )
        assertNull(e!!.title)
        assertEquals("Fantasy", e.genre)
    }

    @Test
    fun `chapters are renumbered from the facet order`() {
        val chapters = FacetMapping.chapterEntities(
            "Author/Book",
            DerivedBook(chapters = listOf(DerivedChapter(0, "One"), DerivedChapter(60_000, "Two"))),
        )
        assertEquals(listOf(0, 1), chapters.map { it.sortIndex })
        assertEquals(listOf("One", "Two"), chapters.map { it.title })
    }

    // ── the tier, as a word ──────────────────────────────────────────────────────────────────

    @Test
    fun `the chapter tier round-trips by name`() {
        for (tier in listOf(ChapterTier.EMBEDDED, ChapterTier.SIDECAR, ChapterTier.NONE)) {
            assertEquals(tier, FacetMapping.tierValue(FacetMapping.tierName(tier)))
        }
    }

    @Test
    fun `undetermined is an absence, not a value`() {
        // The merge lets any answer beat an absence; publishing "UNDETERMINED" would make one
        // device's ignorance outrank another's finding.
        assertNull(FacetMapping.tierName(ChapterTier.UNDETERMINED))
        assertNull(FacetMapping.tierValue(null))
    }

    @Test
    fun `an unknown tier name from a newer Homer is ignored, not guessed`() {
        assertNull(FacetMapping.tierValue("TRANSCRIBED"))
        val e = FacetMapping.bookEntity(
            "Author/Book",
            structure,
            DerivedBook(chapterTier = "TRANSCRIBED"),
            book(chapterTier = ChapterTier.EMBEDDED),
            now = 1,
        )
        assertEquals(ChapterTier.EMBEDDED, e.chapterTier)
    }

    // ── a locally newer row keeps its bibliographic fields ───────────────────────────────────
    //
    // This is the bug behind "the template preview is right and the library is wrong". Applying a
    // path template writes the local rows and moves their `updatedAt`; the pull then wrote the
    // remote facet straight over them, whatever the stamps said, so the apply was reverted by the
    // next sync from an index published before the template existed.

    @Test
    fun `a locally newer row keeps the fields a template can write`() {
        val local = book(updatedAt = 900).copy(
            title = "Local Title",
            author = "Local Author",
            series = "Die Hexen",
            seriesIndex = 2,
            collection = "Scheibenwelt",
            collectionIndex = 3,
            genre = "Local Genre",
            language = "de",
        )
        // structure.updatedAt is 700 — older than the local row's 900.
        val e = FacetMapping.bookEntity("Author/Book", structure, DerivedBook(genre = "Facet Genre"), local, now = 1_000)
        assertEquals("Local Title", e.title)
        assertEquals("Local Author", e.author)
        assertEquals("Die Hexen", e.series)
        assertEquals(2, e.seriesIndex)
        assertEquals("Scheibenwelt", e.collection)
        assertEquals(3, e.collectionIndex)
        assertEquals("Local Genre", e.genre)
        assertEquals("de", e.language)
    }

    @Test
    fun `the winning stamp is kept, or the next pull reverts what this one preserved`() {
        val local = book(updatedAt = 900).copy(collection = "Scheibenwelt")
        val e = FacetMapping.bookEntity("Author/Book", structure, null, local, now = 1_000)
        assertEquals(900, e.updatedAt)
    }

    @Test
    fun `a newer facet still wins, so a real server-side change arrives`() {
        val local = book(updatedAt = 500).copy(title = "Stale Local", collection = "Wrong")
        // structure.updatedAt 700 > local 500.
        val e = FacetMapping.bookEntity("Author/Book", structure, null, local, now = 1_000)
        assertEquals("From Facet", e.title)
        assertNull("the facet has no collection and it is the newer word", e.collection)
        assertEquals(700, e.updatedAt)
    }

    @Test
    fun `equal stamps go to the facet, so a republish of the same state is not a conflict`() {
        val local = book(updatedAt = 700).copy(title = "Local Title")
        val e = FacetMapping.bookEntity("Author/Book", structure, null, local, now = 1_000)
        assertEquals("From Facet", e.title)
    }

    @Test
    fun `local bookkeeping is still kept whichever side wins the fields`() {
        // The point of the fix is narrow: it must not start taking the facet's word for cover files
        // or the date the book appeared here.
        val local = book(updatedAt = 900, localCoverPath = "/data/cover.jpg").copy(collection = "Scheibenwelt")
        val e = FacetMapping.bookEntity("Author/Book", structure, null, local, now = 1_000)
        assertEquals("/data/cover.jpg", e.localCoverPath)
        assertEquals(local.addedAt, e.addedAt)
    }
}
