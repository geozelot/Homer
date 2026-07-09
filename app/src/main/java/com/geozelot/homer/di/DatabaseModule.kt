package com.geozelot.homer.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.geozelot.homer.data.db.HomerDatabase
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookmarkMetaDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
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

    /** v2 -> v3: add the cached-cover column to books. */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `books` ADD COLUMN `localCoverPath` TEXT")
        }
    }

    /** v3 -> v4: add the bookmarks table (DDL must match Room's generated schema). */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`bookId` TEXT NOT NULL, `mediaId` TEXT NOT NULL, " +
                    "`chapterTitle` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, " +
                    "`label` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
        }
    }

    /** v4 -> v5: add the bookmark_meta table (per-book bookmark sync timestamp). */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmark_meta` (" +
                    "`bookId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`bookId`))",
            )
        }
    }

    /** v5 -> v6: add the downloads table (offline-download state per book). */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `downloads` (" +
                    "`bookId` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                    "`downloadedFiles` INTEGER NOT NULL, `totalFiles` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`))",
            )
        }
    }

    /** v6 -> v7: add the book_overrides table (user metadata/hide corrections). */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `book_overrides` (" +
                    "`bookId` TEXT NOT NULL, `title` TEXT, `author` TEXT, `series` TEXT, " +
                    "`seriesIndex` INTEGER, `hidden` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`bookId`))",
            )
        }
    }

    /** v7 -> v8: add the detected-genre column to books. */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `books` ADD COLUMN `genre` TEXT")
        }
    }

    /** v8 -> v9: add genre/tags/finished override columns to book_overrides. */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_overrides` ADD COLUMN `genre` TEXT")
            db.execSQL("ALTER TABLE `book_overrides` ADD COLUMN `tags` TEXT")
            db.execSQL("ALTER TABLE `book_overrides` ADD COLUMN `finished` INTEGER")
        }
    }

    /**
     * v9 -> v10: book/file ids switched from files-root-relative to library-root-relative
     * (Tier 3 needs ids that match across users mounting the shared folder at different
     * paths). Old rows are keyed by the old scheme, so clear the derived + id-keyed data;
     * the next scan rebuilds it. `.homer` re-syncs under the new ids on next open.
     */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "books", "audio_files", "crawl_dirs", "playback_state",
                "bookmarks", "bookmark_meta", "downloads", "book_overrides",
            ).forEach { db.execSQL("DELETE FROM `$it`") }
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomerDatabase =
        Room.databaseBuilder(context, HomerDatabase::class.java, HomerDatabase.NAME)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            )
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

    @Provides
    fun provideBookmarkDao(db: HomerDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideBookmarkMetaDao(db: HomerDatabase): BookmarkMetaDao = db.bookmarkMetaDao()

    @Provides
    fun provideDownloadDao(db: HomerDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideBookOverrideDao(db: HomerDatabase): BookOverrideDao = db.bookOverrideDao()
}
