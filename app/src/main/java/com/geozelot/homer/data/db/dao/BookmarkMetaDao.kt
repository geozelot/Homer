package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.BookmarkMetaEntity

@Dao
interface BookmarkMetaDao {

    @Query("SELECT * FROM bookmark_meta")
    suspend fun getAll(): List<BookmarkMetaEntity>

    /** Newest bookmark-set change, for cheaply deciding whether anything needs pushing. */
    @Query("SELECT MAX(updatedAt) FROM bookmark_meta")
    suspend fun maxUpdatedAt(): Long?

    /** Re-points a book's bookmark-sync timestamp onto a new id after its folder moved/renamed. */
    @Query("UPDATE bookmark_meta SET bookId = :newId WHERE bookId = :oldId")
    suspend fun relink(oldId: String, newId: String)

    @Upsert
    suspend fun upsert(meta: BookmarkMetaEntity)
}
