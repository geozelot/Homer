package com.geozelot.homer.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.data.db.HomerDatabase
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
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

/**
 * Adds collections: a parent grouping above the series, and a position within it.
 *
 * Both nullable with no default, so every existing row reads as "no collection" and renders exactly
 * as it did before — which is the whole non-conflict guarantee for libraries that have none.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE books ADD COLUMN collection TEXT")
        connection.execSQL("ALTER TABLE books ADD COLUMN collectionIndex INTEGER")
        connection.execSQL("ALTER TABLE book_overrides ADD COLUMN collection TEXT")
        connection.execSQL("ALTER TABLE book_overrides ADD COLUMN collectionIndex INTEGER")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomerDatabase =
        Room.databaseBuilder(context, HomerDatabase::class.java, HomerDatabase.NAME)
            // Schema 1 was 2.0.0's baseline: the seventeen migrations that got the 1.x line here
            // were deleted with the rest of the v1 path, because 1.x was withdrawn when 2.0 landed.
            // From 2.0.0 onwards every step carries a real migration — the released version is
            // somebody's actual library now, and losing it is not a thing a version bump may do.
            .addMigrations(MIGRATION_1_2)
            .apply {
                // Destructive fallback for a MISSING FORWARD MIGRATION stays a DEBUG-ONLY
                // convenience. In a release build that case must fail loudly instead of silently
                // wiping every table — positions, bookmarks, overrides, downloads — which is exactly
                // the data-loss class this app has been bitten by before.
                if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true)
            }
            // A DOWNGRADE is a different question, and this one is answered on purpose.
            //
            // 1.1.0 shipped schema 17 in this same `homer.db`; 2.0.0 resets the baseline to 1. Room
            // refuses a downgrade unless told what to do, so without this an existing 1.x install
            // that updates would throw "Cannot downgrade database from version 17 to 1" the first
            // time anything touched a DAO — a crash at launch, on every launch, with nothing on
            // screen to explain it and no way out but clearing app data.
            //
            // Recreating it empty is survivable in a way it would not be for a forward migration,
            // because almost everything in there is a cache of something authoritative elsewhere:
            // positions come back from the server manifest, overrides and chapter cuts from
            // corrections.json, the shelf from structure/derived (or a crawl), and downloads are
            // re-adopted from the files already on disk by LocalMirror.adoptDownloads(). Plain
            // bookmarks are the one thing that is only ever local — they do not survive, and the
            // 2.0.0 notes say so.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
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
