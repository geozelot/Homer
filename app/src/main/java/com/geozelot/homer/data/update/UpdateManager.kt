package com.geozelot.homer.data.update

import android.util.Log
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.data.settings.UpdateSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that knows whether an update exists and how far along installing it we are.
 *
 * Holds a single [state] flow so the About screen, the daily check and the notification all agree.
 * Only one operation runs at a time: a second tap while a check or download is in flight is
 * ignored rather than queued, because both are idempotent and the user's intent is "get on with
 * the one I already asked for".
 */
@Singleton
class UpdateManager @Inject constructor(
    private val source: GitHubReleaseSource,
    private val installer: UpdateInstaller,
    private val settings: UpdateSettings,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The build that is running, for display and for deciding what counts as newer. */
    val currentVersion: AppVersion = AppVersion.parse(BuildConfig.VERSION_NAME)

    private var job: Job? = null

    /**
     * The release the user last asked to install. Kept outside [state] on purpose: a failure
     * replaces the state with [UpdateState.Failed], and retrying after granting the install
     * permission needs to know what it was retrying.
     */
    private var pending: UpdateRelease? = null

    /** True while something is in flight and a second request would be dropped. */
    private val busy: Boolean
        get() = job?.isActive == true

    /**
     * The newest release worth offering on [channel], or null when this build is already current.
     *
     * Shared by the manual check and the daily worker, so both apply exactly one rule for "newer".
     * Throws [UpdateCheckException] rather than returning null when the answer is unknown.
     */
    suspend fun findUpdate(channel: UpdateChannel): UpdateRelease? {
        val newest = source.newestRelease(channel) ?: return null
        if (newest.version <= currentVersion) return null
        // Newer, but nothing to install. Reported only now that it is known to matter — an
        // assetless release older than this build is simply not our problem.
        return newest.release
            ?: throw UpdateCheckException(UpdateFailure.NO_ASSET, "${newest.version.raw} has no APK")
    }

    /** Checks in the background and moves [state] to UpToDate, Available or Failed. */
    fun check() {
        if (busy) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            val channel = settings.channel.first()
            try {
                val release = findUpdate(channel)
                settings.setLastCheckedAtMs(System.currentTimeMillis())
                _state.value =
                    if (release == null) UpdateState.UpToDate(System.currentTimeMillis())
                    else UpdateState.Available(release)
            } catch (e: UpdateCheckException) {
                Log.w(TAG, "update check failed: ${e.message}")
                _state.value = UpdateState.Failed(e.reason)
            }
        }
    }

    /**
     * Downloads [release] and hands it straight to the system installer.
     *
     * Safe to call again after a failure: a completed download is reused, so retrying once the
     * user has granted the install permission costs nothing.
     */
    fun downloadAndInstall(release: UpdateRelease) {
        if (busy) return
        pending = release
        job = scope.launch {
            try {
                _state.value = UpdateState.Downloading(release, 0f)
                val apk = installer.download(release) { fraction ->
                    _state.value = UpdateState.Downloading(release, fraction)
                }
                _state.value = UpdateState.ReadyToInstall(release)

                if (!installer.canInstallPackages()) {
                    // Not a failure of ours — the user has to grant this in system settings, and
                    // the UI turns this state into the button that takes them there.
                    _state.value = UpdateState.Failed(UpdateFailure.INSTALL_NOT_ALLOWED)
                    return@launch
                }

                _state.value = UpdateState.Installing(release)
                installer.install(apk)
                // The outcome arrives at InstallResultReceiver; on success this process is replaced.
            } catch (e: UpdateInstallException) {
                Log.w(TAG, "update install failed: ${e.message}")
                _state.value = UpdateState.Failed(e.reason)
            }
        }
    }

    /**
     * The "try again" action: resumes the install the user was already attempting, or falls back
     * to a fresh check when the failure happened before any release had been chosen.
     */
    fun retryOrCheck() {
        val release = pending
        if (release == null) check() else downloadAndInstall(release)
    }

    /** Called by [InstallResultReceiver]. [failure] is null when the install succeeded. */
    fun onInstallFinished(failure: UpdateFailure?) {
        if (failure == null) pending = null
        _state.value =
            if (failure == null) UpdateState.UpToDate(System.currentTimeMillis())
            else UpdateState.Failed(failure)
    }

    /** Clears a failure so the screen goes back to offering the check again. */
    fun dismissFailure() {
        if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    /** Lets the worker publish what it found without the user having opened the screen. */
    fun publishFound(release: UpdateRelease) {
        if (_state.value is UpdateState.Idle || _state.value is UpdateState.UpToDate) {
            _state.value = UpdateState.Available(release)
        }
    }

    private companion object {
        const val TAG = "HomerUpdate"
    }
}
