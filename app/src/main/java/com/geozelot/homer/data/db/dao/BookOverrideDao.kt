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

    /** Newest override change, for cheaply deciding whether anything needs pushing. */
    @Query("SELECT MAX(updatedAt) FROM book_overrides")
    suspend fun maxUpdatedAt(): Long?

    @Query("SELECT * FROM book_overrides WHERE bookId = :bookId")
    suspend fun findById(bookId: String): BookOverrideEntity?

    @Query("SELECT * FROM book_overrides WHERE bookId = :bookId")
    fun observeById(bookId: String): Flow<BookOverrideEntity?>

    /** Re-points a book's overrides onto a new id after its folder moved/renamed. */
    @Query("UPDATE book_overrides SET bookId = :newId WHERE bookId = :oldId")
    suspend fun relink(oldId: String, newId: String)

    @Upsert
    suspend fun upsert(override: BookOverrideEntity)
}
