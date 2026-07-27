package com.geozelot.homer.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the single Nextcloud account's credentials. The app password is stored
 * only in Keystore-backed [EncryptedSharedPreferences]; nothing sensitive is written
 * in plaintext.
 */
interface CredentialStore {
    /** Current credentials, or `null` when logged out. Emits on save/clear. */
    val credentials: StateFlow<NextcloudCredentials?>

    /**
     * Flips to `true` once the initial (potentially slow, Keystore-backed) read has completed.
     * Until then [credentials] being `null` means "not loaded yet", not "logged out" — auth
     * gating waits on this so it never flashes the login screen on a cold start.
     */
    val loaded: StateFlow<Boolean>

    fun save(credentials: NextcloudCredentials)

    fun clear()
}

@Singleton
class EncryptedCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialStore {

    // Building the master key + EncryptedSharedPreferences touches the Keystore and disk, which
    // is too slow for the main thread (ANR/jank risk). It's confined to [scope] (IO) instead;
    // callers read [credentials]/[loaded] reactively, and per-request readers (WebDAV, workers)
    // already tolerate a transient null before the load lands.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _credentials = MutableStateFlow<NextcloudCredentials?>(null)
    override val credentials: StateFlow<NextcloudCredentials?> = _credentials.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            _credentials.value = readFromPrefs()
            _loaded.value = true
        }
    }

    override fun save(credentials: NextcloudCredentials) {
        // Update in-memory state immediately (drives navigation); persist off the main thread.
        _credentials.value = credentials
        _loaded.value = true
        scope.launch {
            prefs.edit()
                .putString(KEY_SERVER, credentials.serverUrl)
                .putString(KEY_LOGIN, credentials.loginName)
                .putString(KEY_PASSWORD, credentials.appPassword)
                .apply()
        }
    }

    override fun clear() {
        _credentials.value = null
        scope.launch { prefs.edit().clear().apply() }
    }

    private fun readFromPrefs(): NextcloudCredentials? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val login = prefs.getString(KEY_LOGIN, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return NextcloudCredentials(server, login, password)
    }

    private companion object {
        const val PREFS_NAME = "homer_credentials"
        const val KEY_SERVER = "server_url"
        const val KEY_LOGIN = "login_name"
        const val KEY_PASSWORD = "app_password"
    }
}
