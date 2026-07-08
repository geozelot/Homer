package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single audio file belonging to a [BookEntity]. For multi-file books each file is
 * effectively one chapter; [sortIndex] fixes playback order (derived from natural
 * filename sorting). [etag] and [lastModified] drive incremental rescans.
 */
@Entity(
    tableName = "audio_files",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class AudioFileEntity(
    @PrimaryKey val relativePath: String,
    val bookId: String,
    val fileName: String,
    val sortIndex: Int,
    val sizeBytes: Long,
    val etag: String?,
    val lastModified: Long?,
    val contentType: String?,
    val durationMs: Long?,
)
