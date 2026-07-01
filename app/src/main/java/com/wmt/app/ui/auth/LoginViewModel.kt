package com.wmt.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class LoginState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun login() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update {
                it.copy(
                    emailError = if (s.email.isBlank()) "Email is required" else null,
                    passwordError = if (s.password.isBlank()) "Password is required" else null,
                )
            }
            return
        }

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(s.email.trim(), s.password)) {
                is Resource.Success -> {
                    registerFcmToken()
                    // isLoggedIn flips in DataStore; RootViewModel routes to Main.
                    _state.update { it.copy(loading = false) }
                }
                is Resource.Error -> applyError(result.error)
                is Resource.Loading -> Unit
            }
        }
    }

    private fun applyError(error: AppError) {
        when (error) {
            is AppError.Validation -> _state.update {
                it.copy(
                    loading = false,
                    emailError = error.fieldErrors["email"]?.firstOrNull(),
                    passwordError = error.fieldErrors["password"]?.firstOrNull(),
                    error = if (error.fieldErrors.isEmpty()) error.message else null,
                )
            }
            else -> _state.update { it.copy(loading = false, error = error.message) }
        }
    }

    private suspend fun registerFcmToken() {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            authRepository.registerDeviceToken(token)
        }
    }
}
