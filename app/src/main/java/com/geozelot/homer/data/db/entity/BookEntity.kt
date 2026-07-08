package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A detected audiobook. For v1 the identity is the book's folder path relative to
 * the library root (see SCOPE.md — content-hash identity is a later enhancement).
 *
 * [chapterTier] follows the three-tier model: 1 = embedded chapters, 2 = sidecar,
 * 3 = none/manual. Multi-file books derive their chapters from the ordered file list
 * regardless of tier.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val series: String?,
    val seriesIndex: Int?,
    val relativePath: String,
    /** Relative path of a cover image file in the book folder (remote), if any. */
    val coverFilePath: String?,
    /** Absolute path of a locally cached cover (e.g. extracted embedded art), if any. */
    val localCoverPath: String?,
    val chapterTier: Int,
    val isMultiFile: Boolean,
    val fileCount: Int,
    val totalDurationMs: Long?,
    val addedAt: Long,
    val updatedAt: Long,
)
