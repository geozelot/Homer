package com.geozelot.homer.data.auth

import android.util.Log
import com.geozelot.homer.data.db.HomerDatabase
import com.geozelot.homer.data.library.IndexPassStore
import com.geozelot.homer.data.library.LibraryIndexManager
import com.geozelot.homer.data.library.SetupState
import com.geozelot.homer.data.settings.LibrarySettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signing out, in full: forget the server AND the library it described.
 *
 * Sign-out used to clear the credentials and nothing else, while the dialog promised to "clear the
 * library from this device". Everything survived — the books, positions, bookmarks and corrections
 * — so signing into a *different* account showed the previous one's shelf, and the first-run flow
 * skipped discovery entirely because it could see books and a crawl timestamp and concluded there
 * was nothing to set up.
 *
 * Two things make this a class of its own rather than three lines in a ViewModel.
 *
 * **It must outlive its caller.** Clearing the credentials flips the auth gate, which tears down
 * the library screen and its ViewModel; work launched in `viewModelScope` would be cancelled
 * somewhere in the middle, leaving half a library behind. This scope belongs to the singleton.
 *
 * **The order is load-bearing.** Index passes are stopped first and the credentials go before the
 * database, so a worker that is still winding down cannot fetch anything, and therefore cannot
 * write books back into a table that has just been emptied. The setup marker is reset before the
 * credentials, because clearing them is what puts the setup flow on screen and the flow immediately
 * marks itself as running — see [invoke].
 */
@Singleton
class SignOut @Inject constructor(
    private val credentialStore: CredentialStore,
    private val db: HomerDatabase,
    private val librarySettings: LibrarySettings,
    private val libraryIndexManager: LibraryIndexManager,
    private val passes: IndexPassStore,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "sign-out did not finish cleanly", e) },
    )

    /**
     * Returns immediately; the clearing runs on this singleton's own scope.
     *
     * Files on disk are deliberately untouched. Downloaded audio is gigabytes the user chose to
     * fetch, and a sign-out is as often a re-authentication as it is a departure — deleting it
     * would make fixing a password the most expensive thing in the app.
     */
    operator fun invoke() {
        scope.launch {
            // Stop the passes before anything else: a crawl in flight writes books, and it would
            // write them into the table this is about to empty.
            libraryIndexManager.cancel()
            passes.clear()

            // Setup is due again — recorded BEFORE the credentials go, and that ordering is the
            // whole of it. Clearing the credentials is what makes the setup flow appear, and the
            // flow's first act is to mark itself as under way; resetting the marker afterwards,
            // which is what `clearLibraryState` used to do, wiped that mark. The flow was then
            // dismissed the instant the new credentials landed — so signing out and connecting
            // somewhere else dropped the user into the library with no folder chosen, every time.
            librarySettings.setSetupState(SetupState.NOT_STARTED)

            // Before the database, so a worker still winding down has nothing to fetch with.
            credentialStore.clear()

            db.clearAllTables()
            librarySettings.clearLibraryState()
            Log.i(TAG, "signed out: credentials, library and library settings cleared")
        }
    }

    private companion object {
        const val TAG = "HomerAuth"
    }
}
