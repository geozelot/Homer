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
