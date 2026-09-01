package com.geozelot.homer.data.library

import android.content.Context
import android.net.Uri
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.metadata.BookLanguage
import com.geozelot.homer.data.metadata.CoverCache
import com.geozelot.homer.data.sync.facet.LibraryIndexRepository
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
 * change out via [HomerSyncRepository]. Corrections to the fields the shared library index
 * carries (title, author, series, index, genre) are also published to the library root via
 * [LibraryIndexRepository.publishEdits], so a household reading the same folder sees the fix
 * instead of each member re-typing it — when the shared index is on and this device may write
 * there. The hidden flag and custom covers stay device-local.
 *
 * All methods are `suspend` — callers run them in their own scope.
 */
@Singleton
class BookEditor @Inject constructor(
    private val bookOverrideDao: BookOverrideDao,
    private val bookDao: BookDao,
    private val coverCache: CoverCache,
    private val homerSync: HomerSyncRepository,
    private val libraryIndex: LibraryIndexRepository,
    @ApplicationContext private val context: Context,
) {
    /**
     * Saves metadata corrections + the hidden flag.
     *
     * ## A field becomes an override only when it DIFFERS from detection
     *
     * The dialog prefills from the *effective* book — detection with any existing correction already
     * applied — so the values it hands back are, for every field the user did not touch, exactly what
     * detection says. Writing them all as corrections regardless is what the version before this did,
     * and it quietly pinned the book: once any field was an override, no path template and no shared
     * structure update could ever move it again. Opening the dialog on a book and pressing Save
     * without typing anything was enough to freeze it for good.
     *
     * That is the likeliest explanation for "the template preview is right but the library is wrong"
     * on a folder that had been hand-edited at some point — the template's own output had been frozen
     * as a correction, and a later template change could not touch it.
     *
     * So each field is compared against the detected value and kept only if it says something new.
     * Blank still means "revert to detection", which is now the same code path as "type what
     * detection already says".
     *
     * The comparison is case-SENSITIVE on purpose: fixing the capitalisation of a title is a real
     * correction, and treating it as a no-op would make it impossible to make.
     */
    suspend fun saveOverride(
        bookId: String,
        title: String,
        author: String,
        series: String,
        seriesIndex: String,
        collection: String,
        collectionIndex: String,
        genre: String,
        language: String,
        tags: String,
        hidden: Boolean,
        downloadOnPlay: Boolean?,
    ) {
        val existing = bookOverrideDao.findById(bookId)
        // The legacy `finished` flag (no longer set from the UI — "completed" now resets progress
        // instead) is preserved as-is so an edit never clobbers it.
        val finished = existing?.finished
        val detected = bookDao.findById(bookId)
        val tagList = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }

        /** [typed], unless it is only repeating what detection already says. */
        fun correction(typed: String, detectedValue: String?): String? =
            typed.trim().ifBlank { null }?.takeUnless { it == detectedValue?.trim() }

        val correctedSeries = correction(series, detected?.series)
        val correctedCollection = correction(collection, detected?.collection)
        val row = BookOverrideEntity(
            bookId = bookId,
            title = correction(title, detected?.title),
            author = correction(author, detected?.author),
            series = correctedSeries,
            seriesIndex = seriesIndex.trim().toIntOrNull()?.takeUnless { it == detected?.seriesIndex },
            collection = correctedCollection,
            collectionIndex = collectionIndex.trim().toIntOrNull()?.takeUnless { it == detected?.collectionIndex },
            // Several genres, comma-separated on the way in and newline-delimited in the column —
            // the same shape `tags` has always had. Compared as the ENCODED value so that adding a
            // second genre, or reordering them (the first is the one the shelf uses), counts as a
            // correction while retyping the same list does not.
            genre = genresFromInput(genre)?.takeUnless { it == detected?.genre },
            // Normalised on both sides, or a template capturing "German" and a user typing "de"
            // would read as a disagreement and be stored as a correction saying the same thing.
            language = (BookLanguage.normalise(language) ?: language.trim().ifBlank { null })
                ?.takeUnless { it == detected?.language },
            // Tags exist only as corrections — detection never produces one — so there is nothing
            // to compare them against.
            tags = tagList.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            finished = finished,
            downloadOnPlay = downloadOnPlay,
            hidden = hidden,
            updatedAt = System.currentTimeMillis(),
        ).let { candidate ->
            // Against the EFFECTIVE series, not the correction's own: a book can be corrected into a
            // collection while its series still comes from the folder tree, and comparing only the
            // two override fields would miss exactly that case.
            if (redundantCollection(correctedSeries ?: detected?.series, correctedCollection)) {
                candidate.copy(collection = null, collectionIndex = null)
            } else {
                candidate
            }
        }

        // Nothing corrected, nothing personal, and nothing to retract: drop the row instead of
        // leaving a freshly-stamped tombstone. A tombstone here would be read as a local edit newer
        // than any incoming correction (`FacetMapping.overrideEntity`) and would shield the book from
        // other devices' fixes for ever — which is the pinning this method exists to stop.
        val retracting = existing?.hasMetadataEdit() == true
        if (!row.hasMetadataEdit() && !retracting && !hidden && downloadOnPlay == null && finished == null) {
            bookOverrideDao.deleteById(bookId)
        } else {
            bookOverrideDao.upsert(row)
        }
        homerSync.sync(force = true)
        libraryIndex.publishEdits()
    }

    /**
     * Reverts a book to pure detection. Stored as an all-null "cleared" override (not a row
     * delete) with a fresh timestamp, so the reset propagates via last-write-wins instead of
     * being resurrected on the next pull.
     */
    suspend fun clearOverride(bookId: String) {
        bookOverrideDao.upsert(blank(bookId))
        homerSync.sync(force = true)
        libraryIndex.publishEdits()
    }

    /**
     * Applies a shelf-level edit — the name, the author, the genre — to every book on that shelf.
     *
     * Each book keeps its own title, index, tags, finished flag and hidden flag. Blank reverts that
     * field to detection.
     *
     * Genre belongs here because it is a property of the work, not of one volume: a series is one
     * genre, and fixing it a book at a time on a twenty-volume series was the tedious way to do
     * something that is true of all of them.
     *
     * ## [namesCollection] is the whole point of this signature
     *
     * A shelf card is drawn for a series AND for a collection, and the edit dialog is reachable from
     * both. The version before this had one field labelled "Series" that always wrote the `series`
     * override — so editing a COLLECTION wrote the collection's name into every member's series.
     * Editing Discworld set `series = "Discworld"` on all forty-one books, destroying Rincewind, the
     * Watch and the witches in a single action, and the shelf redrew as a collection holding one
     * sub-series named after itself. It looked exactly like the edit having been reverted, which is
     * how it went unnoticed.
     *
     * So the caller has to say which level it is naming, and there is no default: getting it wrong
     * silently flattens a hierarchy, and that is not a mistake a default should be able to make.
     */
    suspend fun saveShelfOverride(
        bookIds: List<String>,
        name: String,
        author: String,
        genre: String,
        /** True when [name] names the COLLECTION; false when it names the series. */
        namesCollection: Boolean,
        /**
         * The parent grouping a plain series belongs to. Ignored when [namesCollection] — there,
         * [name] IS the collection.
         *
         * Set at shelf level rather than per book because that is the shape of the decision:
         * "Rincewind belongs to Discworld" is one thing somebody knows, and applying it a book at a
         * time would be the same value typed forty-one times with forty-one chances to type it
         * differently.
         */
        collection: String = "",
    ) {
        val now = System.currentTimeMillis()
        val n = name.trim().ifBlank { null }
        val a = author.trim().ifBlank { null }
        val g = genresFromInput(genre)
        val c = if (namesCollection) n else collection.trim().ifBlank { null }
        for (id in bookIds) {
            val existing = bookOverrideDao.findById(id)
            val base = existing ?: blank(id)
            // Untouched when the name belongs to the collection: each book keeps whichever thread it
            // is on, which is the thing the collection is a parent OF.
            val series = if (namesCollection) base.series else n
            // Compared against the EFFECTIVE series, since a book's thread usually comes from the
            // folder tree rather than from a correction.
            val effectiveSeries = series ?: bookDao.findById(id)?.series
            val collectionFor = c?.takeUnless { redundantCollection(effectiveSeries, it) }
            bookOverrideDao.upsert(
                base.copy(
                    series = series,
                    author = a,
                    genre = g,
                    collection = collectionFor,
                    // A position in a collection that was just dropped is a number about nothing.
                    collectionIndex = if (collectionFor == null) null else base.collectionIndex,
                    updatedAt = now,
                ),
            )
        }
        homerSync.sync(force = true)
        libraryIndex.publishEdits()
    }

    /** Quick hide/show, preserving any existing metadata override. */
    suspend fun setHidden(bookId: String, hidden: Boolean) {
        val existing = bookOverrideDao.findById(bookId)
        bookOverrideDao.upsert(
            existing?.copy(hidden = hidden, updatedAt = System.currentTimeMillis())
                ?: blank(bookId).copy(hidden = hidden),
        )
        homerSync.sync(force = true)
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
