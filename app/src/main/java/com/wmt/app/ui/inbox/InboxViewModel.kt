package com.wmt.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.Notification
import com.wmt.app.data.remote.RealtimeClient
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

data class InboxUiState(
    val loading: Boolean = true,
    val notifications: List<Notification> = emptyList(),
    val error: String? = null,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val networkMonitor: NetworkMonitor,
    private val realtimeClient: RealtimeClient,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(isRefresh = false)
        viewModelScope.launch { repository.refreshUnreadCount() }
        observeConnectivity()
        // Live updates: refresh when the user's notification channel fires.
        viewModelScope.launch { realtimeClient.inboxEvents.collect { poll() } }
    }

    fun refresh() {
        load(isRefresh = true)
        viewModelScope.launch { repository.refreshUnreadCount() }
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
            repository.notifications().collect { resource ->
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
        navigate: (projectId: Int?, taskId: Int?) -> Unit,
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
        navigate(n.data.projectId, n.data.taskId)
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
