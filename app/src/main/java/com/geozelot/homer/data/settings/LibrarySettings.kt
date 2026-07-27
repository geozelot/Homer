package com.geozelot.homer.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "homer_settings")

/**
 * Non-secret app settings (DataStore). Currently just the library root: the WebDAV
 * folder (relative to the files root) where the audiobook crawl begins. Empty = the
 * whole drive.
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

    /** Library sort key: "author" | "title" | "recent" | "duration". */
    val sortMode: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_SORT_MODE] ?: "author" }

    suspend fun setSortMode(value: String) {
        context.settingsDataStore.edit { it[KEY_SORT_MODE] = value }
    }

    /**
     * Sync level (per-user, per-device): 1 = on-device only (no `.homer` reads/writes),
     * 2 = progress sync via the user's `.homer/index.json`, 3 = shared library cache (later).
     * Default 2 preserves the existing cross-device behaviour.
     */
    val syncTier: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_SYNC_TIER] ?: TIER_PROGRESS }

    suspend fun setSyncTier(tier: Int) {
        context.settingsDataStore.edit { it[KEY_SYNC_TIER] = tier }
    }

    /** Library grouping: "none" | "author" | "genre". */
    val groupMode: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_GROUP_MODE] ?: "none" }

    suspend fun setGroupMode(value: String) {
        context.settingsDataStore.edit { it[KEY_GROUP_MODE] = value }
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

    private companion object {
        const val TIER_PROGRESS = 2
        val KEY_LIBRARY_ROOT = stringPreferencesKey("library_root")
        val KEY_GRID_VIEW = booleanPreferencesKey("library_grid_view")
        val KEY_SORT_MODE = stringPreferencesKey("library_sort_mode")
        val KEY_GROUP_MODE = stringPreferencesKey("library_group_mode")
        val KEY_SYNC_TIER = intPreferencesKey("sync_tier")
        val KEY_ONLINE_COVERS = booleanPreferencesKey("online_cover_lookup")
        val KEY_STORAGE_RELOCATED = booleanPreferencesKey("storage_relocated")
        val KEY_CUSTOM_STORAGE_URI = stringPreferencesKey("custom_storage_uri")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val KEY_CERT_PIN = booleanPreferencesKey("cert_pinning_enabled")
        val KEY_PINNED_CERT = stringPreferencesKey("pinned_server_cert")
    }
}

/**
 * Global playback preferences (same DataStore file as [LibrarySettings]). These apply to
 * every book; a per-book override is a later enhancement.
 */
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
        const val DEFAULT_SLEEP_FADE = 5
        const val DEFAULT_SLEEP_EXTEND = "15"
        const val DEFAULT_SEEK_SECONDS = 15
        const val DEFAULT_VOLUME_MODE = "normal"
        const val DEFAULT_AUTO_REWIND = 0
    }
}
