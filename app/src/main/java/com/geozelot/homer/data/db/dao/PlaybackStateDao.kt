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
    /** When the position was last saved — used to order the Continue shelf by recency. */
    val updatedAt: Long,
    /** Raw offset within the saved chapter (independent of any measured durations). */
    val positionMs: Long,
    /** Index of the saved chapter within the book; 0 when it can't be resolved. */
    val chapterIndex: Int,
    val fileCount: Int,
    /** How many of [fileCount] files have a measured duration. */
    val measuredCount: Int,
) {
    /**
     * Real listening progress — past the very start of the first chapter. Deliberately derived
     * from the chapter index + raw offset, NOT from [elapsedMs]: elapsed depends on measured
     * durations and collapses to 0 whenever they're missing, which would hide a book the user is
     * hours into (and did — it emptied the Continue shelf).
     */
    val started: Boolean get() = positionMs > 0L || chapterIndex > 0

    /**
     * True only when every file has a measured duration, so a whole-book total, percentage and
     * time-left are trustworthy. A partial measurement makes elapsed exceed the (also partial)
     * total, which otherwise reads as "finished".
     */
    val fullyMeasured: Boolean get() = fileCount > 0 && measuredCount == fileCount
}

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE bookId = :bookId")
    suspend fun findByBookId(bookId: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state")
    suspend fun getAll(): List<PlaybackStateEntity>

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
                WHERE af.bookId = ps.bookId AND af.durationMs IS NOT NULL) AS measuredCount
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

    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)
}
