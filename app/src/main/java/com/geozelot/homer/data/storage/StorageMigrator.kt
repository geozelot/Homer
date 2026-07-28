package com.geozelot.homer.data.storage

import android.net.Uri
import android.util.Log
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.metadata.CoverCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves all of Homer's on-device data — offline downloads, cached covers, and the local `.homer`
 * progress mirror — from one [StorageArea] to another when the user changes the storage folder.
 *
 * The move is driven off Room (every downloaded file's path and every cover's path are known), so
 * no directory listing is needed. Each file is copied then deleted from the source individually
 * (copy-verify-delete), so peak disk use stays bounded and an interruption can resume — a re-run
 * skips files already present at the target. The library itself lives on Nextcloud and is
 * untouched; only the local cache moves.
 *
 * Progress is published on [progress] for the UI and mirrored to an [onProgress] callback for the
 * worker's notification.
 */
@Singleton
class StorageMigrator @Inject constructor(
    private val storageLocation: StorageLocation,
    private val bookDao: BookDao,
    private val audioFileDao: AudioFileDao,
    private val coverCache: CoverCache,
    private val localMirror: LocalMirror,
) {
    data class Progress(val label: String, val done: Int, val total: Int)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private data class CoverJob(val bookId: String, val rel: String, val custom: Boolean)

    /**
     * Moves everything from [sourceUri] (null = current default area) to [targetUri] (null =
     * default). With [overwrite] the target's existing Homer data is cleared first (used when the
     * user chooses to replace an existing library in the chosen folder).
     */
    suspend fun migrate(
        sourceUri: String?,
        targetUri: String?,
        overwrite: Boolean,
        onProgress: suspend (Progress) -> Unit = {},
    ) {
        if (sourceUri == targetUri) return // same location — nothing to move
        val source = storageLocation.areaFor(sourceUri)
        val target = storageLocation.areaFor(targetUri)

        try {
            // Enumerate the work from Room: existing download files, then covers.
            val books = bookDao.getAll()
            val downloadRels = buildList {
                for (b in books) for (f in audioFileDao.findForBook(b.id)) {
                    val rel = "downloads/${f.relativePath}"
                    if (source.exists(rel)) add(rel)
                }
            }
            val coverJobs = buildList {
                for (b in books) {
                    if (b.localCoverPath != null) {
                        val rel = "covers/${coverCache.coverName(b.id)}"
                        if (source.exists(rel)) add(CoverJob(b.id, rel, custom = false))
                    }
                    b.customCoverPath?.let { path ->
                        val name = Uri.parse(path).lastPathSegment?.substringAfterLast('/')
                        if (name != null) {
                            val rel = "covers/$name"
                            if (source.exists(rel)) add(CoverJob(b.id, rel, custom = true))
                        }
                    }
                }
            }
            val total = downloadRels.size + coverJobs.size

            // Copy each file independently: a single unwritable name (e.g. a character the target
            // volume rejects) must not abort the whole move. [overwrite] re-copies files that
            // already exist at the target (this device wins) but NEVER bulk-deletes the target
            // first — so a move that finds little/nothing at the source can't wipe good data.
            var done = 0
            var failures = 0
            for (rel in downloadRels) {
                runCatching { copyThenDeleteSource(source, target, rel, overwrite) }
                    .onFailure { failures++; Log.w(TAG, "skipped $rel", it) }
                report("Moving downloads", ++done, total, onProgress)
            }
            for (job in coverJobs) {
                runCatching { copyThenDeleteSource(source, target, job.rel, overwrite) }
                    .onFailure { failures++; Log.w(TAG, "skipped ${job.rel}", it) }
                report("Moving covers", ++done, total, onProgress)
            }
            if (failures > 0) Log.w(TAG, "$failures file(s) could not be moved to the new location")

            // Clean up the source's now-empty Homer subtree.
            source.delete("downloads"); source.delete("covers"); source.delete(".homer")

            report("Finishing…", done, total, onProgress)
            // The active location was already committed by the caller (so switching is instant and
            // not gated on this worker running). Repoint the cover paths (absolute Uris) at the new
            // area now that its files are in place.
            val targetArea = storageLocation.areaFor(targetUri)
            for (job in coverJobs) {
                val u = targetArea.uri(job.rel)?.toString() ?: continue
                if (job.custom) bookDao.updateCustomCover(job.bookId, u) else bookDao.updateLocalCover(job.bookId, u)
            }
            // Recompute download status against the new area and refresh the progress mirror.
            localMirror.adoptDownloads()
            localMirror.export()
            Log.i(TAG, "migration done: ${downloadRels.size} files, ${coverJobs.size} covers → ${targetUri ?: "default"}")
        } catch (e: Exception) {
            Log.w(TAG, "storage migration failed", e)
            throw e
        } finally {
            _progress.value = null
        }
    }

    /**
     * Copies [rel] from [source] to [target] (skipping if already present unless [overwrite]),
     * verifies it landed, then removes it from [source]. If the source file isn't there, nothing
     * is copied and — crucially — the target's existing copy is left untouched.
     */
    private suspend fun copyThenDeleteSource(source: StorageArea, target: StorageArea, rel: String, overwrite: Boolean) {
        if (overwrite || !target.exists(rel)) {
            val input = source.openInputStream(rel) ?: return // nothing at source → don't touch target
            target.writeStream(rel) { out -> input.use { it.copyTo(out) } }
        }
        if (target.exists(rel)) source.delete(rel)
    }

    private suspend fun report(label: String, done: Int, total: Int, onProgress: suspend (Progress) -> Unit) {
        val p = Progress(label, done, total)
        _progress.value = p
        onProgress(p)
    }

    private companion object {
        const val TAG = "HomerStore"
    }
}
