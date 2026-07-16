package com.geozelot.homer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.ChapterDao
import com.geozelot.homer.data.db.dao.CrawlDirDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.ChapterEntity
import com.geozelot.homer.data.db.entity.BookmarkMetaEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.db.entity.CrawlDirEntity
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.db.entity.PlaybackStateEntity

@Database(
    entities = [
        BookEntity::class,
        AudioFileEntity::class,
        CrawlDirEntity::class,
        PlaybackStateEntity::class,
        BookmarkEntity::class,
        BookmarkMetaEntity::class,
        DownloadEntity::class,
        BookOverrideEntity::class,
        ChapterEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
abstract class HomerDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun audioFileDao(): AudioFileDao
    abstract fun crawlDirDao(): CrawlDirDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookmarkMetaDao(): BookmarkMetaDao
    abstract fun downloadDao(): DownloadDao
    abstract fun bookOverrideDao(): BookOverrideDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        const val NAME = "homer.db"
    }
}
