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


fun NotesResponse.toDomain() = com.wmt.app.domain.model.NoteList(
    notes = notes.map {
        com.wmt.app.domain.model.NoteRow(
            id = it.id,
            title = it.title,
            preview = it.snippet?.ifBlank { null } ?: it.excerpt?.ifBlank { null },
            folderName = it.folder?.name,
            owner = it.owner,
            isMine = it.isMine,
            canEdit = it.canEdit,
            updatedAt = it.updatedAt,
        )
    },
    folders = folders.map { com.wmt.app.domain.model.NoteFolder(it.id, it.name, it.depth, it.noteCount) },
    counts = mapOf(
        com.wmt.app.domain.model.NoteScope.MINE to counts.mine,
        com.wmt.app.domain.model.NoteScope.SHARED to counts.shared,
        com.wmt.app.domain.model.NoteScope.ARCHIVED to counts.archived,
    ),
)

fun NoteDetailDto.toDomain() = com.wmt.app.domain.model.Note(
    id = id,
    title = title,
    content = content,
    owner = owner,
    isMine = isMine,
    archived = archived,
    role = role,
    canEdit = canEdit,
    canArchive = canArchive,
    canDelete = canDelete,
    updatedAt = updatedAt,
)

fun PersonTaskDto.toDomain() = com.wmt.app.domain.model.PersonTask(
    id = id,
    title = title,
    status = status,
    priority = priority,
    dueDate = dueDate,
    projectId = project?.id,
    projectName = project?.name,
    inWeek = inWeek,
    daysLate = daysLate,
)

fun PersonOverviewResponse.toDomain() = com.wmt.app.domain.model.PersonOverview(
    id = profile.id,
    name = profile.name,
    position = profile.position?.ifBlank { null },
    email = profile.email,
    isActive = profile.isActive,
    placement = listOfNotNull(profile.team, profile.department, profile.division)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { null },
    openTasks = kpis.tasksActive,
    overdueTasks = kpis.tasksOverdue,
    completedTasks = kpis.tasksCompleted,
    totalTasks = kpis.tasksTotal,
    completionRate = kpis.completionRate,
    onTimeRate = kpis.onTimeRate,
    projectsInvolved = kpis.projectsInvolved,
    activity30 = kpis.activity30,
    lastActivityAt = kpis.lastActivityAt,
    overdue = upcoming.overdue.map { it.toDomain() },
    overdueCount = upcoming.overdueCount,
    upcoming = upcoming.tasks.map { it.toDomain() },
    weekCount = upcoming.weekCount,
    monthCount = upcoming.monthCount,
    listLimit = upcoming.limit,
    cover = currentCover?.takeIf { it.delegates.isNotEmpty() }?.let { c ->
        c.delegates.joinToString(", ") + (c.period?.let { " ($it)" } ?: "")
    },
    coverRunning = currentCover?.running == true,
    // A row whose task has gone is dropped, as the web page drops it.
    delegatedAway = delegatedAway.mapNotNull { d ->
        d.task?.let { com.wmt.app.domain.model.DelegatedTask(it.toDomain(), d.delegate, d.endsOn) }
    },
    recentActivity = recentActivity.mapNotNull { a ->
        val text = a.description?.ifBlank { null }
            ?: listOfNotNull(a.action, a.entity).joinToString(" ").ifBlank { null }
            ?: return@mapNotNull null
        com.wmt.app.domain.model.PersonActivity(text, a.createdAt)
    },
)
