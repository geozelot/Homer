package com.geozelot.homer.ui.login

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import com.geozelot.homer.R
import com.geozelot.homer.data.auth.AuthRepository
import com.geozelot.homer.data.library.ShareResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Login Flow v2 UX: collect a server URL, initiate the flow, emit the
 * browser URL for the screen to open in a Custom Tab, then poll until the user
 * finishes authenticating. Success is observed app-wide via [AuthRepository]'s
 * credential state, so this VM does not navigate itself.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    enum class Status { Idle, Connecting, WaitingForBrowser }

    /** Which onboarding entry point is active. */
    enum class Mode { ACCOUNT, SHARE }

    data class UiState(
        val mode: Mode = Mode.ACCOUNT,
        val serverUrl: String = "",
        val shareUrl: String = "",
        val sharePassword: String = "",
        val status: Status = Status.Idle,
        /**
         * What went wrong, as a resource rather than a sentence.
         *
         * These are the only messages the setup flow shows that a person acts on — "the password is
         * wrong", "check the URL" — and they were written here as English literals, so on a German
         * phone the one screen that has to be understood was the one screen that was not translated.
         */
        @StringRes val error: Int? = null,
    ) {
        val canSubmit: Boolean get() = serverUrl.isNotBlank() && status == Status.Idle
        val canSubmitShare: Boolean get() = shareUrl.isNotBlank() && status == Status.Idle
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** One-shot browser-open requests (the login URL) for the screen to consume. */
    private val _openBrowser = Channel<String>(Channel.BUFFERED)
    val openBrowser = _openBrowser.receiveAsFlow()

    /** One-shot "sync account linked" signal (sync mode only) so the screen can navigate back. */
    private val _linked = Channel<Unit>(Channel.BUFFERED)
    val linked = _linked.receiveAsFlow()

    /** In sync mode the account is added as a progress-sync account (see [pollSyncAccount]) rather
     *  than replacing the library — used when the library is a share. */
    private var syncMode = false

    private var loginJob: Job? = null

    fun setSyncMode(enabled: Boolean) {
        syncMode = enabled
        // The error goes with the mode. This ViewModel is one instance for the whole setup flow, so
        // without clearing it a failed share link a step ago is still on screen under a form that
        // has nothing to do with it.
        if (enabled) _uiState.value = _uiState.value.copy(mode = Mode.ACCOUNT, error = null)
    }

    fun setMode(mode: Mode) {
        _uiState.value = _uiState.value.copy(mode = mode, error = null)
    }

    fun onServerUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(serverUrl = value, error = null)
    }

    fun onShareUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(shareUrl = value, error = null)
    }

    fun onSharePasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(sharePassword = value, error = null)
    }

    /** Opens a public share link as the library. Success flips AuthState app-wide (no nav here). */
    fun openShare() {
        if (!_uiState.value.canSubmitShare) return
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = Status.Connecting, error = null)
            try {
                when (val result = authRepository.useShare(_uiState.value.shareUrl, _uiState.value.sharePassword)) {
                    is ShareResolver.Result.Ok -> Unit // AuthState flips; app navigates away
                    ShareResolver.Result.PasswordRequired -> fail(R.string.login_err_share_password)
                    ShareResolver.Result.NotFound -> fail(R.string.login_err_share_not_found)
                    ShareResolver.Result.Unreachable -> fail(R.string.login_err_unreachable)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "openShare failed", e)
                fail(R.string.login_err_share_failed)
            }
        }
    }

    fun startLogin() {
        if (!_uiState.value.canSubmit) return
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = Status.Connecting, error = null)
            try {
                Log.d(TAG, "startLogin: server='${_uiState.value.serverUrl}'") // Log.d: stripped from release
                val init = authRepository.beginLogin(_uiState.value.serverUrl)
                _openBrowser.send(init.login)
                _uiState.value = _uiState.value.copy(status = Status.WaitingForBrowser)

                var elapsed = 0L
                while (elapsed < POLL_TIMEOUT_MS) {
                    val credentials = try {
                        if (syncMode) authRepository.pollSyncAccount(init) else authRepository.pollOnce(init)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A transient failure (e.g. a connectivity blip while the
                        // browser is foregrounded) must NOT abort the flow — the login
                        // session stays valid for minutes, so just retry next tick.
                        Log.w(TAG, "poll attempt failed; will retry", e)
                        null
                    }
                    if (credentials != null) {
                        Log.i(TAG, "startLogin: success, credentials saved")
                        // Account mode: AuthState flips app-wide. Sync mode: signal the screen to
                        // navigate back (the library is unchanged; only the sync account was added).
                        if (syncMode) _linked.send(Unit)
                        return@launch
                    }
                    delay(POLL_INTERVAL_MS)
                    elapsed += POLL_INTERVAL_MS
                }
                Log.w(TAG, "startLogin: timed out")
                fail(R.string.login_err_timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Only reached for setup failures (e.g. an unreachable server address).
                Log.e(TAG, "startLogin failed", e)
                // The exception's own message was preferred here, which meant an OkHttp
                // string in whatever language OkHttp has — none — reaching a user in place of a
                // sentence. It goes to the log, where it is useful, and the screen says what to do.
                fail(R.string.login_err_login_failed)
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        _uiState.value = _uiState.value.copy(status = Status.Idle, error = null)
    }

    private fun fail(@StringRes message: Int) {
        _uiState.value = _uiState.value.copy(status = Status.Idle, error = message)
    }

    private companion object {
        const val TAG = "HomerAuth"
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 5 * 60 * 1_000L
    }
}
