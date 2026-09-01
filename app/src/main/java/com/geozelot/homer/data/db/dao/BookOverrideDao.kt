package com.geozelot.homer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.db.entity.EditFields
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
     * The predicate is [EditFields.CORRECTED] — the same set the Kotlin-side check uses, from the
     * same declaration. `finished`, `hidden` and `downloadOnPlay` are absent on purpose: those are
     * claims about the reader, never published, and counting them here would promise to share
     * something that never leaves the device.
     *
     * Three of the nine columns were absent by ACCIDENT until the set was consolidated, so a book
     * whose only correction was being put into a collection was not counted and the Library screen
     * said "no corrections" while holding a folder full of them.
     */
    @Query("SELECT COUNT(*) FROM book_overrides WHERE " + EditFields.CORRECTED)
    fun observeCorrectionCount(): Flow<Int>

    /**
     * Corrections edited since [since] — the ones the shared index has not been told about.
     *
     * The same field test as the count above, because a row of all-nulls is a CLEARED correction
     * kept as a tombstone rather than an edit anybody is waiting to see published. Note this cannot
     * see a correction that was cleared after the last publish: clearing writes a fresh timestamp,
     * so it counts, but a row DELETED outright would not — nothing deletes them, which is why the
     * tombstone exists.
     */
    @Query("SELECT COUNT(*) FROM book_overrides WHERE updatedAt > :since AND " + EditFields.CORRECTED)
    fun observeUnpublishedCount(since: Long): Flow<Int>

    @Query("SELECT * FROM book_overrides WHERE bookId = :bookId")
    suspend fun findById(bookId: String): BookOverrideEntity?

    @Query("SELECT * FROM book_overrides WHERE bookId = :bookId")
    fun observeById(bookId: String): Flow<BookOverrideEntity?>

    /** Re-points a book's overrides onto a new id after its folder moved/renamed. */
    @Query("UPDATE book_overrides SET bookId = :newId WHERE bookId = :oldId")
    suspend fun relink(oldId: String, newId: String)

    /** Drops one book's override, for when a correction is cleared and nothing personal remains. */
    @Query("DELETE FROM book_overrides WHERE bookId = :bookId")
    suspend fun deleteById(bookId: String)

    /** Drops overrides for books that are no longer indexed (no FK here, so a prune orphans them). */
    @Query("DELETE FROM book_overrides WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun deleteOrphans()

    @Upsert
    suspend fun upsert(override: BookOverrideEntity)
}
