package com.wmt.app.data.remote

import android.net.Uri
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
import com.pusher.client.channel.SubscriptionEventListener
import com.pusher.client.util.HttpAuthorizer
import com.wmt.app.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over the Pusher-protocol client (Soketi). Connects to the same host as
 * the configured API server on [Constants.WS_PORT], authenticating private channels via
 * the Sanctum-guarded `/api/broadcasting/auth` endpoint with the bearer token.
 */
@Singleton
class RealtimeClient @Inject constructor() {

    @Volatile
    private var pusher: Pusher? = null

    private val _inboxEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Emits on any event on the current user's notification channel. */
    val inboxEvents: SharedFlow<Unit> = _inboxEvents.asSharedFlow()

    private val noopStatus = object : PrivateChannelEventListener {
        override fun onAuthenticationFailure(message: String?, e: Exception?) {}
        override fun onSubscriptionSucceeded(channelName: String?) {}
        override fun onEvent(event: PusherEvent?) {}
    }

    @Synchronized
    fun connect(baseUrl: String, token: String, userId: Int) {
        if (pusher != null) return
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
        p.connect()
        val channel = p.subscribePrivate("private-App.Models.User.$userId", noopStatus)
        channel.bindGlobal(SubscriptionEventListener { _inboxEvents.tryEmit(Unit) })
        pusher = p
    }

    /** Emits whenever the given task's channel fires (e.g. a new comment). */
    fun observeTask(taskId: Int): Flow<Unit> = callbackFlow {
        val active = pusher
        var channelName: String? = null
        if (active != null) {
            channelName = "private-task.$taskId"
            val channel = active.subscribePrivate(channelName, noopStatus)
            channel.bindGlobal(SubscriptionEventListener { trySend(Unit) })
        }
        awaitClose { channelName?.let { name -> runCatching { pusher?.unsubscribe(name) } } }
    }

    fun disconnect() {
        runCatching { pusher?.disconnect() }
        pusher = null
    }
}
