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
     */
    val needsSetup: StateFlow<Boolean> =
        combine(librarySettings.setupState, authRepository.credentials) { setup, credentials ->
            setupIsDue(setup, hasCredentials = credentials != null)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
