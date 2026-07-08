package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.BookmarkMetaEntity

@Dao
interface BookmarkMetaDao {

    @Query("SELECT updatedAt FROM bookmark_meta WHERE bookId = :bookId")
    suspend fun updatedAt(bookId: String): Long?

    @Query("SELECT * FROM bookmark_meta")
    suspend fun getAll(): List<BookmarkMetaEntity>

    @Upsert
    suspend fun upsert(meta: BookmarkMetaEntity)
}
