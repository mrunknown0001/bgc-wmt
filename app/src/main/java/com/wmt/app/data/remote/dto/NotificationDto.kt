package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationDataDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "type") val type: String = "",
    @Json(name = "task_id") val taskId: Int? = null,
    @Json(name = "task_title") val taskTitle: String? = null,
    @Json(name = "project_id") val projectId: Int? = null,
    @Json(name = "project_name") val projectName: String? = null,
    @Json(name = "approval_project_id") val approvalProjectId: Int? = null,
    @Json(name = "approval_item_id") val approvalItemId: Int? = null,
    @Json(name = "approval_request_id") val approvalRequestId: Int? = null,
    @Json(name = "approval_project_name") val approvalProjectName: String? = null,
    @Json(name = "item_title") val itemTitle: String? = null,
    @Json(name = "requester") val requester: String? = null,
    @Json(name = "step_name") val stepName: String? = null,
    @Json(name = "decided_by") val decidedBy: String? = null,
    @Json(name = "outcome") val outcome: String? = null,
    @Json(name = "decision_comment") val decisionComment: String? = null,
    @Json(name = "rule_name") val ruleName: String? = null,
    @Json(name = "reason") val reason: String? = null,
    @Json(name = "mentioned_by") val mentionedBy: String? = null,
    @Json(name = "assigned_by") val assignedBy: String? = null,
    @Json(name = "comment_preview") val commentPreview: String? = null,
)

@JsonClass(generateAdapter = true)
data class NotificationDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "data") val data: NotificationDataDto = NotificationDataDto(),
    @Json(name = "read_at") val readAt: String? = null,
    @Json(name = "bookmarked_at") val bookmarkedAt: String? = null,
    @Json(name = "archived_at") val archivedAt: String? = null,
    @Json(name = "created_at") val createdAt: String = "",
)

/** One switch at a time — `POST /api/notification-preferences` takes a single key. */
@JsonClass(generateAdapter = true)
data class NotificationPreferenceRequest(
    @Json(name = "type") val type: String,
    @Json(name = "enabled") val enabled: Boolean,
)
