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
)

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE bookId = :bookId")
    suspend fun findByBookId(bookId: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state")
    suspend fun getAll(): List<PlaybackStateEntity>

    /** All saved positions — for a future "continue listening" / progress UI. */
    @Query("SELECT * FROM playback_state")
    fun observeAll(): Flow<List<PlaybackStateEntity>>

    /**
     * Whole-book elapsed time per started book: durations of all chapters before the
     * saved one, plus the saved offset within it. Books without measured durations sum
     * to a partial (best-effort) value; re-emits when positions or durations change.
     */
    @Query(
        """
        SELECT ps.bookId AS bookId,
               ps.positionMs + COALESCE((
                 SELECT SUM(af.durationMs) FROM audio_files af
                 WHERE af.bookId = ps.bookId
                   AND af.sortIndex < (
                     SELECT cur.sortIndex FROM audio_files cur
                     WHERE cur.relativePath = ps.currentMediaId
                   )
               ), 0) AS elapsedMs
        FROM playback_state ps
        """,
    )
    fun observeProgress(): Flow<List<BookProgress>>

    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)
}
