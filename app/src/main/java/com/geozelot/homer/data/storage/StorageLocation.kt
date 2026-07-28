package com.geozelot.homer.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
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

    /**
     * The active custom location token, or null for the default area. A token is either a SAF tree
     * `content://…` Uri or an absolute filesystem path (all-files access). The all-files path wins
     * if both are somehow set.
     */
    suspend fun currentLocation(): String? =
        librarySettings.customStoragePath.first() ?: librarySettings.customStorageUri.first()

    /** The active storage area, honouring permissions; falls back to the default if a grant is lost. */
    suspend fun area(): StorageArea {
        librarySettings.customStoragePath.first()?.let { path ->
            if (hasAllFilesAccess()) return FileStorageArea(File(path), sanitize = true)
        }
        librarySettings.customStorageUri.first()?.let(Uri::parse)?.let { uri ->
            if (hasPermission(uri)) return SafStorageArea(context, uri)
        }
        return defaultArea
    }

    /**
     * Builds an area for an explicit [token] (see [currentLocation]) — null → the default area,
     * a `content://` token → SAF, any other string → a filesystem path (all-files). Used by the
     * migrator to hold the source and target areas at once.
     */
    fun areaFor(token: String?): StorageArea = when {
        token == null -> defaultArea
        token.startsWith("content://") -> SafStorageArea(context, Uri.parse(token))
        else -> FileStorageArea(File(token), sanitize = true) // a public path needs safe names
    }

    /** Whether the app holds all-files access (needed for a filesystem-path location). */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** Takes a durable read/write grant on a SAF tree (idempotent). */
    fun takePersistable(uriString: String) {
        context.contentResolver.takePersistableUriPermission(Uri.parse(uriString), RW_FLAGS)
    }

    /** Releases a previously-held SAF grant (best-effort). */
    fun releasePersistable(uriString: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), RW_FLAGS) }
    }

    /**
     * Commits the active storage location to [token]: null = default (clears both custom kinds),
     * a `content://` = a SAF folder (takes the grant), anything else = a filesystem path. The two
     * custom kinds are mutually exclusive, so setting one clears the other.
     */
    suspend fun commit(token: String?) {
        when {
            token == null -> {
                librarySettings.setCustomStoragePath(null)
                librarySettings.setCustomStorageUri(null)
            }
            token.startsWith("content://") -> {
                context.contentResolver.takePersistableUriPermission(Uri.parse(token), RW_FLAGS)
                librarySettings.setCustomStoragePath(null)
                librarySettings.setCustomStorageUri(token)
            }
            else -> {
                librarySettings.setCustomStorageUri(null)
                librarySettings.setCustomStoragePath(token)
            }
        }
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
