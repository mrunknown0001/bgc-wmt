package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/*
 * Wire shapes for the approvals API.
 *
 * Unlike the task endpoints — which hand-serialize into tidy arrays — the approvals
 * controllers return Eloquent models directly, so these mirror the table columns plus
 * whatever relations each endpoint eager-loads and whatever the controller stitches on
 * per request. Fields that only some endpoints populate are therefore nullable, and
 * everything carries a default: a shape that varies by endpoint must never fail to parse.
 */

// ---- Requests (approval items) ----

/**
 * An approval request. The column set is fixed; the trailing blocks are per-endpoint --
 * /my-approvals adds the quorum and step-position figures, /my-requests adds the
 * permission flags, and the item payload adds the deep relations.
 */
@JsonClass(generateAdapter = true)
data class ApprovalItemDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "approval_project_id") val approvalProjectId: Int = 0,
    @Json(name = "approval_section_id") val approvalSectionId: Int? = null,
    @Json(name = "approval_chain_version_id") val approvalChainVersionId: Int? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "requested_by") val requestedBy: Int? = null,
    @Json(name = "status") val status: String = "pending",
    @Json(name = "submitted_at") val submittedAt: String? = null,
    @Json(name = "decided_at") val decidedAt: String? = null,
    @Json(name = "archived_at") val archivedAt: String? = null,
    @Json(name = "series_number") val seriesNumber: String? = null,
    @Json(name = "position") val position: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,

    // Relations, by eager-load. Keys are the snake_case relation names.
    @Json(name = "approval_project") val approvalProject: ApprovalProjectDto? = null,
    @Json(name = "requester") val requester: UserSummaryDto? = null,
    @Json(name = "section") val section: ApprovalSectionDto? = null,
    @Json(name = "step_instances") val stepInstances: List<ApprovalStepInstanceDto> = emptyList(),
    @Json(name = "chain_version") val chainVersion: ApprovalChainVersionDto? = null,
    @Json(name = "custom_field_values")
    val customFieldValues: List<ApprovalFieldValueDto> = emptyList(),
    @Json(name = "comments") val comments: List<ApprovalCommentDto> = emptyList(),
    @Json(name = "attachments") val attachments: List<ApprovalAttachmentDto> = emptyList(),

    // Stitched on by /my-approvals to drive the queue card: "2 of 3 approved, step 2 of 4".
    // current_step_number is also a real column, overwritten there with the live value.
    @Json(name = "current_step_number") val currentStepNumber: Int? = null,
    @Json(name = "total_steps") val totalSteps: Int? = null,
    @Json(name = "quorum_required") val quorumRequired: Int? = null,
    @Json(name = "approvals_received") val approvalsReceived: Int? = null,
    @Json(name = "step_due_at") val stepDueAt: String? = null,
    @Json(name = "is_overdue") val isOverdue: Boolean? = null,

    // Stitched on by /my-requests, so a screen never offers an action the server would
    // then refuse with a 403.
    @Json(name = "current_step_name") val currentStepName: String? = null,
    @Json(name = "can_resubmit") val canResubmit: Boolean? = null,
    @Json(name = "can_cancel") val canCancel: Boolean? = null,
    @Json(name = "can_edit") val canEdit: Boolean? = null,
    @Json(name = "is_content_frozen") val isContentFrozen: Boolean? = null,
)

// ---- Chain, steps and decisions ----

@JsonClass(generateAdapter = true)
data class ApprovalChainVersionDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "approval_chain_id") val approvalChainId: Int? = null,
    @Json(name = "chain") val chain: ApprovalChainDto? = null,
    @Json(name = "steps") val steps: List<ApprovalStepDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ApprovalChainDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "is_active") val isActive: Boolean? = null,
    @Json(name = "is_default") val isDefault: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class ApprovalStepDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "step_number") val stepNumber: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "approver_type") val approverType: String? = null,
    @Json(name = "quorum_mode") val quorumMode: String? = null,
    @Json(name = "quorum_count") val quorumCount: Int? = null,
)

/** One attempt at one step of the chain — the unit a decision is recorded against. */
@JsonClass(generateAdapter = true)
data class ApprovalStepInstanceDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "approval_step_id") val approvalStepId: Int? = null,
    @Json(name = "step_number") val stepNumber: Int = 0,
    @Json(name = "attempt_number") val attemptNumber: Int = 1,
    @Json(name = "status") val status: String = "pending",
    @Json(name = "quorum_required") val quorumRequired: Int? = null,
    @Json(name = "activated_at") val activatedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "due_at") val dueAt: String? = null,
    @Json(name = "step") val step: ApprovalStepDto? = null,
    @Json(name = "approvers") val approvers: List<ApprovalApproverDto> = emptyList(),
    @Json(name = "decisions") val decisions: List<ApprovalDecisionDto> = emptyList(),
    /** withCount alias from /my-approvals; absent elsewhere. */
    @Json(name = "approvals_received_count") val approvalsReceivedCount: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ApprovalApproverDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "user") val user: UserSummaryDto? = null,
)

