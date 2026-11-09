package com.wmt.app.domain.model

/** An approval request somebody shared with this user. */
data class SharedRequest(
    val shareId: Int,
    val sharedBy: String?,
    val sharedAt: String?,
    /** Not yet opened. Opening the request clears it on the server. */
    val isUnread: Boolean,
    val requestId: Int,
    val title: String,
    val seriesNumber: String?,
    val status: String,
    val projectId: Int,
    val projectName: String?,
    val requester: String?,
) {
    val statusEnum: ApprovalStatus get() = ApprovalStatus.from(status)
}

data class SharedRequests(
    val page: Page<SharedRequest> = Page(),
    /** Across everything shared, not just this page: the badge. */
    val unread: Int = 0,
)

/** Cover for an approver who is away. */
data class ApprovalCover(
    val id: Int,
    val userId: Int,
    val userName: String,
    val delegateName: String,
    val startsOn: String?,
    val endsOn: String?,
    /** The server's wording of the period. */
    val period: String?,
    val reason: String?,
    val active: Boolean,
    /** Cover for this user, rather than cover naming them as the stand-in. */
    val isMine: Boolean,
    val canManage: Boolean,
)

data class PersonOption(val id: Int, val name: String, val position: String?)

/** One time correction in the queue. */
data class TimeCorrection(
    val id: Int,
    val kind: String,
    val status: String,
    val reason: String?,
    val requester: String?,
    val requestedAt: String?,
    val reviewer: String?,
    val reviewedAt: String?,
    val reviewNote: String?,
    val originalDuration: String?,
    val requestedDuration: String?,
    val loggedOn: String?,
    val taskId: Int?,
    val taskTitle: String?,
    val projectId: Int?,
    val projectName: String?,
) {
    val isAddition: Boolean get() = kind == "add"
    val isPending: Boolean get() = status == "pending"
}

enum class CorrectionTab(val raw: String, val label: String) {
    TO_DECIDE("to_decide", "To decide"),
    MINE("mine", "My requests");

    companion object {
        fun from(raw: String?): CorrectionTab = entries.firstOrNull { it.raw == raw } ?: MINE
    }
}

data class TimeCorrections(
    val page: Page<TimeCorrection> = Page(),
    /** The tab the server settled on: asking for "to decide" without the right gets "mine". */
    val tab: CorrectionTab = CorrectionTab.MINE,
    val canDecideAny: Boolean = false,
    val toDecideCount: Int = 0,
    val mineCount: Int = 0,
)

/** A division, department or team, with its people and the units inside it. */
data class PersonnelUnit(
    val type: String,
    val id: Int,
    val name: String,
    val members: List<PersonnelMember>,
    val children: List<PersonnelUnit>,
)

data class PersonnelMember(
    val id: Int,
    val name: String,
    val position: String?,
    val openTasks: Int,
    val overdueTasks: Int,
    /** "Away — covered by …", when somebody is holding their work. */
    val coveredBy: String? = null,
)

data class OverdueTask(
    val id: Int,
    val title: String,
    val priority: String,
    val dueDate: String?,
    val daysLate: Int,
    val bucket: String?,
    val projectId: Int?,
    val projectName: String?,
    val assigneeName: String?,
) {
    val priorityEnum: TaskPriority get() = TaskPriority.from(priority)
}

data class OverdueBucket(val key: String, val label: String, val count: Int)

data class PersonnelOverdue(
    val tasks: List<OverdueTask> = emptyList(),
    val total: Int = 0,
    val people: Int = 0,
    val buckets: List<OverdueBucket> = emptyList(),
    val limit: Int = 0,
    val capped: Boolean = false,
)


/** One person's overview, as the web's person page shows it to the same viewer. */
data class PersonOverview(
    val id: Int,
    val name: String,
    val position: String?,
    val email: String?,
    val isActive: Boolean,
    /** "Team · Department · Division", whichever of them are set. */
    val placement: String?,
    val openTasks: Int,
    val overdueTasks: Int,
    val completedTasks: Int,
    val totalTasks: Int,
    val completionRate: Int,
    val onTimeRate: Int,
    val projectsInvolved: Int,
    val activity30: Int,
    val lastActivityAt: String?,
    val overdue: List<PersonTask>,
    val overdueCount: Int,
    /** Due today through the end of the month; [PersonTask.inWeek] marks this week's. */
    val upcoming: List<PersonTask>,
    val weekCount: Int,
    val monthCount: Int,
    /** How many rows each list carries at most; the counts are not capped. */
    val listLimit: Int,
    /** "Away — covered by Dee (period)", when whole-person cover is set up. */
    val cover: String?,
    val coverRunning: Boolean,
    /** Their tasks held by a stand-in right now: assigned elsewhere, owed back. */
    val delegatedAway: List<DelegatedTask>,
    val recentActivity: List<PersonActivity>,
)

data class PersonTask(
    val id: Int,
    val title: String,
    val status: String,
    val priority: String,
    val dueDate: String?,
    val projectId: Int?,
    val projectName: String?,
    val inWeek: Boolean,
    val daysLate: Int?,
) {
    val priorityEnum: TaskPriority get() = TaskPriority.from(priority)
    val statusEnum: TaskStatus get() = TaskStatus.from(status)
}

data class DelegatedTask(val task: PersonTask, val delegate: String?, val endsOn: String?)

data class PersonActivity(val description: String, val createdAt: String?)
