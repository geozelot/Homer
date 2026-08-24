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
import com.geozelot.homer.data.db.dao.ChapterDao
import com.geozelot.homer.data.metadata.CoverCache
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.DeviceIdentity
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.sync.HomerCatalog
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

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

    init {
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
        val local = CorrectionsFacet(
            books = bookOverrideDao.getAll()
                .mapNotNull { o -> FacetMapping.correctionOf(o, deviceId)?.let { o.bookId to it } }
                .toMap(),
        )
        report(
            LibraryFacets.CORRECTIONS_FILE,
            store.save(LibraryFacets.CORRECTIONS_FILE, CorrectionsFacet.serializer()) { remote ->
                FacetMerge.corrections(local, remote.valueOr(CorrectionsFacet()))
            },
        )
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

            // Nothing at all where a v1 catalog sits: convert it once rather than make the user
            // re-crawl and re-measure a library the old index already paid for.
            if (structureRead is FacetStore.Load.Missing) {
                val converted = convertLegacy()
                if (converted != null) {
                    lastStructure = converted.structure
                    lastDerived = converted.derived
                    lastCorrections = converted.corrections
                    apply(converted.structure, converted.derived, converted.corrections)
                    return true
                }
            }

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

            // Nothing moved anywhere: no point walking several hundred books to write back what
            // is already stored.
            val anyChanged = structureRead is FacetStore.Load.Present ||
                derivedRead is FacetStore.Load.Present ||
                correctionsRead is FacetStore.Load.Present
            if (anyChanged) apply(structure, derived, corrections)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "pull failed", e)
            false
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
                // is silence, and silence must not erase what this device worked out itself.
                if (d?.chapterTier != null) {
                    chapterDao.replaceForBook(id, FacetMapping.chapterEntities(id, d))
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

    /** Reads a v1 `catalog.json`, converts it, and publishes the result if this device may. */
    private suspend fun convertLegacy(): LegacyCatalogConverter.Converted? {
        val legacyPath = "${dirOf()}/$LEGACY_FILE"
        val body = runCatching { webDavClient.getText(legacyPath) }.getOrNull()?.content
        if (body.isNullOrBlank()) return null
        val legacy = runCatching { json.decodeFromString<HomerCatalog>(body) }.getOrNull() ?: run {
            Log.w(TAG, "a v1 catalog is present but unreadable; ignoring it")
            return null
        }
        Log.i(TAG, "converting a v1 catalog of ${legacy.books.size} books")
        val converted = LegacyCatalogConverter.convert(legacy)
        if (canPublish()) {
            // Reported, not discarded. Publishing the converted index is the whole point of the
            // migration, and a silent failure here leaves everyone else's devices with nothing.
            report(
                LibraryFacets.STRUCTURE_FILE,
                store.save(LibraryFacets.STRUCTURE_FILE, StructureFacet.serializer()) { converted.structure },
            )
            report(
                LibraryFacets.DERIVED_FILE,
                store.save(LibraryFacets.DERIVED_FILE, DerivedFacet.serializer()) { converted.derived },
            )
        }
        return converted
    }

    // ── pushing ──────────────────────────────────────────────────────────────────────────────

    /** Publishes this device's view, facet by facet, writing only what changed. */
    suspend fun push() {
        if (!canPublish() || credentialStore.awaitCredentials() == null || !networkMonitor.isOnline()) return
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

        for (book in bookDao.getAll()) {
            val files = audioFileDao.findForBook(book.id)
            structure[book.id] = FacetMapping.structureOf(book, files)
            FacetMapping.derivedOf(book, files, chapterDao.findForBook(book.id))?.let { derived[book.id] = it }
            overrides[book.id]?.let { o ->
                FacetMapping.correctionOf(o, deviceId)?.let { corrections[book.id] = it }
            }
        }

        val crawledAt = librarySettings.lastFullCrawlAt.first()
        return Local(
            structure = StructureFacet(
                lastFullCrawl = crawledAt.takeIf { it > 0 }?.let { CrawlMarker(at = it, by = deviceId) },
                books = structure,
            ),
            derived = DerivedFacet(books = derived),
            corrections = CorrectionsFacet(books = corrections),
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

    private fun <T> FacetStore.Load<T>.valueOr(empty: T): T = (this as? FacetStore.Load.Present)?.value ?: empty

    private suspend fun dirOf(): String {
        val root = librarySettings.libraryRoot.first().trim('/')
        return listOf(root, LibraryFacets.DIR).filter { it.isNotBlank() }.joinToString("/")
    }

    private suspend fun pathOf(file: String): String = "${dirOf()}/$file"

    private companion object {
        const val TAG = "HomerSync"
        const val LEGACY_FILE = "catalog.json"
        const val COVERS_DIR = "covers"
        const val SQL_PARAM_CHUNK = 400
        const val EDIT_PUBLISH_IDLE_MS = 30_000L
    }
}
