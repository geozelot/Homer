package com.geozelot.homer.data.sync.facet

import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.DavRead
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The three WebDAV operations a facet file needs, and nothing else.
 *
 * A seam rather than a layer: [FacetStore] holds all the interesting decisions — when an ETag may
 * be cached, what a failed parse means, how a lost write is retried — and those are worth testing
 * without a server. [WebDavClient] is a concrete class used in a dozen places, so it is wrapped
 * here instead of being turned into an interface for one caller's benefit.
 */
interface FacetTransport {
    /** Conditional GET. [DavRead.NotModified] when [ifNoneMatch] still matches the server's ETag. */
    suspend fun read(path: String, ifNoneMatch: String?): DavRead

    /**
     * PUT, returning the new ETag if the server reported one.
     *
     * Exactly one guard applies. [ifMatch] is for updating what we read; [onlyIfAbsent] is for
     * creating a file we believe nobody has yet. With neither, the write is unconditional.
     *
     * @throws com.geozelot.homer.data.webdav.PreconditionFailedException when the guard fails —
     *   somebody got there first, and the write must be retried against what is there now.
     */
    suspend fun write(path: String, content: String, ifMatch: String?, onlyIfAbsent: Boolean = false): String?

    suspend fun ensureDir(path: String)
}

@Singleton
class WebDavFacetTransport @Inject constructor(
    private val dav: WebDavClient,
) : FacetTransport {

    override suspend fun read(path: String, ifNoneMatch: String?): DavRead =
        dav.readText(path, ifNoneMatch)

    override suspend fun write(path: String, content: String, ifMatch: String?, onlyIfAbsent: Boolean): String? =
        dav.putText(path, content, ifMatch, ifNoneMatch = if (onlyIfAbsent) "*" else null)

    override suspend fun ensureDir(path: String) {
        dav.mkcol(path)
    }
}

/**
 * Where the library lives, as a files-root-relative path.
 *
 * The other seam. It exists so [FacetStore] can be exercised without DataStore, and therefore
 * without Android — the store's rules about ETags and lost writes are worth testing on the JVM.
 */
interface LibraryRootSource {
    suspend fun root(): String
}

@Singleton
class SettingsLibraryRoot @Inject constructor(
    private val librarySettings: LibrarySettings,
) : LibraryRootSource {
    override suspend fun root(): String = librarySettings.libraryRoot.first().trim('/')
}
