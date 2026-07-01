package com.wmt.app.domain.model

data class NotificationData(
    val title: String,
    val body: String,
    val type: String,
    val taskId: Int?,
    val projectId: Int?,
)

data class Notification(
    val id: String,
    val data: NotificationData,
    val readAt: String?,
    val createdAt: String,
) {
    val isUnread: Boolean get() = readAt == null
}
