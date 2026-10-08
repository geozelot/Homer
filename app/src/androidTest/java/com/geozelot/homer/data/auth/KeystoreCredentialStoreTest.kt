package com.geozelot.homer.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The credential store against the real Android Keystore, including taking over what the
 * Jetpack Security store left behind.
 *
 * The takeover is the part that cannot be got wrong quietly: a phone updating into this release
 * either comes back signed in, or the person has to find their server address and log in again.
 * So the old file is written here with the old library, exactly as 2.2.0-BETA.116 wrote it.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreCredentialStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = KeystoreCredentialStore.Names(
        prefs = "test_secrets",
        keyAlias = "test_credentials_key",
        legacyPrefs = "test_legacy_credentials",
    )

    private val account = NextcloudCredentials("https://cloud.example.com", "alice", "app-pass-123", WebDavKind.ACCOUNT)
    private val share = NextcloudCredentials("https://cloud.example.com", "AbCdToken", "secret", WebDavKind.SHARE)
    private val syncAccount = NextcloudCredentials("https://other.example.com", "bob", "bob-pass", WebDavKind.ACCOUNT)

    @Before
    @After
    fun clean() {
        context.deleteSharedPreferences(names.prefs)
        context.deleteSharedPreferences(names.legacyPrefs)
        runCatching { deleteKeystoreKey(names.keyAlias) }
    }

    private fun store() = KeystoreCredentialStore(context, names)

    @Suppress("DEPRECATION")
    private fun writeLegacy(library: NextcloudCredentials?, sync: NextcloudCredentials?) {
        val prefs = EncryptedSharedPreferences.create(
            context,
            names.legacyPrefs,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.edit().apply {
            library?.let {
                putString(LegacyCredentialStore.KEY_SERVER, it.serverUrl)
                putString(LegacyCredentialStore.KEY_LOGIN, it.loginName)
                putString(LegacyCredentialStore.KEY_PASSWORD, it.appPassword)
                putString(LegacyCredentialStore.KEY_KIND, it.kind.name)
            }
            sync?.let {
                putString(LegacyCredentialStore.KEY_SYNC_SERVER, it.serverUrl)
                putString(LegacyCredentialStore.KEY_SYNC_LOGIN, it.loginName)
                putString(LegacyCredentialStore.KEY_SYNC_PASSWORD, it.appPassword)
            }
        }.commit()
    }

    private fun legacyFileExists() =
        File(context.applicationInfo.dataDir, "shared_prefs/${names.legacyPrefs}.xml").exists()

    @Test
    fun anEmptyStoreIsSignedOut() = runBlocking {
        val store = store()
        assertNull(store.awaitCredentials())
        assertNull(store.awaitSyncAccount())
    }

    @Test
    fun theOldStoreIsTakenOverAndDeleted() = runBlocking {
        writeLegacy(share, syncAccount)
        val store = store()
        assertEquals(share, store.awaitCredentials())
        assertEquals(syncAccount, store.awaitSyncAccount())
        assertFalse("the old file should be gone", legacyFileExists())

        // And it stays signed in from the new store alone.
        val again = store()
        assertEquals(share, again.awaitCredentials())
        assertEquals(syncAccount, again.awaitSyncAccount())
    }

    @Test
    fun anAccountTakenOverIsItsOwnSyncAccount() = runBlocking {
        writeLegacy(account, null)
        assertEquals(account, store().awaitSyncAccount())
    }

    @Test
    fun anythingAlreadyInTheNewStoreWinsOverTheOldOne() = runBlocking {
        val first = store()
        first.awaitCredentials()
        first.save(account)
        first.idle()
        writeLegacy(share, null)

        val store = store()
        assertEquals(account, store.awaitCredentials())
        assertFalse("the old file is removed either way", legacyFileExists())
    }

    @Test
    fun aSaveIsThereOnTheNextLaunch() = runBlocking {
        val store = store()
        store.awaitCredentials()
        store.save(account)
        store.setSyncAccount(syncAccount)
        store.idle()

        val next = store()
        assertEquals(account, next.awaitCredentials())
    }

    @Test
    fun aClearIsThereOnTheNextLaunch() = runBlocking {
        val store = store()
        store.awaitCredentials()
        store.save(share)
        store.setSyncAccount(syncAccount)
        store.clear()
        store.idle()

        val next = store()
        assertNull(next.awaitCredentials())
        assertNull(next.awaitSyncAccount())
    }

    @Test
    fun aDamagedStoreIsSignedOutRatherThanACrash() = runBlocking {
        context.getSharedPreferences(names.prefs, Context.MODE_PRIVATE).edit()
            .putString("library", "AQIDBAUGBwgJCgsMDQ4P")
            .commit()
        assertNull(store().awaitCredentials())
    }
}
