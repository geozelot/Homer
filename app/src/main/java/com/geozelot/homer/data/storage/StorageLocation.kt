package com.geozelot.homer.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single, siloed root for all of Homer's on-device data — offline downloads, the cover
 * cache, and (later) a local `.homer` mirror — under one `Homer/` folder.
 *
 * The default is app-external storage (`Android/data/<pkg>/files/Homer`, or internal `filesDir`
 * if the device has no external files dir): no permission needed, larger than internal, and gone
 * on uninstall. A user-chosen custom SAF location (survives uninstall, user-accessible) is a
 * later phase; this class owns the default File root and the legacy internal dirs so a one-time
 * migration can move off them.
 */
@Singleton
class StorageLocation @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "Homer").apply { mkdirs() }

    val downloadsDir: File = File(root, "downloads").apply { mkdirs() }
    val coversDir: File = File(root, "covers").apply { mkdirs() }

    /** Old internal locations, kept only so the one-time relocation can reclaim their space. */
    private val legacyDownloads = File(context.filesDir, "downloads")

    /** Removes the pre-relocation internal download files (best-effort). */
    fun deleteLegacyDownloads() {
        runCatching { legacyDownloads.deleteRecursively() }
    }
}
