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
import com.geozelot.homer.ui.setup.SetupFlow

/**
 * Root of the app: a spinner while the credential store loads, then either setup or the library.
 *
 * The gate is *setup*, not authentication. Signing in is a step inside setup — for a share link it
 * is the whole of it — so an auth-state gate would hand over to the library the moment the
 * credentials landed, before a folder had been chosen or the owner's rules read. See
 * [AppViewModel.needsSetup].
 */
@Composable
fun HomerApp() {
    val viewModel: AppViewModel = hiltViewModel()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val needsSetup by viewModel.needsSetup.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        // Once, as soon as there is anything to notify about — and hoisted above the branch so that
        // handing over from setup to the library does not unmount it and ask again. A scan and a
        // download both post a progress notification, and setup ends by starting a scan.
        if (authState != AuthState.Unknown) NotificationPermissionRequest()

        when {
            // Still reading the Keystore, or setup has not answered yet. Neither branch may be
            // guessed at: guessing "configured" flashes "where do your books live?" at somebody
            // whose library is fine, and guessing "not configured" mounts the whole library graph
            // — ViewModel, player connection, a crawl request — a moment before setup replaces it.
            authState == AuthState.Unknown || needsSetup == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            // onDone is not a navigation here: needsSetup goes false when the flow records that
            // it finished, and this composable is replaced.
            needsSetup == true -> SetupFlow(firstRun = true, onDone = {})

            else -> LibraryNavHost()
        }
    }
}
