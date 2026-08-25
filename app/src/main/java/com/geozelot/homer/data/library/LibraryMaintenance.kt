package com.geozelot.homer.data.library

import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.settings.LibrarySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this device is one of the devices that *maintains* the library, or one that only reads it.
 *
 * The distinction decides who pays for the expensive work. Crawling a library is one request per
 * folder; measuring it is one per file, thousands of them. That cost is worth paying once by
 * somebody who can publish the answer, and worth paying by nobody at all otherwise — a device that
 * cannot write the shared index would spend a night measuring a library and share none of it, and
 * every other reader would do the same measuring over again.
 *
 * So there are two situations, and only two:
 *
 *  - **Reading a shared index it cannot write.** It reads structure, durations, genres, languages
 *    and cover-cache pointers from the index, and runs no crawl, no measure pass and no cover
 *    extraction. Whoever maintains the index is trusted to notice new and deleted books.
 *  - **Everything else** — an index it can write, or no index at all. It does the work, because it
 *    is either sharing the result or the only one who wants it.
 */
@Singleton
class LibraryMaintenance @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val credentialStore: CredentialStore,
) {
    /**
     * True while this device may run the expensive passes.
     *
     * With the index switched off this is always true: nobody else is being served, so nobody else
     * is being made to repeat the work, and the device has no other way to learn what is in the
     * library.
     */
    val maintains: Flow<Boolean> = combine(
        librarySettings.sharedCatalogEnabled,
        librarySettings.libraryWritable,
        credentialStore.credentials,
    ) { indexed, writable, credentials ->
        when {
            !indexed -> true
            credentials == null -> false
            // A share link is the only backend that can be read-only. An account's own folder is
            // always writable by the account that owns it.
            credentials.kind == WebDavKind.SHARE -> writable
            else -> true
        }
    }

    /** The same question, answered once — for a worker or a request that cannot collect a flow. */
    suspend fun maintainsNow(): Boolean = maintains.first()

    /**
     * True when this device reads an index somebody else keeps.
     *
     * The inverse of [maintains] only while an index is in use, which is what the UI needs: "you
     * are a reader here" is a different statement from "you have no index".
     */
    val readsOnly: Flow<Boolean> = combine(librarySettings.sharedCatalogEnabled, maintains) { indexed, mayMaintain ->
        indexed && !mayMaintain
    }
}
