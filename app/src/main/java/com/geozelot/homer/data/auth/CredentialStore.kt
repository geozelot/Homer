package com.geozelot.homer.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
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
 * Secrets are sealed under a key that lives in the Android Keystore; nothing sensitive is written in
 * plaintext. See [KeystoreCredentialStore].
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

/**
 * [CredentialStore], sealed with AES-GCM under a key that never leaves the Android Keystore.
 *
 * ## Why not Jetpack Security any more
 *
 * Up to 2.2.0 this was `EncryptedSharedPreferences`. Jetpack Security is deprecated upstream: its
 * last release marks every API deprecated and points at the Keystore directly, which is all it was
 * ever doing underneath. Staying on it meant a dependency that will not follow AndroidX forward,
 * holding the one thing in Homer that cannot be re-derived from the server. What it did is small
 * enough to do here — [AesGcmCipher] with [keystoreKey], and [CredentialCodec] for the shape — and
 * the old file is taken over once by [LegacyCredentialStore] and then deleted.
 *
 * ## One thread, in order
 *
 * Every read and write of the file runs on [scope], which runs one thing at a time in the order it
 * was asked for. The initial load — including taking over the old store — is asked for first, in
 * the constructor, so a save that races ahead of it in memory still lands on disk AFTER it rather
 * than being overwritten by credentials the user has just replaced.
 */
@Singleton
class KeystoreCredentialStore internal constructor(
    private val context: Context,
    private val names: Names,
) : CredentialStore {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Names())

    /** Where this store keeps things — fixed in the app, distinct in the tests. */
    internal data class Names(
        val prefs: String = PREFS_NAME,
        val keyAlias: String = KEY_ALIAS,
        val legacyPrefs: String = LegacyCredentialStore.NAME,
    )

    // Keystore and disk work, too slow for the main thread (ANR/jank risk), so confined here;
    // callers read the flows reactively, and per-request readers (WebDAV, workers) already
    // tolerate a transient null before the load lands. One at a time — see the class KDoc.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(names.prefs, Context.MODE_PRIVATE) }
    private val codec by lazy { CredentialCodec(AesGcmCipher { keystoreKey(names.keyAlias) }) }

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
            // A store that cannot be opened — a key lost to a backup restore, a damaged file,
            // security hardware misbehaving after an update — is signed out, not a crash: thrown
            // out of this coroutine, nothing would catch it, and the process would die on every
            // launch with no way back in.
            val (storedLibrary, storedSync) = try {
                takeOverLegacyStore()
                read(SLOT_LIBRARY) to read(SLOT_SYNC)?.copy(kind = WebDavKind.ACCOUNT)
            } catch (e: Exception) {
                Log.e(TAG, "credential store is unreadable; treating this device as signed out", e)
                null to null
            }
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
        scope.launch { persist("save") { putString(SLOT_LIBRARY, codec.seal(SLOT_LIBRARY, credentials)) } }
    }

    override fun setSyncAccount(account: NextcloudCredentials?) {
        _separateSyncAccount.value = account
        scope.launch {
            persist("update the sync account in") {
                if (account == null) remove(SLOT_SYNC) else putString(SLOT_SYNC, codec.seal(SLOT_SYNC, account))
            }
        }
    }

    override fun clear() {
        _credentials.value = null
        _separateSyncAccount.value = null
        _loaded.value = true
        scope.launch {
            persist("clear") {
                remove(SLOT_LIBRARY)
                remove(SLOT_SYNC)
            }
        }
    }

    /** Waits until every read and write asked for so far has run — for the tests, which cannot see [scope]. */
    internal suspend fun idle() = scope.launch { }.join()

    /** What is stored in [slot], or null for nothing. Throws when it is there and cannot be opened. */
    private fun read(slot: String): NextcloudCredentials? = prefs.getString(slot, null)?.let { codec.open(slot, it) }

    /**
     * Moves the pre-2.2.0 store's contents into this one, then deletes it.
     *
     * Only into an EMPTY store: anything here was written by this build and is newer than the old
     * file by definition, which is then simply removed. Written with `commit`, so the old file goes
     * only once its contents are on disk here. A failure to READ it propagates — signed out for
     * this launch, and the old file left in place for the next one to try again.
     */
    private fun takeOverLegacyStore() {
        val legacy = LegacyCredentialStore(context, names.legacyPrefs)
        if (!legacy.exists()) return
        if (!prefs.contains(SLOT_LIBRARY) && !prefs.contains(SLOT_SYNC)) {
            val (library, sync) = legacy.read()
            prefs.edit(commit = true) {
                library?.let { putString(SLOT_LIBRARY, codec.seal(SLOT_LIBRARY, it)) }
                sync?.let { putString(SLOT_SYNC, codec.seal(SLOT_SYNC, it)) }
            }
            Log.i(TAG, "took over the old credential store (library: ${library != null}, sync: ${sync != null})")
        }
        legacy.delete()
    }

    /** One guarded write: a Keystore failure is logged rather than left to take the process down. */
    private fun persist(what: String, block: SharedPreferences.Editor.() -> Unit) {
        try {
            prefs.edit(action = block)
        } catch (e: Exception) {
            Log.e(TAG, "could not $what the credential store", e)
        }
    }

    private companion object {
        const val TAG = "HomerAuth"
        const val PREFS_NAME = "homer_secrets"
        const val KEY_ALIAS = "homer_credentials_key"
        const val SLOT_LIBRARY = "library"
        const val SLOT_SYNC = "sync"
    }
}
