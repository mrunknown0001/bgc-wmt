package com.wmt.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.BuildConfig
import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.util.Constants
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerSetupState(
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
)

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // Dev builds land on staging already typed in; release builds start empty so nobody
    // ships a phone pointed at the dev server.
    private val _state = MutableStateFlow(
        ServerSetupState(url = if (BuildConfig.DEBUG) Constants.STAGING_BASE_URL else ""),
    )
    val state: StateFlow<ServerSetupState> = _state.asStateFlow()

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, error = null) }
    }

    fun connect() {
        val raw = _state.value.url.trim()
        val normalized = when {
            raw.isEmpty() -> {
                _state.update { it.copy(error = "Please enter a server URL.") }
                return
            }
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> "https://$raw"
        }

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = settingsRepository.checkHealth(normalized)) {
                is Resource.Success -> {
                    settingsRepository.saveServerUrl(normalized)
                    _state.update { it.copy(loading = false, connected = true) }
                }
                is Resource.Error -> _state.update {
                    it.copy(
                        loading = false,
                        error = result.error.message,
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
