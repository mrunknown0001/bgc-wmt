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
        load(isRefresh = false)
        viewModelScope.launch { repository.refreshUnreadCount() }
        observeConnectivity()
        // Live updates: refresh when the user's notification channel fires, and on
        // foreground FCM pushes (covers the websocket being down — the push already
        // proves a new notification exists).
        viewModelScope.launch { realtimeClient.inboxEvents.collect { poll() } }
        viewModelScope.launch { inAppBus.messages.collect { poll() } }
    }

    fun refresh() {
        load(isRefresh = true)
        viewModelScope.launch { repository.refreshUnreadCount() }
    }

    fun setFilter(filter: InboxFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter, notifications = emptyList()) }
        load(isRefresh = false)
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
        load(isRefresh = false)
        viewModelScope.launch { repository.refreshUnreadCount() }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        if (isRefresh) {
            _state.update { it.copy(refreshing = true) }
        } else {
            _state.update { it.copy(loading = it.notifications.isEmpty()) }
        }
        loadJob = viewModelScope.launch {
            repository.notifications(_state.value.filter.param).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _state.update {
                        it.copy(
                            loading = it.notifications.isEmpty() && resource.data == null,
                            notifications = resource.data ?: it.notifications,
                        )
                    }
                    is Resource.Success -> _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            notifications = resource.data,
                        )
                    }
                    is Resource.Error -> _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = resource.error.message,
                            notifications = resource.data ?: it.notifications,
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
