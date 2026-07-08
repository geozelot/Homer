package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE bookId = :bookId")
    suspend fun findByBookId(bookId: String): PlaybackStateEntity?

    /** All saved positions — for a future "continue listening" / progress UI. */
    @Query("SELECT * FROM playback_state")
    fun observeAll(): Flow<List<PlaybackStateEntity>>

    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)
}
