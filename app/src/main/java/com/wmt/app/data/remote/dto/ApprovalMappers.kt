package com.wmt.app.data.remote.dto

import com.wmt.app.domain.model.ApprovalAttachment
import com.wmt.app.domain.model.ApprovalComment
import com.wmt.app.domain.model.ApprovalCounts
import com.wmt.app.domain.model.ApprovalCustomField
import com.wmt.app.domain.model.ApprovalDecision
import com.wmt.app.domain.model.ApprovalFieldOption
import com.wmt.app.domain.model.ApprovalFieldType
import com.wmt.app.domain.model.ApprovalFieldValue
import com.wmt.app.domain.model.ApprovalProjectSummary
import com.wmt.app.domain.model.ApprovalRequest
import com.wmt.app.domain.model.ApprovalRequestDetail
import com.wmt.app.domain.model.ApprovalRequestForm
import com.wmt.app.domain.model.ApprovalSection
import com.wmt.app.domain.model.ApprovalStepProgress
import com.wmt.app.domain.model.ApprovalTrailEntry
import com.wmt.app.domain.model.MyApprovals
import com.wmt.app.domain.model.MyApprovalsStats
import com.wmt.app.domain.model.MyRequests
import com.wmt.app.domain.model.MyRequestsStats
import com.wmt.app.domain.model.Page
import com.wmt.app.util.DateUtils
import java.time.temporal.WeekFields

/*
 * DTO to domain mapping for approvals.
 *
 * Two things happen here that the API leaves to the client. Custom field values arrive
 * as raw columns with no rendered label, so select values are resolved against their
 * field options; and the queue figures the list endpoints stitch on (quorum, step
 * position) are derived from the loaded step instances when a payload does not carry
 * them, so the detail screen shows the same numbers as the card it was opened from.
 */

/** Status the API uses for the step currently awaiting a decision. */
private const val STATUS_ACTIVE = "active"
private const val DECISION_APPROVED = "approved"

fun ApprovalCountsDto.toDomain(): ApprovalCounts = ApprovalCounts(
    toApprove = toApprove,
    myRequestsActionNeeded = myRequestsActionNeeded,
)

/** Carries a Laravel paginator page into the domain, mapping each row with [map]. */
fun <T, R> Paginated<T>.toPage(map: (T) -> R): Page<R> = Page(
    items = data.map(map),
    page = currentPage,
    lastPage = lastPage,
    total = total,
)

fun MyApprovalsResponse.toDomain(): MyApprovals = MyApprovals(
    pending = pending.toPage { it.toDomain() },
    stats = MyApprovalsStats(
        pending = stats.pending,
        decidedThisWeek = stats.decidedThisWeek,
    ),
)

fun MyRequestsResponse.toDomain(): MyRequests = MyRequests(
    items = items.toPage { it.toDomain() },
    stats = MyRequestsStats(
        total = stats.total,
        pending = stats.pending,
        approved = stats.approved,
        rejected = stats.rejected,
        changesRequested = stats.changesRequested,
    ),
)

fun ApprovalTrailResponse.toDomain(): Page<ApprovalTrailEntry> = trail.toPage { it.toDomain() }

fun ApprovalTrailEntryDto.toDomain(): ApprovalTrailEntry = ApprovalTrailEntry(
    id = id,
    decision = decision,
    comment = comment,
    decidedAt = decidedAt,
    stepName = stepName,
    requestId = itemId,
    requestTitle = itemTitle,
    projectId = projectId,
    projectName = projectName,
    requesterName = requester,
)

