package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow

/** How far into a book the saved position sits, measured across all chapters. */
data class BookProgress(
    val bookId: String,
    val elapsedMs: Long,
    /** When the position was last saved — used to order the Currently-listening shelf by recency. */
    val updatedAt: Long,
    /** Raw offset within the saved chapter (independent of any measured durations). */
    val positionMs: Long,
    /** Index of the saved chapter within the book; 0 when it can't be resolved. */
    val chapterIndex: Int,
    val fileCount: Int,
    /** How many of [fileCount] files have a measured duration. */
    val measuredCount: Int,
    /**
     * How many files are still WAITING to be measured — no duration, and no attempt written off.
     *
     * The difference between this and `fileCount - measuredCount` is the whole point: a file that
     * has been probed and proven unreadable is neither measured nor pending. It is never going to
     * produce a number, and a book must not be held in "unmeasured" for ever because of it.
     */
    val pendingCount: Int,
) {
    /**
     * Real listening progress — past the very start of the first chapter. Deliberately derived
     * from the chapter index + raw offset, NOT from [elapsedMs]: elapsed depends on measured
     * durations and collapses to 0 whenever they're missing, which would hide a book the user is
     * hours into (and did — it emptied the Currently-listening shelf).
     */
    val started: Boolean get() = positionMs > 0L || chapterIndex > 0

    /**
     * True when nothing is left to measure, so a whole-book total, percentage and time-left are as
     * good as they are going to get.
     *
     * It used to read `measuredCount == fileCount`, and the reasoning behind that still holds for
     * the case it was written for: a book measured half way makes elapsed exceed the (also partial)
     * total, which reads as "finished" and hides it. But it held a book hostage to a file that
     * could never be measured at all — one unreadable chapter in twenty-two, and the book showed no
     * length and no time left for ever, with nothing on screen to say why.
     *
     * So the test is "is anything still coming", not "did everything arrive". A file only leaves
     * [pendingCount] once the probe gave an answer it could TRUST — the file was on the device, or
     * the device was still online — so a dropped connection still counts as pending and the book
     * measures properly later. See `DurationEnricher.markDurationAttempted`.
     *
     * **The honest cost:** a book carrying a written-off file reports a total short by that file's
     * length, so its time-left runs a little fast and it may read as finished slightly early. That
     * is a small, bounded error that corrects itself the moment the file is fixed — against a book
     * that says nothing at all, for ever.
     */
    val fullyMeasured: Boolean get() = fileCount > 0 && measuredCount > 0 && pendingCount == 0
}

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE bookId = :bookId")
    suspend fun findByBookId(bookId: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state")
    suspend fun getAll(): List<PlaybackStateEntity>

    /** Newest position write, for cheaply deciding whether anything needs pushing. */
    @Query("SELECT MAX(updatedAt) FROM playback_state")
    suspend fun maxUpdatedAt(): Long?

    /**
     * Per-book progress: the saved chapter's index and offset, the whole-book elapsed time
     * (durations of all chapters before the saved one plus the offset), and how much of the book
     * has actually been measured. Re-emits when positions or durations change.
     *
     * The saved chapter is resolved by matching `currentMediaId` against `audio_files.relativePath`
     * **scoped to the same book** — without that scope a path reassigned to another book (part-folder
     * merging does this) yields a foreign `sortIndex` and wildly wrong elapsed. The lookup is
     * COALESCEd to 0 so an unresolvable id degrades to "chapter 0" instead of poisoning the whole
     * row: `sortIndex < NULL` matches nothing, which silently made elapsed collapse to the raw
     * offset. Callers must judge "started" from [BookProgress.started] and only trust totals when
     * [BookProgress.fullyMeasured].
     */
    @Query(
        """
        SELECT ps.bookId AS bookId,
               ps.updatedAt AS updatedAt,
               ps.positionMs AS positionMs,
               COALESCE((
                 SELECT cur.sortIndex FROM audio_files cur
                 WHERE cur.relativePath = ps.currentMediaId AND cur.bookId = ps.bookId
               ), 0) AS chapterIndex,
               ps.positionMs + COALESCE((
                 SELECT SUM(af.durationMs) FROM audio_files af
                 WHERE af.bookId = ps.bookId
                   AND af.sortIndex < COALESCE((
                     SELECT cur.sortIndex FROM audio_files cur
                     WHERE cur.relativePath = ps.currentMediaId AND cur.bookId = ps.bookId
                   ), 0)
               ), 0) AS elapsedMs,
               (SELECT COUNT(*) FROM audio_files af WHERE af.bookId = ps.bookId) AS fileCount,
               (SELECT COUNT(*) FROM audio_files af
                WHERE af.bookId = ps.bookId AND af.durationMs IS NOT NULL) AS measuredCount,
               (SELECT COUNT(*) FROM audio_files af
                WHERE af.bookId = ps.bookId
                  AND af.durationMs IS NULL AND af.durationAttempted = 0) AS pendingCount
        FROM playback_state ps
        """,
    )
    fun observeProgress(): Flow<List<BookProgress>>

    /**
     * Re-points a book's saved position onto a new id after its folder moved/renamed.
     * `OR REPLACE` because bookId is the primary key: if the destination already has a row this
     * would otherwise throw and abort the scan half-way through its mutate-then-prune sequence.
     */
    @Query("UPDATE OR REPLACE playback_state SET bookId = :newId WHERE bookId = :oldId")
    suspend fun relink(oldId: String, newId: String)

    /**
     * Rewrites the saved chapter path — needed alongside [relink], because `currentMediaId` is
     * `‹bookId›/‹file›` and a stale prefix resolves to no row in [observeProgress], losing both the
     * chapter and the elapsed time the user was at.
     */
    @Query("UPDATE playback_state SET currentMediaId = :mediaId WHERE bookId = :bookId")
    suspend fun updateCurrentMediaId(bookId: String, mediaId: String)

    /**
     * Drops positions whose book is no longer indexed. There is deliberately no foreign key here
     * (a rescan must not cascade progress away), so a prune leaves rows that nothing can ever read
     * or clean up. Expressed as a subquery so there is no host-parameter limit.
     */
    @Query("DELETE FROM playback_state WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun deleteOrphans()

    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)
}
