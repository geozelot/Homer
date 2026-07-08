package com.geozelot.homer.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun save(credentials: NextcloudCredentials)

    fun clear()
}

@Singleton
class EncryptedCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialStore {

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

    private val _credentials = MutableStateFlow(readFromPrefs())
    override val credentials: StateFlow<NextcloudCredentials?> = _credentials.asStateFlow()

    override fun save(credentials: NextcloudCredentials) {
        prefs.edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_LOGIN, credentials.loginName)
            .putString(KEY_PASSWORD, credentials.appPassword)
            .apply()
        _credentials.value = credentials
    }

    override fun clear() {
        prefs.edit().clear().apply()
        _credentials.value = null
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
