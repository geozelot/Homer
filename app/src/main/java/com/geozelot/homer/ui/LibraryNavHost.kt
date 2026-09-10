package com.geozelot.homer.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geozelot.homer.R
import com.geozelot.homer.ui.home.FilterToken
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Surface2
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
 * The width at which the library stops being a phone screen and gets a player beside it.
 *
 * 840dp is the canonical "expanded" breakpoint — the same number `WindowWidthSizeClass` uses, read
 * off the configuration rather than out of `material3-window-size-class`. That library exists to
 * answer this one question, needs an Activity to be asked it, and is still experimental; a
 * dependency is a lot to carry for a constant this app can read directly. `screenWidthDp` is the
 * WINDOW's width, so a split-screen or freeform window gets the layout its actual size deserves.
 */
private val TwoPaneMinWidth = 840.dp

/**
 * How much of an expanded window the player takes.
 *
 * Fixed rather than a fraction: the player is a fixed amount of content — a cover, a title, a
 * transport — and giving it half of a 1200dp tablet would stretch the cover to a poster while the
 * library lost a column it could have used. The library takes what is left, which is what it can
 * always spend on more books.
 */
private val PlayerPaneWidth = 420.dp

/**
 * Navigation within the authenticated area: the library list, the player, and the settings tree.
 * Book ids are folder paths, so they're URL-encoded into the route and decoded by the nav args.
 */
@Composable
fun LibraryNavHost() {
    val navController = rememberNavController()

    // Wide enough for two panes? Read every recomposition, because it changes under the app: a fold
    // opens, a tablet rotates, a split-screen divider moves.
    val twoPane = LocalConfiguration.current.screenWidthDp.dp >= TwoPaneMinWidth
    // What the docked pane is showing, where there IS one. Null means "whatever is playing", which
    // is what the pane falls back to — so the pane is never blank while audio is running, and a
    // reader who has not chosen anything yet is told so rather than shown an empty player.
    var paneBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var paneAtMs by rememberSaveable { mutableStateOf(-1L) }

    // The two layouts are two representations of one thing, so a device that changes shape mid-book
    // maps between them instead of dropping what was open. Unfolding adopts the pushed player into
    // the pane; folding pushes an explicitly chosen book back onto the stack. A pane that was only
    // following the playing book pushes nothing — the mini-player comes back and says the same.
    LaunchedEffect(twoPane) {
        if (twoPane) {
            val top = navController.currentBackStackEntry
            if (top?.destination?.route?.startsWith("player/") == true) {
                paneBookId = top.arguments?.getString(ARG_BOOK_ID)
                paneAtMs = top.arguments?.getLong(ARG_AT_MS) ?: -1L
                navController.popBackStack()
            }
        } else {
            paneBookId?.let { id ->
                paneBookId = null
                val at = paneAtMs
                paneAtMs = -1L
                navController.navigate(
                    if (at < 0) "player/${Uri.encode(id)}" else "player/${Uri.encode(id)}?at=$at",
                )
            }
        }
    }

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
            val library = navController.libraryViewModel(entry)
            // Opening a book AT a position is what tapping a bookmark in the library does. On one
            // pane that is a query argument rather than a second route — it is the same
            // destination, and a player reached with no position is the overwhelmingly common
            // case. On two, opening a book is not navigation at all: the player is already there.
            val openBook: (String, Long) -> Unit = { bookId, atMs ->
                if (twoPane) {
                    paneBookId = bookId
                    paneAtMs = atMs
                } else if (atMs < 0) {
                    entry.navigateOnce(navController, "player/${Uri.encode(bookId)}")
                } else {
                    entry.navigateOnce(navController, "player/${Uri.encode(bookId)}?at=$atMs")
                }
            }
            val home = @Composable { modifier: Modifier ->
                HomeScreen(
                    onBookClick = { openBook(it, -1L) },
                    onBookClickAt = openBook,
                    onOpenSettings = { entry.navigateOnce(navController, ROUTE_SETTINGS) },
                    onOpenTemplates = { entry.navigateOnce(navController, ROUTE_SETTINGS_TEMPLATES) },
                    showMiniPlayer = !twoPane,
                    modifier = modifier,
                )
            }
            if (twoPane) {
                Row(modifier = Modifier.fillMaxSize()) {
                    home(Modifier.weight(1f).fillMaxHeight())
                    PlayerPane(
                        selectedBookId = paneBookId,
                        startAtMs = paneAtMs,
                        library = library,
                        // The library is right there, so a filter applies in place. On one pane
                        // this pops back to it, which here would be popping to what you can see.
                        onFilter = library::addFilterToken,
                        onReadFolderDifferently = { bookId ->
                            library.seedTemplateFor(bookId)
                            navController.navigate(ROUTE_SETTINGS_TEMPLATES)
                        },
                        modifier = Modifier.width(PlayerPaneWidth).fillMaxHeight(),
                    )
                }
            } else {
                home(Modifier)
            }
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
 * The player, docked beside the library on a window wide enough for both.
 *
 * It shows the book the reader last opened here, and failing that whatever is playing — so the pane
 * is never blank while there is audio, and a reader who has just arrived is told what it is for
 * instead of being shown a player with no book in it.
 *
 * [PlayerScreen] is the same screen the phone pushes, not a reduced copy. It is handed no way back
 * because there is nowhere to go: the pane is not covering the library, so a back arrow would close
 * something the reader can already see beside it.
 */
@Composable
private fun PlayerPane(
    selectedBookId: String?,
    startAtMs: Long,
    library: HomeViewModel,
    onFilter: (FilterToken) -> Unit,
    onReadFolderDifferently: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing by library.miniPlayerBook.collectAsStateWithLifecycle()
    val entries by library.entries.collectAsStateWithLifecycle()
    val maintains by library.maintainsLibrary.collectAsStateWithLifecycle()
    val bookId = selectedBookId ?: playing?.id

    Row(modifier = modifier) {
        // A hairline, not a gap: the two panes are one surface with a seam, the way the expanded
        // series enclosure is one card with rules inside it.
        Spacer(Modifier.width(1.dp).fillMaxHeight().background(Surface2))
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (bookId == null) {
                EmptyPlayerPane()
            } else {
                PlayerScreen(
                    bookId = bookId,
                    startAtMs = startAtMs,
                    details = entries.findBook(bookId),
                    onFilter = onFilter,
                    onReadFolderDifferently =
                        if (maintains) ({ onReadFolderDifferently(bookId) }) else null,
                    onBack = null,
                )
            }
        }
    }
}

/** What the pane says before anything has been played. */
@Composable
private fun EmptyPlayerPane() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.player_pane_empty), style = SerifTitle, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.player_pane_empty_hint), color = Muted, fontSize = 13.sp)
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
