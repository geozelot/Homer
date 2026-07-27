package com.geozelot.homer.data.library

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.sync.HomerCatalog
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A Homer-bearing location found by the discovery sweep, with what it carries. */
data class DiscoveredLibrary(
    /** Files-root-relative folder path ("" = the account's files root). */
    val relativePath: String,
    val kind: Kind,
    /** A private progress manifest (`.homer/index.json`) is present. */
    val hasPrivateIndex: Boolean,
    /** A shared library catalog (`.homer/catalog.json`) is present. */
    val hasSharedCatalog: Boolean,
    /** Nextcloud owner of the folder, if the server exposed it. */
    val owner: String?,
    /** Book count read from the shared catalog, if present. */
    val bookCount: Int?,
    /** True if this is the folder currently configured as the library root. */
    val isCurrentRoot: Boolean,
) {
    enum class Kind { FILES_ROOT, LIBRARY_ROOT, SHARED_FOLDER }
}

/**
 * Sweeps the server for Homer-bearing folders — the account's files root (private progress), the
 * configured library root, and shared-with-me top-level folders (a bounded, marker-based probe;
 * NOT a full-tree crawl). Read-only and best-effort: offline or on any error it returns whatever
 * it managed to find. Drives the Library & Sync screen so it's clear what libraries exist and
 * which one is in use.
 */
@Singleton
class LibraryDiscovery @Inject constructor(
    private val webDavClient: WebDavClient,
    private val credentialStore: CredentialStore,
    private val librarySettings: LibrarySettings,
    private val networkMonitor: NetworkMonitor,
    private val json: Json,
) {
    suspend fun discover(): List<DiscoveredLibrary> = withContext(Dispatchers.IO) {
        if (credentialStore.credentials.value == null || !networkMonitor.isOnline()) {
            return@withContext emptyList()
        }
        val libraryRoot = librarySettings.libraryRoot.first().trim('/')
        // Keyed by path so the library-root / files-root / shared passes don't duplicate an entry.
        val found = LinkedHashMap<String, DiscoveredLibrary>()

        // The account's own files root — carries the private progress index (Tier 2).
        probe("", DiscoveredLibrary.Kind.FILES_ROOT, libraryRoot, checkIndex = true)?.let { found[""] = it }

        // The configured library root — always surfaced (even with no catalog yet), since it's the
        // folder in use. When it IS the files root, the entry above already covers it.
        if (libraryRoot.isNotEmpty()) {
            probe(libraryRoot, DiscoveredLibrary.Kind.LIBRARY_ROOT, libraryRoot, always = true)
                ?.let { found[libraryRoot] = it }
        }

        // Shared-with-me / sibling folders: one bounded pass over the files-root's top-level
        // folders, each probed for a shared catalog. Not recursive.
        val children = runCatching { webDavClient.propfind("", depth = 1) }.getOrElse {
            Log.w(TAG, "discovery: listing files root failed", it)
            emptyList()
        }.filter { it.isCollection && it.path.isNotEmpty() }
        if (children.size > MAX_TOPLEVEL_PROBES) {
            Log.w(TAG, "discovery: ${children.size} top-level folders; probing first $MAX_TOPLEVEL_PROBES")
        }
        for (child in children.take(MAX_TOPLEVEL_PROBES)) {
            if (found.containsKey(child.path)) continue
            probe(child.path, DiscoveredLibrary.Kind.SHARED_FOLDER, libraryRoot)?.let { found[child.path] = it }
        }

        found.values.toList()
    }

    /**
     * Probes one folder for `.homer` markers. Returns null when nothing Homer-related is present
     * (unless [always], used for the configured root so it's shown regardless). [checkIndex]
     * additionally looks for the private progress manifest (only meaningful at the files root).
     */
    private suspend fun probe(
        relativePath: String,
        kind: DiscoveredLibrary.Kind,
        libraryRoot: String,
        checkIndex: Boolean = false,
        always: Boolean = false,
    ): DiscoveredLibrary? {
        val base = if (relativePath.isEmpty()) HOMER_DIR else "$relativePath/$HOMER_DIR"
        val catalogText = runCatching { webDavClient.getText("$base/catalog.json") }.getOrNull()?.content
        val hasCatalog = !catalogText.isNullOrBlank()
        val hasIndex = checkIndex &&
            runCatching { webDavClient.getText("$base/index.json") }.getOrNull() != null

        if (!hasCatalog && !hasIndex && !always) return null

        val bookCount = catalogText?.let {
            runCatching { json.decodeFromString<HomerCatalog>(it).books.size }.getOrNull()
        }
        val owner = if (hasCatalog) runCatching { webDavClient.fetchOwnerId(relativePath) }.getOrNull() else null

        return DiscoveredLibrary(
            relativePath = relativePath,
            kind = kind,
            hasPrivateIndex = hasIndex,
            hasSharedCatalog = hasCatalog,
            owner = owner,
            bookCount = bookCount,
            isCurrentRoot = relativePath == libraryRoot,
        )
    }

    private companion object {
        const val TAG = "HomerScan"
        const val HOMER_DIR = ".homer"
        const val MAX_TOPLEVEL_PROBES = 60
    }
}
