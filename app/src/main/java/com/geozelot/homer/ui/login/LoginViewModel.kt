package com.geozelot.homer.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import com.geozelot.homer.data.auth.AuthRepository
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

    data class UiState(
        val serverUrl: String = "",
        val status: Status = Status.Idle,
        val error: String? = null,
    ) {
        val canSubmit: Boolean get() = serverUrl.isNotBlank() && status == Status.Idle
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** One-shot browser-open requests (the login URL) for the screen to consume. */
    private val _openBrowser = Channel<String>(Channel.BUFFERED)
    val openBrowser = _openBrowser.receiveAsFlow()

    private var loginJob: Job? = null

    fun onServerUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(serverUrl = value, error = null)
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
                        authRepository.pollOnce(init)
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
                        return@launch // AuthRepository state flips app-wide
                    }
                    delay(POLL_INTERVAL_MS)
                    elapsed += POLL_INTERVAL_MS
                }
                Log.w(TAG, "startLogin: timed out")
                fail("Login timed out. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Only reached for setup failures (e.g. an unreachable server address).
                Log.e(TAG, "startLogin failed", e)
                fail(e.message ?: "Login failed. Check the server address and try again.")
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        _uiState.value = _uiState.value.copy(status = Status.Idle, error = null)
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(status = Status.Idle, error = message)
    }

    private companion object {
        const val TAG = "HomerAuth"
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 5 * 60 * 1_000L
    }
}
