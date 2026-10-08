package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.BookOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [stampedAgainst]: a write moves the clock of the half it changed, and only that one.
 *
 * Every local write to an override goes through it. A clock moved for no reason is a decision
 * invented out of nothing, and each sync channel believes the newest decision — which is how hiding
 * a book used to outrank a correction somebody else had made to it.
 */
class OverrideStampTest {

    private fun row(
        title: String? = null,
        hidden: Boolean = false,
        downloadOnPlay: Boolean? = null,
        updatedAt: Long = 0,
        correctedAt: Long = 0,
    ) = BookOverrideEntity(
        bookId = "b",
        title = title,
        author = null,
        series = null,
        seriesIndex = null,
        downloadOnPlay = downloadOnPlay,
        hidden = hidden,
        updatedAt = updatedAt,
        correctedAt = correctedAt,
    )

    @Test
    fun `hiding moves only the reader's clock`() {
        val existing = row(title = "T", updatedAt = 100, correctedAt = 200)
        val stored = existing.copy(hidden = true).stampedAgainst(existing, now = 999)
        assertEquals(999L, stored.updatedAt)
        assertEquals(200L, stored.correctedAt)
    }

    @Test
    fun `a correction moves only the correction's clock`() {
        val existing = row(hidden = true, updatedAt = 100, correctedAt = 200)
        val stored = existing.copy(title = "Fixed").stampedAgainst(existing, now = 999)
        assertEquals(100L, stored.updatedAt)
        assertEquals(999L, stored.correctedAt)
    }

    @Test
    fun `saving what is already there moves neither`() {
        // The edit dialog saves every field every time; pressing Save on an untouched book is not
        // a decision about anything.
        val existing = row(title = "T", hidden = true, updatedAt = 100, correctedAt = 200)
        val stored = existing.copy(updatedAt = 0, correctedAt = 0).stampedAgainst(existing, now = 999)
        assertEquals(existing, stored)
    }

    @Test
    fun `clearing a correction is a correction`() {
        // The cleared row has to outrank the correction it cleared, or the next pull brings it back.
        val existing = row(title = "Wrong", correctedAt = 200)
        val stored = existing.copy(title = null).stampedAgainst(existing, now = 999)
        assertEquals(999L, stored.correctedAt)
    }

    @Test
    fun `a new row earns only the clock of what it says`() {
        val hidden = row(hidden = true).stampedAgainst(null, now = 999)
        assertEquals(999L, hidden.updatedAt)
        assertEquals(0L, hidden.correctedAt)

        val corrected = row(title = "T").stampedAgainst(null, now = 999)
        assertEquals(0L, corrected.updatedAt)
        assertEquals(999L, corrected.correctedAt)
    }

    @Test
    fun `an empty new row earns nothing`() {
        val stored = row().stampedAgainst(null, now = 999)
        assertEquals(0L, stored.updatedAt)
        assertEquals(0L, stored.correctedAt)
    }

    @Test
    fun `un-setting a play mode is a reader decision`() {
        val existing = row(downloadOnPlay = true, updatedAt = 100)
        val stored = existing.copy(downloadOnPlay = null).stampedAgainst(existing, now = 999)
        assertEquals(999L, stored.updatedAt)
    }
}
