package com.geozelot.homer.data.sync

import kotlinx.serialization.Serializable

/**
 * The central `.homer` manifest — the authoritative cross-device store (SCOPE D1). One
 * JSON file pinned at the WebDAV files-root (`.homer/index.json`), independent of the
 * configurable library folder so changing that folder never moves the manifest. [books] is
 * keyed by book identity (the full files-root folder path, matching Room's book id).
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

/**
 * Per-book synced state. The resume position and the bookmark set are reconciled
 * independently, each last-write-wins on its own timestamp ([updatedAt] for the
 * position, [bookmarksUpdatedAt] for the bookmark list). [mediaId] is null when the
 * book has bookmarks but no saved position.
 */
@Serializable
data class HomerBookState(
    val mediaId: String? = null,
    val positionMs: Long = 0,
    val updatedAt: Long = 0,
    val bookmarks: List<HomerBookmark> = emptyList(),
    val bookmarksUpdatedAt: Long = 0,
    val override: HomerOverride? = null,
) {
    val hasPosition: Boolean get() = mediaId != null && updatedAt > 0
}

/** A synced bookmark. No cross-device id: the whole list is replaced last-write-wins. */
@Serializable
data class HomerBookmark(
    val mediaId: String,
    val positionMs: Long,
    val chapterTitle: String,
    val label: String? = null,
    val createdAt: Long,
)

/**
 * A synced metadata/hide override, reconciled last-write-wins on [updatedAt]. A "cleared"
 * override is an all-null, not-hidden entry with a fresh timestamp, so a reset propagates
 * to other devices rather than being resurrected.
 */
@Serializable
data class HomerOverride(
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val genre: String? = null,
    val tags: List<String> = emptyList(),
    val finished: Boolean? = null,
    /** Per-book playback mode: null = follow global, true = download on play, false = stream. */
    val downloadOnPlay: Boolean? = null,
    val hidden: Boolean = false,
    val updatedAt: Long,
)
