package com.geozelot.homer.data.sync.facet

import kotlinx.serialization.Serializable

/**
 * The shared library index, split into four files under `<libraryRoot>/.homer/`.
 *
 * They are peers, not layers. Each answers a different question, is produced by a different actor,
 * and therefore merges by a different rule:
 *
 *  - [StructureFacet] — what the library **is**. Produced by crawling the folder tree.
 *  - [DerivedFacet] — what devices **computed** by reading the files. Expensive, and the whole
 *    reason to share anything.
 *  - [CorrectionsFacet] — what a person **said**. Outranks everything detected.
 *  - [LibraryPolicy] — what the **owner** allows. Written by one party and read by everyone else.
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

    /**
     * The owner's rules for this library — see [LibraryPolicy].
     *
     * A fourth file rather than a field in [StructureFacet], because it is the one thing here that
     * merges by "the owner said so" rather than by contribution, and `FacetMerge.structure` is
     * exactly the code path that must never be able to touch it. It is also read on its own, from
     * folders that have no index at all, and before the expensive read that decides everything else.
     */
    const val POLICY_FILE = "policy.json"
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
    /**
     * Path templates, keyed by the folder they apply to (empty key = the whole library).
     *
     * Here rather than in a fourth facet because a template IS a correction — it is a person saying
     * what these folders mean — and it therefore wants the rule this facet already has: newest edit
     * wins per key. A fourth file would have needed a fourth merge rule for the same semantics.
     *
     * Keyed by scope rather than listed, so two people editing different folders' patterns do not
     * overwrite each other; two people editing the SAME folder's do, newest winning, which is what
     * "one deliberate act" means everywhere else in this facet.
     *
     * Defaulted, so an index written by 2.0.0 reads back as "no templates" rather than failing.
     */
    val templates: Map<String, TemplateRule> = emptyMap(),
)

/**
 * One folder's patterns, and when somebody last said so.
 *
 * A list rather than a single pattern: a folder can need more than one — a scope with two layouts
 * in it is ordinary — and they are tried in the order written.
 */
