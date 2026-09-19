package com.wmt.app.fcm

import com.wmt.app.domain.model.NotificationTarget

/**
 * A push message, read out of the key/value data FCM delivers.
 *
 * Parsing lives here rather than in the service for two reasons: it is the one place
 * that decides where a push points, so the notification the service posts and the
 * launch intent the activity reads back cannot disagree; and it is plain data, so the
 * routing rules are testable without a device.
 */
data class PushPayload(
    val title: String,
    val body: String,
    val type: String?,
    val target: NotificationTarget?,
    val notificationId: String?,
) {
    companion object {
        private const val DEFAULT_TITLE = "WMT"

        /**
         * Every key [targetOf] reads, so a launch intent can be turned back into the
         * same map the message arrived as.
         */
        val TARGET_KEYS = listOf(
            "type",
            "project_id",
            "task_id",
            "approval_project_id",
            "approval_item_id",
            "approval_request_id",
            "item_id",
            "request_id",
        )

        fun from(
            data: Map<String, String>,
            notificationTitle: String? = null,
            notificationBody: String? = null,
        ): PushPayload = PushPayload(
            title = data["title"] ?: notificationTitle ?: DEFAULT_TITLE,
            body = data["body"] ?: notificationBody.orEmpty(),
            type = data["type"]?.takeIf { it.isNotBlank() },
            target = targetOf(data),
            notificationId = data["notification_id"]?.takeIf { it.isNotBlank() },
        )

        /** Where the ids in [data] point, if anywhere. */
        fun targetOf(data: Map<String, String>): NotificationTarget? {
            val type = data["type"]?.takeIf { it.isNotBlank() }
            val approval = NotificationTarget.isApprovalType(type)
            return NotificationTarget.resolve(
                type = type,
                projectId = data.id("project_id"),
                taskId = data.id("task_id"),
                approvalProjectId = data.id("approval_project_id"),
                approvalRequestId = data.id("approval_item_id")
                    ?: data.id("approval_request_id")
                    // Unqualified keys are read only on an approval message, where
                    // nothing else lays claim to them.
                    ?: data.id("item_id").takeIf { approval }
                    ?: data.id("request_id").takeIf { approval },
            )
        }

        /**
         * FCM data is always strings, and the ids the server sends are positive, so a
         * zero or a blank is the same as an absent key.
         */
        private fun Map<String, String>.id(key: String): Int? =
            this[key]?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    }
}
