package com.wmt.app.testing

import com.wmt.app.domain.model.ApprovalRequest
import com.wmt.app.domain.model.ApprovalRequestDetail

/**
 * Builders for approval domain objects in tests. The domain models take every field
 * explicitly, which is right for data coming off the wire but tedious in a test that
 * cares about two of them.
 */
fun approvalRequest(
    id: Int = 12,
    projectId: Int = 4,
    projectName: String? = "Finance",
    title: String = "Petty cash",
    description: String? = null,
    status: String = "pending",
    seriesNumber: String? = "PC-00007",
    requesterName: String? = "Dana",
    sectionName: String? = null,
    submittedAt: String? = "2026-09-10T02:00:00.000000Z",
    decidedAt: String? = null,
    currentStepNumber: Int? = 2,
    currentStepName: String? = "Finance head",
    totalSteps: Int? = 3,
    quorumRequired: Int? = 2,
    approvalsReceived: Int? = 1,
    stepDueAt: String? = null,
    isOverdue: Boolean = false,
    canResubmit: Boolean = false,
    canCancel: Boolean = false,
    canEdit: Boolean = false,
    isContentFrozen: Boolean = false,
) = ApprovalRequest(
    id = id,
    projectId = projectId,
    projectName = projectName,
    title = title,
    description = description,
    status = status,
    seriesNumber = seriesNumber,
    requesterName = requesterName,
    sectionName = sectionName,
    submittedAt = submittedAt,
    decidedAt = decidedAt,
    currentStepNumber = currentStepNumber,
    currentStepName = currentStepName,
    totalSteps = totalSteps,
    quorumRequired = quorumRequired,
    approvalsReceived = approvalsReceived,
    stepDueAt = stepDueAt,
    isOverdue = isOverdue,
    canResubmit = canResubmit,
    canCancel = canCancel,
    canEdit = canEdit,
    isContentFrozen = isContentFrozen,
)

fun approvalDetail(
    request: ApprovalRequest = approvalRequest(),
    canDecide: Boolean = true,
    canEdit: Boolean = false,
) = ApprovalRequestDetail(
    request = request,
    canDecide = canDecide,
    canEdit = canEdit,
)
