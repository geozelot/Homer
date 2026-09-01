package com.geozelot.homer.data.library

import android.util.Log
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.EditFields
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
    suspend fun preview(
        templates: List<ScopedTemplate>,
        limit: Int = PREVIEW_SAMPLE,
        /**
         * Narrows the sample to one folder — the scope of the pattern being edited.
         *
         * Writing a pattern for a folder while the examples come from the whole library is writing
         * blind: the rows that would tell you whether it is right are the ones it applies to, and
         * they may not be in a library-wide sample at all.
         */
        focus: String? = null,
    ): List<Preview> {
        val all = bookDao.getAll()
        val scope = focus?.trim('/').orEmpty()
        val books = if (scope.isEmpty()) {
            all
        } else {
            // Falls back to the whole library rather than showing nothing: a scope being typed is
            // half-written most of the time, and an empty preview mid-word reads as a broken rule.
            all.filter { it.id.startsWith("$scope/", ignoreCase = true) || it.id.equals(scope, true) }
                .ifEmpty { all }
        }
        val previews = books.map { Preview(it.id, it, apply(it, templates)) }
        val changed = previews.filterNot { it.before.sameFieldsAs(it.after) }
        // Changed books first, then unchanged ones to fill the sample out — and SPREAD across the
        // library in both halves. Taking them in table order gave five examples from one series,
        // because a template that affects one folder affects all of it and those rows sit together:
        // five rows about the same book in five different orders, saying nothing about the rest of
        // the library the pattern also has to be right about.
        return (spread(changed, limit) + spread(previews - changed.toSet(), limit)).take(limit)
    }

    /**
     * Picks from [rows] one folder at a time, so a sample of five comes from five different places.
     *
     * Round-robin over the parent folders rather than a random sample: deterministic, so the preview
     * does not reshuffle under the reader on every keystroke, and it guarantees breadth rather than
     * merely making narrowness unlikely.
     */
    private fun spread(rows: List<Preview>, limit: Int): List<Preview> {
        if (rows.size <= limit) return rows
        val byFolder = rows.groupBy { it.id.trim('/').substringBeforeLast('/', "") }
        val queues = byFolder.values.map { it.toMutableList() }
        val out = mutableListOf<Preview>()
        while (out.size < limit && queues.any { it.isNotEmpty() }) {
            for (queue in queues) {
                if (out.size == limit) break
                if (queue.isNotEmpty()) out += queue.removeAt(0)
            }
        }
        return out
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
        val updated = planWrites(books, active, System.currentTimeMillis())
        // Chunked rather than written in one statement: a library can hold thousands of books and
        // a single transaction over all of them is a long lock on the table the shelf reads from.
        updated.chunked(WRITE_CHUNK).forEach { bookDao.upsert(it) }
        Log.i(TAG, "templates applied: ${updated.size} of ${books.size} book(s) changed")
        return Result(changed = updated.size, examined = books.size)
    }

    companion object {
        private const val TAG = "HomerTemplate"
        private const val WRITE_CHUNK = 200

        /** How many example books the editor shows. Five fits on screen beside the patterns. */
        const val PREVIEW_SAMPLE = 5

        /**
         * Which books a re-derive would write, and what they would become.
         *
         * Pure, and separated out because of the one line in it that matters: `updatedAt` moves on
         * the books that CHANGED, and only those.
         *
         * The structure facet merges on that timestamp. Leaving it alone meant a template fix lost
         * every tie against another device's stale copy and simply never arrived — invisibly, since
         * the device that applied it saw the right answer all along. A maintainer re-derives on its
         * next crawl and recovers; a reader never crawls, so for the people this library is shared
         * with it would have stayed wrong for ever.
         *
         * Unchanged books are left out entirely rather than rewritten with a new stamp, or a library
         * of three hundred would republish in full every time somebody adjusted one pattern.
         */
        fun planWrites(
            books: List<BookEntity>,
            templates: List<ScopedTemplate>,
            now: Long,
        ): List<BookEntity> = books.mapNotNull { book ->
            apply(book, templates).takeUnless { book.sameFieldsAs(it) }?.copy(updatedAt = now)
        }

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
            // Normalised even when no template matches, so a re-derive HEALS a book already carrying
            // `collection == series` rather than only refusing to create a new one.
            val parsed = ScopedTemplate.parseFirst(book.id, templates)
                ?: return book.withoutRedundantCollection()
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
            // A template that captures the same folder name for both — easy to do with `{**}` — is
            // describing one shelf, not a hierarchy. See `redundantCollection`.
            ).withoutRedundantCollection()
        }
    }
}

/**
 * Whether the fields a template can write are identical — the test for "this changed nothing".
 *
 * The field list lives in [EditFields], which is also where the correction-side check and the two
 * SQL predicates get theirs. Four separate copies of it drifted before that existed.
 */
internal fun BookEntity.sameFieldsAs(other: BookEntity): Boolean =
    EditFields.sameDetected(this, other)
