package com.geozelot.homer.data.library

import com.geozelot.homer.data.db.entity.BookEntity
import com.geozelot.homer.data.db.entity.BookOverrideEntity

/**
 * Applies a user [override] on top of a detected [BookEntity] (D2: override > detected).
 * Null override fields fall through to the detected value. [BookOverrideEntity.hidden] is
 * not a book field and is handled by the caller.
 */
fun BookEntity.applyOverride(override: BookOverrideEntity?): BookEntity =
    if (override == null) {
        this
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
        )
    }

/**
 * Whether this override actually corrects a bibliographic field.
 *
 * Not "is there a row": a row also exists to carry the hidden flag, a per-book play mode, or as the
 * all-null tombstone that a cleared correction leaves behind. Counting those as edits would put
 * every book somebody had ever hidden on the `is:edited` shelf.
 */
fun BookOverrideEntity.hasMetadataEdit(): Boolean =
    title != null || author != null || series != null || seriesIndex != null ||
        collection != null || collectionIndex != null || genre != null || language != null ||
        tags != null