@Serializable
data class TemplateRule(
    val patterns: List<String> = emptyList(),
    val editedAt: Long = 0,
    /** Which device published it, for the same reason a correction records one. */
    val by: String? = null,
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
    /**
     * The parent grouping above the series, and the position in it — as a PERSON stated them.
     *
     * `StructureBook` carries the same two fields for the shape a crawl read off the folders; these
     * are the correction that outranks it, which is what makes "put this standalone into Discworld"
     * expressible at all. They arrived late: both were present on `BookOverrideEntity` and applied
     * locally by `applyOverride` while this class had nowhere to put them, so a per-book collection
     * edit worked on the device that made it and reached no other.
     *
     * Defaulted, so a corrections file written before they existed reads back as "no opinion" rather
     * than failing to parse — and therefore NOT a schema bump, on the same rule as `CrawlMarker`'s
     * device name.
     */
    val collection: String? = null,
    val collectionIndex: Int? = null,
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

// ── policy ───────────────────────────────────────────────────────────────────────────────────

/**
 * The library owner's rules for every OTHER device that opens this folder.
 *
 * ## What it is for
 *
 * A library shared with several people is cheap to read and expensive to *establish*. Crawling it
 * is one request per folder; measuring it is one per file, thousands of them. A device that points
 * at somebody else's folder and builds its own private index therefore spends a night working out
 * what the folder already knows — and shares none of it, so the next reader along does it again.
 *
 * This file lets the owner say "use the index that is here" once, and have every Homer honour it.
 *
 * ## Honoured, not enforced — and the UI must say so
 *
 * A file in a folder that the client can read cannot stop that client doing anything. A modified
 * Homer, an rclone mount or a WebDAV drive ignores it completely. What it stops is *Homer's own
 * default behaviour*, which is the whole of the problem: nobody sets out to re-crawl a shared
 * library, they point the app at a folder and let it work. Every string that describes this says
 * "honoured", never "enforced".
 *
 * ## It binds other devices, not the owner
 *
 * The owner wrote the rules and can change them in one tap, so applying them to their own device
 * would be theatre. `LibraryPolicyRepository` resolves ownership separately and exempts it.
 */
@Serializable
data class LibraryPolicy(
    val version: Int = LibraryFacets.SCHEMA_VERSION,
    /**
     * The `oc:owner-id` of the folder when the rules were written.
     *
     * Stored as well as probed, so a device can notice that the folder has changed hands rather
     * than silently trusting whatever a live probe answers. Null when the server did not expose it.
     */
    val ownerId: String? = null,
    /**
     * Devices here must read this library's index rather than establishing their own.
     *
     * A device that cannot publish becomes a reader: no crawl, no measure pass, no cover
     * extraction. It is not a switch that reader can turn off — that is exactly the escape hatch
     * this exists to close.
     */
    val sharedIndexRequired: Boolean = false,
    /**
     * Whether a write-capable device other than the owner may publish metadata corrections here.
     *
     * False suppresses the *publish*, never the local edit: somebody fixing a garbled title for
     * their own shelf costs the owner nothing, and taking that away would be a worse app for no
     * gain. What disappears is every affordance that promises the fix will travel.
     */
    val editsAllowed: Boolean = true,
    val createdAt: Long = 0,
    /** The device the rules were first written from, for "set from Pixel 7". */
    val createdBy: String? = null,
    val editedAt: Long = 0,
) {
    companion object {
        /**
         * How far up the tree a policy is looked for.
         *
         * A rule at `Audiobooks/.homer/policy.json` has to bind a device pointed at
         * `Audiobooks/Krimis`, or the whole thing is bypassed by one tap in the folder picker. The
         * cap keeps a cold resolve to a handful of small GETs; six levels is deeper than any real
         * library nests below its own root.
         */
        const val MAX_LOOKUP_LEVELS = 6

        /**
         * The folders to look in, nearest first, for a library rooted at [root].
         *
         * Files-root-relative, so `""` is the account's files root — or, for a share link, the
         * share itself, which is why the same walk works for both without a special case.
         */
        fun lookupFolders(root: String, maxLevels: Int = MAX_LOOKUP_LEVELS): List<String> {
            val folders = mutableListOf<String>()
            var current = root.trim('/')
            while (folders.size < maxLevels) {
                folders += current
                if (current.isEmpty()) break
                current = current.substringBeforeLast('/', "")
            }
            return folders
        }
    }
}

/**
 * The rules actually in force here, resolved: what they say, who set them, and where they came from.
 *
 * Not serialised. It exists because three of the questions the UI asks — *may I?*, *who says so?*,
 * *from which folder?* — cannot be answered by the file alone: the nearest of several candidates
 * wins, the owner is a live probe, and an unreadable file still means something.
 */
data class PolicyInForce(
    val sharedIndexRequired: Boolean,
    val editsAllowed: Boolean,
    /** The account the rules were set by, for captions. Null when the server exposed no owner. */
    val owner: String?,
    /** Files-root-relative folder the file was found in, or null when there is no file. */
    val atFolder: String?,
    /**
     * False when a policy file is present but could not be read — a newer schema, or damage.
     *
     * Deliberately fails *closed*, unlike every other facet: [ofCurrentSchema] treats an unknown
     * schema as absent, which for the other three means "rebuild it" and for this one would mean
     * "the rules do not apply". A future build's stricter policy would then be ignored by this one,
     * and the ignoring would look exactly like the crawl-everything problem it prevents. So an
     * unreadable policy is read as the strictest one: use the shared index, publish nothing.
     */
    val understood: Boolean = true,
) {
    /** Whether anything at all was found — for "no rules here" versus "rules that say yes". */
    val present: Boolean get() = atFolder != null

    companion object {
        /** No policy anywhere: exactly the behaviour of every library that exists today. */
        val OPEN = PolicyInForce(
            sharedIndexRequired = false,
            editsAllowed = true,
            owner = null,
            atFolder = null,
        )

        fun of(policy: LibraryPolicy, atFolder: String, owner: String? = null): PolicyInForce =
            PolicyInForce(
                sharedIndexRequired = policy.sharedIndexRequired,
                editsAllowed = policy.editsAllowed,
                owner = owner ?: policy.ownerId,
                atFolder = atFolder,
            )

        /** A policy file that is there but unreadable — see [understood]. */
        fun unreadable(atFolder: String): PolicyInForce = PolicyInForce(
            sharedIndexRequired = true,
            editsAllowed = false,
            owner = null,
            atFolder = atFolder,
            understood = false,
        )
    }
}

/**
 * One resolve of a library's rules: what they say, whether this account owns the folder, and which
 * root the answer describes.
 *
 * The root is the load-bearing field. A resolution is about one folder, and the library root changes
 * — by adopting a shared library, by opening a share link, by editing the path — so an answer kept
 * without its subject would quietly go on applying to somewhere else. Callers therefore get to tell
 * "no rules here" from "nothing asked about here yet", and the second is answered by waiting for a
 * resolve rather than by assuming the library is open.
 */
data class PolicyResolution(
    /** The library root this describes, or null when nothing has ever been resolved. */
    val forRoot: String?,
    val policy: PolicyInForce,
    /**
     * Whether the signed-in account owns the folder: true, false, or null when the server exposed
     * no owner at all. Null is not "somebody else's" — it is "unanswerable here", which is the
     * ordinary case on a WebDAV server that is not Nextcloud.
     */
    val owned: Boolean?,
    /** Wall-clock of the resolve; 0 = never. Throttles re-resolving. */
    val checkedAt: Long,
) {
    /** Whether this answer is about [root]. */
    fun describes(root: String): Boolean = forRoot != null && forRoot == root.trim('/')

    /** The owner's own device, which the rules deliberately do not bind. */
    val isOwner: Boolean get() = owned == true

    companion object {
        /** Nothing resolved yet. */
        val NONE = PolicyResolution(forRoot = null, policy = PolicyInForce.OPEN, owned = null, checkedAt = 0)
    }
}
