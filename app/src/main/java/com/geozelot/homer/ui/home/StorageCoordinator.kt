package com.geozelot.homer.ui.home

import android.util.Log
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.download.DownloadStorage
import com.geozelot.homer.data.library.LibraryIndexManager
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.storage.LocalMirror
import com.geozelot.homer.data.storage.StorageLocation
import com.geozelot.homer.data.storage.StorageMigrationManager
import com.geozelot.homer.data.storage.StorageMigrator
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** A storage-folder change awaiting the user's decision when the target already holds a library. */
data class PendingStorageChange(val source: String?, val target: String?)

// ── Where downloads live ──────────────────────────────────────────────────────

/**
 * Everything [HomeViewModel] does about WHERE the library's local data sits: pointing storage at
 * a folder, moving data between folders, adopting a library already found in one, and noticing
 * that a granted folder has become unreachable.
 *
 * A plain collaborator rather than more ViewModel: the rules here — grant handling, load vs
 * replace, what a move must carry — are about folders, not about the screen, and they were a
 * third of the ViewModel's dependencies. Suspend functions throughout; the ViewModel supplies
 * the scope.
 */
class StorageCoordinator @Inject constructor(
    private val storageLocation: StorageLocation,
    private val storageMigrationManager: StorageMigrationManager,
    private val storageMigrator: StorageMigrator,
    private val downloadStorage: DownloadStorage,
    private val downloadDao: DownloadDao,
    private val bookDao: BookDao,
    private val localMirror: LocalMirror,
    private val libraryIndexManager: LibraryIndexManager,
    private val librarySettings: LibrarySettings,
) {
    /** Live progress of a storage move (null when none is running) — drives a blocking overlay. */
    val migrationProgress: StateFlow<StorageMigrator.Progress?> get() = storageMigrator.progress

    private val _storageAccessLost = MutableStateFlow(false)

    /**
     * True when a custom storage folder is configured but Homer can no longer reach it.
     *
     * Not derived from the settings flows: they only say which folder was CHOSEN, and the grant on
     * it can be withdrawn while the app is not running — so this is refreshed by asking the system,
     * on the screens that state where downloads go.
     */
    val storageAccessLost: StateFlow<Boolean> = _storageAccessLost.asStateFlow()

    private val _pendingChange = MutableStateFlow<PendingStorageChange?>(null)
    /** Set when a chosen folder already holds a Homer library and the user must pick load vs replace. */
    val pendingChange: StateFlow<PendingStorageChange?> = _pendingChange.asStateFlow()

    /** Whether the app currently holds all-files access (for the storage folder browser). */
    fun hasAllFilesAccess(): Boolean = storageLocation.hasAllFilesAccess()

    /** Re-asks whether the chosen storage folder is still reachable. Cheap; call it on entry. */
    suspend fun refreshAccess() {
        val lost = storageLocation.customLocationUnavailable()
        if (lost != _storageAccessLost.value) {
            Log.w(TAG_STORAGE, "custom storage folder reachable=${!lost}")
        }
        _storageAccessLost.value = lost
    }

    /**
     * One-time relocation to the siloed Homer/ storage root. Offline downloads lived in internal
     * storage; drop them (start fresh, per design) so they re-download into the new location, and
     * reclaim the old files. Covers relocate lazily as new ones are extracted.
     */
    suspend fun relocateLegacyDownloadsOnce() {
        if (!librarySettings.storageRelocated.first()) {
            downloadDao.deleteAll()
            storageLocation.deleteLegacyDownloads()
            librarySettings.setStorageRelocated(true)
        }
    }

    /**
     * Deletes every downloaded file and forgets the download state.
     *
     * The only way to reclaim the space, and the only way to be rid of files left behind by a
     * library this device no longer has — signing into a different account orphans them, and
     * nothing else on disk knows they are orphans. Files first, rows second: a process death in
     * between then leaves a row still pointing at what is left, for a later pass to finish.
     */
    suspend fun deleteAllDownloads() {
        runCatching { downloadStorage.deleteAll() }
            .onFailure { Log.w(TAG_STORAGE, "could not delete the downloads folder", it) }
        downloadDao.deleteAll()
    }

    /** A storage-location token is either a SAF `content://` tree or an absolute filesystem path. */
    private fun isSafToken(token: String?) = token?.startsWith("content://") == true

    /**
     * Points storage at [target] (a SAF tree, an absolute path, or null for the default). If the
     * folder already holds a Homer library the user is asked what to do ([pendingChange]);
     * otherwise everything is moved into it.
     */
    suspend fun requestChange(target: String?) {
        val source = storageLocation.currentLocation()
        Log.d(TAG_STORAGE, "requestStorageChange: source=$source target=$target")
        if (source == target) return // already there
        // A SAF folder needs a durable grant taken before we can read/write it (and some pickers
        // refuse certain folders, throwing here). An all-files path needs no per-folder grant.
        if (isSafToken(target)) {
            try {
                storageLocation.takePersistable(target!!)
                Log.d(TAG_STORAGE, "took persistable permission for $target")
            } catch (e: Exception) {
                Log.w(TAG_STORAGE, "could not take a durable permission for the chosen folder", e)
                return
            }
        }
        val hasExisting = runCatching {
            val area = storageLocation.areaFor(target)
            area.exists(MIRROR_MARKER)
        }.getOrDefault(false)
        if (hasExisting) {
            // The folder already has Homer data — let the user choose load vs replace.
            Log.d(TAG_STORAGE, "target already has a Homer library; prompting load-vs-replace")
            _pendingChange.value = PendingStorageChange(source, target)
        } else {
            // Empty target: switch the location NOW (synchronous + reliable, independent of the
            // worker), then move any existing local data across in the background.
            commitLocation(source, target)
            storageMigrationManager.migrate(source, target, overwrite = false)
        }
    }

    /** Commits the active storage location immediately and releases the old SAF grant if any. */
    private suspend fun commitLocation(source: String?, target: String?) {
        storageLocation.commit(target)
        if (isSafToken(source) && source != target) storageLocation.releasePersistable(source!!)
        Log.d(TAG_STORAGE, "storage location committed to ${target ?: "default"}")
    }

    /** Adopt the library already in the chosen folder (merge progress, keep its downloads). */
    suspend fun loadPending() {
        val p = _pendingChange.value ?: return
        _pendingChange.value = null
        // Before commitLocation, which releases the old folder's SAF grant and makes it
        // unreadable. Hand-picked covers are the one part of the local cache that isn't
        // re-derivable, so they're carried across rather than dropped.
        storageMigrator.carryCustomCovers(p.source, p.target)
        commitLocation(p.source, p.target)
        adoptCurrentArea()
    }

    /** Overwrite the chosen folder's Homer data with this device's (switch now, move in background). */
    suspend fun replacePending() {
        val p = _pendingChange.value ?: return
        _pendingChange.value = null
        commitLocation(p.source, p.target)
        storageMigrationManager.migrate(p.source, p.target, overwrite = true)
    }

    /** Abandon a pending storage change, releasing the SAF grant taken to probe the folder. */
    suspend fun cancelPending() {
        val p = _pendingChange.value ?: return
        _pendingChange.value = null
        val target = p.target
        if (!isSafToken(target)) return
        if (target != storageLocation.currentLocation()) storageLocation.releasePersistable(target!!)
    }

    /**
     * Adopts whatever the (now active) area already holds: import its progress mirror (LWW),
     * recompute download status against it, and re-fetch covers (their old Uris are stale). Used
     * when the user loads an existing library in the chosen folder rather than moving into it.
     *
     * Only *detected* art is discarded here — it costs one enrichment pass to rebuild. Custom
     * covers are the user's own images and can't be recovered, so [loadPending] copies them
     * into the new area first (see [StorageMigrator.carryCustomCovers]).
     */
    private suspend fun adoptCurrentArea() {
        bookDao.resetCoverArt()
        localMirror.import()
        localMirror.adoptDownloads()
        libraryIndexManager.fetchMissingCovers()
    }

    private companion object {
        const val MIRROR_MARKER = "progress.json"
        const val TAG_STORAGE = "HomerStore"
    }
}
