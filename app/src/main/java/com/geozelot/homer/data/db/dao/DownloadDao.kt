package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads WHERE bookId = :bookId")
    fun observeByBookId(bookId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE bookId = :bookId")
    suspend fun findByBookId(bookId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}
