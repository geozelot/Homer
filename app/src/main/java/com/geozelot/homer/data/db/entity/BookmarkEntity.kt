package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A place in a book the listener marked, and one of two quite different things.
 *
 * A **note** ([BookmarkKind.NOTE]) is personal: somewhere you want to come back to. It travels with
 * your progress, to your own devices, and nobody else ever sees it.
 *
 * A **cut** ([BookmarkKind.CUT]) is a claim about the book — "a chapter starts here" — for a
 * single-file book whose tags carry no chapters. It is library data, so it is published with the
 * metadata corrections and becomes everybody's chapter list, not just yours. Which is why the two
 * cannot be the same row type with a different colour: they go to different files and merge by
 * different rules.
 *
 * [chapterTitle] is denormalized so the list renders without a join; [label] is an optional note
 * (null = show the timestamp), and doubles as a cut's chapter title.
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
    /** [BookmarkKind]. A plain string, so an unknown kind from a newer build reads as a note. */
    val kind: String = BookmarkKind.NOTE,
)

/**
 * What a mark means. Strings rather than an enum ordinal: the value is published in
 * `corrections.json`, and a name survives a reordering that a number would not.
 */
object BookmarkKind {
    const val NOTE = "note"
    const val CUT = "cut"
}
