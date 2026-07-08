package com.geozelot.homer.ui.home

import androidx.lifecycle.ViewModel
import com.geozelot.homer.data.auth.AuthRepository
import com.geozelot.homer.data.auth.NextcloudCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val account: StateFlow<NextcloudCredentials?> = authRepository.credentials

    fun logout() = authRepository.logout()
}
