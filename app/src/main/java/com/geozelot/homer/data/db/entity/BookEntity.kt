package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A detected audiobook. Its [id] (primary key) is the book's folder path relative to the
 * library root — load-bearing, since fetch URLs are `libraryRoot + id` and the `.homer`
 * catalog/manifest key by it.
 *
 * [contentHash] is a secondary, path-independent fingerprint (over the book's file
 * names + sizes) used to recognise the same book after its folder is moved or renamed, so
 * a scan can re-link the user's position/overrides/bookmarks onto the new [id] instead of
 * orphaning them. Null until the first scan that sees the book's files populates it.
 *
 * [chapterTier] follows the three-tier model: 1 = embedded chapters, 2 = sidecar,
 * 3 = none/manual. Multi-file books derive their chapters from the ordered file list
 * regardless of tier.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    /** Path-independent fingerprint (file names + sizes) for move/rename survival; null until scanned. */
    val contentHash: String? = null,
    val title: String,
    val author: String?,
    val series: String?,
    val seriesIndex: Int?,
    /** Detected genre from embedded tags (filled lazily on first open); user-overridable. */
    val genre: String? = null,
    val relativePath: String,
    /** Relative path of a cover image file in the book folder (remote), if any. */
    val coverFilePath: String?,
    /** Absolute path of a locally cached cover (e.g. extracted embedded art), if any. */
    val localCoverPath: String?,
    /** Absolute path of a user-chosen custom cover (device-local; wins over all detected art). */
    val customCoverPath: String? = null,
    /** True once cover extraction has been tried (so art-less books aren't re-probed forever). */
    val coverAttempted: Boolean = false,
    /**
     * True once the tag/chapter probe has been tried and came up empty (no genre, no chapter
     * marks). Without it a book whose tags simply carry no genre re-streams its first file on
     * every single open. A full refresh clears it.
     */
    val metadataAttempted: Boolean = false,
    val chapterTier: Int,
    val isMultiFile: Boolean,
    val fileCount: Int,
    val totalDurationMs: Long?,
    val addedAt: Long,
    val updatedAt: Long,
)
