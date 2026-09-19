package com.wmt.app.domain.model

/**
 * Where tapping a notification should land.
 *
 * Both halves of the inbox resolve to one of these — the push FCM delivers and the
 * stored notification the API lists — so the same message behaves the same way whether
 * it arrives as a banner or is found in the list later.
 */
sealed interface NotificationTarget {

    data class Task(val projectId: Int, val taskId: Int) : NotificationTarget

    data class Project(val projectId: Int) : NotificationTarget

    data class ApprovalRequest(val projectId: Int, val requestId: Int) : NotificationTarget

    /**
     * An approval notification that named no request this client can open.
     *
     * The detail route needs the approval project as well as the item, so when either is
     * missing the queue is the nearest place that still shows what arrived, rather than
     * a tap that does nothing.
     */
    data object ApprovalQueue : NotificationTarget

    companion object {
        /** The server's approval notification types are all `approval_*`. */
        fun isApprovalType(type: String?): Boolean = type?.startsWith("approval") == true

        /**
         * The destination these ids describe, or null when none of them name anything
         * this app can open.
         *
         * Approvals win over the task pair: an approval notification may also carry the
         * task a request was raised from, and the request is what the message is about.
         * A task needs its project — the detail route is keyed by both — so a task id
         * without one falls through to the project, then to nothing.
         */
        fun resolve(
            type: String? = null,
            projectId: Int? = null,
            taskId: Int? = null,
            approvalProjectId: Int? = null,
            approvalRequestId: Int? = null,
        ): NotificationTarget? = when {
            approvalProjectId != null && approvalRequestId != null ->
                ApprovalRequest(approvalProjectId, approvalRequestId)
            isApprovalType(type) || approvalRequestId != null -> ApprovalQueue
            projectId != null && taskId != null -> Task(projectId, taskId)
            projectId != null -> Project(projectId)
            else -> null
        }
    }
}
