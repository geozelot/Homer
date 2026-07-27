package com.geozelot.homer.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.settings.LibrarySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the app-lock setting for the [BiometricGate] at the top of the UI. */
@HiltViewModel
class LockViewModel @Inject constructor(
    librarySettings: LibrarySettings,
) : ViewModel() {
    val appLockEnabled: StateFlow<Boolean> = librarySettings.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
