package com.geozelot.homer.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.settings.UpdateSettings
import com.geozelot.homer.data.update.UpdateChannel
import com.geozelot.homer.data.update.UpdateInstaller
import com.geozelot.homer.data.update.UpdateManager
import com.geozelot.homer.data.update.UpdateRelease
import com.geozelot.homer.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the update section of the About screen.
 *
 * Safe to obtain with a bare `hiltViewModel()` in a settings destination — unlike `HomeViewModel`,
 * a second instance costs nothing, because all the actual state lives in the singleton
 * [UpdateManager] and this only forwards to it.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager,
    private val settings: UpdateSettings,
    private val installer: UpdateInstaller,
) : ViewModel() {

    val state: StateFlow<UpdateState> = manager.state

    /** The running build, as shown at the top of the screen. */
    val currentVersion: String = manager.currentVersion.raw

    val autoCheck: StateFlow<Boolean> =
        settings.autoCheck.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val channel: StateFlow<UpdateChannel> =
        settings.channel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateChannel.STABLE)

    val lastCheckedAtMs: StateFlow<Long> =
        settings.lastCheckedAtMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun check() = manager.check()

    fun install(release: UpdateRelease) = manager.downloadAndInstall(release)

    fun retry() = manager.retryOrCheck()

    fun dismissFailure() = manager.dismissFailure()

    fun setAutoCheck(value: Boolean) {
        viewModelScope.launch { settings.setAutoCheck(value) }
    }

    fun setChannel(value: UpdateChannel) {
        viewModelScope.launch { settings.setChannel(value) }
    }

    /** Read on every recomposition of the permission row: the user can change it from Settings. */
    fun canInstallPackages(): Boolean = installer.canInstallPackages()

    fun unknownSourcesIntent(): Intent = installer.unknownSourcesSettingsIntent()
}
