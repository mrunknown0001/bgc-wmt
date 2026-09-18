package com.wmt.app.domain.model

/** The two figures behind the Approvals tab badge. */
data class ApprovalCounts(
    val toApprove: Int = 0,
    val myRequestsActionNeeded: Int = 0,
) {
    val total: Int get() = toApprove + myRequestsActionNeeded
    val isEmpty: Boolean get() = total == 0
}

/**
 * An approval request, as it appears in a list.
 *
 * One model serves both lists the API exposes, because they are the same row seen from
 * two sides: the approver queue fills in the quorum and step-position figures, while the
 * requestor's list fills in the permission flags. A field the current endpoint did not
 * supply stays null rather than being guessed at.
 */
data class ApprovalRequest(
    val id: Int,
    val projectId: Int,
    val projectName: String?,
    val title: String,
    val description: String?,
    val status: String,
    val seriesNumber: String?,
    val requesterName: String?,
    val sectionName: String?,
    val submittedAt: String?,
    val decidedAt: String?,
    // Approver queue figures.
    val currentStepNumber: Int?,
    val currentStepName: String?,
    val totalSteps: Int?,
    val quorumRequired: Int?,
    val approvalsReceived: Int?,
    val stepDueAt: String?,
    val isOverdue: Boolean,
    // Server-decided permissions, so a screen never offers an action that would 403.
    val canResubmit: Boolean,
    val canCancel: Boolean,
    val canEdit: Boolean,
    val isContentFrozen: Boolean,
) {
    val statusEnum: ApprovalStatus get() = ApprovalStatus.from(status)

    /** The reference to show, falling back to the id when no series number is set. */
    val reference: String get() = seriesNumber ?: "#$id"

    /** Step 2 of 4, when the endpoint supplied both halves. */
    val stepProgressLabel: String?
        get() {
            val current = currentStepNumber ?: return null
            val total = totalSteps ?: return null
            return "Step $current of $total"
        }

    /** 2 of 3 approved, when the endpoint supplied the quorum figures. */
    val quorumLabel: String?
        get() {
            val required = quorumRequired ?: return null
            return "${approvalsReceived ?: 0} of $required approved"
        }
}

/** Everything the request detail screen shows, including this user's permissions on it. */
data class ApprovalRequestDetail(
    val request: ApprovalRequest,
    val canDecide: Boolean,
    val canEdit: Boolean,
    val steps: List<ApprovalStepProgress> = emptyList(),
    val fieldValues: List<ApprovalFieldValue> = emptyList(),
    val comments: List<ApprovalComment> = emptyList(),
    val attachments: List<ApprovalAttachment> = emptyList(),
    val sections: List<ApprovalSection> = emptyList(),
)

/**
 * One step of the chain and how it has gone. A step can be attempted more than once
 * (resubmitting starts a fresh attempt), so attemptNumber distinguishes them.
 */
data class ApprovalStepProgress(
    val instanceId: Int,
    val stepNumber: Int,
    val name: String,
    val status: String,
    val attemptNumber: Int,
    val quorumRequired: Int?,
    val dueAt: String?,
    val approverNames: List<String> = emptyList(),
    val decisions: List<ApprovalDecision> = emptyList(),
) {
    val statusEnum: ApprovalStepStatus get() = ApprovalStepStatus.from(status)

    val approvalsReceived: Int get() = decisions.count { it.isApproved }

    val isAwaitingDecision: Boolean get() = statusEnum == ApprovalStepStatus.ACTIVE
}

data class ApprovalDecision(
    val id: Int,
    val decision: String,
    val comment: String?,
    val decidedAt: String?,
    val deciderName: String?,
) {
    val decisionEnum: ApprovalDecisionType get() = ApprovalDecisionType.from(decision)

    val isApproved: Boolean get() = decisionEnum == ApprovalDecisionType.APPROVED
}

data class ApprovalComment(
    val id: Int,
    val body: String,
    val createdAt: String?,
    val authorName: String?,
    val attachments: List<ApprovalAttachment> = emptyList(),
)

/**
 * A file on a request or one of its comments. downloadUrl is the bearer-token route, so
 * fetching it needs the authenticated client rather than a bare URL load.
 */
