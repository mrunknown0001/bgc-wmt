package com.wmt.app.domain.model

data class NotificationData(
    val title: String,
    val body: String,
    val type: String,
    val taskId: Int?,
    val projectId: Int?,
    val approvalProjectId: Int? = null,
    val approvalRequestId: Int? = null,
) {
    /**
     * Where tapping this notification should land, resolved the same way a push is, so
     * a message opens the same screen from the list as it does from the banner.
     */
    val target: NotificationTarget?
        get() = NotificationTarget.resolve(
            type = type,
            projectId = projectId,
            taskId = taskId,
            approvalProjectId = approvalProjectId,
            approvalRequestId = approvalRequestId,
        )
}

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
