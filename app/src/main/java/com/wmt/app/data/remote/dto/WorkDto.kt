package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/*
 * The endpoints that mirror a web page for the phone (WMT 54a6ae4): requests shared with
 * me, approval cover, the time corrections queue, My Personnel and the people picker.
 * Each server response is the web page's own data, keys unchanged -- which is why some
 * are camelCase (coveredBy, viewerId) and some snake_case: they are what the page reads.
 */

// ---- Requests shared with me: GET /api/shared-requests ----

@JsonClass(generateAdapter = true)
data class SharedRequestsResponse(
    @Json(name = "shares") val shares: Paginated<SharedRequestDto> = Paginated(),
    @Json(name = "stats") val stats: SharedStatsDto = SharedStatsDto(),
)

@JsonClass(generateAdapter = true)
data class SharedRequestDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "shared_by") val sharedBy: String? = null,
    @Json(name = "shared_at") val sharedAt: String? = null,
    /** Null until the recipient opens the request; opening it sets this on the server. */
    @Json(name = "viewed_at") val viewedAt: String? = null,
    @Json(name = "item") val item: SharedItemDto = SharedItemDto(),
)

@JsonClass(generateAdapter = true)
data class SharedItemDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "series_number") val seriesNumber: String? = null,
    @Json(name = "status") val status: String = "",
    @Json(name = "approval_project_id") val approvalProjectId: Int = 0,
    @Json(name = "approval_project") val approvalProject: IdNameDto? = null,
    /** A name, not an object: the page shows who asked, nothing more. */
    @Json(name = "requester") val requester: String? = null,
)

@JsonClass(generateAdapter = true)
data class SharedStatsDto(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "unread") val unread: Int = 0,
    @Json(name = "approved") val approved: Int = 0,
    @Json(name = "rejected") val rejected: Int = 0,
)

@JsonClass(generateAdapter = true)
data class IdNameDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
)

// ---- Approval cover: /api/approval-delegations ----

@JsonClass(generateAdapter = true)
data class DelegationsResponse(
    @Json(name = "delegations") val delegations: List<DelegationDto> = emptyList(),
    @Json(name = "can_manage_others") val canManageOthers: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class DelegationDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "user") val user: IdNameDto = IdNameDto(),
    @Json(name = "delegate") val delegate: IdNameDto = IdNameDto(),
    @Json(name = "starts_on") val startsOn: String? = null,
    @Json(name = "ends_on") val endsOn: String? = null,
    /** The server's own wording of the period, e.g. "10 Nov – 14 Nov 2026". */
    @Json(name = "period") val period: String? = null,
    @Json(name = "reason") val reason: String? = null,
    @Json(name = "active") val active: Boolean = false,
    @Json(name = "is_mine") val isMine: Boolean = false,
    @Json(name = "can_manage") val canManage: Boolean = false,
)

/** POST body. user_id must be the caller unless they hold manage-users. */
@JsonClass(generateAdapter = true)
data class CreateDelegationRequest(
    @Json(name = "user_id") val userId: Int,
    @Json(name = "delegate_id") val delegateId: Int,
    @Json(name = "starts_on") val startsOn: String,
    @Json(name = "ends_on") val endsOn: String? = null,
    @Json(name = "reason") val reason: String? = null,
)

@JsonClass(generateAdapter = true)
data class CreateDelegationResponse(
    @Json(name = "delegation") val delegation: DelegationDto = DelegationDto(),
    /** Pending approvals the stand-in was added to straight away. */
    @Json(name = "backfilled") val backfilled: Int = 0,
)

@JsonClass(generateAdapter = true)
data class RemoveDelegationResponse(
    /** Pending approvals the stand-in was taken back off. */
    @Json(name = "removed") val removed: Int = 0,
)

// ---- People picker: GET /api/people-options ----

@JsonClass(generateAdapter = true)
data class PeopleOptionsResponse(
    @Json(name = "users") val users: List<PersonOptionDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PersonOptionDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "position") val position: String? = null,
)

// ---- Time corrections queue: GET /api/time-corrections ----

