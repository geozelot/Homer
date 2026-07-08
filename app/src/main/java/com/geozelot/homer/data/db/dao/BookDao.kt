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

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: String): BookEntity?

    /** Books with no cover yet (no folder image and no cached art) — enrichment targets. */
    @Query("SELECT * FROM books WHERE coverFilePath IS NULL AND localCoverPath IS NULL")
    suspend fun booksNeedingCover(): List<BookEntity>

    @Query("UPDATE books SET localCoverPath = :path WHERE id = :bookId")
    suspend fun updateLocalCover(bookId: String, path: String)

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
