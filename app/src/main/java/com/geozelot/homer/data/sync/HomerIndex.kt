package com.geozelot.homer.data.sync

import kotlinx.serialization.Serializable

/**
 * The central `.homer` manifest — the authoritative cross-device store (SCOPE D1). One
 * JSON file at the library root; [books] is keyed by book identity (the book folder path
 * relative to the library root, matching Room's book id).
 *
 * Forward-compatible by design: [HomerBookState] will grow bookmarks/overrides fields, and
 * the DI Json is lenient with unknown keys, so newer devices can add fields older ones ignore.
 */
@Serializable
data class HomerIndex(
    val version: Int = SCHEMA_VERSION,
    val books: Map<String, HomerBookState> = emptyMap(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** Per-book synced state. Currently the resume position; extended in later phases. */
@Serializable
data class HomerBookState(
    val mediaId: String,
    val positionMs: Long,
    val updatedAt: Long,
)
