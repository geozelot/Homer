package com.geozelot.homer.data.library

import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.settings.LibrarySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where this device stands in this library: what it is, what it may do, and why not.
 *
 * ## Who pays for the expensive work
 *
 * Crawling a library is one request per folder; measuring it is one per file, thousands of them.
 * That cost is worth paying once by somebody who can publish the answer, and worth paying by nobody
 * at all otherwise — a device that cannot write the shared index would spend a night measuring a
 * library and share none of it, and every other reader would do the same measuring over again.
 *
 * ## One derivation, not six
 *
 * This class holds the whole answer as a [LibraryStanding], and everything that used to work it out
 * for itself now asks. There were five such places, they took different inputs, and they could
 * therefore disagree — which is what made *Try missing covers* silently do nothing. The owner's
 * rules ([com.geozelot.homer.data.sync.facet.LibraryPolicy]) are a sixth input, and adding it to
 * five separate derivations would have been the same bug waiting to happen.
 *
 * The derivation itself lives in [LibraryStanding.of], as a function of plain facts, so it can be
 * exercised over every combination without a server or a device.
 */
@Singleton
class LibraryMaintenance @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val credentialStore: CredentialStore,
) {
    /**
     * Everything about this device's position in the library, recomputed whenever any input moves.
     *
     * The library root is one of those inputs — not because the standing depends on the path, but
     * because a policy resolution is about one folder, and changing the root makes the answer it
     * carries describe somewhere else.
     */
    val standing: Flow<LibraryStanding> = combine(
        credentialStore.credentials,
        librarySettings.sharedCatalogEnabled,
        librarySettings.libraryWritable,
        librarySettings.policyResolution,
        librarySettings.libraryRoot,
    ) { credentials, sharedIndexEnabled, writable, resolution, root ->
        LibraryStanding.of(
            kind = credentials?.kind,
            writable = writable,
            sharedIndexEnabled = sharedIndexEnabled,
            resolution = resolution,
            libraryRoot = root,
        )
    }.distinctUntilChanged()

    /** True while this device may run the expensive passes. */
    val maintains: Flow<Boolean> = standing.map { it.maintains }.distinctUntilChanged()

    /**
     * True when this device reads an index somebody else keeps.
     *
     * Not the inverse of [maintains]: "you are a reader here" is a different statement from "you
     * have no index", and the UI says different things about them.
     */
    val readsOnly: Flow<Boolean> = standing.map { it.readsOnly }.distinctUntilChanged()

    /** The same question, answered once — for a worker or a request that cannot collect a flow. */
    suspend fun maintainsNow(): Boolean = standing.first().maintains

    /** The whole standing, answered once. */
    suspend fun standingNow(): LibraryStanding = standing.first()
}
