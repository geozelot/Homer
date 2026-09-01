package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity
import com.geozelot.homer.data.db.entity.EditFields

/**
 * Applies a user [override] on top of a detected [BookEntity] (D2: override > detected).
 * Null override fields fall through to the detected value. [BookOverrideEntity.hidden] is
 * not a book field and is handled by the caller.
 */
fun BookEntity.applyOverride(override: BookOverrideEntity?): BookEntity =
    if (override == null) {
        withoutRedundantCollection()
    } else {
        copy(
            title = override.title ?: title,
            author = override.author ?: author,
            series = override.series ?: series,
            seriesIndex = override.seriesIndex ?: seriesIndex,
            collection = override.collection ?: collection,
            collectionIndex = override.collectionIndex ?: collectionIndex,
            genre = override.genre ?: genre,
            language = override.language ?: language,
        ).withoutRedundantCollection()
    }

/**
 * Whether naming [collection] as a parent of [series] says anything at all.
 *
 * It does not when they are the same name. A collection is a level ABOVE a series — Discworld over
 * Rincewind — and "Discworld inside Discworld" is not a hierarchy, it is the same shelf written
 * twice. The shelf already handles a series with no collection by letting it stand in as its own
 * (`effectiveCollection()`), so the redundant value is not merely useless, it is the thing that
 * fallback exists to avoid having to store.
 *
 * Compared trimmed and case-insensitively, because these arrive from three places that punctuate
 * differently: a folder name, a path template's capture, and something somebody typed.
 *
 * This is not a hypothetical tidy-up. Until it was fixed, the shelf edit dialog wrote a collection's
 * name into every member's `series` field, leaving thousands of books with `series == collection` and
 * their real sub-series gone. Dropping the redundant collection is what turns that wreckage back
 * into an ordinary series rather than freezing it as a collection of one thing named after itself.
 */
internal fun redundantCollection(series: String?, collection: String?): Boolean {
    val c = collection?.trim() ?: return false
    if (c.isEmpty()) return false
    return c.equals(series?.trim(), ignoreCase = true)
}

/**
 * The detected book with a collection that only repeats its series dropped.
 *
 * Applied on the way OUT of detection (`TemplateApplier.apply`) and on the way THROUGH to the shelf
 * (`applyOverride`), so neither `structure.json` nor the library list can carry the state.
 */
internal fun BookEntity.withoutRedundantCollection(): BookEntity =
    if (redundantCollection(series, collection)) copy(collection = null, collectionIndex = null) else this

/**
 * The same rule for a correction, applied before it is stored or published.
 *
 * [collectionIndex] goes with it: a position within a collection that does not exist is not a
 * smaller truth, it is a number about nothing, and leaving it behind would keep putting a `#` on
 * the cover of a book whose collection was just dropped.
 */
internal fun BookOverrideEntity.withoutRedundantCollection(): BookOverrideEntity =
    if (redundantCollection(series, collection)) copy(collection = null, collectionIndex = null) else this

/**
 * Whether this override actually corrects a bibliographic field.
 *
 * The field list lives in [EditFields] rather than here. It used to be spelled out in this function
 * AND in four other places, and the five drifted — see that class for what it cost.
 */
fun BookOverrideEntity.hasMetadataEdit(): Boolean = EditFields.corrected(this)
