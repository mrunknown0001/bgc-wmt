package com.wmt.app.data.remote.dto

import com.wmt.app.domain.model.ApprovalCover
import com.wmt.app.domain.model.CorrectionTab
import com.wmt.app.domain.model.PersonOption
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
