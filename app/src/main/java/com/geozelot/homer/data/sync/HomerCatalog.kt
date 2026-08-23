package com.geozelot.homer.data.sync

import kotlinx.serialization.Serializable

/**
 * The shared library index — `‹libraryRoot›/.homer/catalog.json`, inside the shared
 * folder so every user sees it. It holds the full scanned catalog (books, files, durations,
 * effective structural metadata) so a new device can discover and play the whole library
 * without crawling or re-probing. Keyed by book id (library-root-relative, so it matches
 * across users mounting the shared folder at different paths). Reconciled last-write-wins on
 * each book's [CatalogBook.updatedAt]; lenient JSON so the schema can grow.
 */
@Serializable
data class HomerCatalog(
    val version: Int = SCHEMA_VERSION,
    val books: Map<String, CatalogBook> = emptyMap(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** A book in the shared catalog. Metadata is *effective* (detected + structural overrides). */
@Serializable
data class CatalogBook(
    val title: String,
    val author: String? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val genre: String? = null,
    /**
     * The book's path-independent fingerprint. Shared because it is derived from the files, so it
     * is identical for every device seeing the same folder — and a device that adopts a book from
     * the catalog without it can never recognise that book again once its folder is renamed
     * (re-linking requires a non-null hash), permanently orphaning the position and bookmarks.
     */
    val contentHash: String? = null,
    /** Library-relative path to a folder cover image, if any. */
    val coverFilePath: String? = null,
    /** True when extracted cover art is cached under `.homer/covers/<id>` (the shared cover cache). */
    val hasCachedCover: Boolean = false,
    val totalDurationMs: Long? = null,
    val isMultiFile: Boolean = false,
    val files: List<CatalogFile> = emptyList(),
    val updatedAt: Long = 0,
)

/** One audio file (chapter) within a [CatalogBook]. Paths are library-root-relative. */
@Serializable
data class CatalogFile(
    val relativePath: String,
    val fileName: String,
    val sortIndex: Int,
    val sizeBytes: Long = 0,
    val durationMs: Long? = null,
    val etag: String? = null,
    val lastModifiedMs: Long? = null,
    val contentType: String? = null,
)
