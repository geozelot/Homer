package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.CrawlDirEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CrawlDirDao {

    @Query("SELECT * FROM crawl_dirs")
    suspend fun getAll(): List<CrawlDirEntity>

    /**
     * When the library was last crawled — the newest folder timestamp. Null before the first scan.
     * Every crawled folder stamps its own row, so the maximum is the end of the most recent pass.
     */
    @Query("SELECT MAX(lastScanned) FROM crawl_dirs")
    fun observeLastScanned(): Flow<Long?>

    /** As [observeLastScanned], read once — "has this device ever crawled?" is a one-off question. */
    @Query("SELECT MAX(lastScanned) FROM crawl_dirs")
    suspend fun lastScanned(): Long?

    @Upsert
    suspend fun upsert(dir: CrawlDirEntity)
}
