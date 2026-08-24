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

    /**
     * Drops every folder this pass did not visit.
     *
     * Only safe after a COMPLETE crawl, which stamps every folder it saw with the same [scannedAt]
     * — so anything carrying a different one no longer exists. Expressed as "not this timestamp"
     * rather than "not in this list of ids" on purpose: a library has thousands of folders and an
     * `IN (:ids)` would blow SQLite's 999-parameter cap.
     *
     * Without it the table only ever grows: a renamed folder leaves its old row behind for ever,
     * and every incremental scan reads the whole table to build its ETag map.
     */
    @Query("DELETE FROM crawl_dirs WHERE lastScanned != :scannedAt")
    suspend fun deleteNotScannedAt(scannedAt: Long)

    @Upsert
    suspend fun upsert(dir: CrawlDirEntity)
}
