package com.wmt.app.ui.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.ApprovalCounts
import com.wmt.app.domain.model.ApprovalRequest
import com.wmt.app.domain.model.ApprovalStatus
import com.wmt.app.domain.model.MyApprovalsStats
import com.wmt.app.domain.model.MyRequestsStats
import com.wmt.app.domain.model.Page
import com.wmt.app.domain.model.UserCapabilities
import com.wmt.app.domain.repository.ApprovalRepository
import com.wmt.app.domain.repository.AuthRepository
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

/** The two sides of approvals: what is waiting on you, and what you have raised. */
enum class ApprovalsTab(val label: String) {
    TO_APPROVE("To approve"),
    MY_REQUESTS("My requests"),
}

data class ApprovalsUiState(
    val tab: ApprovalsTab = ApprovalsTab.TO_APPROVE,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** Requests awaiting this user, accumulated across pages. */
    val queue: Page<ApprovalRequest> = Page(),
    /** Requests this user raised, accumulated across pages. */
    val myRequests: Page<ApprovalRequest> = Page(),
    val counts: ApprovalCounts = ApprovalCounts(),
    val queueStats: MyApprovalsStats = MyApprovalsStats(),
    val requestStats: MyRequestsStats = MyRequestsStats(),
    /** Status filter, My requests only. Null is everything. */
    val statusFilter: ApprovalStatus? = null,
    val searchQuery: String = "",
    val error: String? = null,
    val offline: Boolean = false,
    val capabilities: UserCapabilities = UserCapabilities(),
    /**
     * Whether [capabilities] have been read yet. Until they have, an empty set is
     * indistinguishable from an account with no approvals rights, and rendering that
     * would flash the wrong answer on every open.
     */
    val capabilitiesResolved: Boolean = false,
) {
    /**
     * Which tabs this person has. An approver sees both, since holding approvals access
     * also entitles them to their own submissions; a pure requestor sees only their own.
     */
    val visibleTabs: List<ApprovalsTab>
        get() = buildList {
            if (capabilities.canAccessApprovals) add(ApprovalsTab.TO_APPROVE)
            if (capabilities.canRequest || capabilities.canAccessApprovals) {
                add(ApprovalsTab.MY_REQUESTS)
            }
        }

    val page: Page<ApprovalRequest>
        get() = if (tab == ApprovalsTab.TO_APPROVE) queue else myRequests

    val isEmpty: Boolean get() = page.items.isEmpty()
    val isSearching: Boolean get() = searchQuery.isNotBlank()
    val isFiltered: Boolean get() = isSearching || statusFilter != null
}

/**
 * Backs the Approvals section: the approver queue, the requestor's own submissions, and
 * the counters above them.
 *
 * Each list is only ever requested for someone the server says may see it. The queue in
 * particular answers 403 to a pure requestor, so asking would turn an ordinary state
 * into an error on screen.
 */
