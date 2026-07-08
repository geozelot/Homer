package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-directory crawl bookkeeping. Nextcloud propagates a collection's ETag when any
 * descendant changes, so comparing a directory's stored [etag] against a fresh
 * PROPFIND lets an incremental rescan skip unchanged subtrees.
 */
@Entity(tableName = "crawl_dirs")
data class CrawlDirEntity(
    @PrimaryKey val path: String,
    val etag: String?,
    val lastScanned: Long,
)
