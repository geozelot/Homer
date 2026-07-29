package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.CrawlDirEntity

@Dao
interface CrawlDirDao {

    @Query("SELECT * FROM crawl_dirs")
    suspend fun getAll(): List<CrawlDirEntity>

    @Upsert
    suspend fun upsert(dir: CrawlDirEntity)
}
