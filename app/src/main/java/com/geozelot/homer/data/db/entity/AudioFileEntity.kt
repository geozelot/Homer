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
    /**
     * True once a duration probe was tried and yielded nothing (unsupported container, timeout).
     * Without it such a file is re-probed — headers plus a couple of seconds of audio over the
     * network — on every open of its book, forever. A full refresh clears it.
     */
    val durationAttempted: Boolean = false,
)

/**
 * The whole-book length these files support, or null while anything is still coming.
 *
 * ## Why it is not simply the sum
 *
 * A PARTIAL sum under-reports the book, so whole-book elapsed exceeds it and the book reads as
 * "finished" — which is what once silently emptied the Currently-listening shelf. So nothing is
 * reported until the measuring has stopped.
 *
 * ## Why "stopped" is not "every file measured"
 *
 * A file that has been probed and proven unreadable — an unsupported container, a damaged rip —
 * will never yield a number. Holding the book until it does means one bad chapter in twenty-two
 * leaves the whole book with no length and no time left, for ever, with nothing on screen saying
 * why. [AudioFileEntity.durationAttempted] is only set on an answer the probe could TRUST (the file
 * was on the device, or the device was still online), so a dropped connection still counts as
 * pending and the book measures properly later.
 *
 * The total then runs short by the written-off file's length: time-left is a little fast and the
 * book may read as finished slightly early. Bounded, self-correcting the moment the file is fixed,
 * and far better than a book that says nothing at all.
 *
 * ## One function, three callers
 *
 * `LibraryScanner.planWrites` and `DurationEnricher` both decide this, and `BookProgress`
 * .fullyMeasured asks the same question in SQL. The first two used to carry the rule as their own
 * expression and had to be changed in step — exactly the duplication that drifts.
 */
fun bookTotalDurationMs(files: List<AudioFileEntity>): Long? {
    if (files.isEmpty()) return null
    if (files.any { it.durationMs == null && !it.durationAttempted }) return null
    val measured = files.mapNotNull { it.durationMs }
    return measured.sum().takeIf { measured.isNotEmpty() }
}
