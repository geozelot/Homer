package com.geozelot.homer.ui.home

import android.net.Uri
import android.util.Log
import com.geozelot.homer.data.library.BookEditor
import com.geozelot.homer.data.library.TemplateApplier
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.sync.facet.LibraryIndexRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

// ── Correcting a book ─────────────────────────────────────────────────────────

/**
 * Everything [HomeViewModel] does to CHANGE a book: metadata overrides, covers, the hidden flag,
 * and the path templates that re-derive whole folders at once.
 *
 * A plain collaborator, like [StorageCoordinator]: the template draft is state about editing, not
 * about the screen, and holding it here keeps the ViewModel to wiring. The flows exposed are cold —
 * the ViewModel `stateIn`s them, because the scope is its to own.
 */
class BookEditCoordinator @Inject constructor(
    private val bookEditor: BookEditor,
    private val librarySettings: LibrarySettings,
    private val templateApplier: TemplateApplier,
    private val libraryIndex: LibraryIndexRepository,
) {
    // ── overrides & covers ───────────────────────────────────────────────────────────────────

    /** Saves metadata corrections + hidden flag; blank fields revert to detection. */
    suspend fun saveOverride(
        bookId: String,
        title: String,
        author: String,
        series: String,
        seriesIndex: String,
        collection: String,
        collectionIndex: String,
        genres: List<String>,
        language: String,
        tags: String,
        hidden: Boolean,
        downloadOnPlay: Boolean?,
    ) {
        bookEditor.saveOverride(
            bookId, title, author, series, seriesIndex, collection, collectionIndex,
            genres, language, tags, hidden, downloadOnPlay,
        )
    }

    /**
     * Applies a shelf-level edit (name, author, genre) to every member book (see
     * [BookEditor.saveShelfOverride]). Members re-group under the new name; the change syncs like
     * any override.
     */
    suspend fun saveShelfOverride(
        bookIds: List<String>,
        name: String,
        author: String,
        genres: List<String>,
        namesCollection: Boolean,
        collection: String,
    ) = bookEditor.saveShelfOverride(bookIds, name, author, genres, namesCollection, collection)

    /** Reverts a book to pure detection (see [BookEditor.clearOverride]). */
    suspend fun clearOverride(bookId: String) = bookEditor.clearOverride(bookId)

    /** Copies a user-picked image into the cover cache and sets it as the book's custom cover. */
    suspend fun setCustomCover(bookId: String, uri: Uri) = bookEditor.setCustomCover(bookId, uri)

    /** Clears a custom cover, reverting to detected/extracted/online art. */
    suspend fun clearCustomCover(bookId: String) = bookEditor.clearCustomCover(bookId)

    /** Quick hide/show from the context menu, preserving any existing metadata override. */
    suspend fun setHidden(bookId: String, hidden: Boolean) = bookEditor.setHidden(bookId, hidden)

    // ── path templates ───────────────────────────────────────────────────────────────────────

    /**
     * The templates being EDITED, which is not the same as the ones in force.
     *
     * A draft, so the preview can show what a half-written pattern would do without that pattern
     * being applied to the library the moment a character lands in the field. Seeded from the stored
     * list the first time it is read.
     */
    private val _templateDraft = MutableStateFlow<List<String>?>(null)

    val templateDraft: Flow<List<String>> =
        combine(_templateDraft, librarySettings.pathTemplates) { draft, stored -> draft ?: stored }

    /** Whether the draft differs from what is stored — what Apply is enabled by. */
    val templateDraftDirty: Flow<Boolean> =
        combine(_templateDraft, librarySettings.pathTemplates) { draft, stored ->
            draft != null && draft != stored
        }

    /** Which draft row is being edited, so the preview can show the books IT is about. */
    private val _templateFocus = MutableStateFlow<Int?>(null)
    val templateFocus: StateFlow<Int?> = _templateFocus.asStateFlow()

    fun focusTemplateRow(index: Int?) {
        _templateFocus.value = index
    }

    /**
     * What the draft would make of a sample of the library — changed books first.
     *
     * Recomputed on every keystroke, which is affordable because it reads books already in memory
     * and writes nothing. This is the pass that has to exist before Apply is offered at all: a
     * silent mis-parse across a subtree is far worse than no feature.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val templatePreview: Flow<List<TemplateApplier.Preview>> =
        combine(templateDraft, _templateFocus) { lines, focus -> lines to focus }
            // Settles before it reads. Mapped straight off the draft it ran a full `SELECT * FROM
            // books` and re-parsed every row on each character typed, and the pattern field is
            // exactly where somebody types slowly and watches — so the preview lagged the typing on
            // the libraries big enough to need it.
            .debounce(PREVIEW_DEBOUNCE_MS)
            .mapLatest { (lines, focus) ->
                val scope = focus?.let { lines.getOrNull(it) }
                    ?.takeIf { '\t' in it }
                    ?.substringBefore('\t')
                templateApplier.preview(TemplateApplier.templatesFrom(lines), focus = scope)
            }

    fun setTemplateDraft(lines: List<String>) {
        _templateDraft.value = lines
    }

    /** Throws the draft away, reverting the editor to what is stored. */
    fun discardTemplateDraft() {
        _templateDraft.value = null
    }

    /** The draft as it stands: what has been typed, or what is stored when nothing has. */
    private suspend fun currentDraft(): List<String> =
        _templateDraft.value ?: librarySettings.pathTemplates.first()

    /**
     * Starts a template for the folder [bookId] sits in, seeded with the pattern currently reading
     * it.
     *
     * Authoring a template from nothing is the miserable part of this feature: you have to work out
     * both the scope and the shape before you can see whether either is right. Coming from a book,
     * both are known — the folder is the book's own, and the shape is whichever pattern Homer is
     * already matching, which is by definition the one that needs changing.
     *
     * Prepended, because a narrower scope has to be tried before a broader one, and idempotent: the
     * same seed twice is one row, not two identical ones.
     */
    suspend fun seedTemplateFor(bookId: String, scopeOverride: String? = null) {
        // A shelf passes the folder its books share; a single book uses its own.
        val scope = scopeOverride?.trim('/') ?: bookId.trim('/').substringBeforeLast('/', "")
        // The pattern actually IN FORCE for this book, which is the one that needs changing —
        // the user's own if one matches, and only then the conventional default. Seeding from
        // DEFAULTS regardless would hand somebody who already has a pattern for this folder a
        // different line to edit, and adding it would leave two competing rules for one folder.
        val active = templateApplier.activeTemplates()
        val shape = active.firstOrNull { it.parse(bookId) != null }?.template?.source
            ?: "{author}/{title}"
        val seeded = if (scope.isBlank()) shape else "$scope\t$shape"
        val existing = currentDraft()
        _templateDraft.value = if (seeded in existing) existing else listOf(seeded) + existing
        Log.i(TAG_STORAGE, "seeded a template for '$scope' from '$shape'")
    }

    /**
     * Stores the draft, re-derives every book under it, and shares both halves.
     *
     * BOTH halves, because applying a template changes two different things that live in two
     * different facets: the patterns themselves ride `corrections.json`, and the fields they
     * re-derived ride `structure`. Publishing only the patterns would leave every other device to
     * work the books out again for itself — and a reader device never re-derives at all, so for the
     * people the library is shared with the fix would simply not arrive.
     */
    suspend fun applyTemplates() {
        val lines = currentDraft()
        librarySettings.setPathTemplates(lines)
        val result = templateApplier.applyAll(TemplateApplier.templatesFrom(lines))
        _templateDraft.value = null
        // The patterns, coalesced like any other edit.
        libraryIndex.publishEdits()
        // …and the re-derived books, but only if any actually changed: `push()` uploads the
        // whole structure facet, which is not a thing to do because somebody opened the editor
        // and pressed Apply on an unchanged pattern.
        if (result.changed > 0) libraryIndex.push()
    }

    private companion object {
        const val TAG_STORAGE = "HomerStore"

        /** How long the template editor settles before the preview reads the library. */
        const val PREVIEW_DEBOUNCE_MS = 250L
    }
}
