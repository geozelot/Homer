package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline-download state for a book. Standalone (no FK to books) so a rescan can't cascade
 * away a download record. [status] is one of [DownloadStatus]; progress is file-granular.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val bookId: String,
    val status: String,
    val downloadedFiles: Int,
    val totalFiles: Int,
    val updatedAt: Long,
)

object DownloadStatus {
    const val DOWNLOADING = "downloading"
    const val PAUSED = "paused"
    const val DONE = "done"
    const val FAILED = "failed"
}
