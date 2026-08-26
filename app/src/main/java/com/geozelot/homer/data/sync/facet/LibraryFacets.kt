package com.geozelot.homer.data.sync.facet

import kotlinx.serialization.Serializable

/**
 * The shared library index, split into three files under `<libraryRoot>/.homer/`.
 *
 * They are peers, not layers. Each answers a different question, is produced by a different actor,
 * and therefore merges by a different rule:
 *
 *  - [StructureFacet] — what the library **is**. Produced by crawling the folder tree.
 *  - [DerivedFacet] — what devices **computed** by reading the files. Expensive, and the whole
 *    reason to share anything.
 *  - [CorrectionsFacet] — what a person **said**. Outranks everything detected.
 *
 * Splitting them is also what ends the whole-catalog rewrite: fixing one book's title used to
 * republish every file entry in the library, several megabytes of it, on every edit.
 *
 * The files are read and written the same way for an account and for a share link. The only
 * difference between them is whether the backend can be written to at all.
 */
object LibraryFacets {
    const val SCHEMA_VERSION = 3
    const val DIR = ".homer"
    const val STRUCTURE_FILE = "structure.json"
    const val DERIVED_FILE = "derived.json"
    const val CORRECTIONS_FILE = "corrections.json"
}

// ── structure ────────────────────────────────────────────────────────────────────────────────

/**
 * Records the crawl that produced a [StructureFacet]'s book set.
 *
 * Only a **complete** crawl is recorded here, because this marker exists for exactly one purpose:
 * authorising deletion. A book missing from a crawl that post-dates it is genuinely gone; without
 * a marker there is no way to distinguish that from a device that has simply never looked.
 */
@Serializable
data class CrawlMarker(
    /** Wall-clock at which the crawl finished. */
    val at: Long,
    /** The device id that ran it. */
    val by: String,
    /**
     * That device's own name for itself — for "last full crawl 3 days ago, from Pixel 7". [by] is a
     * UUID, which is the right thing to compare and the wrong thing to show a person.
     *
     * Optional, and deliberately NOT a schema bump: a marker from a device that does not write it
     * still means exactly what it meant, and treating those facets as another schema would make
     * every library re-crawl to gain a caption. A missing name reads as "another device".
     */
    val byName: String? = null,
)

/**
 * What the shared index is doing right now, so a slow read is not silence: a first pull on a slow
 * link would otherwise leave an empty shelf with no explanation.
 */
enum class IndexActivity { IDLE, READING, PUBLISHING }

/**
 * A [CrawlMarker] resolved for display: whose crawl it was, in words rather than a UUID.
 *
 * Not serialised — it exists because the answer "was this us?" is a comparison the UI must not be
 * asked to make.
 */
data class CrawlSummary(
    val at: Long,
    val byThisDevice: Boolean,
    /** The other device's name for itself, or null when it published none. */
    val deviceName: String?,
)

@Serializable
data class StructureFacet(
    val version: Int = LibraryFacets.SCHEMA_VERSION,
    /**
     * The most recent crawl that saw the WHOLE tree, or null if none has. An incremental scan
     * leaves this untouched: it can add books, and it must never be able to remove one.
     */
    val lastFullCrawl: CrawlMarker? = null,
    val books: Map<String, StructureBook> = emptyMap(),
)

/**
 * A book as it exists on disk. Title, author and series are derived from the folder path, so they
 * belong here rather than in [DerivedFacet] — two devices crawling the same tree produce the same
 * answer, and a device adopting a catalog without crawling needs them.
 */
@Serializable
data class StructureBook(
    val title: String,
    val author: String? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    /**
     * The parent grouping above the series, and the position in it.
     *
     * Structural rather than derived: the collection is read from the folder the book sits in, the
     * same place the series comes from, so it is a fact about the library's shape. Defaulted so an
     * index written by an older build reads back as "no collection" rather than failing to parse.
     */
    val collection: String? = null,
    val collectionIndex: Int? = null,
    /**
     * The path-independent fingerprint. Shared because it is derived from the files, and a device
     * that adopts a book without it can never recognise that book again once its folder is
     * renamed — orphaning the position and bookmarks permanently.
     */
    val contentHash: String? = null,
    /** Library-relative path to a folder cover image: a file on disk, so a structural fact. */
    val coverFilePath: String? = null,
    val isMultiFile: Boolean = false,
    val files: List<StructureFile> = emptyList(),
    val updatedAt: Long = 0,
)

