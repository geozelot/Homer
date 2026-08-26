package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User corrections that take precedence over folder-tree detection (SCOPE §7, D2). Kept in
 * its own table keyed by book id so it survives rescans (detection is re-derived, overrides
 * re-applied on top). A null field means "no override — use the detected value".
 *
 * Path-keyed for v1: if a book folder moves its override orphans (content-hash identity,
 * which would make overrides move-safe, is deferred).
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
    val updatedAt: Long,
)
