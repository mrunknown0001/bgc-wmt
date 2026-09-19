package com.wmt.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.wmt.app.data.remote.RealtimeClient
import com.wmt.app.data.remote.SessionManager
import com.wmt.app.fcm.InAppMessage
import com.wmt.app.fcm.InAppNotificationBus
import com.wmt.app.domain.model.ApprovalCounts
import com.wmt.app.domain.model.NotificationTarget
import com.wmt.app.domain.model.UserSummary
import com.wmt.app.domain.repository.ApprovalRepository
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.NotificationRepository
import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.util.AppError
import com.wmt.app.util.Constants
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

data class RootUiState(
    val booting: Boolean = true,
    val hasServer: Boolean = false,
    val isLoggedIn: Boolean = false,
    val unreadCount: Int = 0,
    /** Drives the top-bar avatar, which is how Profile is reached. */
    val currentUser: UserSummary? = null,
    /**
     * Whether the Approvals destination appears at all. Decided by the server via the
     * user capabilities, never inferred from a role name here.
     */
    val canUseApprovals: Boolean = false,
    val approvalCounts: ApprovalCounts = ApprovalCounts(),
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val realtimeClient: RealtimeClient,
    private val approvalRepository: ApprovalRepository,
    inAppNotificationBus: InAppNotificationBus,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val inAppMessages: kotlinx.coroutines.flow.SharedFlow<InAppMessage> = inAppNotificationBus.messages

    private val _approvalCounts = MutableStateFlow(ApprovalCounts())

    val uiState: StateFlow<RootUiState> = combine(
        settingsRepository.serverUrl,
        authRepository.isLoggedIn,
        notificationRepository.unreadCount,
        authRepository.currentUser,
        _approvalCounts,
    ) { serverUrl, loggedIn, unread, user, approvals ->
        RootUiState(
            booting = false,
            hasServer = !serverUrl.isNullOrBlank(),
            isLoggedIn = loggedIn,
            unreadCount = unread,
            currentUser = user?.summary,
            canUseApprovals = user?.capabilities?.canUseApprovals == true,
            approvalCounts = approvals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootUiState())

    /** Guards the one-off rotation for sessions predating expiry tracking. */
    private var rotatedUnknownExpiry = false

    private val _deepLinks = MutableSharedFlow<NotificationTarget>(extraBufferCapacity = 4)
    val deepLinks = _deepLinks.asSharedFlow()

    init {
        // Force re-login whenever any request returns 401.
        viewModelScope.launch {
            sessionManager.unauthorizedEvents.collect {
                realtimeClient.disconnect()
                _approvalCounts.value = ApprovalCounts()
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
        // Rotate the token before it lapses. The swap only works while the app is live,
        // so each launch checks once logged in and a long-running process re-checks.
        viewModelScope.launch {
            authRepository.isLoggedIn.first { it }
            while (true) {
                if (uiState.value.isLoggedIn) maybeRefreshToken()
                delay(Constants.TOKEN_REFRESH_CHECK_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            uiState.first { it.isLoggedIn && it.canUseApprovals }
            refreshApprovalCounts()
        }
        // Live unread badge: any event on the user's notification channel (or a socket
        // reconnect) refreshes the count immediately, wherever the user is in the app.
        viewModelScope.launch {
            realtimeClient.inboxEvents.collect {
                if (uiState.value.isLoggedIn) {
                    notificationRepository.refreshUnreadCount()
                    refreshApprovalCounts()
                }
            }
        }
        // Periodically refresh the unread badge while authenticated.
        viewModelScope.launch {
            while (true) {
                if (uiState.value.isLoggedIn) {
                    notificationRepository.refreshUnreadCount()
                    refreshApprovalCounts()
                }
                delay(Constants.UNREAD_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Refreshes the Approvals badge.
     *
     * Skipped for anyone the server says cannot use approvals: the endpoint answers
     * zeros for them, so the call would be pure overhead on every poll. A failure
     * leaves the previous figures in place rather than flashing an empty badge.
     */
    private suspend fun refreshApprovalCounts() {
        if (!uiState.value.canUseApprovals) return
        val result = approvalRepository.counts()
        if (result is Resource.Success) {
            _approvalCounts.value = result.data
        }
    }

    /**
     * Rotates the token once it is within [Constants.TOKEN_REFRESH_MARGIN_MS] of expiry.
     * A session stored before the app tracked expiry has no deadline to compare against,
     * so it is rotated once to learn one.
     */
    private suspend fun maybeRefreshToken() {
        val expiresAt = authRepository.tokenExpiresAt.first()
        if (expiresAt == null) {
            if (rotatedUnknownExpiry) return
            rotatedUnknownExpiry = true
        } else if (expiresAt - System.currentTimeMillis() > Constants.TOKEN_REFRESH_MARGIN_MS) {
            return
        }
        val result = authRepository.refreshToken()
        // A 401 has already forced logout through the interceptor; a 429 tells us when to
        // come back, and anything else just waits for the next scheduled check.
        if (result is Resource.Error) {
            val error = result.error
            if (error is AppError.RateLimited) {
                delay((error.retryAfterSeconds ?: 60).coerceIn(1, 3600) * 1000L)
            }
        }
    }

    fun onDeepLink(target: NotificationTarget?) {
        if (target == null) return
        _deepLinks.tryEmit(target)
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
