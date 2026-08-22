package com.geozelot.homer.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geozelot.homer.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the shipped migrations against the schemas exported to `app/schemas/`.
 *
 * These have to be instrumented: [MigrationTestHelper] drives a real SQLite database. Only 15, 16
 * and 17 are exported, so that's the range they can cover — the DB predates schema export.
 *
 * Two things are checked, and the second is the one that matters. Room's own validation
 * ([MigrationTestHelper.runMigrationsAndValidate]) proves the migrated *shape* matches what the
 * entities declare, which catches a hand-written `ALTER TABLE` drifting from its entity. It says
 * nothing about the *data*, and every serious incident in this app's history has been data loss
 * under a schema that validated perfectly — so each migration also gets a row written before it
 * and read back after.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HomerDatabase::class.java,
    )

    /** The whole chain from the oldest exported schema to current, validated against 17.json. */
    @Test
    fun migratesFromOldestExportedSchemaToCurrent() {
        helper.createDatabase(TEST_DB, 15).close()
        helper.runMigrationsAndValidate(TEST_DB, 17, true, *DatabaseModule.ALL_MIGRATIONS).close()
    }

    /**
     * v15 -> v16 clears the legacy forced `finished` flag, and must clear ONLY that.
     *
     * The flag became both unsettable and unclearable when "completed" was redefined as a progress
     * reset, so a leftover `finished = 1` hid the book from the library forever. The rest of the
     * override row is the user's hand-typed metadata: wiping any of it alongside the flag would
     * trade one silent data loss for another.
     */
    @Test
    fun v16_clearsTheForcedFinishedFlagAndNothingElse() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                "INSERT INTO book_overrides " +
                    "(bookId, title, author, series, seriesIndex, genre, tags, finished, downloadOnPlay, hidden, updatedAt) " +
                    "VALUES ('a/b', 'Corrected Title', 'Corrected Author', 'Saga', 2, 'Fantasy', 'tag', 1, 1, 0, 100)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 16, true, *DatabaseModule.ALL_MIGRATIONS).use { db ->
            db.query("SELECT title, author, series, seriesIndex, genre, tags, finished, downloadOnPlay FROM book_overrides")
                .use { c ->
                    assertTrue("the override row must survive the migration", c.moveToFirst())
                    assertEquals("Corrected Title", c.getString(0))
                    assertEquals("Corrected Author", c.getString(1))
                    assertEquals("Saga", c.getString(2))
                    assertEquals(2, c.getInt(3))
                    assertEquals("Fantasy", c.getString(4))
                    assertEquals("tag", c.getString(5))
                    assertTrue("the forced finished flag must be cleared", c.isNull(6))
                    assertEquals("downloadOnPlay is a different flag and must be left alone", 1, c.getInt(7))
                }
        }
    }

    /**
     * v16 -> v17 adds the "we already tried" probe flags. Measured durations must come through
     * untouched — they cost a full stream each to obtain, and losing them takes a book's time-left,
     * progress ring and auto-finish with them — and both new flags must default to 0 so existing
     * rows get exactly one more attempt rather than being written off as unmeasurable.
     */
    @Test
    fun v17_addsProbeFlagsWithoutLosingMeasuredDurations() {
        helper.createDatabase(TEST_DB, 16).use { db ->
            db.execSQL(
                "INSERT INTO books " +
                    "(id, contentHash, title, author, series, seriesIndex, genre, relativePath, " +
                    "coverFilePath, localCoverPath, customCoverPath, coverAttempted, chapterTier, " +
                    "isMultiFile, fileCount, totalDurationMs, addedAt, updatedAt) " +
                    "VALUES ('a/b', 'hash', 'Book', 'Author', NULL, NULL, 'Fantasy', 'a/b', " +
                    "NULL, NULL, NULL, 1, 0, 1, 1, 3600000, 1, 2)",
            )
            db.execSQL(
                "INSERT INTO audio_files " +
                    "(relativePath, bookId, fileName, sortIndex, sizeBytes, etag, lastModified, contentType, durationMs) " +
                    "VALUES ('a/b/01.mp3', 'a/b', '01.mp3', 0, 4096, 'etag', 1, 'audio/mpeg', 3600000)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 17, true, *DatabaseModule.ALL_MIGRATIONS).use { db ->
            db.query("SELECT durationMs, durationAttempted FROM audio_files").use { c ->
                assertTrue("the audio file row must survive the migration", c.moveToFirst())
                assertEquals("a measured duration must not be lost", 3600000L, c.getLong(0))
                assertEquals("existing files get one more probe, not a write-off", 0, c.getInt(1))
            }
            db.query("SELECT genre, totalDurationMs, metadataAttempted FROM books").use { c ->
                assertTrue("the book row must survive the migration", c.moveToFirst())
                assertEquals("Fantasy", c.getString(0))
                assertEquals(3600000L, c.getLong(1))
                assertEquals("existing books get one more probe, not a write-off", 0, c.getInt(2))
            }
        }
    }

    private companion object {
        const val TEST_DB = "homer-migration-test"
    }
}
