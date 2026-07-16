package com.geozelot.homer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An embedded chapter mark within a single-file book (ID3v2 CHAP, parsed on first open).
 * [startMs] is the offset into the file; navigation seeks to it. Multi-file books don't use
 * this table — their ordered file list already is their chapter list.
 *
 * Cascade-deleted with the book. Re-derived on the next open after a rescan clears it, cheap
 * because it rides the same headless probe that measures durations.
 */
@Entity(
    tableName = "chapters",
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
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val sortIndex: Int,
    val title: String?,
    val startMs: Long,
)

/** The three-tier chapter model (SCOPE §3). Multi-file books navigate by file regardless. */
object ChapterTier {
    const val UNDETERMINED = 0
    const val EMBEDDED = 1
    const val SIDECAR = 2
    const val NONE = 3
}
