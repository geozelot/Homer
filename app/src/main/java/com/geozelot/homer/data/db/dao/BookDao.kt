package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY author IS NULL, author, series, seriesIndex, title")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    /** Books with no cover yet and not yet tried — enrichment targets. */
    @Query("SELECT * FROM books WHERE coverFilePath IS NULL AND localCoverPath IS NULL AND coverAttempted = 0")
    suspend fun booksNeedingCover(): List<BookEntity>

    @Query("UPDATE books SET localCoverPath = :path WHERE id = :bookId")
    suspend fun updateLocalCover(bookId: String, path: String)

    /** Sets (or clears, with null) a user-chosen custom cover path. */
    @Query("UPDATE books SET customCoverPath = :path WHERE id = :bookId")
    suspend fun updateCustomCover(bookId: String, path: String?)

    /** Clears all custom covers (e.g. when the storage location changes and their Uris go stale). */
    @Query("UPDATE books SET customCoverPath = NULL")
    suspend fun clearCustomCovers()

    /** Marks a book's cover as tried (whether or not art was found) so it isn't re-probed. */
    @Query("UPDATE books SET coverAttempted = 1 WHERE id = :bookId")
    suspend fun markCoverAttempted(bookId: String)

    /** Clears cached extracted art + the attempted flag so covers are re-fetched. */
    @Query("UPDATE books SET localCoverPath = NULL, coverAttempted = 0")
    suspend fun resetCoverArt()

    /** Re-arms cover enrichment for books that still have no art (e.g. after enabling online
     *  lookup), without disturbing books that already have a cover. */
    @Query("UPDATE books SET coverAttempted = 0 WHERE localCoverPath IS NULL AND coverFilePath IS NULL")
    suspend fun retryCoversWithoutArt()

    @Query("UPDATE books SET totalDurationMs = :totalDurationMs WHERE id = :bookId")
    suspend fun updateTotalDuration(bookId: String, totalDurationMs: Long)

    @Query("UPDATE books SET genre = :genre WHERE id = :bookId")
    suspend fun updateGenre(bookId: String, genre: String)

    @Query("UPDATE books SET chapterTier = :tier WHERE id = :bookId")
    suspend fun updateChapterTier(bookId: String, tier: Int)

    /** Ids of books at or beneath [path] (used to preserve skipped subtrees on rescan). */
    @Query("SELECT id FROM books WHERE id = :path OR id LIKE :path || '/%'")
    suspend fun idsUnder(path: String): List<String>

    @Upsert
    suspend fun upsert(books: List<BookEntity>)

    /** Removes books whose folders no longer exist (ids not seen in the latest scan). */
    @Query("DELETE FROM books WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<String>)

    @Query("DELETE FROM books")
    suspend fun deleteAll()
}
