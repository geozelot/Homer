package com.geozelot.homer.data.sync.facet

import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.db.entity.ChapterEntity
import com.geozelot.homer.data.db.entity.ChapterTier

/**
 * Between the database and the three facets.
 *
 * The split the old catalog could not express runs right through here. Publishing reads the
 * **detected** values off [BookEntity] and the **deliberate** ones off [BookOverrideEntity], and
 * sends them to different files — where the single catalog published the *effective* value and
 * lost the distinction forever. Consuming puts them back, and leaves everything that is nobody
 * else's business exactly where it was.
 */
object FacetMapping {

    // ── publishing: database → facets ────────────────────────────────────────────────────────

    /**
     * What crawling found. Deliberately the raw [book] fields, never the override-applied ones:
     * a correction belongs in [correctionOf], and mixing it in here would republish somebody's
     * edit as though the folder tree had said it.
     */
    fun structureOf(book: BookEntity, files: List<AudioFileEntity>): StructureBook = StructureBook(
        title = book.title,
        author = book.author,
        series = book.series,
        seriesIndex = book.seriesIndex,
        collection = book.collection,
        collectionIndex = book.collectionIndex,
        contentHash = book.contentHash,
        coverFilePath = book.coverFilePath,
        isMultiFile = book.isMultiFile,
        // Written in sort order, because the position IS the sort index once published.
        files = files.sortedBy { it.sortIndex }.map {
            StructureFile(
                path = bookRelative(book.id, it.relativePath),
                sizeBytes = it.sizeBytes,
                etag = it.etag,
            )
        },
        updatedAt = book.updatedAt,
    )

    /**
     * What reading the files taught this device, or null when it has learned nothing worth
     * sharing. An empty entry would reach other devices as "I looked and found none of it", which
     * is a stronger claim than silence and would suppress their own probing.
     */
    fun derivedOf(
        book: BookEntity,
        files: List<AudioFileEntity>,
        chapters: List<ChapterEntity>,
    ): DerivedBook? {
        val durations = files
            .mapNotNull { f -> f.durationMs?.let { bookRelative(book.id, f.relativePath) to it } }
            .toMap()
        val tier = tierName(book.chapterTier)
        val hasCachedCover = book.localCoverPath != null
        if (book.genre == null && book.language == null && book.totalDurationMs == null &&
            !hasCachedCover && tier == null && durations.isEmpty()
        ) {
            return null
        }
        return DerivedBook(
            genre = book.genre,
            language = book.language,
            totalDurationMs = book.totalDurationMs,
            hasCachedCover = hasCachedCover,
            chapterTier = tier,
            chapters = chapters.sortedBy { it.sortIndex }.map { DerivedChapter(it.startMs, it.title) },
            fileDurationsMs = durations,
            updatedAt = book.updatedAt,
        )
    }

    /**
     * The half of an override that is a claim about the *book*, or null when the override only
     * carries claims about the *reader*.
     *
     * `finished`, `hidden` and `downloadOnPlay` are never published. On a folder shared with other
     * people they would say who has read what.
     */
    fun correctionOf(
        override: BookOverrideEntity?,
        cuts: List<BookmarkEntity>,
        deviceId: String?,
    ): BookCorrection? {
        val nothingCorrected = override == null || (
            override.title == null && override.author == null && override.series == null &&
                override.seriesIndex == null && override.genre == null && override.language == null &&
                override.tags == null
            )
        // Cuts alone are worth publishing: a book can have a hand-made chapter list and no
        // corrected field anywhere, and it is the chapter list other readers most want.
        if (nothingCorrected && cuts.isEmpty()) return null
        return BookCorrection(
            title = override?.title,
            author = override?.author,
            series = override?.series,
            seriesIndex = override?.seriesIndex,
            genre = override?.genre,
            language = override?.language,
            tags = override?.tags,
            chapters = cuts.map { DerivedChapter(startMs = it.positionMs, title = it.label) },
            // The later of the two, because either can be the reason to republish.
            editedAt = maxOf(override?.updatedAt ?: 0L, cuts.maxOfOrNull { it.createdAt } ?: 0L),
            editedBy = deviceId,
        )
    }

    // ── consuming: facets → database ─────────────────────────────────────────────────────────

