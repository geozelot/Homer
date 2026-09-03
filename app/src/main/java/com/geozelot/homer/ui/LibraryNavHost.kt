package com.geozelot.homer.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geozelot.homer.ui.about.DiagnosticsScreen
import com.geozelot.homer.ui.about.LicensesScreen
import com.geozelot.homer.ui.about.PrivacyScreen
import com.geozelot.homer.ui.home.HomeScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.ui.home.findBook
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.player.PlayerScreen
import com.geozelot.homer.ui.settings.AboutSettingsScreen
import com.geozelot.homer.ui.settings.DeviceStorageScreen
import com.geozelot.homer.ui.settings.LibrarySyncScreen
import com.geozelot.homer.ui.settings.LibraryUpkeepScreen
import com.geozelot.homer.ui.settings.PlaybackSettingsScreen
import com.geozelot.homer.ui.settings.PrivacySettingsScreen
import com.geozelot.homer.ui.settings.SettingsHubScreen
import com.geozelot.homer.ui.setup.SetupEntry
import com.geozelot.homer.ui.setup.SetupFlow
import com.geozelot.homer.ui.settings.TemplatesScreen
import com.geozelot.homer.ui.settings.StorageDialogsHost
import com.geozelot.homer.ui.storage.StorageBrowserScreen

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_LICENSES = "licenses"
private const val ROUTE_PRIVACY = "privacy"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_SETUP = "setup"
private const val ARG_SETUP_ENTRY = "entry"
private const val ROUTE_STORAGE_BROWSER = "storage_browser"
private const val ARG_AT_MS = "at"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SETTINGS_LIBRARY = "settings/library"
private const val ROUTE_SETTINGS_UPKEEP = "settings/upkeep"
private const val ROUTE_SETTINGS_TEMPLATES = "settings/upkeep/templates"
private const val ROUTE_SETTINGS_DEVICE = "settings/device"
private const val ROUTE_SETTINGS_PLAYBACK = "settings/playback"
private const val ROUTE_SETTINGS_PRIVACY = "settings/privacy"
private const val ROUTE_SETTINGS_ABOUT = "settings/about"
private const val ARG_BOOK_ID = "bookId"

/**
 * Navigation within the authenticated area: the library list, the player, and the settings tree.
 * Book ids are folder paths, so they're URL-encoded into the route and decoded by the nav args.
 */
