package com.geozelot.homer.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geozelot.homer.ui.about.DiagnosticsScreen
import com.geozelot.homer.ui.about.LicensesScreen
import com.geozelot.homer.ui.about.PrivacyScreen
import com.geozelot.homer.ui.home.HomeScreen
import com.geozelot.homer.ui.login.LoginScreen
import com.geozelot.homer.ui.player.PlayerScreen

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_LICENSES = "licenses"
private const val ROUTE_PRIVACY = "privacy"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_LINK_SYNC = "link_sync"
private const val ARG_BOOK_ID = "bookId"

/**
 * Navigation within the authenticated area: the library list and the player. Book ids
 * are folder paths, so they're URL-encoded into the route and decoded by the nav args.
 */
@Composable
fun LibraryNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_LIBRARY) {
        composable(ROUTE_LIBRARY) {
            HomeScreen(
                onBookClick = { bookId ->
                    navController.navigate("player/${Uri.encode(bookId)}")
                },
                onOpenLicenses = { navController.navigate(ROUTE_LICENSES) },
                onOpenPrivacy = { navController.navigate(ROUTE_PRIVACY) },
                onOpenDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                onLinkSyncAccount = { navController.navigate(ROUTE_LINK_SYNC) },
            )
        }
        composable(
            route = "player/{$ARG_BOOK_ID}",
            arguments = listOf(navArgument(ARG_BOOK_ID) { type = NavType.StringType }),
            // The player slides up from the bottom (like expanding the mini-player) and back down.
            enterTransition = { slideInVertically(tween(300)) { it } },
            popExitTransition = { slideOutVertically(tween(300)) { it } },
        ) { entry ->
            val bookId = entry.arguments?.getString(ARG_BOOK_ID).orEmpty()
            PlayerScreen(bookId = bookId, onBack = { navController.popBackStack() })
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
        composable(ROUTE_LINK_SYNC) {
            // Reuses the login flow to add a progress-sync account to a share library.
            LoginScreen(syncMode = true, onLinked = { navController.popBackStack() })
        }
    }
}
