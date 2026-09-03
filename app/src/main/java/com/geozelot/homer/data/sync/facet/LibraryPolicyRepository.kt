package com.geozelot.homer.data.sync.facet

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.settings.DeviceIdentity
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the rules a library imposes, and — for the owner alone — writes them.
 *
 * ## The walk is the whole point
 *
 * [LibraryPolicy] is looked for at the configured root and then at each folder above it, nearest
 * first. Without that, a rule set on `Audiobooks/` is bypassed by pointing the app at
 * `Audiobooks/Krimis` instead, which is one tap in the folder picker — the setting would be
 * decoration. The walk is capped ([LibraryPolicy.MAX_LOOKUP_LEVELS]) and its answer is mirrored in
 * settings, so the cost is a handful of small GETs on a cold resolve and nothing at all afterwards.
 *
 * ## Failing safe means failing quiet, not failing open
 *
 * A level that errors — as opposed to answering 404 — aborts the walk without recording anything.
 * A nearer folder that could not be read might be the one carrying the strictest rule, so the honest
 * result is "not established", which callers answer by waiting rather than by assuming the library
 * is open. The previous resolution stays in force meanwhile.
 */
@Singleton
class LibraryPolicyRepository @Inject constructor(
    private val store: FacetStore,
    private val webDavClient: WebDavClient,
    private val credentialStore: CredentialStore,
    private val librarySettings: LibrarySettings,
    private val networkMonitor: NetworkMonitor,
    private val deviceIdentity: DeviceIdentity,
    private val json: Json,
) {
    private val resolveMutex = Mutex()

    /** The rules in force, with the root they were resolved for. */
    val resolution: Flow<PolicyResolution> = librarySettings.policyResolution

    /**
     * Re-resolves the rules for the configured root, and returns what is now in force.
     *
     * Throttled: the answer changes when an owner edits it, which is rare, and this is called from
     * every foreground pull. [force] is for the moments where the answer is the point — setup,
     * adopting a library, opening the rules panel.
     */
    suspend fun refresh(force: Boolean = false): PolicyResolution = resolveMutex.withLock {
        val current = librarySettings.policyResolution.first()
        val root = librarySettings.libraryRoot.first().trim('/')
        val fresh = current.describes(root) &&
            System.currentTimeMillis() - current.checkedAt < FRESH_FOR_MS
        if (!force && fresh) return@withLock current

        if (credentialStore.awaitCredentials() == null || !networkMonitor.isOnline()) return@withLock current

        val policy = probe(root) ?: run {
            Log.i(TAG, "could not resolve rules for '$root'; keeping what we had")
            return@withLock current
        }
        val owned = probeOwnership(root)
        // One timestamp for the record and the return value: two calls to the clock would have the
        // caller and the mirror disagree about when this was established, and the difference is
        // what the freshness throttle reads.
        val now = System.currentTimeMillis()
        librarySettings.setPolicyResolution(forRoot = root, policy = policy, owned = owned, checkedAt = now)
        Log.i(
            TAG,
            "rules for '$root': " + when {
                !policy.present -> "none"
                !policy.understood -> "present but unreadable — treating as strict"
                else -> "shared index ${if (policy.sharedIndexRequired) "required" else "optional"}, " +
                    "edits ${if (policy.editsAllowed) "allowed" else "locked"}, from '${policy.atFolder}'"
            } + ", owned=$owned",
        )
        PolicyResolution(forRoot = root, policy = policy, owned = owned, checkedAt = now)
    }

    /**
     * Reads the rules that would apply to a library rooted at [root], without recording anything.
     *
     * For the setup flow, which asks about a folder before adopting it. Null when nothing could be
     * established; [PolicyInForce.OPEN] when the walk completed and found no rules.
     */
    suspend fun probe(root: String): PolicyInForce? {
        for (folder in LibraryPolicy.lookupFolders(root)) {
            val path = policyPath(folder)
            val raw = try {
                webDavClient.getText(path)?.content
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not a 404 — getText answers null for that. Something went wrong reading a folder
                // that may be the one carrying the rule, so nothing is concluded.
                Log.w(TAG, "reading rules at '$path' failed", e)
                return null
            } ?: continue

            if (raw.isBlank()) continue
            val policy = try {
                json.decodeFromString(LibraryPolicy.serializer(), raw)
            } catch (e: Exception) {
                Log.w(TAG, "rules at '$path' are unreadable; treating as strict", e)
                return PolicyInForce.unreadable(folder)
            }
            // A file from a LATER schema may carry a rule this build has never heard of, so it is
            // read as the strictest one rather than as the subset we happen to understand. An
            // earlier schema is accepted as-is: the two flags are booleans whose meaning is fixed
            // by contract, and re-reading every older library as strict on a schema bump would
            // impose rules nobody set.
            if (policy.version > LibraryFacets.SCHEMA_VERSION) {
                Log.i(TAG, "rules at '$path' are schema v${policy.version}; treating as strict")
                return PolicyInForce.unreadable(folder)
            }
            return PolicyInForce.of(policy, folder, owner = policy.ownerId ?: probeOwnerId(folder))
        }
        return PolicyInForce.OPEN
    }

    /**
     * Whether the signed-in account owns the folder the library lives in — the one party allowed to
     * write the rules, and the one party they do not bind.
     *
     * A share link is never ownership, whatever the server says about the folder behind it: the
     * whole nature of a share is that somebody else holds the account.
     */
    suspend fun ownsLibrary(): Boolean {
        val cached = librarySettings.policyResolution.first()
        val root = librarySettings.libraryRoot.first().trim('/')
        if (cached.describes(root) && cached.owned != null) return cached.owned
        return probeOwnership(root) == true
    }

    /**
     * Writes the rules for the configured root. Owner only.
     *
     * Turning the rules *off* is a write of `sharedIndexRequired = false, editsAllowed = true`
     * rather than a DELETE: the file records when this library first had rules, and deleting it
     * would make "no rules" indistinguishable from "never had any".
     *
     * The two timestamps are kept apart deliberately: [LibraryPolicy.createdAt] says when this
     * library started having rules, [LibraryPolicy.editedAt] when they last changed, and a UI that
     * says "set by andre in March, changed on Tuesday" needs both.
     */
    suspend fun write(sharedIndexRequired: Boolean, editsAllowed: Boolean): WriteResult {
        val credentials = credentialStore.awaitCredentials() ?: return WriteResult.NotAllowed
        if (credentials.kind == WebDavKind.SHARE) return WriteResult.NotAllowed
        if (!networkMonitor.isOnline()) return WriteResult.Unavailable
        val root = librarySettings.libraryRoot.first().trim('/')
        val owned = probeOwnership(root)
        // Null — the server exposed no owner — is not a refusal. A plain WebDAV backend cannot
        // answer the question, and on one of those the account reaching the folder is the only
        // party there is.
        if (owned == false) return WriteResult.NotAllowed

        val now = System.currentTimeMillis()
        val deviceId = deviceIdentity.id()
        val result = store.save(LibraryFacets.POLICY_FILE, LibraryPolicy.serializer()) { remote ->
            val existing = (remote as? FacetStore.Load.Present)?.value
            LibraryPolicy(
                ownerId = credentials.loginName,
                sharedIndexRequired = sharedIndexRequired,
                editsAllowed = editsAllowed,
                createdAt = existing?.createdAt?.takeIf { it > 0 } ?: now,
                createdBy = existing?.createdBy ?: deviceId,
                editedAt = now,
            )
        }
        return when (result) {
            is FacetStore.SaveResult.Written, is FacetStore.SaveResult.AlreadyCurrent -> {
                // Recorded straight away: the owner has just said what the rules are, and making
                // them wait for the next resolve to see their own answer reads as the tap not
                // landing.
                librarySettings.setPolicyResolution(
                    forRoot = root,
                    policy = PolicyInForce(
                        sharedIndexRequired = sharedIndexRequired,
                        editsAllowed = editsAllowed,
                        owner = credentials.loginName,
                        atFolder = root,
                    ),
                    owned = true,
                    checkedAt = now,
                )
                Log.i(
                    TAG,
                    "wrote rules for '$root': shared index " +
                        (if (sharedIndexRequired) "required" else "optional") +
                        ", edits " + (if (editsAllowed) "allowed" else "locked"),
                )
                WriteResult.Written
            }
            is FacetStore.SaveResult.Unavailable -> {
                Log.w(TAG, "could not write rules for '$root': ${result.message}")
                WriteResult.Unavailable
            }
            // Declined cannot happen — the merge above always returns a value — and Contended means
            // three lost races, which for a file only one party writes is a server problem.
            else -> WriteResult.Unavailable
        }
    }

    private suspend fun probeOwnership(root: String): Boolean? {
        val credentials = credentialStore.awaitCredentials() ?: return null
        // A share is never ownership, and asking the server would answer about the folder behind
        // the link — which is somebody else's by definition.
        if (credentials.kind == WebDavKind.SHARE) return false
        val owner = probeOwnerId(root) ?: return null
        return owner == credentials.loginName
    }

    private suspend fun probeOwnerId(folder: String): String? =
        runCatching { webDavClient.fetchOwnerId(folder) }.getOrNull()

    private fun policyPath(folder: String): String =
        listOf(folder, LibraryFacets.DIR, LibraryFacets.POLICY_FILE)
            .filter { it.isNotBlank() }
            .joinToString("/")

    sealed interface WriteResult {
        data object Written : WriteResult

        /** Not the owner, or a share link — the rules are not this device's to set. */
        data object NotAllowed : WriteResult

        data object Unavailable : WriteResult
    }

    private companion object {
        const val TAG = "HomerIndex"

        /**
         * How long a resolution is trusted before being looked up again.
         *
         * Rules change when an owner edits them, which is rare; this is called from every
         * foreground pull, which is not. Six hours means a reader picks up a new rule the same day
         * without a resolve on every app start.
         */
        const val FRESH_FOR_MS = 6 * 60 * 60 * 1000L
    }
}
