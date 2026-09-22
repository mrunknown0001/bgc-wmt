package com.wmt.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.wmt.app.domain.model.User
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.NotificationRepository
import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val serverUrl: String? = null,
    val themeMode: String = "system",
    /** Server-keyed email notification switches; empty until the first read lands. */
    val notificationPreferences: Map<String, Boolean> = emptyMap(),
)

data class ChangePasswordUiState(
    val submitting: Boolean = false,
    val error: String? = null,
    val success: String? = null,
)

data class SignOutOthersUiState(
    val submitting: Boolean = false,
    val error: String? = null,
    val success: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    val state: StateFlow<ProfileUiState> = combine(
        authRepository.currentUser,
        settingsRepository.serverUrl,
        settingsRepository.themeMode,
        notificationRepository.preferences,
    ) { user, serverUrl, themeMode, preferences ->
        ProfileUiState(
            user = user,
            serverUrl = serverUrl,
            themeMode = themeMode,
            notificationPreferences = preferences,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(),
    )

    private val _pushDiag = MutableStateFlow<String?>(null)
    val pushDiag: StateFlow<String?> = _pushDiag.asStateFlow()

    private val _changePassword = MutableStateFlow(ChangePasswordUiState())
    val changePassword: StateFlow<ChangePasswordUiState> = _changePassword.asStateFlow()

    fun changePassword(current: String, new: String, confirm: String) {
        if (new != confirm) {
            _changePassword.value = ChangePasswordUiState(error = "New passwords don't match.")
            return
        }
        if (new.length < 8) {
            _changePassword.value = ChangePasswordUiState(error = "New password must be at least 8 characters.")
            return
        }
        _changePassword.value = ChangePasswordUiState(submitting = true)
        viewModelScope.launch {
            _changePassword.value = when (val result = authRepository.changePassword(current, new, confirm)) {
                is Resource.Success -> ChangePasswordUiState(success = result.data)
                is Resource.Error -> ChangePasswordUiState(error = result.error.message)
                is Resource.Loading -> ChangePasswordUiState(submitting = true)
            }
        }
    }

    fun clearChangePasswordState() {
        _changePassword.value = ChangePasswordUiState()
    }

    private val _signOutOthers = MutableStateFlow(SignOutOthersUiState())
    val signOutOthers: StateFlow<SignOutOthersUiState> = _signOutOthers.asStateFlow()

    fun logoutOtherDevices(password: String) {
        if (password.isBlank()) {
            _signOutOthers.value = SignOutOthersUiState(error = "Enter your current password.")
            return
        }
        _signOutOthers.value = SignOutOthersUiState(submitting = true)
        viewModelScope.launch {
            _signOutOthers.value = when (val result = authRepository.logoutOtherDevices(password)) {
                is Resource.Success -> SignOutOthersUiState(success = result.data)
                is Resource.Error -> SignOutOthersUiState(error = result.error.message)
                is Resource.Loading -> SignOutOthersUiState(submitting = true)
            }
        }
    }

    fun clearSignOutOthersState() {
        _signOutOthers.value = SignOutOthersUiState()
    }

    /** Fetches the FCM token and registers it, surfacing the exact outcome for debugging. */
    fun runPushDiagnostics() {
        _pushDiag.value = "Running…"
        viewModelScope.launch {
            val outcome = runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                token to authRepository.registerDeviceToken(token)
            }
            _pushDiag.value = outcome.fold(
                onSuccess = { (token, reg) ->
                    val regText = when (reg) {
                        is Resource.Success -> "register: OK ✓"
                        is Resource.Error -> "register FAILED: ${reg.error.message}"
                        is Resource.Loading -> "register: loading"
                    }
                    "FCM token: ${token.take(24)}…\n$regText"
                },
                onFailure = { e -> "FCM token FAILED: ${e.javaClass.simpleName}: ${e.message}" },
            )
        }
    }

    init {
        viewModelScope.launch {
            runCatching { authRepository.refreshProfile() }
        }
        // The cached switches render immediately; this only catches changes made on the
        // web since the session payload was stored.
        viewModelScope.launch {
            runCatching { notificationRepository.refreshPreferences() }
        }
    }

    fun setTheme(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    private val _preferenceError = MutableStateFlow<String?>(null)

    /** Set when a switch could not be saved; the switch has already snapped back. */
    val preferenceError: StateFlow<String?> = _preferenceError.asStateFlow()

    private val _savingPreferences = MutableStateFlow<Set<String>>(emptySet())

    /** Keys with a write in flight, so a switch can't be flipped twice mid-save. */
    val savingPreferences: StateFlow<Set<String>> = _savingPreferences.asStateFlow()

    fun setPreference(type: String, enabled: Boolean) {
        if (type in _savingPreferences.value) return
        _savingPreferences.value = _savingPreferences.value + type
        viewModelScope.launch {
            val result = notificationRepository.setPreference(type, enabled)
            _savingPreferences.value = _savingPreferences.value - type
            if (result is Resource.Error) {
                _preferenceError.value = result.error.message
            }
        }
    }

    fun clearPreferenceError() {
        _preferenceError.value = null
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    fun changeServer(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            settingsRepository.clearServerUrl()
            onDone()
        }
    }
}
