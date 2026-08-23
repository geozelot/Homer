package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY author IS NULL, author, series, seriesIndex, title")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    /**
     * Books with no *locally cached* cover yet and not yet tried — enrichment targets.
     *
     * Note this deliberately includes books that have a `coverFilePath` (a cover image sitting in
     * the book's folder on the server). Those used to be excluded, which meant their art was
     * streamed from WebDAV every single time it was displayed and was unavailable offline.
     * Caching them locally is one small GET each and takes them off the hot path for good.
     */
    @Query("SELECT * FROM books WHERE localCoverPath IS NULL AND coverAttempted = 0")
    suspend fun booksNeedingCover(): List<BookEntity>

    /** All books still without cached art (ignores the attempted flag) — shared-cache targets. */
    @Query("SELECT * FROM books WHERE localCoverPath IS NULL")
    suspend fun booksWithoutArt(): List<BookEntity>

    @Query("UPDATE books SET localCoverPath = :path WHERE id = :bookId")
    suspend fun updateLocalCover(bookId: String, path: String)

    /** Sets (or clears, with null) a user-chosen custom cover path. */
    @Query("UPDATE books SET customCoverPath = :path WHERE id = :bookId")
    suspend fun updateCustomCover(bookId: String, path: String?)

    /** Marks a book's cover as tried (whether or not art was found) so it isn't re-probed. */
    @Query("UPDATE books SET coverAttempted = 1 WHERE id = :bookId")
    suspend fun markCoverAttempted(bookId: String)

    /** Clears cached extracted art + the attempted flag so covers are re-fetched. */
    @Query("UPDATE books SET localCoverPath = NULL, coverAttempted = 0")
    suspend fun resetCoverArt()

    /** Marks a book's tag/chapter probe as tried and fruitless, so it isn't repeated on every open. */
    @Query("UPDATE books SET metadataAttempted = 1 WHERE id = :bookId")
    suspend fun markMetadataAttempted(bookId: String)

    /** Re-arms the tag/chapter probe (a full refresh — the user's way to retry a failed probe). */
    @Query("UPDATE books SET metadataAttempted = 0")
    suspend fun resetMetadataAttempted()

    /** Re-arms cover enrichment for books that still have no art (e.g. after enabling online
     *  lookup), without disturbing books that already have a cover. */
    @Query("UPDATE books SET coverAttempted = 0 WHERE localCoverPath IS NULL AND coverFilePath IS NULL")
    suspend fun retryCoversWithoutArt()

    @Query("UPDATE books SET totalDurationMs = :totalDurationMs WHERE id = :bookId")
    suspend fun updateTotalDuration(bookId: String, totalDurationMs: Long)

    @Query("UPDATE books SET genre = :genre WHERE id = :bookId")
    suspend fun updateGenre(bookId: String, genre: String)

    @Query("UPDATE books SET chapterTier = :tier WHERE id = :bookId")
    suspend fun updateChapterTier(bookId: String, tier: Int)

    /** Ids of books at or beneath [path] (used to preserve skipped subtrees on rescan). */
    @Query("SELECT id FROM books WHERE id = :path OR id LIKE :path || '/%'")
    suspend fun idsUnder(path: String): List<String>

    @Query("SELECT id FROM books")
    suspend fun allIds(): List<String>

    /**
     * Books with no known total length — the work list for a "measure lengths" pass. A book only
     * gets a total once EVERY one of its files is measured, so this is exactly the set that still
     * has probing to do (and books whose files all proved unmeasurable are skipped cheaply inside
     * the enricher, which remembers that).
     */
    @Query("SELECT id FROM books WHERE totalDurationMs IS NULL ORDER BY id")
    suspend fun idsWithoutDuration(): List<String>

    @Upsert
    suspend fun upsert(books: List<BookEntity>)

    /**
     * Removes the given books (folders no longer present). Callers must chunk: every id is one SQL
     * host parameter and SQLite caps those at 999 — which is why the prune can't be expressed as a
     * single `NOT IN (:keepIds)` over a whole library (see [allIds] and `LibraryScanner`).
     */
    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
