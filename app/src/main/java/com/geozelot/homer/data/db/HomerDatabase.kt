package com.geozelot.homer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.CrawlDirDao
import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.CrawlDirEntity

@Database(
    entities = [
        BookEntity::class,
        AudioFileEntity::class,
        CrawlDirEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HomerDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun audioFileDao(): AudioFileDao
    abstract fun crawlDirDao(): CrawlDirDao

    companion object {
        const val NAME = "homer.db"
    }
}
