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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists Homer's WebDAV credentials. Two independent slots:
 *  - [credentials] — the **library backend**: a signed-in account OR a public share link.
 *  - [syncAccount] — the account used for **private cross-device progress** (`.homer/index.json`).
 *    A bare share can't host per-user progress, so this is null unless the user also signs in;
 *    when the library itself is an account, that account *is* the sync account.
 *
 * Secrets live only in Keystore-backed [EncryptedSharedPreferences]; nothing sensitive is written
 * in plaintext.
 */
interface CredentialStore {
    /** Current library backend (account or share), or `null` when logged out. Emits on save/clear. */
    val credentials: StateFlow<NextcloudCredentials?>

    /**
     * The account for private progress sync, or `null` for device-local-only. Derived: the library
     * itself when it's an account; otherwise the separately-added sync account (see [setSyncAccount]).
     */
    val syncAccount: StateFlow<NextcloudCredentials?>

    /**
     * Flips to `true` once the initial (potentially slow, Keystore-backed) read has completed.
     * Until then [credentials] being `null` means "not loaded yet", not "logged out" — auth
     * gating waits on this so it never flashes the login screen on a cold start.
     */
    val loaded: StateFlow<Boolean>

    /**
     * Suspends until the initial load has completed, then returns the library backend (or null if
     * logged out). Background workers must use this rather than reading [credentials] eagerly.
     */
    suspend fun awaitCredentials(): NextcloudCredentials?

    /** Suspends until loaded, then returns the sync account (or null for device-local-only). */
    suspend fun awaitSyncAccount(): NextcloudCredentials?

    /** Sets the library backend (an account from Login Flow, or a resolved share). */
    fun save(credentials: NextcloudCredentials)

    /** Sets or clears the separately-added sync account (only meaningful when the library is a share). */
    fun setSyncAccount(account: NextcloudCredentials?)

    fun clear()
}

@Singleton
class EncryptedCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialStore {

    // Building the master key + EncryptedSharedPreferences touches the Keystore and disk, which
    // is too slow for the main thread (ANR/jank risk). It's confined to [scope] (IO) instead;
    // callers read the flows reactively, and per-request readers (WebDAV, workers) already
    // tolerate a transient null before the load lands.
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

    /** The separately-added sync account (used only when the library is a share). */
    private val _separateSyncAccount = MutableStateFlow<NextcloudCredentials?>(null)

    override val syncAccount: StateFlow<NextcloudCredentials?> =
        combine(_credentials, _separateSyncAccount) { lib, separate ->
            if (lib?.kind == WebDavKind.ACCOUNT) lib else separate
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    override suspend fun awaitCredentials(): NextcloudCredentials? {
        _loaded.first { it }
        return _credentials.value
    }

    override suspend fun awaitSyncAccount(): NextcloudCredentials? {
        _loaded.first { it }
        val lib = _credentials.value
        return if (lib?.kind == WebDavKind.ACCOUNT) lib else _separateSyncAccount.value
    }

    init {
        scope.launch {
            val storedLibrary = readLibrary()
            val storedSync = readSyncAccount()
            // Don't clobber a save()/clear() that raced ahead of this initial read.
            if (!_loaded.value) {
                _credentials.value = storedLibrary
                _separateSyncAccount.value = storedSync
                _loaded.value = true
            }
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
                .putString(KEY_KIND, credentials.kind.name)
                .apply()
        }
    }

    override fun setSyncAccount(account: NextcloudCredentials?) {
        _separateSyncAccount.value = account
        scope.launch {
            prefs.edit().apply {
                if (account == null) {
                    remove(KEY_SYNC_SERVER); remove(KEY_SYNC_LOGIN); remove(KEY_SYNC_PASSWORD)
                } else {
                    putString(KEY_SYNC_SERVER, account.serverUrl)
                    putString(KEY_SYNC_LOGIN, account.loginName)
                    putString(KEY_SYNC_PASSWORD, account.appPassword)
                }
            }.apply()
        }
    }

    override fun clear() {
        _credentials.value = null
        _separateSyncAccount.value = null
        _loaded.value = true
        scope.launch { prefs.edit().clear().apply() }
    }

    private fun readLibrary(): NextcloudCredentials? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val login = prefs.getString(KEY_LOGIN, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        // Absent for accounts stored before share support — default to ACCOUNT.
        val kind = prefs.getString(KEY_KIND, null)
            ?.let { runCatching { WebDavKind.valueOf(it) }.getOrNull() }
            ?: WebDavKind.ACCOUNT
        return NextcloudCredentials(server, login, password, kind)
    }

    /** The separately-added sync account is always an ACCOUNT. */
    private fun readSyncAccount(): NextcloudCredentials? {
        val server = prefs.getString(KEY_SYNC_SERVER, null) ?: return null
        val login = prefs.getString(KEY_SYNC_LOGIN, null) ?: return null
        val password = prefs.getString(KEY_SYNC_PASSWORD, null) ?: return null
        return NextcloudCredentials(server, login, password, WebDavKind.ACCOUNT)
    }

    private companion object {
        const val PREFS_NAME = "homer_credentials"
        const val KEY_SERVER = "server_url"
        const val KEY_LOGIN = "login_name"
        const val KEY_PASSWORD = "app_password"
        const val KEY_KIND = "webdav_kind"
        const val KEY_SYNC_SERVER = "sync_server_url"
        const val KEY_SYNC_LOGIN = "sync_login_name"
        const val KEY_SYNC_PASSWORD = "sync_app_password"
    }
}
