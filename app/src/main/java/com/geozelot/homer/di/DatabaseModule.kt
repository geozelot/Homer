package com.geozelot.homer.di

import android.content.Context
import androidx.room.Room
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.data.db.HomerDatabase
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.ChapterDao
import com.geozelot.homer.data.db.dao.CrawlDirDao
import com.geozelot.homer.data.db.dao.DownloadDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomerDatabase =
        Room.databaseBuilder(context, HomerDatabase::class.java, HomerDatabase.NAME)
            // NO MIGRATIONS, deliberately. Schema 1 is 2.0.0's baseline: the seventeen migrations
            // that got the 1.x line here were deleted with the rest of the v1 path, because 1.x is
            // withdrawn when 2.0 lands and nothing in the wild will ever need them again.
            //
            // The FIRST schema change after 2.0.0 ships adds a Migration(1, 2) here. Until then
            // this list is empty on purpose and an empty list is not an oversight.
            .apply {
                // Destructive fallback is a DEBUG-ONLY convenience. In a release build a missing
                // migration (or an APK downgrade to an older schema) must fail loudly instead of
                // silently wiping every table — positions, bookmarks, overrides, downloads — which
                // is exactly the data-loss class this app has been bitten by before.
                if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true)
            }
            .build()

    @Provides
    fun provideBookDao(db: HomerDatabase): BookDao = db.bookDao()

    @Provides
    fun provideAudioFileDao(db: HomerDatabase): AudioFileDao = db.audioFileDao()

    @Provides
    fun provideCrawlDirDao(db: HomerDatabase): CrawlDirDao = db.crawlDirDao()

    @Provides
    fun providePlaybackStateDao(db: HomerDatabase): PlaybackStateDao = db.playbackStateDao()

    @Provides
    fun provideBookmarkDao(db: HomerDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideBookmarkMetaDao(db: HomerDatabase): BookmarkMetaDao = db.bookmarkMetaDao()

    @Provides
    fun provideDownloadDao(db: HomerDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideBookOverrideDao(db: HomerDatabase): BookOverrideDao = db.bookOverrideDao()

    @Provides
    fun provideChapterDao(db: HomerDatabase): ChapterDao = db.chapterDao()
}