@JsonClass(generateAdapter = true)
data class TimeCorrectionsResponse(
    @Json(name = "amendments") val amendments: Paginated<TimeCorrectionDto> = Paginated(),
    @Json(name = "filters") val filters: TimeCorrectionFiltersDto = TimeCorrectionFiltersDto(),
    @Json(name = "can_decide_any") val canDecideAny: Boolean = false,
    @Json(name = "counts") val counts: TimeCorrectionCountsDto = TimeCorrectionCountsDto(),
)

@JsonClass(generateAdapter = true)
data class TimeCorrectionDto(
    @Json(name = "id") val id: Int = 0,
    /** "change" an existing entry, or "add" a day that has none. */
    @Json(name = "kind") val kind: String = "change",
    @Json(name = "status") val status: String = "pending",
    @Json(name = "reason") val reason: String? = null,
    @Json(name = "requester") val requester: String? = null,
    @Json(name = "requested_at") val requestedAt: String? = null,
    @Json(name = "reviewer") val reviewer: String? = null,
    @Json(name = "reviewed_at") val reviewedAt: String? = null,
    @Json(name = "review_note") val reviewNote: String? = null,
    @Json(name = "original_duration") val originalDuration: String? = null,
    @Json(name = "requested_duration") val requestedDuration: String? = null,
    @Json(name = "current_duration") val currentDuration: String? = null,
    @Json(name = "logged_on") val loggedOn: String? = null,
    @Json(name = "task_id") val taskId: Int? = null,
    @Json(name = "task_title") val taskTitle: String? = null,
    @Json(name = "project_id") val projectId: Int? = null,
    @Json(name = "project_name") val projectName: String? = null,
)

@JsonClass(generateAdapter = true)
data class TimeCorrectionFiltersDto(
    /** The tab the server settled on, which may differ from the one asked for. */
    @Json(name = "tab") val tab: String = "mine",
    @Json(name = "status") val status: String = "pending",
)

@JsonClass(generateAdapter = true)
data class TimeCorrectionCountsDto(
    @Json(name = "to_decide") val toDecide: Int = 0,
    @Json(name = "mine") val mine: Int = 0,
)

// ---- My Personnel: GET /api/my-personnel and /api/my-personnel/overdue ----
// A 403 means this person heads or leads nothing: the screen is simply not theirs.

@JsonClass(generateAdapter = true)
data class MyPersonnelResponse(
    @Json(name = "units") val units: List<PersonnelUnitDto> = emptyList(),
    /**
     * Who is away and who holds their work, keyed by user id. Untyped for the same reason
     * as a task's customFieldValues: an empty PHP array serializes as `[]`, not `{}`.
     */
    @Json(name = "coveredBy") val coveredBy: Any? = null,
    @Json(name = "viewerId") val viewerId: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PersonnelUnitDto(
    /** division, department or team. */
    @Json(name = "type") val type: String = "",
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "members") val members: List<PersonnelMemberDto> = emptyList(),
    @Json(name = "children") val children: List<PersonnelUnitDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PersonnelMemberDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "position") val position: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "open_tasks") val openTasks: Int = 0,
    @Json(name = "overdue_tasks") val overdueTasks: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PersonnelOverdueResponse(
    @Json(name = "tasks") val tasks: List<OverdueTaskDto> = emptyList(),
    @Json(name = "summary") val summary: OverdueSummaryDto = OverdueSummaryDto(),
    @Json(name = "buckets") val buckets: List<OverdueBucketDto> = emptyList(),
    @Json(name = "limit") val limit: Int = 0,
    /** More are overdue than were sent; say so rather than presenting a sample as all. */
    @Json(name = "capped") val capped: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class OverdueTaskDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "status") val status: String = "",
    @Json(name = "priority") val priority: String = "medium",
    @Json(name = "due_date") val dueDate: String? = null,
    @Json(name = "days_late") val daysLate: Int = 0,
    @Json(name = "bucket") val bucket: String? = null,
    @Json(name = "project") val project: IdNameDto? = null,
    @Json(name = "assignee") val assignee: IdNameDto? = null,
)

@JsonClass(generateAdapter = true)
data class OverdueSummaryDto(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "people") val people: Int = 0,
    @Json(name = "projects") val projects: Int = 0,
)

@JsonClass(generateAdapter = true)
data class OverdueBucketDto(
    @Json(name = "key") val key: String = "",
    @Json(name = "label") val label: String = "",
    @Json(name = "count") val count: Int = 0,
)

