package com.geozelot.homer.data.db.entity

/**
 * The fields a person's edit can touch — declared once, because five copies of this list drifted.
 *
 * ## What went wrong, so nobody re-scatters it
 *
 * "Which fields can an edit change" was spelled out separately in `hasMetadataEdit`,
 * `FacetMapping.correctionOf`, `BookEntity.sameFieldsAs`, and two DAO queries. Every one of them was
 * a hand-written list of the same names, and nothing connected them. Three fields were added over
 * time — `collection`, `collectionIndex`, `language` — and each landed in some of the five:
 *
 *  - `correctionOf` did not count them, so a per-book collection edit was treated as "nothing
 *    corrected" and never published. Putting a book into a collection by hand reached no other
 *    device at all.
 *  - `observeCorrectionCount` did not count them either, so the Library screen reported no
 *    corrections while holding a folder full of them.
 *  - `HomerOverride` had no representation for them, so a sync wrote them back as null.
 *
 * None of those looked like a bug in the code that contained it. They were field lists disagreeing
 * with other field lists, which no type checked and no name suggested.
 *
 * ## How this stops it happening again
 *
 * The two behavioural checks are derived from one list of accessors, so they cannot disagree. The
 * SQL — which cannot share Kotlin code — is a constant here beside them, and `EditFieldsTest`
 * asserts it names exactly the same columns.
 *
 * The guard that actually matters is the reflection test: it reads the real fields off
 * [BookOverrideEntity] and [BookEntity] and fails if any of them is in neither the edited set nor
 * the explicitly-excluded set. **Adding a field to either entity therefore breaks the build until
 * somebody says which half it belongs to** — which is the single question that has been getting
 * answered by accident.
 */
object EditFields {

    /**
     * Everything a correction can carry, as column names.
     *
     * Also the property names, which the reflection guard relies on: Room maps them one to one here
     * (no `@ColumnInfo` renaming on this table), so the SQL and the Kotlin are talking about the same
     * strings and a test can compare them.
     */
    val CORRECTION_COLUMNS = listOf(
        "title",
        "author",
        "series",
        "seriesIndex",
        "collection",
        "collectionIndex",
        "genre",
        "language",
        "tags",
    )

    /**
     * The rest of the override row: claims about the READER, never published to a shared folder.
     *
     * Listed rather than derived so the reflection guard has something to check against. `bookId` and
     * `updatedAt` are bookkeeping; the other three are the ones that would say who has read what.
     */
    val READER_COLUMNS = listOf("bookId", "finished", "downloadOnPlay", "hidden", "updatedAt")

    /**
     * What a path template may write on the detected layer — the correction set minus `tags`.
     *
     * Tags exist only as a correction: nothing detects one, so there is nothing for a template to
     * write and nothing for a re-derive to compare.
     */
    val DETECTED_COLUMNS = CORRECTION_COLUMNS - "tags"

    /**
     * Everything else on [BookEntity], which a template must leave alone.
     *
     * Identity and paths (an id is the fetch URL and every facet's key), this device's own
     * bookkeeping (what art it has cached, what it has already tried), and facts measured from the
     * files rather than parsed from their names.
     */
    val BOOK_OTHER_COLUMNS = listOf(
        "id",
        "contentHash",
        "relativePath",
        "coverFilePath",
        "localCoverPath",
        "customCoverPath",
        "coverAttempted",
        "metadataAttempted",
        "chapterTier",
        "isMultiFile",
        "fileCount",
        "totalDurationMs",
        "addedAt",
        "updatedAt",
    )

    /**
     * "This row carries a correction", as a SQL predicate over `book_overrides`.
     *
     * A `const val` because Room needs the `@Query` string to be a compile-time constant, so this
     * cannot be built from [CORRECTION_COLUMNS] at runtime however much it would like to be. That is
     * the one place the set is written twice, and `EditFieldsTest` compares the two.
     */
    const val CORRECTED = "(title IS NOT NULL OR author IS NOT NULL OR series IS NOT NULL " +
        "OR seriesIndex IS NOT NULL OR collection IS NOT NULL OR collectionIndex IS NOT NULL " +
        "OR genre IS NOT NULL OR language IS NOT NULL OR tags IS NOT NULL)"

    /** One reader per correction field, in [CORRECTION_COLUMNS] order. */
    private val onOverride: List<(BookOverrideEntity) -> Any?> = listOf(
        { it.title },
        { it.author },
        { it.series },
        { it.seriesIndex },
        { it.collection },
        { it.collectionIndex },
        { it.genre },
        { it.language },
        { it.tags },
    )

    /** One reader per template-writable field, in [DETECTED_COLUMNS] order. */
    private val onBook: List<(BookEntity) -> Any?> = listOf(
        { it.title },
        { it.author },
        { it.series },
        { it.seriesIndex },
        { it.collection },
        { it.collectionIndex },
        { it.genre },
        { it.language },
    )

    /**
     * Whether this override actually corrects a bibliographic field.
     *
     * Not "is there a row": a row also exists to carry the hidden flag, a per-book play mode, or as
     * the all-null tombstone a cleared correction leaves behind. Counting those as edits would put
     * every book somebody had ever hidden on the `is:edited` shelf.
     */
    fun corrected(override: BookOverrideEntity): Boolean = onOverride.any { it(override) != null }

    /** Whether the fields a template can write are identical — the test for "this changed nothing". */
    fun sameDetected(a: BookEntity, b: BookEntity): Boolean = onBook.all { it(a) == it(b) }

    /** Sanity, for the tests: the accessor lists and the name lists are the same length. */
    internal val correctionArity: Int get() = onOverride.size
    internal val detectedArity: Int get() = onBook.size
}
