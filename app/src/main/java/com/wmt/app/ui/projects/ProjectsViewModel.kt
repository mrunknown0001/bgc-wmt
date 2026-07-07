package com.wmt.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.repository.ProjectRepository
import com.wmt.app.util.NetworkMonitor
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val loading: Boolean = true,
    val projects: List<Project> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val creating: Boolean = false,
    val createError: String? = null,
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        load(isRefresh = false)
        observeConnectivity()
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            if (q.isBlank()) {
                load(isRefresh = false)
            } else {
                loadJob?.cancel()
                _state.update { it.copy(loading = it.projects.isEmpty()) }
                when (val result = repository.searchProjects(q.trim())) {
                    is Resource.Success -> _state.update {
                        it.copy(loading = false, error = null, projects = result.data)
                    }
                    is Resource.Error -> _state.update {
                        it.copy(loading = false, error = result.error.message)
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun refresh() = load(isRefresh = true)

    /**
     * Silently refetches when the screen re-enters composition (back-nav from a
     * project, tab switch) so task-count/progress changes don't linger stale. The
     * first call (initial composition, right after init's load) is skipped.
     */
    fun onScreenVisible() {
        if (firstVisible) {
            firstVisible = false
            return
        }
        // Don't clobber active search results with the unfiltered list.
        if (_state.value.query.isBlank()) load(isRefresh = false)
    }

    private var firstVisible = true

    fun clearCreateError() = _state.update { it.copy(createError = null) }

    fun createProject(
        name: String,
        description: String?,
        status: String,
        dueDate: String?,
        onSuccess: () -> Unit,
    ) {
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            when (val result = repository.createProject(name, description, status, dueDate)) {
                is Resource.Success -> {
                    _state.update { it.copy(creating = false) }
                    load(isRefresh = true)
                    onSuccess()
                }
                is Resource.Error -> _state.update {
                    it.copy(creating = false, createError = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        if (isRefresh) {
            _state.update { it.copy(refreshing = true) }
        } else {
            _state.update { it.copy(loading = it.projects.isEmpty()) }
        }
        loadJob = viewModelScope.launch {
            repository.projects().collect { resource ->
                when (resource) {
                    is Resource.Loading -> _state.update {
                        it.copy(
                            loading = it.projects.isEmpty() && resource.data.isNullOrEmpty(),
                            projects = resource.data ?: it.projects,
                        )
                    }
                    is Resource.Success -> _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            projects = resource.data,
                        )
                    }
                    is Resource.Error -> _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = resource.error.message,
                            projects = resource.data ?: it.projects,
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
