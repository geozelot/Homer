package com.geozelot.homer.data.library

import com.geozelot.homer.data.webdav.DavResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Guards [BookDetector.buildBooks]'s content fingerprint.
 *
 * `contentHash` is what lets a scan recognise a book that moved: the stored value is compared
 * against a freshly computed one, so ANY change to how the canonical string is built silently
 * breaks move-relinking for every book already in the database. Nothing about that failure is
 * loud — books simply stop reattaching to their progress and hand-typed overrides — so the exact
 * hash is pinned here rather than left to be re-derived by whoever edits the function next.
 */
class BookDetectorTest {

    private val detector = BookDetector()

    private fun audio(path: String, size: Long) = DavResource(
        path = path,
        isCollection = false,
        contentLength = size,
        lastModifiedMs = 1L,
        contentType = "audio/mpeg",
        etag = "etag",
    )

    private fun hashOf(files: List<DavResource>): String? = detector.buildBooks(
        folders = listOf(BookDetector.AudioFolder("lib/Author/Book", files, emptyList())),
        folderImages = emptyMap(),
        libraryRoot = "lib",
        now = 0L,
    ).single().book.contentHash

    /**
     * Pinned value. If this fails, the canonical string changed — which is a migration, not a
     * refactor. Recomputing the constant to make it pass would ship the breakage.
     */
    @Test
    fun contentHashIsStable() {
        val hash = hashOf(listOf(audio("lib/Author/Book/01.mp3", 1000), audio("lib/Author/Book/02.mp3", 2000)))
        assertEquals("0a3d5d038b8adb12c818d8117e67b38340e82a56", hash)
    }

    /** Neither the folder the book sits in nor the order the files arrive in may affect it. */
    @Test
    fun contentHashIgnoresPathAndFileOrder() {
        val here = hashOf(listOf(audio("lib/Author/Book/01.mp3", 1000), audio("lib/Author/Book/02.mp3", 2000)))
        val moved = detector.buildBooks(
            folders = listOf(
                BookDetector.AudioFolder(
                    "lib/Someone Else/Renamed",
                    // Reversed: sorting inside the hash has to make this irrelevant.
                    listOf(audio("lib/Someone Else/Renamed/02.mp3", 2000), audio("lib/Someone Else/Renamed/01.mp3", 1000)),
                    emptyList(),
                ),
            ),
            folderImages = emptyMap(),
            libraryRoot = "lib",
            now = 0L,
        ).single().book.contentHash
        assertEquals("a moved or reordered book must fingerprint the same", here, moved)
    }

    /**
     * The name/size separator has to be a byte a filename cannot contain, or a crafted name can
     * forge the boundary between fields. With a space (or any printable character) these two very
     * different books collide; with NUL they can't. This is why the separator is what it is.
     */
    @Test
    fun contentHashCannotBeForgedByAFileNameThatLooksLikeTwoEntries() {
        val genuine = hashOf(listOf(audio("lib/Author/Book/a", 1), audio("lib/Author/Book/b", 2)))
        val forged = hashOf(listOf(audio("lib/Author/Book/a 1\nb", 2)))
        assertNotEquals(genuine, forged)
    }

    @Test
    fun contentHashIsNullWithoutFiles() {
        assertEquals(null, hashOf(emptyList()))
    }
}
