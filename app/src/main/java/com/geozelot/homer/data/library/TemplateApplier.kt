package com.geozelot.homer.data.library

import android.util.Log
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.metadata.BookLanguage
import com.geozelot.homer.data.settings.LibrarySettings
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-reads every book's fields out of its path, under a given set of templates.
 *
 * Two things about this are easy to get wrong and are therefore the whole point of the class.
 *
 * **It OVERWRITES.** The metadata enricher fills only nulls — `needsLanguage = book.language ==
 * null` — which is right for a lazy background pass that must not undo a correction. Applied here
 * that rule would make the feature appear to do nothing at all: a book already carrying a
 * wrongly-parsed author has a non-null author, so a fill-if-null pass leaves the wrong value
 * exactly where it is. Somebody who has just written a template and pressed Apply is asking for the
 * old answer to be replaced.
 *
 * **It writes the DETECTED layer only.** A per-book correction lives on `book_overrides` and is
 * applied over the top of whatever this writes, so a template can never overrule a person who has
 * edited that particular book. The precedence — correction, then template, then tag, then the
 * conventional default — falls out of that rather than needing to be enforced here.
 *
 * Book ids are never touched. An id is the folder path, the fetch URL and the key every shared
 * facet uses; a template changes what a book is CALLED, not where it is.
 */
@Singleton
class TemplateApplier @Inject constructor(
    private val bookDao: BookDao,
    private val librarySettings: LibrarySettings,
) {
    /** What a re-derive would do, or did: the books it changed, out of the books it looked at. */
    data class Result(val changed: Int, val examined: Int)

    /** One book's before-and-after, for the preview. */
    data class Preview(val id: String, val before: BookEntity, val after: BookEntity)

    /** The user's templates, in order, ahead of the conventional defaults. */
    suspend fun activeTemplates(): List<ScopedTemplate> = templatesFrom(librarySettings.pathTemplates.first())

    /**
     * What [templates] would make of the first [limit] books, changed ones first.
     *
     * Changed first because that is what somebody is checking: a preview whose visible rows all say
     * "no change" hides the one row that says the template has mangled the title. Nothing is
     * written — this is the pass that has to run before Apply is worth offering, since applying
     * rewrites metadata across a whole library in one action.
     */
    suspend fun preview(templates: List<ScopedTemplate>, limit: Int = 12): List<Preview> {
        val books = bookDao.getAll()
        val previews = books.map { Preview(it.id, it, apply(it, templates)) }
        return previews.sortedBy { it.before.sameFieldsAs(it.after) }.take(limit)
    }

    /**
     * Applies the stored templates to every book, writing the ones that come out different.
     *
     * Only the differences are written: a library of three hundred books where a template changed
     * two of them should be two row updates, not three hundred, or every book's `updatedAt` moves
     * and the shared index republishes the lot.
     */
    suspend fun applyAll(templates: List<ScopedTemplate> = emptyList()): Result {
        val active = templates.ifEmpty { activeTemplates() }
        val books = bookDao.getAll()
        val updated = books.mapNotNull { book ->
            apply(book, active).takeUnless { book.sameFieldsAs(it) }
        }
        // Chunked rather than written in one statement: a library can hold thousands of books and
        // a single transaction over all of them is a long lock on the table the shelf reads from.
        updated.chunked(WRITE_CHUNK).forEach { bookDao.upsert(it) }
        Log.i(TAG, "templates applied: ${updated.size} of ${books.size} book(s) changed")
        return Result(changed = updated.size, examined = books.size)
    }

    companion object {
        private const val TAG = "HomerTemplate"
        private const val WRITE_CHUNK = 200

        /** [raw] lines decoded, dropping the ones that do not compile — then the defaults behind them. */
        fun templatesFrom(raw: List<String>): List<ScopedTemplate> =
            raw.mapNotNull { ScopedTemplate.decode(it) } + ScopedTemplate.DEFAULTS

        /**
         * One book, re-read.
         *
         * A field the matching template does not mention is left alone rather than nulled: a template
         * that says nothing about the genre is not a claim that the book has no genre, and treating it
         * as one would wipe every genre in the library the first time somebody wrote a template about
         * folder names. The title falls back to the book's own, never to nothing — a book with no title
         * is unreachable on the shelf.
         */
        fun apply(book: BookEntity, templates: List<ScopedTemplate>): BookEntity {
            val parsed = ScopedTemplate.parseFirst(book.id, templates) ?: return book
            return book.copy(
                title = parsed[TemplateField.TITLE] ?: book.title,
                author = parsed[TemplateField.AUTHOR] ?: book.author,
                series = parsed[TemplateField.SERIES] ?: book.series,
                seriesIndex = parsed[TemplateField.INDEX]?.toIntOrNull() ?: book.seriesIndex,
                collection = parsed[TemplateField.COLLECTION] ?: book.collection,
                collectionIndex = parsed[TemplateField.COLLECTION_INDEX]?.toIntOrNull() ?: book.collectionIndex,
                genre = parsed[TemplateField.GENRE] ?: book.genre,
                // Normalised like every other language Homer stores, so a template capturing "German"
                // and one capturing "de" produce the same shelf.
                language = parsed[TemplateField.LANGUAGE]?.let { BookLanguage.normalise(it) } ?: book.language,
            )
        }
    }
}

/** Whether the fields a template can write are identical — the test for "this changed nothing". */
internal fun BookEntity.sameFieldsAs(other: BookEntity): Boolean =
    title == other.title &&
        author == other.author &&
        series == other.series &&
        seriesIndex == other.seriesIndex &&
        collection == other.collection &&
        collectionIndex == other.collectionIndex &&
        genre == other.genre &&
        language == other.language
