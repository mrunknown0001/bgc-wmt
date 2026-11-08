package com.wmt.app.data.remote.dto

import com.wmt.app.domain.model.ApprovalCover
import com.wmt.app.domain.model.CorrectionTab
import com.wmt.app.domain.model.OverdueBucket
import com.wmt.app.domain.model.OverdueTask
import com.wmt.app.domain.model.PersonOption
import com.wmt.app.domain.model.PersonnelMember
import com.wmt.app.domain.model.PersonnelOverdue
import com.wmt.app.domain.model.PersonnelUnit
import com.wmt.app.domain.model.SharedRequest
import com.wmt.app.domain.model.SharedRequests
import com.wmt.app.domain.model.TimeCorrection
import com.wmt.app.domain.model.TimeCorrections

fun SharedRequestsResponse.toDomain() = SharedRequests(
    page = shares.toPage { it.toDomain() },
    unread = stats.unread,
)

fun SharedRequestDto.toDomain() = SharedRequest(
    shareId = id,
    sharedBy = sharedBy,
    sharedAt = sharedAt,
    isUnread = viewedAt == null,
    requestId = item.id,
    title = item.title,
    seriesNumber = item.seriesNumber,
    status = item.status,
    projectId = item.approvalProjectId,
    projectName = item.approvalProject?.name,
    requester = item.requester,
)

fun DelegationDto.toDomain() = ApprovalCover(
    id = id,
    userId = user.id,
    userName = user.name,
    delegateName = delegate.name,
    startsOn = startsOn,
    endsOn = endsOn,
    period = period,
    reason = reason,
    active = active,
    isMine = isMine,
    canManage = canManage,
)

fun PersonOptionDto.toDomain() = PersonOption(id = id, name = name, position = position)

fun TimeCorrectionsResponse.toDomain() = TimeCorrections(
    page = amendments.toPage { it.toDomain() },
    tab = CorrectionTab.from(filters.tab),
    canDecideAny = canDecideAny,
    toDecideCount = counts.toDecide,
    mineCount = counts.mine,
)

fun TimeCorrectionDto.toDomain() = TimeCorrection(
    id = id,
    kind = kind,
    status = status,
    reason = reason,
    requester = requester,
    requestedAt = requestedAt,
    reviewer = reviewer,
    reviewedAt = reviewedAt,
    reviewNote = reviewNote,
    originalDuration = originalDuration,
    requestedDuration = requestedDuration,
    loggedOn = loggedOn,
    taskId = taskId,
    taskTitle = taskTitle,
    projectId = projectId,
    projectName = projectName,
)

/** The tree, with each member's cover note filled in from the separate coveredBy map. */
fun MyPersonnelResponse.toDomain(): List<PersonnelUnit> {
    val cover = coverNotes(coveredBy)
    fun PersonnelUnitDto.map(): PersonnelUnit = PersonnelUnit(
        type = type,
        id = id,
        name = name,
        members = members.map { m ->
            PersonnelMember(
                id = m.id,
                name = m.name,
                position = m.position,
                openTasks = m.openTasks,
                overdueTasks = m.overdueTasks,
                coveredBy = cover[m.id],
            )
        },
        children = children.map { it.map() },
    )
    return units.map { it.map() }
}

/** `{ "12": {"delegates": ["Dee"], "period": "…"} }` → 12 to "Dee (period)"; `[]` → nothing. */
internal fun coverNotes(raw: Any?): Map<Int, String> {
    val map = raw as? Map<*, *> ?: return emptyMap()
    return map.entries.mapNotNull { (key, value) ->
        val id = key.toString().toIntOrNull() ?: return@mapNotNull null
        val entry = value as? Map<*, *> ?: return@mapNotNull null
        val names = (entry["delegates"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (names.isEmpty()) return@mapNotNull null
        val period = entry["period"] as? String
        id to (names.joinToString(", ") + (period?.let { " ($it)" } ?: ""))
    }.toMap()
}

fun PersonnelOverdueResponse.toDomain() = PersonnelOverdue(
    tasks = tasks.map {
        OverdueTask(
            id = it.id,
            title = it.title,
            priority = it.priority,
            dueDate = it.dueDate,
            daysLate = it.daysLate,
            bucket = it.bucket,
            projectId = it.project?.id,
            projectName = it.project?.name,
            assigneeName = it.assignee?.name,
        )
    },
    total = summary.total,
    people = summary.people,
    buckets = buckets.map { OverdueBucket(it.key, it.label, it.count) },
    limit = limit,
    capped = capped,
)