    /**
     * Rebuilds a book row from the facets, keeping everything that is this device's own business.
     *
     * Cover files, "already tried" flags and the date the book first appeared here are local
     * bookkeeping; taking the facet's word for them would re-run work another device happens not
     * to have done, or claim a book has been in this library since somebody else first saw it.
     *
     * ## A LOCALLY NEWER row keeps its bibliographic fields
     *
     * The facet's `updatedAt` is not decoration. `FacetMerge.structure` has always resolved a
     * conflict by taking whichever side is newer — but it only ran on the PUBLISH path, and a pull
     * wrote the remote facet's title, author, series and collection straight over the local row
     * whatever their stamps said, then overwrote the local stamp too.
     *
     * Which meant applying a path template could not survive a sync. `TemplateApplier.planWrites`
     * moves `updatedAt` onto exactly the books a template changed, and its own comment explains that
     * the structure facet merges on that timestamp — true on the way out, and read by nothing on the
     * way in. So a template that produced visibly correct rows locally was reverted by the next pull
     * from an index published before the template existed, and a reader (who never crawls, so never
     * re-derives) would never see it at all. The symptom is a template whose preview is right and
     * whose library is wrong, which says nothing about where to look.
     *
     * A crawl stamps the books it changes, so a genuine server-side change still wins: only a local
     * act NEWER than the facet's own stamp holds its ground, which is the same rule as on the way
     * out. [now] is unused for these fields on purpose — the winning side's stamp is kept, or the
     * next pull would revert what this one just preserved.
     */
    fun bookEntity(
        id: String,
        structure: StructureBook,
        derived: DerivedBook?,
        existing: BookEntity?,
        now: Long,
    ): BookEntity {
        val fromFacet = facetBookEntity(id, structure, derived, existing, now)
        // Only when the local row is strictly newer. Equal stamps go to the facet, so a republish of
        // the same state is not a conflict.
        val local = existing?.takeIf { it.updatedAt > structure.updatedAt } ?: return fromFacet
        // Exactly the fields a template can write — see `BookEntity.sameFieldsAs`, which is the same
        // set for the same reason. Everything else still comes from the facets.
        return fromFacet.copy(
            title = local.title,
            author = local.author,
            series = local.series,
            seriesIndex = local.seriesIndex,
            collection = local.collection,
            collectionIndex = local.collectionIndex,
            genre = local.genre,
            language = local.language,
            updatedAt = local.updatedAt,
        )
    }

    private fun facetBookEntity(
        id: String,
        structure: StructureBook,
        derived: DerivedBook?,
        existing: BookEntity?,
        now: Long,
    ): BookEntity = BookEntity(
        id = id,
        // Never null out a hash we have: without one, a renamed folder can never be re-linked and
        // the book's position and bookmarks are orphaned.
        contentHash = structure.contentHash ?: existing?.contentHash,
        title = structure.title,
        author = structure.author,
        series = structure.series,
        seriesIndex = structure.seriesIndex,
        collection = structure.collection,
        // Kept when the facet has none, unlike `collection` beside it: an index is somebody's
        // ordering, and an older build republishing the index without the field must not erase it.
        collectionIndex = structure.collectionIndex ?: existing?.collectionIndex,
        genre = derived?.genre ?: existing?.genre,
        language = derived?.language ?: existing?.language,
        relativePath = id,
        coverFilePath = structure.coverFilePath ?: existing?.coverFilePath,
        localCoverPath = existing?.localCoverPath,
        customCoverPath = existing?.customCoverPath,
        coverAttempted = existing?.coverAttempted ?: false,
        metadataAttempted = existing?.metadataAttempted ?: false,
        chapterTier = tierValue(derived?.chapterTier) ?: existing?.chapterTier ?: ChapterTier.UNDETERMINED,
        isMultiFile = structure.isMultiFile,
        fileCount = structure.files.size,
        totalDurationMs = derived?.totalDurationMs ?: existing?.totalDurationMs,
        addedAt = existing?.addedAt ?: now,
        updatedAt = structure.updatedAt,
    )

    /**
     * The book's files, with every duration this device or the facet knows.
     *
     * A measurement is expensive and never wrong, so a local one survives a facet that lacks it —
     * and `durationAttempted` stays local, because "I tried and got nothing" is a fact about this
     * device's attempt, not about the file.
     */
    fun fileEntities(
        bookId: String,
        structure: StructureBook,
        derived: DerivedBook?,
        existing: List<AudioFileEntity>,
    ): List<AudioFileEntity> {
        val byPath = existing.associateBy { it.relativePath }
        return structure.files.mapIndexed { index, file ->
            val relativePath = libraryRelative(bookId, file.path)
            val previous = byPath[relativePath]
            AudioFileEntity(
                relativePath = relativePath,
                bookId = bookId,
                // The last segment, exactly as the scanner derives it from the DAV resource. It
                // feeds contentHash, so a different answer here would orphan every renamed book.
                fileName = relativePath.substringAfterLast('/'),
                sortIndex = index,
                sizeBytes = file.sizeBytes,
                etag = file.etag,
                // Not published, because nothing reads them — so keep whatever this device's own
                // scan found. Overwriting with null would also make every locally-scanned row
                // differ from its published form, and defeat the per-book skip on every pull.
                lastModified = previous?.lastModified,
                contentType = previous?.contentType,
                durationMs = derived?.fileDurationsMs?.get(file.path) ?: previous?.durationMs,
                durationAttempted = previous?.durationAttempted ?: false,
            )
        }
    }

