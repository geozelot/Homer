package com.geozelot.homer.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

    private companion object {
        val KEY_LIBRARY_ROOT = stringPreferencesKey("library_root")
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

    private companion object {
        val KEY_SPEED = floatPreferencesKey("playback_speed")
        val KEY_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
    }
}