@JsonClass(generateAdapter = true)
data class ApprovalDecisionDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "decision") val decision: String = "",
    @Json(name = "comment") val comment: String? = null,
    @Json(name = "decided_at") val decidedAt: String? = null,
    @Json(name = "decided_by") val decidedBy: Int? = null,
    @Json(name = "decider") val decider: UserSummaryDto? = null,
)

// ---- Projects, sections, custom fields ----

@JsonClass(generateAdapter = true)
data class ApprovalProjectDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "status") val status: String = "active",
    @Json(name = "owner_id") val ownerId: Int? = null,
    /** Cast date:Y-m-d server-side, so a plain date with no time. */
    @Json(name = "due_date") val dueDate: String? = null,
    @Json(name = "is_pinned") val isPinned: Boolean? = null,
    @Json(name = "series_prefix") val seriesPrefix: String? = null,
    @Json(name = "owner") val owner: UserSummaryDto? = null,
    @Json(name = "members") val members: List<UserSummaryDto> = emptyList(),
    @Json(name = "sections") val sections: List<ApprovalSectionDto> = emptyList(),
    @Json(name = "custom_fields") val customFields: List<ApprovalCustomFieldDto> = emptyList(),
    @Json(name = "chains") val chains: List<ApprovalChainDto> = emptyList(),
    /** withCount alias from the project list. */
    @Json(name = "pending_items_count") val pendingItemsCount: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ApprovalSectionDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "approval_project_id") val approvalProjectId: Int? = null,
    @Json(name = "name") val name: String = "",
    @Json(name = "color") val color: String? = null,
    @Json(name = "position") val position: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ApprovalCustomFieldDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    /** text, textarea, number, date, week_of_year, single_select, multi_select, people, formula. */
    @Json(name = "type") val type: String = "text",
    @Json(name = "is_required") val isRequired: Boolean = false,
    @Json(name = "position") val position: Int = 0,
    /**
     * Free-form JSON (default_value, sort_mode, formula, result_type, decimal_places)
     * whose value types vary by field type, so it is carried through untyped rather than
     * risking a parse failure on an unexpected shape.
     */
    @Json(name = "config") val config: Map<String, Any?>? = null,
    @Json(name = "options") val options: List<ApprovalFieldOptionDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ApprovalFieldOptionDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "approval_custom_field_id") val customFieldId: Int? = null,
    @Json(name = "label") val label: String = "",
    @Json(name = "color") val color: String? = null,
    @Json(name = "position") val position: Int = 0,
)

/**
 * One field value. Which column is populated depends on the field type, and the server
 * does not serialize a rendered label, so the app resolves select values against the
 * field options itself.
 */
@JsonClass(generateAdapter = true)
data class ApprovalFieldValueDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "approval_custom_field_id") val customFieldId: Int = 0,
    @Json(name = "value_text") val valueText: String? = null,
    /** Cast decimal:4, which Laravel serializes as a string such as "12.3400". */
    @Json(name = "value_number") val valueNumber: String? = null,
    @Json(name = "value_date") val valueDate: String? = null,
    /** Option ids for multi_select, user ids for people. */
    @Json(name = "value_json") val valueJson: List<Any?>? = null,
    @Json(name = "value_option_id") val valueOptionId: Int? = null,
    @Json(name = "custom_field") val customField: ApprovalCustomFieldDto? = null,
)

// ---- Comments and attachments ----

@JsonClass(generateAdapter = true)
data class ApprovalCommentDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "approval_item_id") val approvalItemId: Int? = null,
    @Json(name = "body") val body: String = "",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "user") val user: UserSummaryDto? = null,
    @Json(name = "attachments") val attachments: List<ApprovalAttachmentDto> = emptyList(),
)

/**
 * The approvals controllers append both download URLs. "url" is the web route, which
 * sits behind session middleware and bounces a bearer-token client to a login page;
 * "api_url" is the same file over the API guard. The app must use [apiUrl].
 */
@JsonClass(generateAdapter = true)
data class ApprovalAttachmentDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "file_name") val fileName: String = "",
    @Json(name = "file_type") val fileType: String = "",
    @Json(name = "file_size") val fileSize: Long = 0,
    @Json(name = "url") val url: String? = null,
    @Json(name = "api_url") val apiUrl: String? = null,
)

// ---- Response wrappers ----

/** GET /api/approvals/counts — the bottom-tab badge. */
@JsonClass(generateAdapter = true)
data class ApprovalCountsDto(
    @Json(name = "to_approve") val toApprove: Int = 0,
    @Json(name = "my_requests_action_needed") val myRequestsActionNeeded: Int = 0,
)

