package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-book "bookmarks last changed" timestamp, used for last-write-wins bookmark sync.
 * Kept in its own table (not on [BookmarkEntity] or [PlaybackStateEntity]) so neither
 * position writes nor a library rescan can clobber it.
 */
@Entity(tableName = "bookmark_meta")
data class BookmarkMetaEntity(
    @PrimaryKey val bookId: String,
    val updatedAt: Long,
)