@Composable
fun LibraryNavHost() {
    val navController = rememberNavController()

    // Storage prompts live above the graph, not inside a screen — the storage change is started
    // from the settings pages, and the load-vs-replace prompt is a question the flow waits on, so
    // a dialog bound to one destination would leave the move stalled and invisible.
    val currentEntry by navController.currentBackStackEntryAsState()
    val libraryEntry = remember(currentEntry) {
        runCatching { navController.getBackStackEntry(ROUTE_LIBRARY) }.getOrNull()
    }
    libraryEntry?.let { StorageDialogsHost(viewModel = hiltViewModel(it)) }

    NavHost(navController = navController, startDestination = ROUTE_LIBRARY) {
        composable(ROUTE_LIBRARY) { entry ->
            HomeScreen(
                onBookClick = { bookId ->
                    entry.navigateOnce(navController, "player/${Uri.encode(bookId)}")
                },
                // Opening a book AT a position, which is what tapping a bookmark in the library
                // does. A query argument rather than a second route: it is the same destination,
                // and a player reached with no position is the overwhelmingly common case.
                onBookClickAt = { bookId, atMs ->
                    entry.navigateOnce(navController, "player/${Uri.encode(bookId)}?at=$atMs")
                },
                onOpenSettings = { entry.navigateOnce(navController, ROUTE_SETTINGS) },
                onOpenTemplates = { entry.navigateOnce(navController, ROUTE_SETTINGS_TEMPLATES) },
            )
        }
        composable(
            route = "player/{$ARG_BOOK_ID}?at={$ARG_AT_MS}",
            arguments = listOf(
                navArgument(ARG_BOOK_ID) { type = NavType.StringType },
                // -1 means "wherever the book was left", which is every arrival but a bookmark's.
                navArgument(ARG_AT_MS) { type = NavType.LongType; defaultValue = -1L },
            ),
            // The player slides up from the bottom (like expanding the mini-player) and back down.
            enterTransition = { slideInVertically(tween(300)) { it } },
            popExitTransition = { slideOutVertically(tween(300)) { it } },
        ) { entry ->
            val bookId = entry.arguments?.getString(ARG_BOOK_ID).orEmpty()
            // The LIBRARY's ViewModel, on the same rule the settings destinations follow: a bare
            // hiltViewModel() here would build a second one whose init re-runs the scan and the
            // sync. It is here so the player's Details card shows the book the library shows,
            // computed once.
            val library = navController.libraryViewModel(entry)
            val entries by library.entries.collectAsStateWithLifecycle()
            val maintains by library.maintainsLibrary.collectAsStateWithLifecycle()
            PlayerScreen(
                bookId = bookId,
                startAtMs = entry.arguments?.getLong(ARG_AT_MS) ?: -1L,
                details = entries.findBook(bookId),
                // A filter only means something on the library, so applying one leaves for it.
                onFilter = { token ->
                    library.addFilterToken(token)
                    navController.popBackStack()
                },
                onReadFolderDifferently = if (maintains) {
                    {
                        library.seedTemplateFor(bookId)
                        navController.navigate(ROUTE_SETTINGS_TEMPLATES)
                    }
                } else {
                    null
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Settings ─────────────────────────────────────────────────────────
        // Every settings destination reuses the LIBRARY entry's HomeViewModel. Resolving it here
        // (hiltViewModel(libraryEntry)) is load-bearing: a bare hiltViewModel() in a settings
        // destination builds a SECOND HomeViewModel whose init re-triggers the scan, cover fetch
        // and sync, and whose state then diverges from the library's.
        composable(ROUTE_SETTINGS) { entry ->
            SettingsHubScreen(
                viewModel = navController.libraryViewModel(entry),
                onBack = { navController.popBackStack() },
                onOpenLibrary = { navController.navigate(ROUTE_SETTINGS_LIBRARY) },
                onOpenUpkeep = { navController.navigate(ROUTE_SETTINGS_UPKEEP) },
                onOpenDevice = { navController.navigate(ROUTE_SETTINGS_DEVICE) },
                onOpenPlayback = { navController.navigate(ROUTE_SETTINGS_PLAYBACK) },
                onOpenPrivacy = { navController.navigate(ROUTE_SETTINGS_PRIVACY) },
                onOpenAbout = { navController.navigate(ROUTE_SETTINGS_ABOUT) },
            )
        }
        composable(ROUTE_SETTINGS_LIBRARY) { entry ->
            LibrarySyncScreen(
                viewModel = navController.libraryViewModel(entry),
                // Every change to the library is the setup flow, opened at the step that answers
                // the row — which is also what makes the migrations free.
                onChange = { navController.navigate("$ROUTE_SETUP/${it.name}") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "$ROUTE_SETUP/{$ARG_SETUP_ENTRY}",
            arguments = listOf(navArgument(ARG_SETUP_ENTRY) { type = NavType.StringType }),
        ) { entry ->
            // firstRun = false: this run may be abandoned, and marking setup as under way would
            // leave the gate holding the user in it.
            SetupFlow(
                firstRun = false,
                entry = runCatching {
                    SetupEntry.valueOf(entry.arguments?.getString(ARG_SETUP_ENTRY).orEmpty())
                }.getOrDefault(SetupEntry.BOOKS),
                onDone = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SETTINGS_UPKEEP) { entry ->
            LibraryUpkeepScreen(
                viewModel = navController.libraryViewModel(entry),
                onOpenTemplates = { navController.navigate(ROUTE_SETTINGS_TEMPLATES) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SETTINGS_TEMPLATES) { entry ->
            TemplatesScreen(
                viewModel = navController.libraryViewModel(entry),
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SETTINGS_DEVICE) { entry ->
            DeviceStorageScreen(
                viewModel = navController.libraryViewModel(entry),
                onOpenStorageBrowser = { navController.navigate(ROUTE_STORAGE_BROWSER) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SETTINGS_PLAYBACK) { entry ->
            PlaybackSettingsScreen(
                viewModel = navController.libraryViewModel(entry),
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SETTINGS_PRIVACY) { entry ->
            PrivacySettingsScreen(
                viewModel = navController.libraryViewModel(entry),
                onOpenPrivacyStatement = { navController.navigate(ROUTE_PRIVACY) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SETTINGS_ABOUT) {
            AboutSettingsScreen(
                onOpenDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                onOpenLicenses = { navController.navigate(ROUTE_LICENSES) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_STORAGE_BROWSER) { entry ->
            // A real destination rather than an overlay inside the library, so system Back leaves
            // the folder picker instead of popping the start destination and exiting the app.
            val viewModel = navController.libraryViewModel(entry)
            StorageBrowserScreen(
                onPicked = { path ->
                    viewModel.setCustomStoragePath(path)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(ROUTE_LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Navigates from [this] destination, but only while it is still the resumed one. Two quick taps on
 * two different book cards used to push two `player/{id}` destinations (the first tap starts the
 * transition, the card underneath is still hittable); once the first navigation is under way this
 * entry is no longer RESUMED, so the second tap is dropped.
 */
private fun NavBackStackEntry.navigateOnce(navController: NavHostController, route: String) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) navController.navigate(route)
}

/**
 * The one [HomeViewModel] the whole authenticated area shares, scoped to the library back-stack
 * entry. [entry] is only a recomposition key: it makes the lookup re-run when the destination
 * changes, not on every recomposition.
 */
@Composable
private fun NavHostController.libraryViewModel(
    entry: androidx.navigation.NavBackStackEntry,
): HomeViewModel {
    val libraryEntry = remember(entry) { getBackStackEntry(ROUTE_LIBRARY) }
    return hiltViewModel(libraryEntry)
}
