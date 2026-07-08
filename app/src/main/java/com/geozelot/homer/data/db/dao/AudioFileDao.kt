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

    @Query("UPDATE audio_files SET durationMs = :durationMs WHERE relativePath = :relativePath")
    suspend fun updateDuration(relativePath: String, durationMs: Long)

    @Upsert
    suspend fun upsert(files: List<AudioFileEntity>)

    @Query("DELETE FROM audio_files WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
