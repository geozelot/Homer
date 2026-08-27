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

    /**
     * A book's chapter cuts, in playing order — the chapter list a listener authored for a
     * single-file book whose tags carry none.
     *
     * Ordered by position rather than by when they were made: a cut added later can belong earlier
     * in the book, and a chapter list out of order is not a chapter list.
     */
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND kind = 'cut' ORDER BY positionMs")
    suspend fun findCutsForBook(bookId: String): List<BookmarkEntity>

    /** Every cut in the library, for publishing them all in one pass. */
    @Query("SELECT * FROM bookmarks WHERE kind = 'cut' ORDER BY bookId, positionMs")
    suspend fun allCuts(): List<BookmarkEntity>

    /**
     * Chapter cuts made since [since]. Cuts ride corrections.json, so a new one is unpublished work.
     *
     * Keyed on `createdAt`, which is all a cut has — so a cut DELETED since the last publish is not
     * counted, and the row will read as shared when one edit is in fact outstanding. Publishing
     * anyway is one small upload, and the alternative is a timestamp on a table that has never
     * needed one.
     */
    @Query("SELECT COUNT(*) FROM bookmarks WHERE kind = 'cut' AND createdAt > :since")
    fun observeCutsSince(since: Long): Flow<Int>

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
