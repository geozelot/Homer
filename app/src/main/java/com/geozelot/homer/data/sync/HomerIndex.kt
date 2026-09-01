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
        /**
         * 2: the override shrank to the reader's own state.
         *
         * Nothing reads this — the DI Json ignores unknown keys, so every version of the manifest
         * parses as every other one — and it is bumped anyway, because it is the only place the
         * shape is written down. See [HomerOverride] for what left and why.
         */
        const val SCHEMA_VERSION = 2
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
 * What THIS READER has decided about a book, reconciled last-write-wins on [updatedAt].
 *
 * ## What is deliberately not here any more
 *
 * Title, author, series, series index, genre and tags used to travel in this object, and that was
 * the second channel for something that already had one. `corrections.json` beside the library
 * carries the same fields plus `collection`, `collectionIndex` and `language` — and because this
 * object never had those three, `toEntity` (which built a whole override row from scratch) wrote
 * them as null every time the manifest's copy won the last-write-wins compare. Putting a book into
 * a collection by hand, numbering it, or setting its language could therefore be undone by a sync
 * that was only supposed to be carrying a reading position.
 *
 * Total for a device on a read-only share with a personal sync account: it can write this file but
 * not the library's `corrections.json`, so the lossy channel was its ONLY channel.
 *
 * The boundary now matches the one `FacetMapping.correctionOf` already draws from the other side —
 * "finished, hidden and downloadOnPlay are never published, on a folder shared with other people
 * they would say who has read what". Those three are exactly what is left here. This file is pinned
 * to the files-root and keyed to the SYNC ACCOUNT, which is right for what one person has read and
 * wrong for what a shared library is called.
 *
 * A metadata correction now reaches other devices only through the shared index. With sharing off,
 * corrections stay on the device that made them — which is what "don't touch the shared folder"
 * has to mean.
 *
 * ⚠ A build older than schema 2 reading this file finds no bibliographic fields and, on its own
 * rules, clears them locally. Do not run a pre-2.1 Homer against a manifest this one has written.
 */
@Serializable
data class HomerOverride(
    /** Tri-state finished flag: null = auto (derive from position), true/false = forced. */
    val finished: Boolean? = null,
    /** Per-book playback mode: null = follow global, true = download on play, false = stream. */
    val downloadOnPlay: Boolean? = null,
    val hidden: Boolean = false,
    val updatedAt: Long,
)
