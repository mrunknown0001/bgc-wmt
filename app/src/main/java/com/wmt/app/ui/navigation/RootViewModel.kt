package com.wmt.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.wmt.app.data.remote.RealtimeClient
import com.wmt.app.data.remote.SessionManager
import com.wmt.app.fcm.InAppMessage
import com.wmt.app.fcm.InAppNotificationBus
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.NotificationRepository
import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Where a notification tap wants to land. */
data class DeepLink(val projectId: Int?, val taskId: Int?)

data class RootUiState(
    val booting: Boolean = true,
    val hasServer: Boolean = false,
    val isLoggedIn: Boolean = false,
    val unreadCount: Int = 0,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val realtimeClient: RealtimeClient,
    inAppNotificationBus: InAppNotificationBus,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val inAppMessages: kotlinx.coroutines.flow.SharedFlow<InAppMessage> = inAppNotificationBus.messages

    val uiState: StateFlow<RootUiState> = combine(
        settingsRepository.serverUrl,
        authRepository.isLoggedIn,
        notificationRepository.unreadCount,
    ) { serverUrl, loggedIn, unread ->
        RootUiState(
            booting = false,
            hasServer = !serverUrl.isNullOrBlank(),
            isLoggedIn = loggedIn,
            unreadCount = unread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootUiState())

    private val _deepLinks = MutableSharedFlow<DeepLink>(extraBufferCapacity = 4)
    val deepLinks = _deepLinks.asSharedFlow()

    init {
        // Force re-login whenever any request returns 401.
        viewModelScope.launch {
            sessionManager.unauthorizedEvents.collect {
                realtimeClient.disconnect()
                authRepository.logout()
            }
        }
        // Open the realtime (Soketi) connection once logged in, for live inbox + comments.
        viewModelScope.launch {
            authRepository.isLoggedIn.first { it }
            val user = authRepository.currentUser.first { it != null } ?: return@launch
            val token = sessionManager.token ?: return@launch
            val baseUrl = sessionManager.serverUrl ?: return@launch
            runCatching { realtimeClient.connect(baseUrl, token, user.id) }
        }
        // Ensure this device's FCM token is registered once we're logged in. Registering
        // only at login was fragile (skipped for already-logged-in sessions); the server
        // upserts, so re-registering each launch is harmless.
        viewModelScope.launch {
            authRepository.isLoggedIn.first { it }
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                authRepository.registerDeviceToken(token)
            }
        }
        // Periodically refresh the unread badge while authenticated.
        viewModelScope.launch {
            while (true) {
                if (uiState.value.isLoggedIn) {
                    notificationRepository.refreshUnreadCount()
                }
                delay(Constants.UNREAD_POLL_INTERVAL_MS)
            }
        }
    }

    fun onDeepLink(projectId: Int?, taskId: Int?) {
        if (projectId == null && taskId == null) return
        _deepLinks.tryEmit(DeepLink(projectId, taskId))
    }

    /** Clears the configured server URL (and session) so the app returns to setup. */
    fun changeServer() {
        viewModelScope.launch {
            realtimeClient.disconnect()
            authRepository.logout()
            settingsRepository.clearServerUrl()
        }
    }
}
