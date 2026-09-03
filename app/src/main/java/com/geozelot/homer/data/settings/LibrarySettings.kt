package com.geozelot.homer.data.settings

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geozelot.homer.data.sync.facet.PolicyInForce
import com.geozelot.homer.data.sync.facet.PolicyResolution
import com.geozelot.homer.data.update.UpdateChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared by [LibrarySettings] and [PlaybackSettings] (one file, one instance — DataStore throws if
 * the same file is opened twice).
 *
 * A truncated write (power loss, a kill mid-flush) leaves the preferences file unreadable, and
 * without a corruption handler every single read throws for good — the app can never start again.
 * Resetting to defaults loses only preferences (the library, positions and bookmarks live in Room),
 * so recovering is strictly better than being permanently unlaunchable.
 */
private val Context.settingsDataStore by preferencesDataStore(
    name = "homer_settings",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.w("HomerSettings", "settings file was corrupt; reset to defaults", it)
        emptyPreferences()
    },
)

/**
 * Non-secret app settings, persisted with DataStore. Covers the library root (the WebDAV folder,
 * relative to the files root, where the crawl begins — empty = the whole drive), plus sync toggles,
 * storage location, cover-lookup, security, and view/sort/group preferences.
 */
@Singleton
class LibrarySettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val libraryRoot: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_LIBRARY_ROOT].orEmpty() }

    suspend fun setLibraryRoot(path: String) {
        context.settingsDataStore.edit { it[KEY_LIBRARY_ROOT] = path.trim().trim('/') }
    }

    /** Whether the library renders as a cover grid (true) or a scannable list (false). */
    val gridView: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_GRID_VIEW] ?: true }

    suspend fun setGridView(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_GRID_VIEW] = value }
    }

    /**
     * The collections that read as one numbered run rather than as their threads.
     *
     * A SET of the exceptions, not a value per collection: threaded is the answer for almost every
     * collection almost always, and storing only the ones that differ means a collection that is
     * renamed or disappears takes its entry with it instead of leaving a preference about a shelf
     * that no longer exists.
     *
     * Local, like the grid toggle and the sort — this is how one person wants to look at a shelf,
     * not a fact about the library, so it neither belongs in the shared index nor wants merging.
     */
    val flatCollections: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[KEY_FLAT_COLLECTIONS] ?: emptySet() }

    suspend fun setCollectionFlat(collection: String, flat: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_FLAT_COLLECTIONS] ?: emptySet()
            val next = if (flat) current + collection else current - collection
            if (next.isEmpty()) prefs.remove(KEY_FLAT_COLLECTIONS) else prefs[KEY_FLAT_COLLECTIONS] = next
        }
    }

    
    /** Library sort key: "author" | "title" | "recent" | "duration". */
    val sortMode: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_SORT_MODE] ?: "author" }

    suspend fun setSortMode(value: String) {
        context.settingsDataStore.edit { it[KEY_SORT_MODE] = value }
    }

    /** Sync my listening progress to my account's `.homer/index.json` (cross-device for me). */
    val progressSyncEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_PROGRESS_SYNC] ?: true }

    suspend fun setProgressSyncEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_PROGRESS_SYNC] = value }
    }

    /** Use the shared library catalog + cover cache at the library root's `.homer/`. */
    val sharedCatalogEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_SHARED_CATALOG] ?: false }

    suspend fun setSharedCatalogEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHARED_CATALOG] = value }
    }

    /**
     * Whether the current library backend can be written to (a read-write share, or an account).
     * Set from the share write-probe at resolve time; gates publishing the shared library cache.
     * Defaults true so account libraries (always writable) keep publishing as before.
     */
    val libraryWritable: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_LIBRARY_WRITABLE] ?: true }

    suspend fun setLibraryWritable(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_LIBRARY_WRITABLE] = value }
    }

    /**
     * The library owner's rules, this device's ownership of the folder, and which root both were
     * resolved for — one record, written in one edit.
     *
     * ## Why it is mirrored rather than read live
     *
     * The gates that consult it — may this device crawl, measure, publish an edit — are asked by
     * workers that run offline, and rules that stop applying the moment the network drops would let
     * through exactly the expensive pass they exist to withhold. They are also asked often, and the
     * resolve behind them is several small GETs up the folder tree.
     *
     * ## Why the root is part of it
     *
     * A resolution describes one folder. Changing the library root — adopting a shared library,
     * opening a share link, editing the path — makes it describe somewhere else, and the gap between
     * the change and the next resolve is precisely when a scan gets enqueued. So the root is stored
     * with it and callers can tell "no rules here" from "not asked yet about here"; the second is
     * answered by waiting rather than by crawling.
     */
    val policyResolution: Flow<PolicyResolution> =
        context.settingsDataStore.data.map { prefs ->
            PolicyResolution(
                forRoot = prefs[KEY_POLICY_ROOT],
                owned = prefs[KEY_LIBRARY_OWNED],
                policy = if (prefs[KEY_POLICY_PRESENT] != true) {
                    PolicyInForce.OPEN
                } else {
                    PolicyInForce(
                        sharedIndexRequired = prefs[KEY_POLICY_SHARED_REQUIRED] ?: false,
                        editsAllowed = prefs[KEY_POLICY_EDITS_ALLOWED] ?: true,
                        owner = prefs[KEY_POLICY_OWNER],
                        // "" is a real answer — the files root, or a share's own root — so the
                        // folder cannot double as the presence flag.
                        atFolder = prefs[KEY_POLICY_AT].orEmpty(),
                        understood = prefs[KEY_POLICY_UNDERSTOOD] ?: true,
                    )
                },
                checkedAt = prefs[KEY_POLICY_CHECKED_AT] ?: 0L,
            )
        }

    /**
     * Records what was resolved for [forRoot]. [owned] is null when the server exposed no owner —
     * distinct from false, which is a positive "somebody else's".
     */
    suspend fun setPolicyResolution(
        forRoot: String,
        policy: PolicyInForce,
        owned: Boolean?,
        checkedAt: Long = System.currentTimeMillis(),
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_POLICY_ROOT] = forRoot
            prefs[KEY_POLICY_CHECKED_AT] = checkedAt
            owned?.let { prefs[KEY_LIBRARY_OWNED] = it } ?: prefs.remove(KEY_LIBRARY_OWNED)
            if (policy.atFolder == null) {
                prefs[KEY_POLICY_PRESENT] = false
                prefs.remove(KEY_POLICY_SHARED_REQUIRED)
                prefs.remove(KEY_POLICY_EDITS_ALLOWED)
                prefs.remove(KEY_POLICY_UNDERSTOOD)
                prefs.remove(KEY_POLICY_OWNER)
                prefs.remove(KEY_POLICY_AT)
                return@edit
            }
            prefs[KEY_POLICY_PRESENT] = true
            prefs[KEY_POLICY_SHARED_REQUIRED] = policy.sharedIndexRequired
            prefs[KEY_POLICY_EDITS_ALLOWED] = policy.editsAllowed
            prefs[KEY_POLICY_UNDERSTOOD] = policy.understood
            prefs[KEY_POLICY_AT] = policy.atFolder
            policy.owner?.let { prefs[KEY_POLICY_OWNER] = it } ?: prefs.remove(KEY_POLICY_OWNER)
        }
    }

    /**
     * Highest local change-timestamp already published to the progress manifest. Local state is
     * "dirty" only when a newer write exists, which lets a sync skip the network entirely instead
     * of downloading and re-uploading the whole manifest to discover nothing changed.
     */
    val lastPushedRevision: Flow<Long> =
        context.settingsDataStore.data.map { it[KEY_LAST_PUSHED_REVISION] ?: 0L }

    suspend fun setLastPushedRevision(value: Long) {
        context.settingsDataStore.edit { it[KEY_LAST_PUSHED_REVISION] = value }
    }

    /**
     * ETag of the shared cover folder the last time every art-less book was swept against it.
     * While it's unchanged, a re-sweep cannot find anything new, so the pass skips ~one request
     * per art-less book.
     */
    val lastCoverSweepEtag: Flow<String?> =
        context.settingsDataStore.data.map { it[KEY_LAST_COVER_SWEEP_ETAG] }

    suspend fun setLastCoverSweepEtag(value: String) {
        context.settingsDataStore.edit { it[KEY_LAST_COVER_SWEEP_ETAG] = value }
    }

    /** How the library is shelved: "none" | "author" | "series" | "genre". The stored key and
     *  values keep their original "group" spelling — renaming them would reset the preference. */
    val shelfMode: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_SHELF_MODE] ?: "none" }

    suspend fun setShelfMode(value: String) {
        context.settingsDataStore.edit { it[KEY_SHELF_MODE] = value }
    }

    /**
     * The user's own path templates, newest-authored first, one per line.
     *
     * Stored as text rather than parsed: a template that no longer compiles — because it names a
     * field a later build renamed, or because it was mistyped — has to survive being loaded so the
     * user can see and fix it. Dropping it at read time would make a typo look like the template
     * vanishing.
     *
     * Scoped to the whole library rather than to a subtree. That is a deliberate simplification of
     * the design: one ordered list, tried before the conventional defaults, covers "my library is
     * laid out differently" and "my titles carry the sub-series in brackets", which are the cases
     * that exist. Per-subtree scoping can be added without changing what is stored, since a scope
     * would be a prefix on each line.
     */
    val pathTemplates: Flow<List<String>> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_PATH_TEMPLATES]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

    /**
     * When the templates were last deliberately set.
     *
     * Published alongside them, and compared against what the shared index carries — which is what
     * decides whether this device adopts somebody else's set or keeps its own. Zero when nobody here
     * has ever written one, so any published set wins.
     */
    val pathTemplatesEditedAt: Flow<Long> =
        context.settingsDataStore.data.map { it[KEY_PATH_TEMPLATES_AT] ?: 0L }

    suspend fun setPathTemplates(templates: List<String>, editedAt: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit { prefs ->
            val cleaned = templates.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) prefs.remove(KEY_PATH_TEMPLATES)
            else prefs[KEY_PATH_TEMPLATES] = cleaned.joinToString("\n")
            prefs[KEY_PATH_TEMPLATES_AT] = editedAt
        }
    }

    /**
     * When corrections were last successfully published to the shared index.
     *
     * What separates "there are corrections" from "there are corrections nobody else has seen". The
     * count of corrections never falls — a published edit is still an edit — so keying the Publish
     * control on it left the control looking permanently pending however many times it had run.
     */
    val correctionsPublishedAt: Flow<Long> =
        context.settingsDataStore.data.map { it[KEY_CORRECTIONS_PUBLISHED_AT] ?: 0L }

    suspend fun setCorrectionsPublishedAt(value: Long) {
        context.settingsDataStore.edit { it[KEY_CORRECTIONS_PUBLISHED_AT] = value }
    }

    /** Whether series are drawn stacked or flat: "stacked" | "flat". Independent of shelving. */
    val seriesMode: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_SERIES_MODE] ?: "stacked" }

    suspend fun setSeriesMode(value: String) {
        context.settingsDataStore.edit { it[KEY_SERIES_MODE] = value }
    }

    /**
     * Opt-in online cover lookup: when a book has no embedded/folder art, query Open Library by
     * title/author for one. Off by default — it reaches a third-party server (title + author only,
     * over the unauthenticated client), which the privacy / Play-Services-free stance keeps opt-in.
     */
    val onlineCoverLookup: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_ONLINE_COVERS] ?: false }

    suspend fun setOnlineCoverLookup(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_ONLINE_COVERS] = value }
    }

    /**
     * When this device last finished a crawl that saw the WHOLE library, and which device that was.
     *
     * The shared index publishes this as its crawl marker, and it is the only thing that authorises
     * deleting a book from other devices — so it is set by a FULL crawl and never by an incremental
     * one, which by definition cannot testify that a missing book is gone rather than unvisited.
     */
    val lastFullCrawlAt: Flow<Long> =
        context.settingsDataStore.data.map { it[KEY_FULL_CRAWL_AT] ?: 0L }

    suspend fun setLastFullCrawlAt(value: Long) {
        context.settingsDataStore.edit { it[KEY_FULL_CRAWL_AT] = value }
    }

    /** One-time guard: whether app data has been relocated from internal storage to the Homer root. */
    val storageRelocated: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_STORAGE_RELOCATED] ?: false }

    suspend fun setStorageRelocated(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_STORAGE_RELOCATED] = value }
    }

    /** Persisted SAF tree Uri for a user-chosen storage folder; null = the app-external default. */
    val customStorageUri: Flow<String?> =
        context.settingsDataStore.data.map { it[KEY_CUSTOM_STORAGE_URI] }

    suspend fun setCustomStorageUri(uri: String?) {
        context.settingsDataStore.edit {
            if (uri == null) it.remove(KEY_CUSTOM_STORAGE_URI) else it[KEY_CUSTOM_STORAGE_URI] = uri
        }
    }

    /** Absolute filesystem path for an all-files-access storage folder; null = not used. */
    val customStoragePath: Flow<String?> =
        context.settingsDataStore.data.map { it[KEY_CUSTOM_STORAGE_PATH] }

    suspend fun setCustomStoragePath(path: String?) {
        context.settingsDataStore.edit {
            if (path == null) it.remove(KEY_CUSTOM_STORAGE_PATH) else it[KEY_CUSTOM_STORAGE_PATH] = path
        }
    }

    /** Require a biometric / device-credential unlock when the app is opened or resumed. */
    val appLockEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_APP_LOCK] ?: false }

    suspend fun setAppLockEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_APP_LOCK] = value }
    }

    /**
     * Pin the Nextcloud server's TLS certificate (trust-on-first-use). When enabled the first
     * connection's certificate is captured into [pinnedServerCert] and every later connection must
     * match it. Disabling clears the captured pin so it re-captures if re-enabled.
     */
    val certPinningEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_CERT_PIN] ?: false }

    suspend fun setCertPinningEnabled(value: Boolean) {
        context.settingsDataStore.edit {
            it[KEY_CERT_PIN] = value
            if (!value) it.remove(KEY_PINNED_CERT)
        }
    }

    /** The captured pin ("sha256/…"), or null before first capture. */
    val pinnedServerCert: Flow<String?> =
        context.settingsDataStore.data.map { it[KEY_PINNED_CERT] }

    suspend fun setPinnedServerCert(pin: String?) {
        context.settingsDataStore.edit {
            if (pin == null) it.remove(KEY_PINNED_CERT) else it[KEY_PINNED_CERT] = pin
        }
    }

    /**
     * Forgets everything that describes ONE library, for signing out.
     *
     * What is here and what is not is the whole point. Gone: the folder, the sharing state, the
     * crawl and publish markers, and the pinned certificate — that last one pins a *specific*
     * server, so carrying it into a different account would break TLS for no reason. Kept: how the
     * user likes their shelf arranged, where downloads live on this device, whether app lock and
     * pinning are switched on at all, and the device id — none of which belong to the account
     * being left.
     */
    suspend fun clearLibraryState() {
        context.settingsDataStore.edit {
            it.remove(KEY_LIBRARY_ROOT)
            it.remove(KEY_SHARED_CATALOG)
            it.remove(KEY_LIBRARY_WRITABLE)
            it.remove(KEY_LAST_PUSHED_REVISION)
            it.remove(KEY_LAST_COVER_SWEEP_ETAG)
            it.remove(KEY_FULL_CRAWL_AT)
            it.remove(KEY_PINNED_CERT)
            // The rules and the ownership belong to the library being left, not to this device.
            it.remove(KEY_POLICY_ROOT)
            it.remove(KEY_POLICY_PRESENT)
            it.remove(KEY_POLICY_SHARED_REQUIRED)
            it.remove(KEY_POLICY_EDITS_ALLOWED)
            it.remove(KEY_POLICY_UNDERSTOOD)
            it.remove(KEY_POLICY_OWNER)
            it.remove(KEY_POLICY_AT)
            it.remove(KEY_POLICY_CHECKED_AT)
            it.remove(KEY_LIBRARY_OWNED)
            // Nothing writes this any more — the language filter became a filter token, which is
            // held for the process rather than stored. Cleared here so an install that HAD one
            // does not carry it around for ever as a preference nothing reads.
            it.remove(KEY_LANGUAGE_FILTER)
        }
    }

    private companion object {
        val KEY_LIBRARY_ROOT = stringPreferencesKey("library_root")
        val KEY_GRID_VIEW = booleanPreferencesKey("library_grid_view")
        val KEY_FLAT_COLLECTIONS = stringSetPreferencesKey("library_flat_collections")
        val KEY_SORT_MODE = stringPreferencesKey("library_sort_mode")
        val KEY_LANGUAGE_FILTER = stringPreferencesKey("library_language_filter")
        val KEY_SHELF_MODE = stringPreferencesKey("library_group_mode")
        val KEY_SERIES_MODE = stringPreferencesKey("library_series_mode")
        val KEY_PATH_TEMPLATES = stringPreferencesKey("library_path_templates")
        val KEY_PATH_TEMPLATES_AT = longPreferencesKey("library_path_templates_at")
        val KEY_CORRECTIONS_PUBLISHED_AT = longPreferencesKey("corrections_published_at")
        val KEY_PROGRESS_SYNC = booleanPreferencesKey("progress_sync_enabled")
        val KEY_SHARED_CATALOG = booleanPreferencesKey("shared_catalog_enabled")
        val KEY_LIBRARY_WRITABLE = booleanPreferencesKey("library_writable")
        val KEY_LIBRARY_OWNED = booleanPreferencesKey("library_owned")
        val KEY_POLICY_ROOT = stringPreferencesKey("policy_resolved_for_root")
        val KEY_POLICY_PRESENT = booleanPreferencesKey("policy_present")
        val KEY_POLICY_SHARED_REQUIRED = booleanPreferencesKey("policy_shared_index_required")
        val KEY_POLICY_EDITS_ALLOWED = booleanPreferencesKey("policy_edits_allowed")
        val KEY_POLICY_UNDERSTOOD = booleanPreferencesKey("policy_understood")
        val KEY_POLICY_OWNER = stringPreferencesKey("policy_owner")
        val KEY_POLICY_AT = stringPreferencesKey("policy_at_folder")
        val KEY_POLICY_CHECKED_AT = longPreferencesKey("policy_checked_at")
        val KEY_LAST_PUSHED_REVISION = longPreferencesKey("sync_last_pushed_revision")
        val KEY_LAST_COVER_SWEEP_ETAG = stringPreferencesKey("last_cover_sweep_etag")
        val KEY_ONLINE_COVERS = booleanPreferencesKey("online_cover_lookup")
        val KEY_STORAGE_RELOCATED = booleanPreferencesKey("storage_relocated")
        val KEY_FULL_CRAWL_AT = longPreferencesKey("last_full_crawl_at")
        val KEY_CUSTOM_STORAGE_URI = stringPreferencesKey("custom_storage_uri")
        val KEY_CUSTOM_STORAGE_PATH = stringPreferencesKey("custom_storage_path")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val KEY_CERT_PIN = booleanPreferencesKey("cert_pinning_enabled")
        val KEY_PINNED_CERT = stringPreferencesKey("pinned_server_cert")
    }
}