data class ApprovalAttachment(
    val id: Int,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val downloadUrl: String?,
) {
    val isImage: Boolean get() = fileType.startsWith("image/")
    val isVideo: Boolean get() = fileType.startsWith("video/")
    val isPdf: Boolean get() = fileType == "application/pdf"
}

data class ApprovalSection(
    val id: Int,
    val name: String,
    val color: String?,
)

/** An approval project as listed. Read-only in the app: setup stays on the web. */
data class ApprovalProjectSummary(
    val id: Int,
    val name: String,
    val description: String?,
    val status: String,
    val ownerName: String?,
    val pendingItems: Int?,
) {
    val statusEnum: ProjectStatus get() = ProjectStatus.from(status)
}

/** What the New Request screen needs to draw itself, from the request-form endpoint. */
data class ApprovalRequestForm(
    val projectId: Int,
    val projectName: String,
    val projectDescription: String?,
    val fields: List<ApprovalCustomField> = emptyList(),
    val sections: List<ApprovalSection> = emptyList(),
)

data class ApprovalCustomField(
    val id: Int,
    val name: String,
    val type: String,
    val isRequired: Boolean,
    val position: Int,
    val options: List<ApprovalFieldOption> = emptyList(),
    /** Pulled out of the field config blob, flattened to text for prefilling a form. */
    val defaultValue: String? = null,
) {
    val typeEnum: ApprovalFieldType get() = ApprovalFieldType.from(type)

    /** Computed server-side, so it is shown but never submitted. */
    val isReadOnly: Boolean get() = typeEnum == ApprovalFieldType.FORMULA
}

data class ApprovalFieldOption(
    val id: Int,
    val label: String,
    val color: String?,
)

/**
 * A stored field value. The server sends no rendered label, so displayValue is resolved
 * here against the field options. It stays null for people fields, whose value is a list
 * of user ids the API gives the app no way to turn into names.
 */
data class ApprovalFieldValue(
    val fieldId: Int,
    val fieldName: String,
    val type: String,
    val displayValue: String?,
    val text: String? = null,
    val number: String? = null,
    val date: String? = null,
    val optionId: Int? = null,
    val optionIds: List<Int> = emptyList(),
) {
    val typeEnum: ApprovalFieldType get() = ApprovalFieldType.from(type)
}

/** One decision this user recorded, for the approval trail. */
data class ApprovalTrailEntry(
    val id: Int,
    val decision: String,
    val comment: String?,
    val decidedAt: String?,
    val stepName: String?,
    val requestId: Int?,
    val requestTitle: String?,
    val projectId: Int?,
    val projectName: String?,
    val requesterName: String?,
) {
    val decisionEnum: ApprovalDecisionType get() = ApprovalDecisionType.from(decision)
}

data class MyApprovalsStats(
    val pending: Int = 0,
    val decidedThisWeek: Int = 0,
)

data class MyRequestsStats(
    val total: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val rejected: Int = 0,
    val changesRequested: Int = 0,
) {
    val needsAction: Int get() = rejected + changesRequested
}

/** The approver queue: a page of requests plus the counters shown above it. */
data class MyApprovals(
    val pending: Page<ApprovalRequest> = Page(),
    val stats: MyApprovalsStats = MyApprovalsStats(),
)

/** The requestor's own submissions, with the per-status counters shown as filters. */
data class MyRequests(
    val items: Page<ApprovalRequest> = Page(),
    val stats: MyRequestsStats = MyRequestsStats(),
)

/**
 * The outcome of recording a decision: the updated request plus the sentence the server
 * wants shown for it, which is the wording the web app uses too.
 */
data class ApprovalDecisionResult(
    val request: ApprovalRequest,
    val message: String?,
)

/**
 * A value being submitted for one custom field.
 *
 * Scalars and lists are genuinely different on the wire: the server stores multi_select
 * and people as JSON arrays and coerces everything else to a single column, so sending a
 * joined string for a multi-select would store one option whose id is nonsense. The
 * caller picks the shape because only it knows the field type.
 */
sealed interface ApprovalFieldInput {

    /** Text, number, date, or a single chosen option id. */
    data class Text(val value: String?) : ApprovalFieldInput

    /** multi_select option ids, or people user ids. */
    data class Selection(val ids: List<Int>) : ApprovalFieldInput
}
