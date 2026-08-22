package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.AudioFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioFileDao {

    @Query("SELECT * FROM audio_files WHERE bookId = :bookId ORDER BY sortIndex")
    fun observeForBook(bookId: String): Flow<List<AudioFileEntity>>

    @Query("SELECT * FROM audio_files WHERE bookId = :bookId ORDER BY sortIndex")
    suspend fun findForBook(bookId: String): List<AudioFileEntity>

    /**
     * Files of several books at once, so a scan doesn't run one query per book. Callers must chunk:
     * every id is one SQL host parameter and SQLite caps those at 999 (same discipline as
     * [BookDao.deleteByIds]). Ordering is by book then position, since the caller groups by book.
     */
    @Query("SELECT * FROM audio_files WHERE bookId IN (:bookIds) ORDER BY bookId, sortIndex")
    suspend fun findForBooks(bookIds: List<String>): List<AudioFileEntity>

    /** The book a given file belongs to — used to recover the current book on an eager reconnect. */
    @Query("SELECT bookId FROM audio_files WHERE relativePath = :relativePath")
    suspend fun findBookIdForFile(relativePath: String): String?

    @Query("UPDATE audio_files SET durationMs = :durationMs WHERE relativePath = :relativePath")
    suspend fun updateDuration(relativePath: String, durationMs: Long)

    /** Records a fruitless duration probe, so this file isn't re-streamed on every book open. */
    @Query("UPDATE audio_files SET durationAttempted = 1 WHERE relativePath = :relativePath")
    suspend fun markDurationAttempted(relativePath: String)

    /** Re-arms duration probing (a full refresh — the user's way to retry a failed probe). */
    @Query("UPDATE audio_files SET durationAttempted = 0")
    suspend fun resetDurationAttempted()

    @Upsert
    suspend fun upsert(files: List<AudioFileEntity>)

    @Query("DELETE FROM audio_files WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    /** Bulk form of [deleteForBook] for a rescan. Chunk the ids — see [findForBooks]. */
    @Query("DELETE FROM audio_files WHERE bookId IN (:bookIds)")
    suspend fun deleteForBooks(bookIds: List<String>)
}
