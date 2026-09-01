package com.geozelot.homer.data.sync

import com.geozelot.homer.data.db.entity.BookOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `.homer` manifest carries the READER's half of an override and nothing else.
 *
 * These exist because the version before this built a whole [BookOverrideEntity] out of a format
 * that could not express three of its fields. `collection`, `collectionIndex` and `language` had no
 * representation in [HomerOverride], so every time the manifest's copy won the last-write-wins
 * compare they were written as null — silently, by a sync whose visible job is carrying a reading
 * position. A device on a read-only share with a personal sync account had no other channel, so for
 * it every collection edit was lost on the next sync, for ever.
 *
 * Nothing about the old code looked wrong. It was a field list that disagreed with another field
 * list, which is the recurring shape of bugs in this area, and it is why the test below asserts on
 * PRESERVATION rather than on the happy path.
 */
class HomerOverrideTest {

    private fun local(
        title: String? = null,
        collection: String? = null,
        collectionIndex: Int? = null,
        language: String? = null,
        tags: String? = null,
        finished: Boolean? = null,
        downloadOnPlay: Boolean? = null,
        hidden: Boolean = false,
        updatedAt: Long = 100,
    ) = BookOverrideEntity(
        bookId = "Pratchett/Discworld/Wyrd Sisters",
        title = title,
        author = null,
        series = null,
        seriesIndex = null,
        collection = collection,
        collectionIndex = collectionIndex,
        language = language,
        tags = tags,
        finished = finished,
        downloadOnPlay = downloadOnPlay,
        hidden = hidden,
        updatedAt = updatedAt,
    )

    // ── what the manifest publishes ──────────────────────────────────────────────────────────

    @Test
    fun `it publishes only the reader's own state`() {
        val wire = local(
            title = "Wyrd Sisters",
            collection = "Discworld",
            collectionIndex = 6,
            language = "de",
            tags = "Klassiker",
            hidden = true,
            downloadOnPlay = true,
            updatedAt = 42,
        ).toHomer()
        assertEquals(HomerOverride(finished = null, downloadOnPlay = true, hidden = true, updatedAt = 42), wire)
    }

    // ── what it does NOT overwrite on the way back in ────────────────────────────────────────

    @Test
    fun `an incoming override keeps the local collection`() {
        val merged = HomerOverride(hidden = true, updatedAt = 200)
            .mergeInto("b", local(collection = "Discworld", collectionIndex = 6, updatedAt = 100))
        assertEquals("Discworld", merged.collection)
        assertEquals(6, merged.collectionIndex)
    }

    @Test
    fun `an incoming override keeps the local language and title and tags`() {
        val merged = HomerOverride(updatedAt = 200)
            .mergeInto("b", local(title = "Wyrd Sisters", language = "de", tags = "Klassiker"))
        assertEquals("Wyrd Sisters", merged.title)
        assertEquals("de", merged.language)
        assertEquals("Klassiker", merged.tags)
    }

    @Test
    fun `the reader's own fields DO come from the manifest`() {
        val merged = HomerOverride(finished = true, downloadOnPlay = false, hidden = true, updatedAt = 200)
            .mergeInto("b", local(finished = null, downloadOnPlay = true, hidden = false))
        assertTrue(merged.finished == true)
        assertFalse(merged.downloadOnPlay == true)
        assertTrue(merged.hidden)
        assertEquals(200, merged.updatedAt)
    }

    @Test
    fun `un-hiding still propagates`() {
        // `hidden = false` with a newer stamp is a claim, not an absence — the whole reason the
        // reader's half is still reconciled last-write-wins.
        val merged = HomerOverride(hidden = false, updatedAt = 200).mergeInto("b", local(hidden = true))
        assertFalse(merged.hidden)
    }

    @Test
    fun `a book this device has never seen an override for gets one`() {
        val merged = HomerOverride(hidden = true, updatedAt = 200).mergeInto("b", null)
        assertEquals("b", merged.bookId)
        assertTrue(merged.hidden)
        assertEquals(200, merged.updatedAt)
        // Nothing bibliographic is invented for it.
        assertNull(merged.title)
        assertNull(merged.collection)
        assertNull(merged.language)
    }

    // ── the round trip cannot lose anything ──────────────────────────────────────────────────

    @Test
    fun `publishing and consuming the same row changes nothing about it`() {
        val row = local(
            title = "Wyrd Sisters",
            collection = "Discworld",
            collectionIndex = 6,
            language = "de",
            tags = "Klassiker\nGelesen",
            hidden = true,
            downloadOnPlay = true,
            updatedAt = 42,
        )
        assertEquals(row, row.toHomer().mergeInto(row.bookId, row))
    }
}
