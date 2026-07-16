package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.geozelot.homer.data.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAll(): List<BookmarkEntity>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Insert
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    /**
     * Re-points a book's bookmarks onto a new id after its folder moved/renamed. Must run while
     * the new book row already exists (FK) and before the old row is pruned (cascade would delete
     * them otherwise).
     */
    @Query("UPDATE bookmarks SET bookId = :newId WHERE bookId = :oldId")
    suspend fun relink(oldId: String, newId: String)
}