/**
 * Global playback preferences (same DataStore file as [LibrarySettings]). These apply to
 * every book; a per-book override is a later enhancement.
 */
/**
 * Shake-to-extend, turned off.
 *
 * A real value rather than an absence, so it round-trips through DataStore and can be offered in the
 * picker. Public because three places need to agree on it: the default here, the decision whether to
 * register the accelerometer at all, and the branch in `extendSleepByPreference` — which falls back
 * to fifteen minutes for anything it does not recognise, so "off" reaching it silently would extend
 * by a quarter of an hour.
 */
const val SLEEP_EXTEND_OFF = "off"

@Singleton
class PlaybackSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Playback speed multiplier; 1.0 = normal. */
    val speed: Flow<Float> =
        context.settingsDataStore.data.map { it[KEY_SPEED] ?: 1.0f }

    suspend fun setSpeed(value: Float) {
        context.settingsDataStore.edit { it[KEY_SPEED] = value }
    }

    /** Whether ExoPlayer trims silent gaps (faster listening without pitch change). */
    val skipSilence: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_SKIP_SILENCE] ?: false }

    suspend fun setSkipSilence(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SKIP_SILENCE] = value }
    }

    /** Restrict downloads to unmetered (Wi‑Fi) networks. */
    val wifiOnlyDownloads: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_WIFI_ONLY] ?: false }

    suspend fun setWifiOnlyDownloads(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_WIFI_ONLY] = value }
    }

    /** Seconds to fade the volume down before the sleep timer pauses; 0 = pause abruptly. */
    val sleepFadeOutSeconds: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_SLEEP_FADE] ?: DEFAULT_SLEEP_FADE }

    suspend fun setSleepFadeOutSeconds(value: Int) {
        context.settingsDataStore.edit { it[KEY_SLEEP_FADE] = value }
    }

    /** What a shake does to a running countdown: "5"/"15"/"30" minutes, "previous", "chapter". */
    val sleepExtend: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_SLEEP_EXTEND] ?: DEFAULT_SLEEP_EXTEND }

    suspend fun setSleepExtend(value: String) {
        context.settingsDataStore.edit { it[KEY_SLEEP_EXTEND] = value }
    }

    /** Last countdown length chosen, so "previous" extend and the default preset can reuse it. */
    val sleepLastDurationMs: Flow<Long> =
        context.settingsDataStore.data.map { it[KEY_SLEEP_LAST_MS] ?: 0L }

    suspend fun setSleepLastDurationMs(value: Long) {
        context.settingsDataStore.edit { it[KEY_SLEEP_LAST_MS] = value }
    }

    /** Seconds the player's skip-back/forward buttons jump; default 15. */
    val seekSeconds: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_SEEK_SECONDS] ?: DEFAULT_SEEK_SECONDS }

    suspend fun setSeekSeconds(value: Int) {
        context.settingsDataStore.edit { it[KEY_SEEK_SECONDS] = value }
    }

    /** Volume override: "reduced" | "normal" | "increased" (a loudness boost). */
    val volumeMode: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_VOLUME_MODE] ?: DEFAULT_VOLUME_MODE }

    suspend fun setVolumeMode(value: String) {
        context.settingsDataStore.edit { it[KEY_VOLUME_MODE] = value }
    }

    /**
     * Seconds to rewind when resuming a paused book, so you re-hear a little of what came before;
     * 0 = off (default, so playback resumes exactly where it stopped unless the user opts in).
     */
    val autoRewindSeconds: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_AUTO_REWIND] ?: DEFAULT_AUTO_REWIND }

    suspend fun setAutoRewindSeconds(value: Int) {
        context.settingsDataStore.edit { it[KEY_AUTO_REWIND] = value }
    }

    /**
     * Global default: when true, pressing Play downloads the whole book for offline use (while it
     * streams immediately); when false, playback just streams and downloads stay manual. A per-book
     * override can force either mode for a specific book. Default true.
     */
    val downloadOnPlay: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_DOWNLOAD_ON_PLAY] ?: true }

    suspend fun setDownloadOnPlay(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_DOWNLOAD_ON_PLAY] = value }
    }

    private companion object {
        val KEY_SPEED = floatPreferencesKey("playback_speed")
        val KEY_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val KEY_SLEEP_FADE = intPreferencesKey("sleep_fade_seconds")
        val KEY_SLEEP_EXTEND = stringPreferencesKey("sleep_extend")
        val KEY_SLEEP_LAST_MS = longPreferencesKey("sleep_last_duration_ms")
        val KEY_SEEK_SECONDS = intPreferencesKey("seek_seconds")
        val KEY_VOLUME_MODE = stringPreferencesKey("volume_mode")
        val KEY_AUTO_REWIND = intPreferencesKey("auto_rewind_seconds")
        val KEY_DOWNLOAD_ON_PLAY = booleanPreferencesKey("download_on_play")
        const val DEFAULT_SLEEP_FADE = 5
        /**
         * Shake-to-extend, off by default.
         *
         * It was "15", and the feature had no off switch at all — so every countdown registered the
         * accelerometer and any single knock over 2.7g added a quarter of an hour. A sleep timer
         * whose failure mode is "the audiobook plays all night" does not get to be on by default,
         * even with the much stricter detector `ShakeDetector` now uses.
         */
        const val DEFAULT_SLEEP_EXTEND = SLEEP_EXTEND_OFF
        const val DEFAULT_SEEK_SECONDS = 15
        const val DEFAULT_VOLUME_MODE = "normal"
        const val DEFAULT_AUTO_REWIND = 0
    }
}


