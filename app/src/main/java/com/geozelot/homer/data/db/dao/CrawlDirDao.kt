package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.CrawlDirEntity

@Dao
interface CrawlDirDao {

    @Query("SELECT * FROM crawl_dirs WHERE path = :path")
    suspend fun findByPath(path: String): CrawlDirEntity?

    @Upsert
    suspend fun upsert(dir: CrawlDirEntity)

    @Query("DELETE FROM crawl_dirs")
    suspend fun clear()
}
