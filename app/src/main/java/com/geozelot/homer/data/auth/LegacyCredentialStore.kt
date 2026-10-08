package com.geozelot.homer.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The credential store Homer kept up to 2.2.0 — Jetpack Security's `EncryptedSharedPreferences` —
 * read once so [KeystoreCredentialStore] can take its contents over, and then deleted.
 *
 * Jetpack Security is deprecated upstream and will not follow AndroidX forward, so nothing else in
 * Homer touches it any more. This class, and the `security-crypto` dependency with it, can be
 * deleted once no install older than 2.2.0 can still be updating: until then a phone that skipped
 * the release carrying this would otherwise come back signed out.
 */
@Suppress("DEPRECATION")
internal class LegacyCredentialStore(private val context: Context, private val name: String = NAME) {

    /** Whether there is anything to take over — read through the plain file, so nothing is decrypted. */
    fun exists(): Boolean = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()

    /** The library credentials and the separate sync account, as the old store held them. */
    fun read(): Pair<NextcloudCredentials?, NextcloudCredentials?> {
        val prefs = EncryptedSharedPreferences.create(
            context,
            name,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val library = credentials(
            prefs.getString(KEY_SERVER, null),
            prefs.getString(KEY_LOGIN, null),
            prefs.getString(KEY_PASSWORD, null),
            // Absent for accounts stored before share support — an account, as the old store said.
            prefs.getString(KEY_KIND, null)
                ?.let { runCatching { WebDavKind.valueOf(it) }.getOrNull() }
                ?: WebDavKind.ACCOUNT,
        )
        val sync = credentials(
            prefs.getString(KEY_SYNC_SERVER, null),
            prefs.getString(KEY_SYNC_LOGIN, null),
            prefs.getString(KEY_SYNC_PASSWORD, null),
            WebDavKind.ACCOUNT,
        )
        return library to sync
    }

    /** Removes the old file and its Jetpack Security master key, once the contents are safe elsewhere. */
    fun delete() {
        context.deleteSharedPreferences(name)
        runCatching { deleteKeystoreKey(MasterKey.DEFAULT_MASTER_KEY_ALIAS) }
    }

    private fun credentials(server: String?, login: String?, password: String?, kind: WebDavKind) =
        if (server == null || login == null || password == null) null else NextcloudCredentials(server, login, password, kind)

    companion object {
        const val NAME = "homer_credentials"
        const val KEY_SERVER = "server_url"
        const val KEY_LOGIN = "login_name"
        const val KEY_PASSWORD = "app_password"
        const val KEY_KIND = "webdav_kind"
        const val KEY_SYNC_SERVER = "sync_server_url"
        const val KEY_SYNC_LOGIN = "sync_login_name"
        const val KEY_SYNC_PASSWORD = "sync_app_password"
    }
}
