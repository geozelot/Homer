package com.geozelot.homer.data.library

import android.util.Log
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.auth.WebDavKind
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.net.NetworkMonitor
import com.geozelot.homer.data.sync.facet.LibraryFacets
import com.geozelot.homer.data.sync.facet.LibraryPolicyRepository
import com.geozelot.homer.data.sync.facet.StructureFacet
import com.geozelot.homer.data.webdav.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks at a folder and reports what is there, so [decideSetup] can say what to do about it.
 *
 * Every question setup asks the server is asked here, once, for one folder: can we write, who owns
 * it, what rules apply, is there already an index, how big is it. Five or six small requests — and
 * the reason they are gathered in one place is that the answers have to be consistent with each
 * other. A findings screen that says "read-only" beside "create a library here" is not a wording
 * problem; it is two probes that disagreed.
 *
 * ## The write probe covers accounts too
 *
 * `ShareResolver` write-probes a share link at resolve time, and that used to be the only place
 * writability was established — so a folder somebody shared *into* an account was assumed writable
 * because the credentials were an account's. It is the same MKCOL either way, and the folder it
 * creates is `.homer`, which is exactly where the index would go.
 */
@Singleton
class LibrarySetupProbe @Inject constructor(
    private val webDavClient: WebDavClient,
    private val credentialStore: CredentialStore,
    private val policyRepository: LibraryPolicyRepository,
    private val networkMonitor: NetworkMonitor,
    private val bookDao: BookDao,
    private val json: Json,
) {
    /**
     * Probes [root]. Null when nothing could be established — offline, unauthenticated, or the
     * folder could not be reached at all — which the screen reports as such rather than guessing.
     */
    suspend fun probe(root: String): SetupProbe? = withContext(Dispatchers.IO) {
        val credentials = credentialStore.awaitCredentials() ?: return@withContext null
        if (!networkMonitor.isOnline()) return@withContext null
        // Whitespace as well as slashes: this comes from a text field, and " Books/" typed with a
        // stray leading space is a 404 that reads as "the folder is not there".
        val folder = root.trim().trim('/')

        // Reachability first: a wrong folder makes every answer below meaningless, and 404 here is
        // the ordinary result of a typed path.
        val status = webDavClient.statusOf(folder, credentials)
        if (status != 207) {
            Log.i(TAG, "'$folder' is not reachable: HTTP $status")
            return@withContext null
        }

        // The rules before the index, because they are what decides whether the index matters.
        // Null means the walk could not complete, and a policy that might exist is not a fact to
        // paper over — the folder is reported as unprobed instead.
        val policy = policyRepository.probe(folder) ?: run {
            Log.i(TAG, "could not establish the rules at '$folder'")
            return@withContext null
        }

        // Asked for a share link too: ownership there is somebody else's by definition, but the
        // NAME is what a reader's "kept by andre" is made of.
        val owner = runCatching { webDavClient.fetchOwnerId(folder) }.getOrNull()
        val structurePath = pathOf(folder, LibraryFacets.STRUCTURE_FILE)
        val hasIndex = runCatching { webDavClient.exists(structurePath) }.getOrDefault(false)

        SetupProbe(
            kind = credentials.kind,
            root = folder,
            writable = probeWritable(folder),
            // Null owner — a server that does not expose one — is not ownership. On such a backend
            // nobody is the owner, which leaves the rules unwritable and unenforced, and that is the
            // honest answer rather than granting everybody the owner's exemption.
            isOwner = credentials.kind == WebDavKind.ACCOUNT && owner != null && owner == credentials.loginName,
            owner = owner,
            policy = policy,
            hasSharedIndex = hasIndex,
            // Downloaded only for the one folder being considered, and only when there is something
            // to count. Discovery deliberately does not do this for its candidate list — it is the
            // whole index, and a list of six folders would be six full downloads.
            remoteBookCount = if (hasIndex) countBooks(structurePath) else null,
            localBookCount = bookDao.count(),
        ).also {
            // Deliberately not the whole probe: it carries the folder's owner, which is an account
            // name and has no business in a release log. Everything a diagnosis needs is here
            // without it.
            Log.i(
                TAG,
                "probed '$folder': index=${it.hasSharedIndex}, books=${it.remoteBookCount}, " +
                    "writable=${it.writable}, ours=${it.isOwner}, " +
                    "rules=${if (!it.policy.present) "none" else if (it.policy.understood) "read" else "unreadable"}",
            )
        }
    }

    /**
     * A write, to answer a question about writing — see [WebDavClient.canWrite] for why MKCOL alone
     * is not one.
     *
     * On a folder that is then not adopted this leaves an empty `.homer` behind with a zero-byte
     * probe file in it: the cheapest possible litter, and the alternative is guessing from
     * credentials, which is the assumption that made a folder shared into an account look writable
     * when it was not.
     */
    private suspend fun probeWritable(folder: String): Boolean =
        webDavClient.canWrite(pathOf(folder, null))

    private suspend fun countBooks(structurePath: String): Int? = try {
        webDavClient.getText(structurePath)?.content
            ?.takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString(StructureFacet.serializer(), it).books.size }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A count is a caption, not a decision: the index is there either way, and a screen that
        // says "a library is here" without a number is better than one that says nothing.
        Log.w(TAG, "could not count the books in '$structurePath'", e)
        null
    }

    private fun pathOf(folder: String, file: String?): String =
        listOfNotNull(folder, LibraryFacets.DIR, file).filter { it.isNotBlank() }.joinToString("/")

    private companion object {
        const val TAG = "HomerSetup"
    }
}
