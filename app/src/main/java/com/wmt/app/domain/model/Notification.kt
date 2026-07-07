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
    val bookmarkedAt: String? = null,
    val archivedAt: String? = null,
) {
    val isUnread: Boolean get() = readAt == null
    val isBookmarked: Boolean get() = bookmarkedAt != null
    val isArchived: Boolean get() = archivedAt != null
}