@HiltViewModel
class ApprovalsViewModel @Inject constructor(
    private val approvalRepository: ApprovalRepository,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(ApprovalsUiState())
    val state: StateFlow<ApprovalsUiState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                val capabilities = user?.capabilities ?: UserCapabilities()
                val hadCapabilities = _state.value.capabilitiesResolved
                _state.update {
                    it.copy(
                        capabilities = capabilities,
                        capabilitiesResolved = user != null,
                        // Someone who cannot see the queue starts on their own requests.
                        tab = if (capabilities.canAccessApprovals) {
                            it.tab
                        } else {
                            ApprovalsTab.MY_REQUESTS
                        },
                        // Nothing to wait for if neither list is available.
                        loading = if (capabilities.canUseApprovals) it.loading else false,
                    )
                }
                // Capabilities usually arrive after the first composition, so the list is
                // requested the moment they say which one to ask for.
                if (!hadCapabilities && user != null) load(isRefresh = false)
            }
        }
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _state.update { it.copy(offline = !online) }
            }
        }
        refreshCounts()
    }

    /** Re-reads the counters and the active list whenever the screen returns to view. */
    fun onScreenVisible() {
        refreshCounts()
        if (_state.value.capabilitiesResolved) load(isRefresh = true)
    }

    fun refresh() {
        refreshCounts()
        load(isRefresh = true)
    }

    fun retry() = load(isRefresh = false)

    fun selectTab(tab: ApprovalsTab) {
        if (tab == _state.value.tab) return
        searchJob?.cancel()
        // Search and status are per-list, so switching starts clean rather than carrying
        // a filter onto a list it was never meant for.
        _state.update {
            it.copy(tab = tab, searchQuery = "", statusFilter = null, error = null)
        }
        load(isRefresh = false)
    }

    /** My requests only: the status chips, driven by the counters the endpoint reports. */
    fun setStatusFilter(status: ApprovalStatus?) {
        if (status == _state.value.statusFilter) return
        _state.update { it.copy(statusFilter = status) }
        load(isRefresh = false)
    }

    /** Search runs on the server, so keystrokes are debounced rather than each one costing a request. */
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

    /** Appends the next page of whichever list is showing. */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.refreshing || !current.page.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = current.page.page + 1
            when (current.tab) {
                ApprovalsTab.TO_APPROVE -> {
                    when (val result = approvalRepository.myApprovals(next, current.searchQuery)) {
                        is Resource.Success -> _state.update {
                            it.copy(
                                loadingMore = false,
                                queue = it.queue.plus(result.data.pending),
                                queueStats = result.data.stats,
                            )
                        }
                        // A failed page leaves what is on screen and keeps hasMore, so
                        // scrolling again retries instead of silently ending the list.
                        is Resource.Error -> _state.update {
                            it.copy(loadingMore = false, error = result.error.message)
                        }
                        is Resource.Loading -> Unit
                    }
                }
                ApprovalsTab.MY_REQUESTS -> {
                    val result = approvalRepository.myRequests(
                        page = next,
                        status = current.statusFilter?.raw,
                        search = current.searchQuery,
                    )
                    when (result) {
                        is Resource.Success -> _state.update {
                            it.copy(
                                loadingMore = false,
                                myRequests = it.myRequests.plus(result.data.items),
                                requestStats = result.data.stats,
                            )
                        }
                        is Resource.Error -> _state.update {
                            it.copy(loadingMore = false, error = result.error.message)
                        }
                        is Resource.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            val result = approvalRepository.counts()
            if (result is Resource.Success) {
                _state.update { it.copy(counts = result.data) }
            }
        }
    }

    private fun load(isRefresh: Boolean) {
        val current = _state.value
        val tab = current.tab
        // Never ask for a list this account is refused; the screen shows why instead.
        val allowed = when (tab) {
            ApprovalsTab.TO_APPROVE -> current.capabilities.canAccessApprovals
            ApprovalsTab.MY_REQUESTS -> current.capabilities.canUseApprovals
        }
        if (!allowed) {
            _state.update { it.copy(loading = false, refreshing = false) }
            return
        }

        listJob?.cancel()
        _state.update {
            if (isRefresh) {
                it.copy(refreshing = true, error = null)
            } else {
                it.copy(loading = it.page.items.isEmpty(), error = null)
            }
        }
        listJob = viewModelScope.launch {
            when (tab) {
                ApprovalsTab.TO_APPROVE -> {
                    when (val result = approvalRepository.myApprovals(FIRST_PAGE, current.searchQuery)) {
                        is Resource.Success -> _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                queue = result.data.pending,
                                queueStats = result.data.stats,
                                error = null,
                            )
                        }
                        is Resource.Error -> _state.update {
                            it.copy(loading = false, refreshing = false, error = result.error.message)
                        }
                        is Resource.Loading -> Unit
                    }
                }
                ApprovalsTab.MY_REQUESTS -> {
                    val result = approvalRepository.myRequests(
                        page = FIRST_PAGE,
                        status = current.statusFilter?.raw,
                        search = current.searchQuery,
                    )
                    when (result) {
                        is Resource.Success -> _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                myRequests = result.data.items,
                                requestStats = result.data.stats,
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
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
