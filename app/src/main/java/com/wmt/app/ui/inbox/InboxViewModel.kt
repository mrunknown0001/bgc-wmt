package com.wmt.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.Notification
import com.wmt.app.domain.model.NotificationTarget
import com.wmt.app.data.remote.RealtimeClient
import com.wmt.app.fcm.InAppNotificationBus
import com.wmt.app.domain.repository.NotificationRepository
import com.wmt.app.util.NetworkMonitor
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Server-side inbox filters (query param values for GET /api/notifications). */
enum class InboxFilter(val param: String?, val label: String) {
    ALL(null, "All"),
    UNREAD("unread", "Unread"),
    MENTIONED("mentioned", "Mentions"),
    BOOKMARKED("bookmarked", "Bookmarked"),
    ARCHIVED("archived", "Archived"),
}

data class InboxUiState(
    val loading: Boolean = true,
    val notifications: List<Notification> = emptyList(),
    val error: String? = null,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val filter: InboxFilter = InboxFilter.ALL,
    /** Highest page fetched so far; the next scroll asks for [page] + 1. */
    val page: Int = 1,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val networkMonitor: NetworkMonitor,
    private val realtimeClient: RealtimeClient,
    private val inAppBus: InAppNotificationBus,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(isRefresh = false, resetPaging = true)
        viewModelScope.launch { repository.refreshUnreadCount() }
        observeConnectivity()
        // Live updates: refresh when the user's notification channel fires, and on
        // foreground FCM pushes (covers the websocket being down — the push already
        // proves a new notification exists).
        viewModelScope.launch { realtimeClient.inboxEvents.collect { poll() } }
        viewModelScope.launch { inAppBus.messages.collect { poll() } }
    }

    /** Pull-to-refresh: back to page one, dropping anything paged in below it. */
    fun refresh() {
        load(isRefresh = true, resetPaging = true)
        viewModelScope.launch { repository.refreshUnreadCount() }
    }

    fun setFilter(filter: InboxFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter, notifications = emptyList(), page = 1, hasMore = false) }
        load(isRefresh = false, resetPaging = true)
    }

    /** Bookmark/unbookmark optimistically; drop from the list when the filter no longer matches. */
    fun toggleBookmark(n: Notification) {
        val bookmarked = !n.isBookmarked
        val now = Instant.now().toString()
        _state.update { st ->
            st.copy(
                notifications = st.notifications.mapNotNull {
                    when {
                        it.id != n.id -> it
                        st.filter == InboxFilter.BOOKMARKED && !bookmarked -> null
                        else -> it.copy(bookmarkedAt = if (bookmarked) now else null)
                    }
                },
            )
        }
        viewModelScope.launch { repository.toggleBookmark(n.id) }
    }

    /** Archive/unarchive optimistically; archived items leave every non-archive filter. */
    fun toggleArchive(n: Notification) {
        val archiving = !n.isArchived
        val now = Instant.now().toString()
        _state.update { st ->
            st.copy(
                notifications = st.notifications.mapNotNull {
                    when {
                        it.id != n.id -> it
                        st.filter == InboxFilter.ARCHIVED && !archiving -> null
                        st.filter != InboxFilter.ARCHIVED && archiving -> null
                        else -> it.copy(archivedAt = if (archiving) now else null)
                    }
                },
            )
        }
        viewModelScope.launch {
            if (archiving) repository.archive(n.id) else repository.unarchive(n.id)
            repository.refreshUnreadCount()
        }
    }

    /** Silent background refresh for near-real-time updates (no spinner). */
    fun poll() {
        // Keeps whatever has been paged in: a poll every 20s must not yank the list
        // back to one page under someone who has scrolled into last month.
        load(isRefresh = false, resetPaging = false)
        viewModelScope.launch { repository.refreshUnreadCount() }
    }

    /**
     * Fetches the page after the one on screen. A failed page leaves the list alone and
     * keeps [InboxUiState.hasMore] set, so scrolling again retries rather than silently
     * ending the list.
     */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.refreshing || current.loading || !current.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = repository.notificationsPage(current.filter.param, current.page + 1)
            _state.update { st ->
                when (result) {
                    is Resource.Success -> st.copy(
                        loadingMore = false,
                        notifications = InboxPaging.appendPage(st.notifications, result.data.items),
                        page = result.data.page,
                        hasMore = result.data.hasMore,
                    )
                    is Resource.Error -> st.copy(loadingMore = false, error = result.error.message)
                    is Resource.Loading -> st.copy(loadingMore = false)
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun load(isRefresh: Boolean, resetPaging: Boolean) {
        loadJob?.cancel()
        if (isRefresh) {
            _state.update { it.copy(refreshing = true) }
        } else {
            _state.update { it.copy(loading = it.notifications.isEmpty()) }
        }
        loadJob = viewModelScope.launch {
            repository.notifications(_state.value.filter.param).collect { resource ->
                when (resource) {
                    // The cached page only fills an empty screen. Dropping it over a
                    // list that has pages loaded would collapse the scroll for the
                    // moment it takes the network page to arrive.
                    is Resource.Loading -> _state.update {
                        it.copy(
                            loading = it.notifications.isEmpty() && resource.data == null,
                            notifications = if (it.notifications.isEmpty()) {
                                resource.data?.items.orEmpty()
                            } else {
                                it.notifications
                            },
                        )
                    }
                    is Resource.Success -> _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            notifications = InboxPaging.mergeFirstPage(
                                fresh = resource.data.items,
                                existing = it.notifications,
                                keepPagedTail = !resetPaging && it.page > 1,
                            ),
                            page = if (resetPaging) resource.data.page else maxOf(it.page, resource.data.page),
                            hasMore = if (resetPaging || it.page <= 1) resource.data.hasMore else it.hasMore,
                        )
                    }
                    is Resource.Error -> _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = resource.error.message,
                            notifications = if (it.notifications.isEmpty()) {
                                resource.data?.items.orEmpty()
                            } else {
                                it.notifications
                            },
                        )
                    }
                }
            }
        }
    }

    fun onNotificationClick(
        n: Notification,
        navigate: (NotificationTarget?) -> Unit,
    ) {
        if (n.isUnread) {
            // Reflect the change in the list immediately; the server call follows.
            val now = Instant.now().toString()
            _state.update { st ->
                st.copy(notifications = st.notifications.map {
                    if (it.id == n.id && it.readAt == null) it.copy(readAt = now) else it
                })
            }
            viewModelScope.launch { repository.markRead(n.id) }
        }
        navigate(n.data.target)
    }

    /** Marks a single notification read (optimistic) without navigating — used by swipe. */
    fun markRead(n: Notification) {
        if (!n.isUnread) return
        val now = Instant.now().toString()
        _state.update { st ->
            st.copy(notifications = st.notifications.map {
                if (it.id == n.id) it.copy(readAt = now) else it
            })
        }
        viewModelScope.launch { repository.markRead(n.id) }
    }

    fun markAllRead() {
        val now = Instant.now().toString()
        _state.update { st ->
            st.copy(notifications = st.notifications.map {
                if (it.readAt == null) it.copy(readAt = now) else it
            })
        }
        viewModelScope.launch { repository.markAllRead() }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _state.update { it.copy(offline = !online) }
            }
        }
    }
}
