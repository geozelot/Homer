package com.geozelot.homer.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a book is allowed to say how long it is.
 *
 * Two failures sit on either side of this rule and it has to miss both. Report too early and a
 * partial sum is smaller than the elapsed time played against it, so the book reads as finished and
 * leaves the Currently-listening shelf. Report too late — wait for a file that can never be
 * measured — and one damaged chapter leaves a whole book with no length for ever.
 */
class BookTotalDurationTest {

    private fun file(name: String, ms: Long?, attempted: Boolean = false) = AudioFileEntity(
        relativePath = "book/$name",
        bookId = "book",
        fileName = name,
        sortIndex = 0,
        sizeBytes = 1,
        etag = null,
        lastModified = null,
        contentType = null,
        durationMs = ms,
        durationAttempted = attempted,
    )

    @Test
    fun `a fully measured book totals its files`() {
        assertEquals(300L, bookTotalDurationMs(listOf(file("1", 100), file("2", 200))))
    }

    @Test
    fun `a book still being measured says nothing`() {
        assertNull(bookTotalDurationMs(listOf(file("1", 100), file("2", null))))
    }

    @Test
    fun `a file proven unreadable is not waited for`() {
        // The case from the device log: 21 of 22 measured, one container no extractor can read.
        val files = listOf(file("1", 100), file("2", null, attempted = true), file("3", 200))
        assertEquals(300L, bookTotalDurationMs(files))
    }

    @Test
    fun `a dropped connection is not a write-off`() {
        // `durationAttempted` is only set on an answer the probe could trust, so an un-attempted
        // null still holds the total back — the book measures properly once the network returns.
        val files = listOf(file("1", 100), file("2", null), file("3", null, attempted = true))
        assertNull(bookTotalDurationMs(files))
    }

    @Test
    fun `a book whose every file is unreadable has no length, not a length of zero`() {
        val files = listOf(file("1", null, attempted = true), file("2", null, attempted = true))
        assertNull(bookTotalDurationMs(files))
    }

    @Test
    fun `a book with no files at all has no length`() {
        assertNull(bookTotalDurationMs(emptyList()))
    }
}