/** GET /api/my-approvals — the approver queue. */
@JsonClass(generateAdapter = true)
data class MyApprovalsResponse(
    @Json(name = "pending") val pending: Paginated<ApprovalItemDto> = Paginated(),
    @Json(name = "stats") val stats: MyApprovalsStatsDto = MyApprovalsStatsDto(),
)

@JsonClass(generateAdapter = true)
data class MyApprovalsStatsDto(
    @Json(name = "pending") val pending: Int = 0,
    @Json(name = "decided_this_week") val decidedThisWeek: Int = 0,
)

/** GET /api/my-requests — the requestor's own submissions. */
@JsonClass(generateAdapter = true)
data class MyRequestsResponse(
    @Json(name = "items") val items: Paginated<ApprovalItemDto> = Paginated(),
    @Json(name = "stats") val stats: MyRequestsStatsDto = MyRequestsStatsDto(),
)

@JsonClass(generateAdapter = true)
data class MyRequestsStatsDto(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "pending") val pending: Int = 0,
    @Json(name = "approved") val approved: Int = 0,
    @Json(name = "rejected") val rejected: Int = 0,
    @Json(name = "changes_requested") val changesRequested: Int = 0,
)

/** GET /api/my-approvals/trail — flattened server-side, unlike the other two lists. */
@JsonClass(generateAdapter = true)
data class ApprovalTrailResponse(
    @Json(name = "trail") val trail: Paginated<ApprovalTrailEntryDto> = Paginated(),
)

@JsonClass(generateAdapter = true)
data class ApprovalTrailEntryDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "decision") val decision: String = "",
    @Json(name = "comment") val comment: String? = null,
    @Json(name = "decided_at") val decidedAt: String? = null,
    @Json(name = "step_name") val stepName: String? = null,
    @Json(name = "item_id") val itemId: Int? = null,
    @Json(name = "item_title") val itemTitle: String? = null,
    @Json(name = "project_id") val projectId: Int? = null,
    @Json(name = "project_name") val projectName: String? = null,
    /** A name, not an object: the trail flattens the requester to a string. */
    @Json(name = "requester") val requester: String? = null,
)

/** GET /api/approval-projects/available — projects a request may be raised against. */
@JsonClass(generateAdapter = true)
data class AvailableApprovalProjectsResponse(
    @Json(name = "projects") val projects: List<ApprovalProjectDto> = emptyList(),
)

/** GET /api/approval-projects/{id}. */
@JsonClass(generateAdapter = true)
data class ApprovalProjectResponse(
    @Json(name = "project") val project: ApprovalProjectDto = ApprovalProjectDto(),
)

/**
 * GET /api/approval-projects/{id}/request-form — what the New Request screen renders
 * itself from. Note the camelCase key: this payload is hand-built, not a model.
 */
@JsonClass(generateAdapter = true)
data class ApprovalRequestFormResponse(
    @Json(name = "project") val project: ApprovalProjectDto = ApprovalProjectDto(),
    @Json(name = "customFields") val customFields: List<ApprovalCustomFieldDto> = emptyList(),
    @Json(name = "sections") val sections: List<ApprovalSectionDto> = emptyList(),
)

/** Create, update and resubmit all answer with the item alone. */
@JsonClass(generateAdapter = true)
data class ApprovalItemResponse(
    @Json(name = "item") val item: ApprovalItemDto = ApprovalItemDto(),
)

/** GET .../items/{id} — the item plus this user's permissions on it. */
@JsonClass(generateAdapter = true)
data class ApprovalItemDetailResponse(
    @Json(name = "item") val item: ApprovalItemDto = ApprovalItemDto(),
    @Json(name = "canDecide") val canDecide: Boolean = false,
    @Json(name = "canEdit") val canEdit: Boolean = false,
    @Json(name = "sections") val sections: List<ApprovalSectionDto> = emptyList(),
)

/** POST .../advance — the decision, echoed with a message to show. */
@JsonClass(generateAdapter = true)
data class ApprovalAdvanceResponse(
    @Json(name = "item") val item: ApprovalItemDto = ApprovalItemDto(),
    @Json(name = "message") val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class ApprovalCommentResponse(
    @Json(name = "comment") val comment: ApprovalCommentDto = ApprovalCommentDto(),
)

// ---- Request bodies ----

/** POST .../advance — action is strictly "approved" or "rejected". */
@JsonClass(generateAdapter = true)
data class ApprovalAdvanceRequest(
    @Json(name = "action") val action: String,
    @Json(name = "comment") val comment: String? = null,
)

/**
 * PUT .../items/{id}. Custom field values are keyed by field id; the server ignores
 * formula fields and drops ids that do not belong to the project.
 *
 * Values are untyped because they are not one type: multi_select and people are arrays
 * server-side, everything else a scalar, and sending the wrong shape stores the wrong
 * thing rather than failing.
 */
@JsonClass(generateAdapter = true)
data class UpdateApprovalItemRequest(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "customFieldValues") val customFieldValues: Map<String, Any?>? = null,
)
