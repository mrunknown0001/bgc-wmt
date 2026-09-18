package com.wmt.app.data.remote.dto

import com.wmt.app.domain.model.AmendmentKind
import com.wmt.app.domain.model.AmendmentOutcome
import com.wmt.app.domain.model.AmendmentStatus
import com.wmt.app.domain.model.TaskTimesheet
import com.wmt.app.domain.model.TimeLog
import com.wmt.app.domain.model.TimeLogAmendment

fun TimeLogsResponse.toDomain() = TaskTimesheet(
    logs = logs.map { it.toDomain() },
    pendingAdditions = pendingAdditions.map { it.toDomain() },
    totalMinutes = totalMinutes,
    estimatedMinutes = estimatedMinutes,
    canReview = canReviewAmendments,
    amendmentsAvailable = amendmentsAvailable,
    today = today,
)

fun TimeLogDto.toDomain() = TimeLog(
    id = id,
    minutes = minutes,
    duration = duration,
    loggedOn = loggedOn,
    note = note,
    userName = user,
    recordedBy = recordedBy,
    isGenerated = generated,
    isAmended = amended,
    pendingAmendment = pendingAmendment?.toDomain(),
)

fun TimeLogAmendmentDto.toDomain() = TimeLogAmendment(
    id = id,
    timeLogId = timeLogId,
    kind = AmendmentKind.from(kind),
    status = AmendmentStatus.from(status),
    loggedOn = loggedOn,
    originalMinutes = originalMinutes,
    requestedMinutes = requestedMinutes,
    originalDuration = originalDuration,
    requestedDuration = requestedDuration,
    reason = reason,
    requesterName = requester,
    reviewerName = reviewer,
    reviewedAt = reviewedAt,
    reviewNote = reviewNote,
)

fun AmendmentResponse.toDomain() = AmendmentOutcome(
    amendment = amendment.toDomain(),
    applied = applied,
    totalMinutes = totalMinutes,
)
