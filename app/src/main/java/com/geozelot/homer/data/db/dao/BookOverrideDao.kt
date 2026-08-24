package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookOverrideDao {

    @Query("SELECT * FROM book_overrides")
    fun observeAll(): Flow<List<BookOverrideEntity>>

    @Query("SELECT * FROM book_overrides")
    suspend fun getAll(): List<BookOverrideEntity>

    /** Newest override change, for cheaply deciding whether anything needs pushing. */
    @Query("SELECT MAX(updatedAt) FROM book_overrides")
    suspend fun maxUpdatedAt(): Long?

    /**
     * How many books carry a SHARED correction — the count the corrections row reports.
     *
     * The predicate is the same one [com.geozelot.homer.data.sync.facet.FacetMapping.correctionOf]
     * uses to decide there is anything to publish. `finished`, `hidden` and `downloadOnPlay` are
     * absent on purpose: those are claims about the reader, never published, and counting them here
     * would promise to share something that never leaves the device.
     */
    @Query(
        "SELECT COUNT(*) FROM book_overrides WHERE title IS NOT NULL OR author IS NOT NULL " +
            "OR series IS NOT NULL OR seriesIndex IS NOT NULL OR genre IS NOT NULL OR tags IS NOT NULL",
    )
    fun observeCorrectionCount(): Flow<Int>

    @Query("SELECT * FROM book_overrides WHERE bookId = :bookId")
    suspend fun findById(bookId: String): BookOverrideEntity?

    @Query("SELECT * FROM book_overrides WHERE bookId = :bookId")
    fun observeById(bookId: String): Flow<BookOverrideEntity?>

    /** Re-points a book's overrides onto a new id after its folder moved/renamed. */
    @Query("UPDATE book_overrides SET bookId = :newId WHERE bookId = :oldId")
    suspend fun relink(oldId: String, newId: String)

    /** Drops overrides for books that are no longer indexed (no FK here, so a prune orphans them). */
    /** Drops one book's override, for when a shared correction is cleared and nothing personal remains. */
    @Query("DELETE FROM book_overrides WHERE bookId = :bookId")
    suspend fun deleteById(bookId: String)

    @Query("DELETE FROM book_overrides WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun deleteOrphans()

    @Upsert
    suspend fun upsert(override: BookOverrideEntity)
}
