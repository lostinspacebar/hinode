package com.lostinspacebar.hinode.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostinspacebar.hinode.data.ConnectionStatus
import com.lostinspacebar.hinode.data.TrixnityMatrixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for authentication (login/logout)
 */
class AuthViewModel(
    private val repository: TrixnityMatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.LoggedOut)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _serverUrl = MutableStateFlow("https://matrix.org")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Try to restore session from saved credentials
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.tryRestoreSession()

            if (result.isSuccess) {
                println("AuthViewModel: Auto-login successful")
                _uiState.value = AuthUiState.LoggedIn
            } else {
                println("AuthViewModel: Auto-login failed, showing login screen")
                _uiState.value = AuthUiState.LoggedOut
            }
        }
    }

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    fun updateUsername(name: String) {
        _username.value = name
    }

    fun updatePassword(pass: String) {
        _password.value = pass
    }

    fun login() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _errorMessage.value = null

            val result = repository.login(
                serverUrl = _serverUrl.value.trim(),
                username = _username.value.trim(),
                password = _password.value
            )

            if (result.isSuccess) {
                _uiState.value = AuthUiState.LoggedIn
                _password.value = "" // Clear password
            } else {
                _uiState.value = AuthUiState.LoggedOut
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Login failed"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = AuthUiState.LoggedOut
            _username.value = ""
            _password.value = ""
            _errorMessage.value = null
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * Authentication UI state
 */
sealed class AuthUiState {
    data object LoggedOut : AuthUiState()
    data object Loading : AuthUiState()
    data object LoggedIn : AuthUiState()
}
