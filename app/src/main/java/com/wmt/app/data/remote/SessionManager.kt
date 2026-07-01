package com.wmt.app.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the values the OkHttp interceptors must read synchronously (server URL +
 * auth token) and broadcasts 401 events so the app can force a re-login.
 */
@Singleton
class SessionManager @Inject constructor() {

    @Volatile
    var serverUrl: String? = null

    @Volatile
    var token: String? = null

    private val _unauthorizedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorizedEvents: SharedFlow<Unit> = _unauthorizedEvents.asSharedFlow()

    fun notifyUnauthorized() {
        token = null
        _unauthorizedEvents.tryEmit(Unit)
    }
}
