package com.wmt.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.DashboardData
import com.wmt.app.domain.repository.DashboardRepository
import com.wmt.app.util.NetworkMonitor
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val data: DashboardData? = null,
    val error: String? = null,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(isRefresh = false)
        observeConnectivity()
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        if (isRefresh) {
            _state.update { it.copy(refreshing = true) }
        } else {
            _state.update { it.copy(loading = it.data == null) }
        }
        loadJob = viewModelScope.launch {
            repository.dashboard().collect { resource ->
                when (resource) {
                    is Resource.Loading -> _state.update {
                        it.copy(
                            loading = it.data == null && resource.data == null,
                            data = resource.data ?: it.data,
                        )
                    }
                    is Resource.Success -> _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            data = resource.data,
                        )
                    }
                    is Resource.Error -> _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = resource.error.message,
                            data = resource.data ?: it.data,
                        )
                    }
                }
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _state.update { it.copy(offline = !online) }
            }
        }
    }
}
