package com.geozelot.homer.data.library

import android.content.Context
import android.net.Uri
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.metadata.CoverCache
import com.geozelot.homer.data.sync.HomerSyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of user metadata corrections (the override layer) and custom covers, so the
 * library screen and the player screen edit books through the same path instead of each
 * re-implementing the override upsert. Every metadata write bumps `updatedAt` and syncs the
 * change out via [HomerSyncRepository]; custom-cover writes are device-local (not synced).
 *
 * All methods are `suspend` — callers run them in their own scope.
 */
@Singleton
class BookEditor @Inject constructor(
    private val bookOverrideDao: BookOverrideDao,
    private val bookDao: BookDao,
    private val coverCache: CoverCache,
    private val homerSync: HomerSyncRepository,
    @ApplicationContext private val context: Context,
) {
    /**
     * Saves metadata corrections + the hidden flag; blank fields revert to detection.
     * [finishedChange] carries the finished toggle: null leaves the existing flag untouched,
     * true/false forces that value.
     */
    suspend fun saveOverride(
        bookId: String,
        title: String,
        author: String,
        series: String,
        seriesIndex: String,
        genre: String,
        tags: String,
        hidden: Boolean,
        finishedChange: Boolean?,
    ) {
        val finished = finishedChange ?: bookOverrideDao.findById(bookId)?.finished
        val tagList = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }
        bookOverrideDao.upsert(
            BookOverrideEntity(
                bookId = bookId,
                title = title.trim().ifBlank { null },
                author = author.trim().ifBlank { null },
                series = series.trim().ifBlank { null },
                seriesIndex = seriesIndex.trim().toIntOrNull(),
                genre = genre.trim().ifBlank { null },
                tags = tagList.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                finished = finished,
                hidden = hidden,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        homerSync.sync()
    }

    /**
     * Reverts a book to pure detection. Stored as an all-null "cleared" override (not a row
     * delete) with a fresh timestamp, so the reset propagates via last-write-wins instead of
     * being resurrected on the next pull.
     */
    suspend fun clearOverride(bookId: String) {
        bookOverrideDao.upsert(blank(bookId))
        homerSync.sync()
    }

    /**
     * Applies a series-level edit (name + author) to every member book, preserving each book's
     * own title/index/genre/tags/finished/hidden. Blank reverts that field to detection.
     */
    suspend fun saveSeriesOverride(bookIds: List<String>, series: String, author: String) {
        val now = System.currentTimeMillis()
        val s = series.trim().ifBlank { null }
        val a = author.trim().ifBlank { null }
        for (id in bookIds) {
            val existing = bookOverrideDao.findById(id)
            bookOverrideDao.upsert(
                existing?.copy(series = s, author = a, updatedAt = now)
                    ?: blank(id).copy(series = s, author = a),
            )
        }
        homerSync.sync()
    }

    /** Quick hide/show, preserving any existing metadata override. */
    suspend fun setHidden(bookId: String, hidden: Boolean) {
        val existing = bookOverrideDao.findById(bookId)
        bookOverrideDao.upsert(
            existing?.copy(hidden = hidden, updatedAt = System.currentTimeMillis())
                ?: blank(bookId).copy(hidden = hidden),
        )
        homerSync.sync()
    }

    /**
     * Mark/unmark finished (preserving other override fields). [finished] = true forces finished,
     * false forces not-finished, null reverts to auto.
     */
    suspend fun setFinished(bookId: String, finished: Boolean?) {
        val existing = bookOverrideDao.findById(bookId)
        bookOverrideDao.upsert(
            existing?.copy(finished = finished, updatedAt = System.currentTimeMillis())
                ?: blank(bookId).copy(finished = finished),
        )
        homerSync.sync()
    }

    /** Copies a user-picked image into the cover cache and sets it as the book's custom cover. */
    suspend fun setCustomCover(bookId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext
        val path = coverCache.writeCustom(bookId, bytes, System.currentTimeMillis())
        bookDao.updateCustomCover(bookId, path)
    }

    /** Clears a custom cover, reverting to detected/extracted/online art. */
    suspend fun clearCustomCover(bookId: String) = bookDao.updateCustomCover(bookId, null)

    /** An empty override row (a "cleared" tombstone) with a fresh timestamp. */
    private fun blank(bookId: String) = BookOverrideEntity(
        bookId = bookId,
        title = null,
        author = null,
        series = null,
        seriesIndex = null,
        hidden = false,
        updatedAt = System.currentTimeMillis(),
    )
}