/**
 * In-app updater preferences. Shares the one settings file with [LibrarySettings] and
 * [PlaybackSettings] (DataStore throws if the same file is opened twice).
 *
 * Automatic checking is OFF by default, matching [LibrarySettings.onlineCoverLookup]: it reaches a
 * third party (GitHub) on a schedule the user did not ask for, and the privacy / Play-Services-free
 * stance keeps that opt-in. Checking by hand from the About screen is always available — that is an
 * explicit action, so it needs no standing consent.
 */
@Singleton
class UpdateSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Whether Homer checks GitHub for a new release on its own, roughly once a day. */
    val autoCheck: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_AUTO_CHECK] ?: false }

    suspend fun setAutoCheck(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_CHECK] = value }
    }

    /** Which releases to offer: finished ones only, or the betas too. */
    val channel: Flow<UpdateChannel> =
        context.settingsDataStore.data.map { UpdateChannel.fromPref(it[KEY_CHANNEL]) }

    suspend fun setChannel(value: UpdateChannel) {
        context.settingsDataStore.edit { it[KEY_CHANNEL] = value.pref }
    }

    /** Wall-clock of the last completed check; 0 = never. Drives the "checked N ago" line. */
    val lastCheckedAtMs: Flow<Long> =
        context.settingsDataStore.data.map { it[KEY_LAST_CHECKED] ?: 0L }

    suspend fun setLastCheckedAtMs(value: Long) {
        context.settingsDataStore.edit { it[KEY_LAST_CHECKED] = value }
    }

    /**
     * The newest version the user has already been notified about. Without it the daily check
     * re-notifies about the same release every day until they install it.
     */
    val notifiedVersion: Flow<String?> =
        context.settingsDataStore.data.map { it[KEY_NOTIFIED_VERSION] }

    suspend fun setNotifiedVersion(value: String) {
        context.settingsDataStore.edit { it[KEY_NOTIFIED_VERSION] = value }
    }

    private companion object {
        val KEY_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
        val KEY_CHANNEL = stringPreferencesKey("update_channel")
        val KEY_LAST_CHECKED = longPreferencesKey("update_last_checked")
        val KEY_NOTIFIED_VERSION = stringPreferencesKey("update_notified_version")
    }
}


/**
 * A stable, random identifier for this installation.
 *
 * Not a setting, but it lives here because DataStore throws if the same file is opened twice and
 * this is the one file. Introduced for the shared library's crawl marker, which has to record
 * *which* device last saw the whole tree — a timestamp alone cannot answer "is this my own crawl
 * or another device's?", and the answer decides whether a book may be deleted.
 *
 * Random, never derived from anything about the device or the account: it is written to a folder
 * that other people can read.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Generated on first read and stable thereafter. */
    suspend fun id(): String {
        context.settingsDataStore.data.first()[KEY_DEVICE_ID]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        // putIfAbsent semantics: two callers racing on first launch must agree on one id.
        var winner = fresh
        context.settingsDataStore.edit { prefs ->
            val existing = prefs[KEY_DEVICE_ID]
            if (existing != null) winner = existing else prefs[KEY_DEVICE_ID] = fresh
        }
        return winner
    }

    /**
     * A short human-readable name for this device, for lines like "last full crawl, from Pixel 7".
     * Falls back to the model when the user has not named their device.
     */
    val label: String get() = Build.MODEL?.trim()?.ifBlank { null } ?: "this device"

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}
