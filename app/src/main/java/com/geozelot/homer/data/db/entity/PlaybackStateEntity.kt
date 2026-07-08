package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Last known playback position for a book, so it resumes where the user left off.
 * Keyed by book. [currentMediaId] is the file's relative path (the [MediaItem] media
 * id) rather than a bare index, so it survives the file list being re-ordered.
 *
 * This is the local (Room) layer; the cross-device `.homer` sync layer builds on top
 * of it later.
 */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val bookId: String,
    val currentMediaId: String,
    val positionMs: Long,
    val updatedAt: Long,
)
