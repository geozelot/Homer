package com.geozelot.homer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.auth.AuthRepository
import com.geozelot.homer.data.auth.AuthState
import com.geozelot.homer.data.library.SetupState
import com.geozelot.homer.data.library.setupIsDue
import com.geozelot.homer.data.settings.LibrarySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Top-level VM: what the root shows — a spinner, the setup flow, or the library. */
@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository,
    librarySettings: LibrarySettings,
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.authState(viewModelScope)

    /**
     * Whether the setup flow owns the screen.
     *
     * Credentials cannot be the gate on their own, in either direction. Signing in is a *step* of
     * setup, so "logged out" stops being true half way through the flow — and gating on it would
     * drop the user into the library before a folder had been chosen. That is what
     * [SetupState.IN_PROGRESS] answers.
     *
     * And [SetupState.NOT_STARTED] has to carry the upgrade: an install from before this flow
     * existed has credentials, a library and a shelf full of books, and must never be shown a
     * wizard — so "not started" only means setup when there is nothing configured either.
     *
     * ## Null is "not known yet", and it is not the same as "no"
     *
     * The initial value used to be `false`, which reads as "go to the library" — and on a cold
     * start the auth state can resolve before the first DataStore read arrives, so a fresh install
     * with nothing configured mounted the whole library graph for a moment before setup replaced
     * it. Long enough to build `HomeViewModel`, connect the player, and request a crawl that was
     * then refused with "NONE (NoLibrary)". Harmless, and visible in a log as work nobody asked for.
     */
    val needsSetup: StateFlow<Boolean?> =
        combine(librarySettings.setupState, authRepository.credentials) { setup, credentials ->
            setupIsDue(setup, hasCredentials = credentials != null)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
