package com.geozelot.homer.data.db

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geozelot.homer.di.ALL_MIGRATIONS
import com.geozelot.homer.di.MIGRATION_1_2
import com.geozelot.homer.di.MIGRATION_2_3
import com.geozelot.homer.di.MIGRATION_3_4
import com.geozelot.homer.di.MIGRATION_4_5
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every migration Homer ships, run against the schemas exported to `app/schemas`.
 *
 * Room checks a migrated database against the schema it expects only when the database is opened,
 * on the user's phone, after the update has installed — and a mismatch there is a crash at launch
 * that a destructive fallback (debug builds only) would have hidden here. This walks each step
 * against the committed schema files instead, and checks that the rows a real library holds come
 * out the other side.
 *
 * Instrumented, because the SQLite under test should be the platform's own: run it with
 * `./gradlew :app:connectedDebugAndroidTest` against an emulator.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = context.getDatabasePath(TEST_DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = HomerDatabase::class,
    )

    /** The schema the app is at now: wherever the last migration ends. */
    private val latest = ALL_MIGRATIONS.last().endVersion

    @Before
    fun clean() {
        context.deleteDatabase(TEST_DB)
    }

    // ── each step against its schema ─────────────────────────────────────────────────────────

    @Test
    fun oneToTwo() = step(1, MIGRATION_1_2)

    @Test
    fun twoToThree() = step(2, MIGRATION_2_3)

    @Test
    fun threeToFour() = step(3, MIGRATION_3_4)

    @Test
    fun fourToFive() = step(4, MIGRATION_4_5)

    @Test
    fun theChainCoversEveryVersion() {
        // No gap and no overlap: a step missing from the list is an update that cannot install.
        for ((index, migration) in ALL_MIGRATIONS.withIndex()) {
            assertEquals(index + 1, migration.startVersion)
            assertEquals(index + 2, migration.endVersion)
        }
    }

    // ── a real library, carried all the way ──────────────────────────────────────────────────

    @Test
    fun aLibraryAtSchemaOneArrivesIntact() {
        helper.createDatabase(1).use { db ->
            db.execSQL(
                "INSERT INTO books (id, title, author, series, seriesIndex, relativePath, coverAttempted, " +
                    "metadataAttempted, chapterTier, isMultiFile, fileCount, totalDurationMs, addedAt, updatedAt) " +
                    "VALUES ('Pratchett/Wyrd Sisters', 'Wyrd Sisters', 'Terry Pratchett', 'Hexen', 1, " +
                    "'Pratchett/Wyrd Sisters', 1, 1, 2, 0, 1, 3600000, 10, 20)",
            )
            db.execSQL(
                "INSERT INTO audio_files (relativePath, bookId, fileName, sortIndex, sizeBytes, durationMs, " +
                    "durationAttempted) VALUES ('Pratchett/Wyrd Sisters/01.mp3', 'Pratchett/Wyrd Sisters', " +
                    "'01.mp3', 0, 1024, 3600000, 1)",
            )
            db.execSQL(
                "INSERT INTO playback_state (bookId, currentMediaId, positionMs, updatedAt) " +
                    "VALUES ('Pratchett/Wyrd Sisters', 'Pratchett/Wyrd Sisters/01.mp3', 123456, 30)",
            )
            db.execSQL(
                "INSERT INTO bookmarks (bookId, mediaId, chapterTitle, positionMs, label, createdAt, kind) " +
                    "VALUES ('Pratchett/Wyrd Sisters', 'Pratchett/Wyrd Sisters/01.mp3', 'One', 5000, 'here', 40, 'NOTE')",
            )
            db.execSQL("INSERT INTO bookmark_meta (bookId, updatedAt) VALUES ('Pratchett/Wyrd Sisters', 40)")
            db.execSQL(
                "INSERT INTO downloads (bookId, status, downloadedFiles, totalFiles, updatedAt) " +
                    "VALUES ('Pratchett/Wyrd Sisters', 'DONE', 1, 1, 50)",
            )
            db.execSQL(
                "INSERT INTO book_overrides (bookId, title, hidden, updatedAt) " +
                    "VALUES ('Pratchett/Wyrd Sisters', 'Wyrd Sisters (Hörbuch)', 0, 60)",
            )
            db.execSQL(
                "INSERT INTO chapters (bookId, sortIndex, title, startMs) VALUES ('Pratchett/Wyrd Sisters', 0, 'One', 0)",
            )
            db.execSQL("INSERT INTO crawl_dirs (path, etag, lastScanned) VALUES ('Pratchett', 'e1', 70)")
        }

        helper.runMigrationsAndValidate(latest, ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Wyrd Sisters|null|null"), db.rows("SELECT title, collection, documentFilePaths FROM books"))
            assertEquals(listOf("3600000"), db.rows("SELECT durationMs FROM audio_files"))
            assertEquals(listOf("123456"), db.rows("SELECT positionMs FROM playback_state"))
            assertEquals(listOf("here|NOTE"), db.rows("SELECT label, kind FROM bookmarks"))
            assertEquals(listOf("DONE"), db.rows("SELECT status FROM downloads"))
            assertEquals(listOf("One"), db.rows("SELECT title FROM chapters"))
            // The correction survives, and its clock is the stamp it already had.
            assertEquals(
                listOf("Wyrd Sisters (Hörbuch)|60|60"),
                db.rows("SELECT title, updatedAt, correctedAt FROM book_overrides"),
            )
            // Dropped on purpose by 2→3 (and again by 3→4), so the first scan lists everything once.
            assertEquals(emptyList<String>(), db.rows("SELECT path FROM crawl_dirs"))
        }

        // And Room itself agrees: the identity hash and every table, opened the way the app opens it.
        val database = Room.databaseBuilder(context, HomerDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS.toTypedArray())
            .build()
        try {
            val overrides = runBlocking { database.bookOverrideDao().getAll() }
            assertEquals(1, overrides.size)
            assertEquals(60L, overrides.single().correctedAt)
        } finally {
            database.close()
        }
    }

    // ── 4 → 5: which rows get a correction clock ─────────────────────────────────────────────

    @Test
    fun fourToFiveGivesTheCorrectionClockOnlyToRowsAboutTheBook() {
        helper.createDatabase(4).use { db ->
            fun override(id: String, columns: String, values: String) = db.execSQL(
                "INSERT INTO book_overrides (bookId, $columns) VALUES ('$id', $values)",
            )
            override("corrected", "title, hidden, updatedAt", "'T', 0, 100")
            override("corrected-and-hidden", "collection, hidden, updatedAt", "'C', 1, 200")
            override("cleared", "hidden, updatedAt", "0, 300")
            override("hidden-only", "hidden, updatedAt", "1, 400")
            override("finished-only", "finished, hidden, updatedAt", "1, 0, 500")
            override("play-mode-only", "downloadOnPlay, hidden, updatedAt", "0, 0, 600")
        }

        helper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5)).use { db ->
            assertEquals(
                listOf(
                    // About the book: the stamp it had is the correction's.
                    "cleared|300|300",
                    "corrected|100|100",
                    "corrected-and-hidden|200|200",
                    // About the reader only: no correction clock, so the hide no longer outranks
                    // a correction made before it.
                    "finished-only|500|0",
                    "hidden-only|400|0",
                    "play-mode-only|600|0",
                ),
                db.rows("SELECT bookId, updatedAt, correctedAt FROM book_overrides ORDER BY bookId"),
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private fun step(from: Int, migration: Migration) {
        assertEquals(from, migration.startVersion)
        helper.createDatabase(from).close()
        helper.runMigrationsAndValidate(migration.endVersion, listOf(migration)).close()
    }

    /** Every row of [sql], each as its columns joined with `|` — null written as "null". */
    private fun SQLiteConnection.rows(sql: String): List<String> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) {
                add(
                    (0 until statement.getColumnCount()).joinToString("|") { column ->
                        if (statement.isNull(column)) "null" else statement.getText(column)
                    },
                )
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
