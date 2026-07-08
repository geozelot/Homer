package com.geozelot.homer.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.geozelot.homer.data.db.HomerDatabase
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.CrawlDirDao
import com.geozelot.homer.data.db.dao.PlaybackStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v1 -> v2: add the playback_state table (preserves the scanned library). */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playback_state` (" +
                    "`bookId` TEXT NOT NULL, `currentMediaId` TEXT NOT NULL, " +
                    "`positionMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`bookId`))",
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomerDatabase =
        Room.databaseBuilder(context, HomerDatabase::class.java, HomerDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            // Safety net for any other version mismatch during development.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookDao(db: HomerDatabase): BookDao = db.bookDao()

    @Provides
    fun provideAudioFileDao(db: HomerDatabase): AudioFileDao = db.audioFileDao()

    @Provides
    fun provideCrawlDirDao(db: HomerDatabase): CrawlDirDao = db.crawlDirDao()

    @Provides
    fun providePlaybackStateDao(db: HomerDatabase): PlaybackStateDao = db.playbackStateDao()
}