fun ApprovalItemDto.toDomain(): ApprovalRequest {
    val active = activeStepInstance()
    return ApprovalRequest(
        id = id,
        projectId = approvalProjectId,
        projectName = approvalProject?.name,
        title = title,
        description = description,
        status = status,
        seriesNumber = seriesNumber,
        requesterName = requester?.name,
        sectionName = section?.name,
        submittedAt = submittedAt,
        decidedAt = decidedAt,
        // Each of these is stitched on by one list endpoint and absent from the other,
        // so fall back to the loaded step instances rather than showing nothing.
        currentStepNumber = currentStepNumber ?: active?.stepNumber,
        currentStepName = currentStepName ?: active?.step?.name,
        totalSteps = totalSteps ?: chainVersion?.steps?.size?.takeIf { it > 0 },
        quorumRequired = quorumRequired ?: active?.quorumRequired,
        approvalsReceived = approvalsReceived ?: active?.approvalsReceived(),
        stepDueAt = stepDueAt ?: active?.dueAt,
        isOverdue = isOverdue ?: false,
        canResubmit = canResubmit ?: false,
        canCancel = canCancel ?: false,
        canEdit = canEdit ?: false,
        isContentFrozen = isContentFrozen ?: false,
    )
}

fun ApprovalItemDetailResponse.toDomain(): ApprovalRequestDetail = ApprovalRequestDetail(
    request = item.toDomain(),
    canDecide = canDecide,
    canEdit = canEdit,
    // Oldest attempt first, so the history reads top to bottom.
    steps = item.stepInstances
        .sortedWith(compareBy({ it.stepNumber }, { it.attemptNumber }))
        .map { it.toDomain() },
    fieldValues = item.customFieldValues.map { it.toDomain() },
    comments = item.comments.map { it.toDomain() },
    attachments = item.attachments.map { it.toDomain() },
    sections = sections.map { it.toDomain() },
)

/** The step awaiting a decision, when the payload carried one. */
private fun ApprovalItemDto.activeStepInstance(): ApprovalStepInstanceDto? =
    stepInstances.firstOrNull { it.status == STATUS_ACTIVE }

/**
 * How many approvals the step has collected. Prefers the count the server computed and
 * otherwise counts the loaded decisions, which the detail payload carries in full.
 */
private fun ApprovalStepInstanceDto.approvalsReceived(): Int =
    approvalsReceivedCount ?: decisions.count { it.decision == DECISION_APPROVED }

fun ApprovalStepInstanceDto.toDomain(): ApprovalStepProgress = ApprovalStepProgress(
    instanceId = id,
    stepNumber = if (stepNumber > 0) stepNumber else step?.stepNumber ?: 0,
    name = step?.name?.takeIf { it.isNotBlank() } ?: "Step $stepNumber",
    status = status,
    attemptNumber = attemptNumber,
    quorumRequired = quorumRequired,
    dueAt = dueAt,
    approverNames = approvers.mapNotNull { it.user?.name },
    decisions = decisions.map { it.toDomain() },
)

fun ApprovalDecisionDto.toDomain(): ApprovalDecision = ApprovalDecision(
    id = id,
    decision = decision,
    comment = comment,
    decidedAt = decidedAt,
    deciderName = decider?.name,
)

fun ApprovalCommentDto.toDomain(): ApprovalComment = ApprovalComment(
    id = id,
    body = body,
    createdAt = createdAt,
    authorName = user?.name,
    attachments = attachments.map { it.toDomain() },
)

/** Prefers the bearer-token download route; the web one would land on a login page. */
fun ApprovalAttachmentDto.toDomain(): ApprovalAttachment = ApprovalAttachment(
    id = id,
    fileName = fileName,
    fileType = fileType,
    fileSize = fileSize,
    downloadUrl = apiUrl ?: url,
)

fun ApprovalSectionDto.toDomain(): ApprovalSection = ApprovalSection(
    id = id,
    name = name,
    color = color,
)

fun ApprovalProjectDto.toSummary(): ApprovalProjectSummary = ApprovalProjectSummary(
    id = id,
    name = name,
    description = description,
    status = status,
    ownerName = owner?.name,
    pendingItems = pendingItemsCount,
)

