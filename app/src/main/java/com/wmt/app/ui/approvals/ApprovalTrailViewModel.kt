package com.wmt.app.ui.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.ApprovalDecisionType
import com.wmt.app.domain.model.ApprovalTrailEntry
import com.wmt.app.domain.model.Page
import com.wmt.app.domain.repository.ApprovalRepository
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

data class ApprovalTrailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val entries: Page<ApprovalTrailEntry> = Page(),
    /** Null is everything; the endpoint accepts only approved or rejected. */
    val decisionFilter: ApprovalDecisionType? = null,
    val searchQuery: String = "",
    val error: String? = null,
) {
    val isEmpty: Boolean get() = entries.items.isEmpty()
    val isFiltered: Boolean get() = decisionFilter != null || searchQuery.isNotBlank()
}

/**
 * Decisions this user has recorded, newest first.
 *
 * Read-only by design: the trail is a record of what was decided, and nothing in the API
 * lets a decision be taken back. Search covers both the comment left and the request
 * title, which is what the server matches on.
 */
@HiltViewModel
class ApprovalTrailViewModel @Inject constructor(
    private val repository: ApprovalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ApprovalTrailUiState())
    val state: StateFlow<ApprovalTrailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun retry() = load(isRefresh = false)

    fun setDecisionFilter(decision: ApprovalDecisionType?) {
        if (decision == _state.value.decisionFilter) return
        _state.update { it.copy(decisionFilter = decision) }
        load(isRefresh = false)
    }

    /** Search runs on the server, so keystrokes are debounced. */
    fun onSearchChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load(isRefresh = false)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        if (_state.value.searchQuery.isEmpty()) return
        _state.update { it.copy(searchQuery = "") }
        load(isRefresh = false)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.refreshing || !current.entries.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = repository.trail(
                page = current.entries.page + 1,
                decision = current.decisionFilter?.raw,
                search = current.searchQuery,
            )
            when (result) {
                is Resource.Success -> _state.update {
                    it.copy(loadingMore = false, entries = it.entries.plus(result.data))
                }
                // A failed page keeps what is on screen and keeps hasMore, so scrolling
                // again retries rather than silently ending the list.
                is Resource.Error -> _state.update {
                    it.copy(loadingMore = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        _state.update {
            if (isRefresh) {
                it.copy(refreshing = true, error = null)
            } else {
                it.copy(loading = it.entries.items.isEmpty(), error = null)
            }
        }
        loadJob = viewModelScope.launch {
            val current = _state.value
            val result = repository.trail(
                page = FIRST_PAGE,
                decision = current.decisionFilter?.raw,
                search = current.searchQuery,
            )
            when (result) {
                is Resource.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        entries = result.data,
                        error = null,
                    )
                }
                is Resource.Error -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
