package com.geozelot.homer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User corrections that take precedence over folder-tree detection (SCOPE §7, D2). Kept in
 * its own table keyed by book id so it survives rescans (detection is re-derived, overrides
 * re-applied on top). A null field means "no override — use the detected value".
 *
 * Path-keyed for v1: if a book folder moves its override orphans (content-hash identity,
 * which would make overrides move-safe, is deferred).
 *
 * ## Two halves, two clocks
 *
 * The row holds two unrelated things that travel by two different channels: the CORRECTION — what
 * the book is called, who wrote it, where it shelves — published to the library's
 * `corrections.json`, and the READER's own state — hidden, finished, play mode — carried by the
 * personal `.homer` manifest. Each channel resolves conflicts last-write-wins, so each needs its own
 * timestamp, and until schema 5 they shared one.
 *
 * That made every write to either half a claim about both. Hiding a book stamped the row, and the
 * stamp then outranked any correction that arrived later but had been made earlier — so a remote fix
 * to a book somebody had once hidden never applied, for ever. And pulling a correction moved the
 * stamp the manifest compares, so a hide made on another device could lose to a correction it had
 * nothing to do with. See [updatedAt] and [correctedAt].
 */
@Entity(tableName = "book_overrides")
data class BookOverrideEntity(
    @PrimaryKey val bookId: String,
    val title: String?,
    val author: String?,
    val series: String?,
    val seriesIndex: Int?,
    /** Corrected parent grouping; null means "no correction", not "no collection". */
    val collection: String? = null,
    /** Corrected position within the collection. */
    val collectionIndex: Int? = null,
    /** Genre override (null = use the detected genre). */
    val genre: String? = null,
    /** Language override as an ISO 639-1 code (null = use the detected language). */
    val language: String? = null,
    /** User tags, newline-delimited (null = none). */
    val tags: String? = null,
    /** Tri-state finished flag: null = auto (derive from position), true/false = forced. */
    val finished: Boolean? = null,
    /** Per-book playback mode: null = follow the global setting, true = download on play, false = stream. */
    val downloadOnPlay: Boolean? = null,
    val hidden: Boolean,
    /**
     * When the READER's half last changed — `finished`, `downloadOnPlay`, `hidden` — and what the
     * personal manifest reconciles on. Zero for a row that has never said anything about the reader.
     *
     * The name predates the split, when it stamped the whole row. It was kept rather than renamed
     * because renaming a column below SQLite 3.25 (Android 11) means rebuilding the table.
     */
    val updatedAt: Long,
    /**
     * When the CORRECTION half last changed, including being cleared — and what an arriving shared
     * correction is compared against. Zero for a row that has never carried one.
     */
    @ColumnInfo(defaultValue = "0")
    val correctedAt: Long = 0,
)