fun ApprovalRequestFormResponse.toDomain(): ApprovalRequestForm = ApprovalRequestForm(
    projectId = project.id,
    projectName = project.name,
    projectDescription = project.description,
    fields = customFields.sortedBy { it.position }.map { it.toDomain() },
    sections = sections.sortedBy { it.position ?: 0 }.map { it.toDomain() },
)

fun ApprovalCustomFieldDto.toDomain(): ApprovalCustomField = ApprovalCustomField(
    id = id,
    name = name,
    type = type,
    isRequired = isRequired,
    position = position,
    options = options.sortedBy { it.position }.map { it.toDomain() },
    defaultValue = config.configText("default_value"),
)

fun ApprovalFieldOptionDto.toDomain(): ApprovalFieldOption = ApprovalFieldOption(
    id = id,
    label = label,
    color = color,
)

fun ApprovalFieldValueDto.toDomain(): ApprovalFieldValue {
    val field = customField
    return ApprovalFieldValue(
        fieldId = customFieldId,
        // The item payload eager-loads the definition; the fallback only guards against
        // a value whose field was deleted underneath it.
        fieldName = field?.name ?: "Field $customFieldId",
        type = field?.type ?: ApprovalFieldType.UNKNOWN.raw,
        displayValue = resolveDisplayValue(field),
        text = valueText,
        number = valueNumber?.trimDecimalZeros(),
        date = valueDate,
        optionId = valueOptionId,
        optionIds = optionIdList(),
    )
}

/**
 * Renders a stored value the way the web app does. Mirrors the server display logic,
 * which is not serialized over the API. Formula fields hold no stored value (they are
 * computed), and people fields resolve to user ids the API cannot turn into names here,
 * so both come back null.
 */
private fun ApprovalFieldValueDto.resolveDisplayValue(
    field: ApprovalCustomFieldDto?,
): String? = when (ApprovalFieldType.from(field?.type)) {
    ApprovalFieldType.TEXT, ApprovalFieldType.TEXTAREA -> valueText?.ifBlank { null }
    ApprovalFieldType.NUMBER -> valueNumber?.trimDecimalZeros()
    ApprovalFieldType.DATE -> DateUtils.formatDate(valueDate) ?: valueDate
    ApprovalFieldType.WEEK_OF_YEAR -> isoWeekLabel(valueDate)
    ApprovalFieldType.SINGLE_SELECT ->
        field?.options?.firstOrNull { it.id == valueOptionId }?.label
    ApprovalFieldType.MULTI_SELECT -> optionIdList()
        .mapNotNull { id -> field?.options?.firstOrNull { it.id == id }?.label }
        .joinToString(", ")
        .ifBlank { null }
    ApprovalFieldType.PEOPLE, ApprovalFieldType.FORMULA -> null
    ApprovalFieldType.UNKNOWN -> valueText?.ifBlank { null }
}

/**
 * value_json holds option ids for multi_select and user ids for people. It arrives as
 * JSON numbers, which decode to Double, so each entry is coerced rather than cast.
 */
private fun ApprovalFieldValueDto.optionIdList(): List<Int> =
    valueJson.orEmpty().mapNotNull { raw ->
        when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

/** "12.3400" reads as 12.34 and "12.0000" as 12, matching the server formatting. */
private fun String.trimDecimalZeros(): String =
    if (!contains('.')) this else trimEnd('0').trimEnd('.')

/** The ISO week of the stored reference date, e.g. Week 31, 2026. */
private fun isoWeekLabel(raw: String?): String? {
    val date = DateUtils.parseDate(raw) ?: return null
    val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
    val year = date.get(WeekFields.ISO.weekBasedYear())
    return "Week $week, $year"
}

/**
 * Reads one entry out of a field config blob as display text. The blob is untyped, so a
 * number arrives as a Double and is flattened back to a plain integer where it is one.
 */
private fun Map<String, Any?>?.configText(key: String): String? = when (val raw = this?.get(key)) {
    null -> null
    is String -> raw.ifBlank { null }
    is Boolean -> raw.toString()
    is Number -> raw.toDouble().let { d ->
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }
    else -> null
}