    /**
     * A file's path relative to its book, which is what the index stores.
     *
     * A file that is somehow not under its own book keeps a library-relative path, marked with a
     * leading `/`. Nothing produces that; the marker is there so the round trip cannot invent a
     * path if something ever does.
     */
    fun bookRelative(bookId: String, relativePath: String): String = when {
        bookId.isEmpty() -> relativePath
        relativePath.startsWith("$bookId/") -> relativePath.removePrefix("$bookId/")
        else -> "/$relativePath"
    }

    /** The inverse of [bookRelative]. */
    fun libraryRelative(bookId: String, path: String): String = when {
        path.startsWith("/") -> path.removePrefix("/")
        bookId.isEmpty() -> path
        else -> "$bookId/$path"
    }

    /**
     * Folds an incoming correction into this device's override row.
     *
     * The bibliographic half is replaced wholesale — that is how a correction gets *cleared*, and
     * why the facet merges per book. The personal half is carried through untouched: a shared
     * correction must never mark a book finished or hidden for somebody who did not do it.
     *
     * An ABSENT correction changes nothing. It means the shared index has no opinion about this
     * book — not that a local edit should be discarded. Treating absence as a clear destroyed
     * corrections made offline before they had ever been published. The cost is that clearing the
     * *last* correction on a book does not propagate, because there is then no entry to carry the
     * clear; clearing one field among several does, since the entry survives.
     */
    fun overrideEntity(
        bookId: String,
        correction: BookCorrection?,
        existing: BookOverrideEntity?,
    ): BookOverrideEntity? {
        if (correction == null) return existing
        return BookOverrideEntity(
            bookId = bookId,
            title = correction.title,
            author = correction.author,
            series = correction.series,
            seriesIndex = correction.seriesIndex,
            genre = correction.genre,
            language = correction.language,
            tags = correction.tags,
            finished = existing?.finished,
            downloadOnPlay = existing?.downloadOnPlay,
            hidden = existing?.hidden ?: false,
            updatedAt = correction.editedAt,
        )
    }

    /**
     * A book's chapters: the cuts somebody made if there are any, otherwise whatever the tags said.
     *
     * A person's cuts outrank a tag for the same reason a corrected title outranks a folder name —
     * somebody looked at this book and decided. And a single-file book with unusable tag chapters is
     * exactly why cutting exists, so a tag that "wins" would defeat the feature.
     */
    fun chapterEntities(
        bookId: String,
        derived: DerivedBook?,
        correction: BookCorrection? = null,
    ): List<ChapterEntity> {
        val chapters = correction?.chapters?.takeIf { it.isNotEmpty() } ?: derived?.chapters.orEmpty()
        return chapters
            // Sorted, because a cut made later can belong earlier in the book and `sortIndex` is
            // read as playing order.
            .sortedBy { it.startMs }
            .mapIndexed { index, chapter ->
                ChapterEntity(bookId = bookId, sortIndex = index, title = chapter.title, startMs = chapter.startMs)
            }
    }

    // ── the chapter tier, as a word rather than a number ─────────────────────────────────────

    /**
     * The tier travels as a name, not an integer: the facets are JSON in a folder other people can
     * open, and a bare `2` there means nothing. `UNDETERMINED` maps to null, because "nobody has
     * worked this out yet" is an absence, and the merge treats absences as yielding to answers.
     */
    fun tierName(tier: Int): String? = when (tier) {
        ChapterTier.EMBEDDED -> "EMBEDDED"
        ChapterTier.SIDECAR -> "SIDECAR"
        ChapterTier.NONE -> "NONE"
        else -> null
    }

    /** Null for an unknown or absent name, so a facet from a newer Homer cannot corrupt the row. */
    fun tierValue(name: String?): Int? = when (name) {
        "EMBEDDED" -> ChapterTier.EMBEDDED
        "SIDECAR" -> ChapterTier.SIDECAR
        "NONE" -> ChapterTier.NONE
        else -> null
    }
}
