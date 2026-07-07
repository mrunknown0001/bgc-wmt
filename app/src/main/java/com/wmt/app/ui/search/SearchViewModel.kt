package com.wmt.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.SearchResults
import com.wmt.app.domain.repository.ProjectRepository
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

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: SearchResults = SearchResults(),
    val error: String? = null,
    /** True once a query has actually been executed (distinguishes idle from no-results). */
    val searched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searching = false, results = SearchResults(), searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce while typing
            _state.update { it.copy(searching = true, error = null) }
            when (val result = repository.globalSearch(query.trim())) {
                is Resource.Success -> _state.update {
                    it.copy(searching = false, results = result.data, searched = true)
                }
                is Resource.Error -> _state.update {
                    it.copy(searching = false, error = result.error.message, searched = true)
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
