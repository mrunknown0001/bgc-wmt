package com.wmt.app.fcm

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class InAppMessage(
    val title: String,
    val body: String,
    val projectId: Int?,
    val taskId: Int?,
)

/** Bridges foreground FCM messages to the UI so it can show an in-app banner/snackbar. */
@Singleton
class InAppNotificationBus @Inject constructor() {
    private val _messages = MutableSharedFlow<InAppMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<InAppMessage> = _messages.asSharedFlow()

    fun emit(message: InAppMessage) {
        _messages.tryEmit(message)
    }
}
