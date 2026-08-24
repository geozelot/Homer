package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.geozelot.homer.data.db.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY sortIndex")
    fun observeForBook(bookId: String): Flow<List<ChapterEntity>>

    /** A one-shot read, for publishing a book's chapters into the shared index. */
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY sortIndex")
    suspend fun findForBook(bookId: String): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Insert
    suspend fun insertAll(chapters: List<ChapterEntity>)

    /** Replaces a book's chapter set in one shot (extraction is all-or-nothing per book). */
    @Transaction
    suspend fun replaceForBook(bookId: String, chapters: List<ChapterEntity>) {
        deleteForBook(bookId)
        if (chapters.isNotEmpty()) insertAll(chapters)
    }
}