/**
 * One file, as small as it can honestly be — this list is the bulk of the index, twelve thousand
 * entries for a large library, and every field is paid for twelve thousand times.
 *
 * What is *not* here, and why:
 *  - the file name is the last segment of [path];
 *  - the sort index is the position in the list, which is written in order;
 *  - the content type and last-modified time are written by the scanner and read by nothing, so
 *    publishing them cost hundreds of kilobytes to tell other devices something no code asks.
 */
@Serializable
data class StructureFile(
    /**
     * Relative to the BOOK, not the library — `01.mp3`, not
     * `Serien Divers/TKKG/038 - Die weisse Schmuggler-Yacht/01.mp3`. The book's own id supplies
     * the rest, and repeating it per file was the single largest thing in the file.
     *
     * A leading `/` means the exceptional case: a file that does not live under its own book,
     * stored library-relative instead. Nothing produces that today; the marker exists so the
     * encoding cannot silently corrupt a path if something ever does.
     */
    val path: String,
    val sizeBytes: Long = 0,
    val etag: String? = null,
)

// ── derived ──────────────────────────────────────────────────────────────────────────────────

@Serializable
data class DerivedFacet(
    val version: Int = LibraryFacets.SCHEMA_VERSION,
    val books: Map<String, DerivedBook> = emptyMap(),
)

/**
 * What reading the files taught us. Every field is nullable or empty-able because "not established
 * yet" is a distinct state from "established as nothing" — and only the former may be overwritten
 * by another device's answer.
 */
@Serializable
data class DerivedBook(
    val genre: String? = null,
    /**
     * ISO 639-1 code read from a tag or the file names.
     *
     * Optional with a default, and deliberately NOT a schema bump: a facet written before this
     * field existed simply carries no language, which is exactly what "nothing established it yet"
     * means. Bumping would make every device treat every older facet as absent and republish a
     * whole library to gain one field.
     */
    val language: String? = null,
    val totalDurationMs: Long? = null,
    /** Whether this book's extracted art is in the shared `.homer/covers/` cache. */
    val hasCachedCover: Boolean = false,
    /** `EMBEDDED` / `NONE`, or null while nothing has established which. */
    val chapterTier: String? = null,
    val chapters: List<DerivedChapter> = emptyList(),
    /** Book-relative file path (see [StructureFile.path]) to its measured length. */
    val fileDurationsMs: Map<String, Long> = emptyMap(),
    val updatedAt: Long = 0,
)

@Serializable
data class DerivedChapter(val startMs: Long, val title: String? = null)

// ── corrections ──────────────────────────────────────────────────────────────────────────────

@Serializable
data class CorrectionsFacet(
    val version: Int = LibraryFacets.SCHEMA_VERSION,
    val books: Map<String, BookCorrection> = emptyMap(),
)

/**
 * A person's deliberate edit to a book's bibliographic fields.
 *
 * Only these fields are ever published. `finished`, `hidden` and `downloadOnPlay` also live on
 * `BookOverrideEntity`, but they are claims about the *reader*, not the book — publishing them to
 * a folder shared with other people would leak one person's listening habits to everyone else.
 *
 * A null field means "no correction to this field", not "clear it": the whole entry is replaced as
 * one act, so clearing a correction means writing the entry again without it, with a newer
 * [editedAt]. That is why this facet merges per BOOK rather than per field — an edit is one
 * deliberate act by one person, and per-field merging would make clearing impossible to express.
 */
@Serializable
data class BookCorrection(
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val genre: String? = null,
    /** A deliberate language, as an ISO 639-1 code. Outranks whatever any tag says. */
    val language: String? = null,
    /** Same encoding as `BookOverrideEntity.tags`. */
    val tags: String? = null,
    /**
     * Chapter cuts a person made in a single-file book, in playing order.
     *
     * Here rather than in [DerivedBook] because of the split the whole design rests on: a derived
     * chapter is what a TAG said, and a cut is what a PERSON said. That also gives them the right
     * merge rule — newest edit wins outright, rather than "non-null wins", which for two people's
     * differing chapter lists would mean whoever wrote first owns them for ever.
     */
    val chapters: List<DerivedChapter> = emptyList(),
    val editedAt: Long = 0,
    /** The device the edit was made on, so the UI can say where a change came from. */
    val editedBy: String? = null,
)
