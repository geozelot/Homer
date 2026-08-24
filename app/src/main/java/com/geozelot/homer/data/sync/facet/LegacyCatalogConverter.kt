package com.geozelot.homer.data.sync.facet

import com.geozelot.homer.data.sync.HomerCatalog

/**
 * Converts a v1 `catalog.json` into the three facets, once.
 *
 * This is a migration, not a compatibility layer: it runs when the facet files are absent and a
 * legacy catalog is present, and after that the old file is never read again. It exists only so a
 * library that has already been crawled and measured does not have to be crawled and measured
 * again — thousands of requests and hours of sweeping that the old catalog already paid for.
 *
 * Two things it deliberately refuses to invent:
 *
 *  - **No corrections.** The v1 format published the *effective* value of every field, so a title
 *    a person fixed by hand and a title derived from a folder name are indistinguishable in it.
 *    Claiming any of them as deliberate would put words in the user's mouth, and would make those
 *    values outrank every future detection on every device. The corrections facet starts empty.
 *  - **No crawl marker.** A legacy catalog is not evidence that any device ever saw the whole
 *    tree. Stamping one would hand it the authority to delete books, which is exactly the power
 *    the marker exists to withhold until a complete crawl has actually run.
 */
object LegacyCatalogConverter {

    /** The three facets a v1 catalog can honestly produce. */
    data class Converted(
        val structure: StructureFacet,
        val derived: DerivedFacet,
        val corrections: CorrectionsFacet,
    )

    fun convert(legacy: HomerCatalog): Converted {
        val structure = LinkedHashMap<String, StructureBook>(legacy.books.size)
        val derived = LinkedHashMap<String, DerivedBook>(legacy.books.size)

        for ((id, book) in legacy.books) {
            structure[id] = StructureBook(
                title = book.title,
                author = book.author,
                series = book.series,
                seriesIndex = book.seriesIndex,
                contentHash = book.contentHash,
                coverFilePath = book.coverFilePath,
                isMultiFile = book.isMultiFile,
                files = book.files.map {
                    StructureFile(
                        relativePath = it.relativePath,
                        fileName = it.fileName,
                        sortIndex = it.sortIndex,
                        sizeBytes = it.sizeBytes,
                        etag = it.etag,
                        lastModifiedMs = it.lastModifiedMs,
                        contentType = it.contentType,
                    )
                },
                updatedAt = book.updatedAt,
            )

            val durations = book.files
                .mapNotNull { file -> file.durationMs?.let { file.relativePath to it } }
                .toMap()

            // Skip a book that taught us nothing measurable, rather than filling the facet with
            // empty entries that merge into other devices as "I looked and found nothing".
            if (book.genre != null || book.totalDurationMs != null || book.hasCachedCover || durations.isNotEmpty()) {
                derived[id] = DerivedBook(
                    genre = book.genre,
                    totalDurationMs = book.totalDurationMs,
                    hasCachedCover = book.hasCachedCover,
                    // v1 never carried chapters, so nothing here has been established about them.
                    chapterTier = null,
                    fileDurationsMs = durations,
                    updatedAt = book.updatedAt,
                )
            }
        }

        return Converted(
            structure = StructureFacet(lastFullCrawl = null, books = structure),
            derived = DerivedFacet(books = derived),
            corrections = CorrectionsFacet(),
        )
    }
}
