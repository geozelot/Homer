package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user bookmark within a book: a saved chapter + offset the listener can jump back to.
 * [chapterTitle] is denormalized so the list renders without a join; [label] is an
 * optional note (null = show the timestamp).
 */
@Entity(
    tableName = "bookmarks",
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
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val mediaId: String,
    val chapterTitle: String,
    val positionMs: Long,
    val label: String?,
    val createdAt: Long,
)
