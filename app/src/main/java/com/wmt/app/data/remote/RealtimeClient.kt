package com.wmt.app.data.remote

import android.net.Uri
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
import com.pusher.client.channel.SubscriptionEventListener
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import com.pusher.client.util.HttpAuthorizer
import com.wmt.app.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over the Pusher-protocol client (Soketi). Connects to the same host as
 * the configured API server on [Constants.WS_PORT], authenticating private channels via
 * the Sanctum-guarded `/api/broadcasting/auth` endpoint with the bearer token.
 */
@Singleton
class RealtimeClient @Inject constructor() {

    private val pusherState = MutableStateFlow<Pusher?>(null)

    private val _inboxEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /**
     * Emits on any event on the current user's notification channel, and on every
     * (re)connect so collectors catch up on whatever happened while the socket was down.
     */
    val inboxEvents: SharedFlow<Unit> = _inboxEvents.asSharedFlow()

    private val noopStatus = object : PrivateChannelEventListener {
        override fun onAuthenticationFailure(message: String?, e: Exception?) {}
        override fun onSubscriptionSucceeded(channelName: String?) {}
        override fun onEvent(event: PusherEvent?) {}
    }

    @Synchronized
    fun connect(baseUrl: String, token: String, userId: Int) {
        if (pusherState.value != null) return
        val host = Uri.parse(baseUrl).host ?: return

        val authorizer = HttpAuthorizer(baseUrl.trimEnd('/') + "/api/broadcasting/auth").apply {
            setHeaders(
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Accept" to "application/json",
                ),
            )
        }
        val options = PusherOptions().apply {
            setHost(host)
            setWsPort(Constants.WS_PORT)
            setWssPort(Constants.WS_PORT)
            setUseTLS(false)
            setAuthorizer(authorizer)
        }

        val p = Pusher(Constants.WS_KEY, options)
        p.connect(
            object : ConnectionEventListener {
                override fun onConnectionStateChange(change: ConnectionStateChange) {
                    if (change.currentState == ConnectionState.CONNECTED) _inboxEvents.tryEmit(Unit)
                }

                override fun onError(message: String?, code: String?, e: Exception?) {}
            },
            ConnectionState.ALL,
        )
        val channel = p.subscribePrivate("private-App.Models.User.$userId", noopStatus)
        channel.bindGlobal(SubscriptionEventListener { _inboxEvents.tryEmit(Unit) })
        pusherState.value = p
    }

    /** Emits whenever the given task's channel fires (comment created/updated/deleted). */
    fun observeTask(taskId: Int): Flow<Unit> = observeChannel("private-task.$taskId")

    /** Emits whenever the given project's channel fires (task created/updated/deleted, automation). */
    fun observeProject(projectId: Int): Flow<Unit> = observeChannel("private-project.$projectId")

    private fun observeChannel(channelName: String): Flow<Unit> = callbackFlow {
        // Suspend until connect() has run so subscriptions opened from a screen that
        // loads before (or during) login/startup aren't silently dropped.
        val active = pusherState.filterNotNull().first()
        val channel = runCatching { active.subscribePrivate(channelName, noopStatus) }
            .getOrElse { active.getPrivateChannel(channelName) }
        channel?.bindGlobal(SubscriptionEventListener { trySend(Unit) })
        awaitClose { runCatching { pusherState.value?.unsubscribe(channelName) } }
    }

    fun disconnect() {
        runCatching { pusherState.value?.disconnect() }
        pusherState.value = null
    }
}
