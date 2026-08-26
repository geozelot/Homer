package com.geozelot.homer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.data.auth.AuthState
import com.geozelot.homer.ui.login.LoginScreen

/**
 * Root of the app. Gates between the login flow and the library based on whether a
 * Nextcloud account is configured. Richer in-library navigation arrives with the
 * library UI phase.
 */
@Composable
fun HomerApp() {
    val viewModel: AppViewModel = hiltViewModel()
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (authState) {
            AuthState.Unknown -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            AuthState.LoggedOut -> LoginScreen()
            is AuthState.LoggedIn -> {
                // Asked here, not at launch: there is nothing to notify about until an account
                // exists, and asked BEFORE the library rather than by the player, because a scan
                // and a download both notify long before anybody opens a book.
                NotificationPermissionRequest()
                LibraryNavHost()
            }
        }
    }
}
