package com.geozelot.homer.data.sync.facet

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.withTransaction
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.db.HomerDatabase
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.ChapterDao
import com.geozelot.homer.data.library.ScopedTemplate
import com.geozelot.homer.data.metadata.CoverCache
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.DeviceIdentity
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * The shared library index: pulling the three facets into the database, and pushing this device's
 * view back out.
 *
 * Replaces the single-catalog repository. The differences that matter are all in what each
 * direction is allowed to do — a pull may now delete a book, but only on a complete crawl's word;
 * a push writes only the facets that actually changed, so a title correction no longer rewrites
 * the whole library.
 */
@Singleton
class LibraryIndexRepository @Inject constructor(
    private val store: FacetStore,
    private val db: HomerDatabase,
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val chapterDao: ChapterDao,
    private val bookOverrideDao: BookOverrideDao,
    private val bookmarkDao: BookmarkDao,
    private val credentialStore: CredentialStore,
    private val librarySettings: LibrarySettings,
    private val networkMonitor: NetworkMonitor,
    private val deviceIdentity: DeviceIdentity,
    private val webDavClient: WebDavClient,
    private val coverCache: CoverCache,
    private val json: Json,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "unhandled index error", e) },
    )
    private var editPublishJob: Job? = null

    // The last facets actually seen. FacetStore caches an ETag per file and answers Unchanged when
    // it still matches — which carries no body, so the value has to be kept here. Both caches are
    // in-memory on the same singleton, so a fresh process gets a full read and neither can be stale.
    private var lastStructure: StructureFacet? = null
    private var lastDerived: DerivedFacet? = null
    private var lastCorrections: CorrectionsFacet? = null

    @Volatile
    private var editsPending = false

    /** The newest full-crawl marker seen in a shared index, from whichever device ran it. */
    private val remoteCrawl = MutableStateFlow<CrawlMarker?>(null)

    private val _activity = MutableStateFlow(IndexActivity.IDLE)

    /**
     * What this repository is doing, for the UI to say so — a pull or a push is otherwise silent,
     * and on a slow link a first pull leaves an empty shelf with no explanation.
     */
    val activity: StateFlow<IndexActivity> = _activity.asStateFlow()

    /**
     * When the library was last crawled end to end, and by whom.
     *
     * Worth showing because it is what authorises deletion: a device prunes a book only on the word
     * of a complete crawl that post-dates it, so "last full crawl, 3 days ago, from Pixel 7" is the
     * line that explains why books can or cannot disappear.
     *
     * A tie goes to this device — the usual reason the two agree is that the remote marker IS this
     * device's own, published earlier, and "from this device" is the truer caption.
     */
    val lastFullCrawl: Flow<CrawlSummary?> =
        combine(librarySettings.lastFullCrawlAt, remoteCrawl) { localAt, remote ->
            when {
                localAt > 0 && localAt >= (remote?.at ?: Long.MIN_VALUE) ->
                    CrawlSummary(at = localAt, byThisDevice = true, deviceName = deviceIdentity.label)
                remote != null -> CrawlSummary(at = remote.at, byThisDevice = false, deviceName = remote.byName)
                else -> null
            }
        }

    init {
        // A different library root means different files behind the same names. Everything cached
        // here — the three last-seen facets, the crawl marker, and FacetStore's ETag per file — is
        // about the library that WAS configured, and a conditional read for `structure.json` would
        // otherwise carry the old library's ETag to the new one's file. Watching the setting rather
        // than asking every caller to remember is the only version of this that stays true: the
        // root is changed by a scan, by adopting a library, and by opening a share link.
        scope.launch {
            librarySettings.libraryRoot.distinctUntilChanged().drop(1).collect { forgetLibrary() }
        }

        // Backgrounding is the natural moment to ship corrections: the user has stopped editing.
        // addObserver must run on the main thread, hence the explicit dispatcher on an IO scope.
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    editPublishJob?.cancel()
                    editPublishJob = scope.launch { publishPendingEdits() }
                }
            })
        }
    }

    /**
     * Notes that the user corrected a book, to be shared so a title fixed here is what everyone
     * reading that folder sees rather than a private note.
     *
     * Coalesced rather than sent per edit — a burst of corrections should be one write. It is only
     * a short delay now: a correction touches `corrections.json`, kilobytes, where the single
     * catalog rewrote the whole library and made every edit genuinely expensive.
     */
    fun publishEdits() {
        editsPending = true
        editPublishJob?.cancel()
        editPublishJob = scope.launch {
            delay(EDIT_PUBLISH_IDLE_MS)
            publishPendingEdits()
        }
    }

    /**
     * Publishes coalesced corrections. Offline, the pending flag stays set so the next trigger
     * retries — and the flag lives in memory only, which is why the `CORRECTIONS` index pass
     * exists as a durable way to ask for the same thing.
     */
    private suspend fun publishPendingEdits() {
        if (!editsPending) return
        if (!pushCorrections()) return
        editsPending = false
    }

    /**
     * Publishes the shared half of every correction, and only that — a metadata edit has no
     * business rewriting the structure or the measurements.
     *
     * Requires the shared index to be switched on: that toggle is the user's consent to write to
     * the shared folder. Returns false only when the attempt could not be made at all (offline, or
     * a share this device may not write), so a caller holding a pending flag knows to keep it;
     * sharing being off is a settled answer, not a deferral.
     */
    suspend fun pushCorrections(): Boolean {
        if (!librarySettings.sharedCatalogEnabled.first()) return true
        if (!networkMonitor.isOnline() || !canPublish()) return false
        val deviceId = deviceIdentity.id()
        // Stamped from BEFORE the local state is read, not after the upload. An edit made while the
        // request is in flight would otherwise be marked as published by an upload that never
        // contained it, and would sit there unshared with nothing saying so.
        val capturedAt = System.currentTimeMillis()
        // Cuts join the overrides here: a book can carry a hand-made chapter list and no corrected
        // field at all, so iterating the override table alone would publish neither.
        val cuts = bookmarkDao.allCuts().groupBy { it.bookId }
        val overrides = bookOverrideDao.getAll().associateBy { it.bookId }
        val local = CorrectionsFacet(
            books = (overrides.keys + cuts.keys).mapNotNull { id ->
                FacetMapping.correctionOf(overrides[id], cuts[id].orEmpty(), deviceId)?.let { id to it }
            }.toMap(),
            templates = localTemplateRules(deviceId),
        )
        val result = store.save(LibraryFacets.CORRECTIONS_FILE, CorrectionsFacet.serializer()) { remote ->
            FacetMerge.corrections(local, remote.valueOr(CorrectionsFacet()))
        }
        report(LibraryFacets.CORRECTIONS_FILE, result)
        // Both of these mean the server now holds what this device knows: one wrote it, the other
        // found it already there. The rest are failures or deferrals and must leave the mark alone,
        // or the UI would claim everything is shared while it sits in a retry queue.
        if (result is FacetStore.SaveResult.Written || result is FacetStore.SaveResult.AlreadyCurrent) {
            librarySettings.setCorrectionsPublishedAt(capturedAt)
        }
        return true
    }

    /**
     * Whether this device may write the shared index.
     *
     * A read-only share reads all three facets exactly like an account does and keeps whatever it
     * works out for itself locally — the only thing it cannot do is publish. That is the whole
     * difference between the two, which is why there is no separate share code path.
     */
    suspend fun canPublish(): Boolean {
        val library = credentialStore.awaitCredentials() ?: return false
        return if (library.kind == WebDavKind.SHARE) librarySettings.libraryWritable.first() else true
    }

    /** Whether a shared index is present at all, without downloading it. */
    suspend fun exists(): Boolean {
        if (credentialStore.awaitCredentials() == null || !networkMonitor.isOnline()) return false
        return runCatching { webDavClient.exists(pathOf(LibraryFacets.STRUCTURE_FILE)) }.getOrDefault(false)
    }

    // ── pulling ──────────────────────────────────────────────────────────────────────────────

    /**
     * Reads the shared index and applies it. Returns whether one was found.
     *
     * Each facet is fetched conditionally and independently, so the common case — nothing changed
     * — costs three 304s of a few hundred bytes rather than a multi-megabyte download.
     */
    suspend fun pull(): Boolean {
        if (credentialStore.awaitCredentials() == null || !networkMonitor.isOnline()) return false
        _activity.value = IndexActivity.READING
        return try {
            // Every facet is version-checked, not just the structure: a facet from another schema
            // is treated as absent, which rebuilds it rather than merging something whose fields
            // mean something else.
            val structureRead = store.load(LibraryFacets.STRUCTURE_FILE, StructureFacet.serializer())
                .ofCurrentSchema(LibraryFacets.STRUCTURE_FILE) { it.version }
            val derivedRead = store.load(LibraryFacets.DERIVED_FILE, DerivedFacet.serializer())
                .ofCurrentSchema(LibraryFacets.DERIVED_FILE) { it.version }
            val correctionsRead = store.load(LibraryFacets.CORRECTIONS_FILE, CorrectionsFacet.serializer())
                .ofCurrentSchema(LibraryFacets.CORRECTIONS_FILE) { it.version }

            // A 304 means our copy IS the remote one — present and current. Reading that as "no
            // shared index" told the settings screen the library had vanished on every open after
            // the first. The value comes from the copy that produced the cached ETag; both live
            // as long as this process does, so one cannot be set without the other.
            val structure = structureRead.resolve(lastStructure) ?: return false
            val derived = derivedRead.resolve(lastDerived) ?: DerivedFacet()
            val corrections = correctionsRead.resolve(lastCorrections) ?: CorrectionsFacet()
            lastStructure = structure
            lastDerived = derived
            lastCorrections = corrections
            noteCrawl(structure)

            // Nothing moved anywhere: no point walking several hundred books to write back what
            // is already stored.
            val anyChanged = structureRead is FacetStore.Load.Present ||
                derivedRead is FacetStore.Load.Present ||
                correctionsRead is FacetStore.Load.Present
            // Before applying: a template the index carries changes what the fields MEAN, so
            // adopting it after would parse this pull under the old rules and the next one under
            // the new.
            adoptTemplates(corrections)
            if (anyChanged) apply(structure, derived, corrections)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "pull failed", e)
            false
        } finally {
            _activity.value = IndexActivity.IDLE
        }
    }

    /** Present gives its value; Unchanged reuses the copy that produced the cached ETag. */
    private fun <T> FacetStore.Load<T>.resolve(cached: T?): T? = when (this) {
        is FacetStore.Load.Present -> value
        is FacetStore.Load.Unchanged -> cached
        else -> null
    }

    /**
     * Writes the facets into the database.
     *
     * One transaction per book, not one around the loop. Replacing a book's files is a delete
     * followed by an insert, and a crash in between used to leave the book in the library with no
     * files at all; per book also keeps the write lock off the position saves that happen during
     * playback.
     */
    private suspend fun apply(
        structure: StructureFacet,
        derived: DerivedFacet,
        corrections: CorrectionsFacet,
    ) {
        val now = System.currentTimeMillis()
        var applied = 0
        for ((id, book) in structure.books) {
            val existing = bookDao.findById(id)
            val existingFiles = if (existing == null) emptyList() else audioFileDao.findForBook(id)
            val existingOverride = bookOverrideDao.findById(id)
            val d = derived.books[id]
            val entity = FacetMapping.bookEntity(id, book, d, existing, now)
            val files = FacetMapping.fileEntities(id, book, d, existingFiles)
            val override = FacetMapping.overrideEntity(id, corrections.books[id], existingOverride)

            // Most books are untouched by any given change, and rewriting them all meant hundreds
            // of transactions and tens of thousands of row writes for one edited title — taking
            // the write lock away from position saves during playback. Both reads are already in
            // hand, so the comparison is free.
            if (entity == existing && files == existingFiles && override == existingOverride) continue

            db.withTransaction {
                bookDao.upsert(listOf(entity))
                if (files != existingFiles) {
                    audioFileDao.deleteForBook(id)
                    audioFileDao.upsert(files)
                }
                // Only rewrite chapters when the facet actually has some: an absent derived entry
                // is silence, and silence must not erase what this device worked out itself. A
                // correction's cuts count as having some, even where no tag ever did — that is the
                // whole point of cutting a book nothing had chapters for.
                val cut = corrections.books[id]?.chapters?.isNotEmpty() == true
                if (d?.chapterTier != null || cut) {
                    chapterDao.replaceForBook(
                        id,
                        FacetMapping.chapterEntities(id, d, corrections.books[id]),
                    )
                }
                // Never deleted. An absent correction means the shared index has nothing to say
                // about this book, NOT that a local one should go — deleting here destroyed an
                // edit made offline before it had ever been published.
                if (override != null && override != existingOverride) bookOverrideDao.upsert(override)
            }
            applied++
        }
        prune(structure)
        Log.i(TAG, "pulled index: ${structure.books.size} books, $applied applied")
    }

    /**
     * Removes books the shared index has proved are gone.
     *
     * Requires a complete crawl to have happened after the book was last touched. Without that
     * marker nothing is deleted at all, because an index that has never seen the whole tree cannot
     * distinguish a deleted book from one it simply did not visit.
     */
    private suspend fun prune(structure: StructureFacet) {
        val marker = structure.lastFullCrawl ?: return
        val stale = bookDao.getAll()
            .filter { it.id !in structure.books && it.updatedAt <= marker.at }
            .map { it.id }
        if (stale.isEmpty()) return
        Log.i(TAG, "pruning ${stale.size} book(s) removed from the library")
        stale.chunked(SQL_PARAM_CHUNK).forEach { bookDao.deleteByIds(it) }
    }


    // ── pushing ──────────────────────────────────────────────────────────────────────────────

    /** Publishes this device's view, facet by facet, writing only what changed. */
    suspend fun push() {
        if (!canPublish() || credentialStore.awaitCredentials() == null || !networkMonitor.isOnline()) return
        _activity.value = IndexActivity.PUBLISHING
        try {
            val local = buildLocal()
            if (local.structure.books.isEmpty()) {
                // Nothing to say. Publishing an empty index over a real one would be the worst
                // possible outcome of a device that has not scanned yet.
                Log.i(TAG, "not publishing: this device has no books yet")
                return
            }

            report(
                LibraryFacets.STRUCTURE_FILE,
                store.save(LibraryFacets.STRUCTURE_FILE, StructureFacet.serializer()) { remote ->
                    FacetMerge.structure(local.structure, remote.valueOr(StructureFacet()))
                        .also(::noteCrawl)
                },
            )
            var remoteDerived = DerivedFacet()
            report(
                LibraryFacets.DERIVED_FILE,
                store.save(LibraryFacets.DERIVED_FILE, DerivedFacet.serializer()) { remote ->
                    remoteDerived = remote.valueOr(DerivedFacet())
                    FacetMerge.derived(local.derived, remoteDerived)
                },
            )
            report(
                LibraryFacets.CORRECTIONS_FILE,
                store.save(LibraryFacets.CORRECTIONS_FILE, CorrectionsFacet.serializer()) { remote ->
                    val merged = FacetMerge.corrections(local.corrections, remote.valueOr(CorrectionsFacet()))
                    merged.takeIf { it.books.isNotEmpty() || remote is FacetStore.Load.Present }
                },
            )
            uploadNewCovers(local.derived, remoteDerived)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "push failed", e)
        } finally {
            _activity.value = IndexActivity.IDLE
        }
    }

    private data class Local(
        val structure: StructureFacet,
        val derived: DerivedFacet,
        val corrections: CorrectionsFacet,
    )

    private suspend fun buildLocal(): Local {
        val overrides = bookOverrideDao.getAll().associateBy { it.bookId }
        val deviceId = deviceIdentity.id()
        val structure = LinkedHashMap<String, StructureBook>()
        val derived = LinkedHashMap<String, DerivedBook>()
        val corrections = LinkedHashMap<String, BookCorrection>()

        val cuts = bookmarkDao.allCuts().groupBy { it.bookId }
        for (book in bookDao.getAll()) {
            val files = audioFileDao.findForBook(book.id)
            structure[book.id] = FacetMapping.structureOf(book, files)
            FacetMapping.derivedOf(book, files, chapterDao.findForBook(book.id))?.let { derived[book.id] = it }
            FacetMapping.correctionOf(overrides[book.id], cuts[book.id].orEmpty(), deviceId)
                ?.let { corrections[book.id] = it }
        }

        val crawledAt = librarySettings.lastFullCrawlAt.first()
        return Local(
            structure = StructureFacet(
                lastFullCrawl = crawledAt.takeIf { it > 0 }?.let {
                    CrawlMarker(at = it, by = deviceId, byName = deviceIdentity.label)
                },
                books = structure,
            ),
            derived = DerivedFacet(books = derived),
            corrections = CorrectionsFacet(books = corrections, templates = localTemplateRules(deviceId)),
        )
    }

    /** Uploads extracted art to the shared cache so other devices download it instead of re-reading. */
    private suspend fun uploadNewCovers(local: DerivedFacet, remote: DerivedFacet) {
        val coversDir = "${dirOf()}/$COVERS_DIR"
        var dirEnsured = false
        for ((id, book) in local.books) {
            if (!book.hasCachedCover) continue
            // The remote facet already answers this; probing the server per book cost one PROPFIND
            // for every cover in the library on every push. Read outside the save's retry loop, so
            // a lost race cannot make the flags read stale and re-upload everything.
            if (remote.books[id]?.hasCachedCover == true) continue
            val bytes = coverCache.readBytes(id) ?: continue
            try {
                val name = coverCache.coverName(id)
                if (!dirEnsured) {
                    webDavClient.mkcol(coversDir)
                    dirEnsured = true
                }
                webDavClient.putBytes("$coversDir/$name", bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "cover upload failed for $id", e)
            }
        }
    }

    /** Says what became of one facet. A publish that half-succeeds must not look like silence. */
    private fun report(file: String, result: FacetStore.SaveResult) {
        when (result) {
            is FacetStore.SaveResult.Written -> Log.i(TAG, "published $file")
            is FacetStore.SaveResult.AlreadyCurrent -> Log.i(TAG, "$file already current")
            is FacetStore.SaveResult.Declined -> Log.i(TAG, "$file: nothing to publish")
            is FacetStore.SaveResult.Contended -> Log.w(TAG, "$file: lost every write race")
            is FacetStore.SaveResult.Unavailable -> Log.w(TAG, "$file NOT published: ${result.message}")
        }
    }

    /**
     * Drops everything remembered about the library that was configured until now.
     *
     * Both halves have to go together: an ETag without its facet answers `Unchanged` with nothing
     * to resolve it against, and a facet without its ETag is simply refetched.
     */
    private fun forgetLibrary() {
        Log.i(TAG, "library root changed; forgetting the cached index")
        lastStructure = null
        lastDerived = null
        lastCorrections = null
        remoteCrawl.value = null
        store.forgetEtags()
    }

    /**
     * Remembers a structure facet's crawl marker, if it is newer than the one already known.
     *
     * Only ever moves forward. A facet read from a device that has not crawled since would
     * otherwise walk the line backwards, and this is the one line the deletion rule rests on.
     */
    private fun noteCrawl(structure: StructureFacet) {
        val marker = structure.lastFullCrawl ?: return
        if (marker.at > (remoteCrawl.value?.at ?: Long.MIN_VALUE)) remoteCrawl.value = marker
    }

    /**
     * This device's path templates, grouped by the folder they apply to.
     *
     * The stamp is the moment they were last stored rather than "now": re-publishing an unchanged
     * set must not make it outrank somebody else's newer edit to the same folder simply because
     * this device happened to sync last.
     */
    private suspend fun localTemplateRules(deviceId: String): Map<String, TemplateRule> {
        val stored = librarySettings.pathTemplates.first()
        if (stored.isEmpty()) return emptyMap()
        val editedAt = librarySettings.pathTemplatesEditedAt.first()
        return stored.mapNotNull { ScopedTemplate.decode(it) }
            .groupBy { it.scope }
            .mapValues { (_, group) ->
                TemplateRule(patterns = group.map { it.template.source }, editedAt = editedAt, by = deviceId)
            }
    }

    /**
     * Takes on the templates the shared index carries, when they are newer than this device's.
     *
     * Whole-set rather than per-scope: the stored form is one ordered list, and the order is part of
     * what a template set means. A device that has never written one adopts whatever is published,
     * which is the point — one person sorts the library out and everybody else's shelf agrees.
     */
    private suspend fun adoptTemplates(corrections: CorrectionsFacet) {
        if (corrections.templates.isEmpty()) return
        val newest = corrections.templates.values.maxOfOrNull { it.editedAt } ?: return
        val localAt = librarySettings.pathTemplatesEditedAt.first()
        if (newest <= localAt) return

        // Merged per SCOPE, not replaced wholesale. `corrections` here is the REMOTE file — pull
        // reads it rather than merging it with what this device holds — so replacing the list would
        // delete every template this device has that the server has never seen. Which is not a rare
        // case: a read-only share user CANNOT publish, so their own patterns are never up there, and
        // the maintainer's next edit would silently wipe them.
        val local = librarySettings.pathTemplates.first().mapNotNull { ScopedTemplate.decode(it) }
        val localByScope = local.groupBy { it.scope }
        val merged = LinkedHashMap<String, List<String>>()
        // Whatever is only here stays; the server has no opinion about a scope it has never held.
        localByScope.forEach { (scope, group) -> merged[scope] = group.map { it.template.source } }
        // …and a scope the server does hold wins, because it is the newer deliberate act.
        corrections.templates.forEach { (scope, rule) -> merged[scope] = rule.patterns }

        val lines = merged.entries
            // Narrowest scope first, so a folder's own pattern is tried before a library-wide one.
            .sortedByDescending { it.key.length }
            .flatMap { (scope, patterns) ->
                patterns.map { if (scope.isBlank()) it else "$scope\t$it" }
            }
        librarySettings.setPathTemplates(lines, editedAt = newest)
        Log.i(TAG, "adopted templates: ${corrections.templates.size} shared scope(s), ${lines.size} pattern(s) in force")
    }

    private fun <T> FacetStore.Load<T>.valueOr(empty: T): T = (this as? FacetStore.Load.Present)?.value ?: empty

    private suspend fun dirOf(): String {
        val root = librarySettings.libraryRoot.first().trim('/')
        return listOf(root, LibraryFacets.DIR).filter { it.isNotBlank() }.joinToString("/")
    }

    private suspend fun pathOf(file: String): String = "${dirOf()}/$file"

    private companion object {
        const val TAG = "HomerSync"
        const val COVERS_DIR = "covers"
        const val SQL_PARAM_CHUNK = 400
        /**
         * How long a burst of edits settles before it is published.
         *
         * Coalescing is the point — twenty edits in a row should be one upload, not twenty — but
         * thirty seconds meant a SINGLE edit sat unshared long enough to look broken: you make it,
         * open the library page, and the control still says there is work to do. Five seconds still
         * folds a burst together and lands one edit while the reader is still looking at it.
         */
        const val EDIT_PUBLISH_IDLE_MS = 5_000L
    }
}
