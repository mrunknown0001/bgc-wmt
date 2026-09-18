package com.wmt.app.ui.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.ApprovalProjectSummary
import com.wmt.app.domain.repository.ApprovalRepository
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewRequestProjectUiState(
    val loading: Boolean = true,
    val projects: List<ApprovalProjectSummary> = emptyList(),
    val error: String? = null,
)

/** Lists the projects this person may raise a request against. */
@HiltViewModel
class NewRequestProjectViewModel @Inject constructor(
    private val repository: ApprovalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NewRequestProjectUiState())
    val state: StateFlow<NewRequestProjectUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.availableProjects()) {
                is Resource.Success -> _state.update {
                    it.copy(loading = false, projects = result.data, error = null)
                }
                is Resource.Error -> _state.update {
                    it.copy(loading = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
