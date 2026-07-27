package com.geozelot.homer.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the single, siloed root for all of Homer's on-device data — offline downloads, the
 * cover cache, and (later) a local `.homer` mirror — under one `Homer/` folder, exposed as a
 * backend-agnostic [StorageArea].
 *
 * The default is app-external storage (`Android/data/<pkg>/files/Homer`, or internal `filesDir`
 * if the device has no external files dir): no permission needed, larger than internal, gone on
 * uninstall. A user-chosen custom SAF folder (survives uninstall, user-accessible) is added in a
 * later phase and will be selected here when configured.
 */
@Singleton
class StorageLocation @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val defaultRoot: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "Homer").apply { mkdirs() }

    private val defaultArea: StorageArea = FileStorageArea(defaultRoot)

    /** Old internal download location, kept only so the one-time relocation can reclaim its space. */
    private val legacyDownloads = File(context.filesDir, "downloads")

    /** The active storage area. (SAF custom backend selection is added in a later phase.) */
    suspend fun area(): StorageArea = defaultArea

    /** Removes the pre-relocation internal download files (best-effort). */
    fun deleteLegacyDownloads() {
        runCatching { legacyDownloads.deleteRecursively() }
    }
}
