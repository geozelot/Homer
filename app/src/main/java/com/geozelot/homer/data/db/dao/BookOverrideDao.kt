package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookOverrideDao {

    @Query("SELECT * FROM book_overrides")
    fun observeAll(): Flow<List<BookOverrideEntity>>

    @Query("SELECT * FROM book_overrides")
    suspend fun getAll(): List<BookOverrideEntity>

    @Query("SELECT * FROM book_overrides WHERE bookId = :bookId")
    suspend fun findById(bookId: String): BookOverrideEntity?

    @Upsert
    suspend fun upsert(override: BookOverrideEntity)
}
