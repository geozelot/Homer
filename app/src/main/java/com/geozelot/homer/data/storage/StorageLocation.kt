package com.geozelot.homer.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.geozelot.homer.data.settings.LibrarySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the single, siloed root for all of Homer's on-device data — offline downloads, the
 * cover cache, and a local `.homer` mirror — under one `Homer/` folder, exposed as a
 * backend-agnostic [StorageArea].
 *
 * Default: app-external storage (`Android/data/<pkg>/files/Homer`, or internal `filesDir` if the
 * device has no external files dir) — no permission, gone on uninstall. Custom: a user-chosen SAF
 * folder ([SafStorageArea]) whose contents survive uninstall and are user-accessible; selected
 * when [LibrarySettings.customStorageUri] is set and its permission is still held (a reinstall
 * drops the permission, so it falls back to the default until the user re-picks the folder).
 */
@Singleton
class StorageLocation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val librarySettings: LibrarySettings,
) {
    private val defaultRoot: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "Homer").apply { mkdirs() }

    private val defaultArea: StorageArea = FileStorageArea(defaultRoot)

    /** Old internal download location, kept only so the one-time relocation can reclaim its space. */
    private val legacyDownloads = File(context.filesDir, "downloads")

    /** The active storage area: the custom SAF folder if configured and still permitted, else the default. */
    suspend fun area(): StorageArea {
        val custom = librarySettings.customStorageUri.first()?.let(Uri::parse)
        return if (custom != null && hasPermission(custom)) SafStorageArea(context, custom) else defaultArea
    }

    /** True when a custom folder is configured AND its permission is still held. */
    suspend fun customActive(): Boolean {
        val custom = librarySettings.customStorageUri.first()?.let(Uri::parse) ?: return false
        return hasPermission(custom)
    }

    /** The configured custom folder Uri string, or null when on the default area. */
    suspend fun currentCustomUri(): String? = librarySettings.customStorageUri.first()

    /**
     * Builds an area for an explicit location — null → the default app-external area, else a SAF
     * area for [uriString]. Used by the migrator to hold the source and target areas at once.
     * The caller must hold a persisted permission for a custom uri (see [takePersistable]).
     */
    fun areaFor(uriString: String?): StorageArea =
        if (uriString == null) defaultArea else SafStorageArea(context, Uri.parse(uriString))

    /** Takes a durable read/write grant on a SAF tree (idempotent). */
    fun takePersistable(uriString: String) {
        context.contentResolver.takePersistableUriPermission(Uri.parse(uriString), RW_FLAGS)
    }

    /** Releases a previously-held SAF grant (best-effort). */
    fun releasePersistable(uriString: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), RW_FLAGS) }
    }

    /** Persists a user-picked SAF tree as the storage folder, taking a durable read/write grant. */
    suspend fun setCustomFolder(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(treeUri, RW_FLAGS)
        librarySettings.setCustomStorageUri(treeUri.toString())
    }

    /** Reverts to the default app-external storage, releasing any held custom grant. */
    suspend fun useDefault() {
        librarySettings.customStorageUri.first()?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), RW_FLAGS) }
        }
        librarySettings.setCustomStorageUri(null)
    }

    private fun hasPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

    /** Removes the pre-relocation internal download files (best-effort). */
    fun deleteLegacyDownloads() {
        runCatching { legacyDownloads.deleteRecursively() }
    }

    private companion object {
        const val RW_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
