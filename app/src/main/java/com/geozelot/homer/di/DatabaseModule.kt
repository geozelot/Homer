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

/**
 * Adds the supplementary PDFs a book carries.
 *
 * One nullable column, no default, so every existing row reads as "no documents" and shows no
 * booklet button — which is also exactly what it showed before.
 *
 * ## …and why the crawl ETags go with it
 *
 * The column is derived from the FOLDER TREE, and nothing else can fill it. An incremental scan
 * skips every subtree whose ETag is unchanged — which, on a library nobody has touched, is all of
 * them — so the books already in the index would never be rewritten and would carry a null column
 * for ever. On a real device that is 171 of 177 books showing no booklet no matter what sits beside
 * them, which does not read as "the scan has not got there yet". It reads as the feature not
 * working.
 *
 * Dropping the stored ETags makes the next ordinary scan list everything once. It costs one full
 * crawl, on the first launch after the update, and nothing after that.
 *
 * **It is safe, and not for a small reason.** That pass is still an INCREMENTAL scan, so
 * `sweepOrphans` stays false and nothing a user typed can be deleted by it; a crawl that fails or
 * is cancelled part-way throws before `applyScan` is reached, so there is no partial pass to prune
 * against; and `planWrites` carries covers, genres, languages, chapter tiers, collection indices
 * and measured durations across a re-detect. This is exactly what the full refresh in Upkeep
 * already does, asked for once on the app's behalf rather than on the user's.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE books ADD COLUMN documentFilePaths TEXT")
        connection.execSQL("DELETE FROM crawl_dirs")
    }
}

/**
 * Changes no schema, and exists anyway.
 *
 * [MIGRATION_2_3] shipped in `2.2.0-BETA.106` WITHOUT the ETag drop below it, so a device that
 * already installed that beta sits at version 3 with the fix applied to nobody: Room does not
 * re-run a migration for a database that has already passed it. That device would keep a null
 * document column on every book until somebody happened to run a full refresh by hand — which is
 * precisely the failure the drop was added to prevent, surviving the fix for it.
 *
 * So the step is repeated as its own version. A device coming from 2 runs both and drops the ETags
 * twice, which costs nothing: the second delete finds an empty table.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DELETE FROM crawl_dirs")
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
