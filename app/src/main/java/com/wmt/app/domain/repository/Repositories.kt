package com.wmt.app.domain.repository

import com.wmt.app.domain.model.ApprovalComment
import com.wmt.app.domain.model.ApprovalCounts
import com.wmt.app.domain.model.ApprovalDecisionResult
import com.wmt.app.domain.model.ApprovalDecisionType
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalProjectSummary
import com.wmt.app.domain.model.ApprovalRequest
import com.wmt.app.domain.model.ApprovalRequestDetail
import com.wmt.app.domain.model.ApprovalRequestForm
import com.wmt.app.domain.model.ApprovalTrailEntry
import com.wmt.app.domain.model.Comment
import com.wmt.app.domain.model.AmendmentOutcome
import com.wmt.app.domain.model.PausePreview
import com.wmt.app.domain.model.TaskTimesheet
import com.wmt.app.domain.model.TaskClock
import com.wmt.app.domain.model.MyApprovals
import com.wmt.app.domain.model.MyRequests
import com.wmt.app.domain.model.Page
import com.wmt.app.domain.model.DashboardData
import com.wmt.app.domain.model.Notification
import com.wmt.app.domain.model.PersonalTodo
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.model.ProjectDetail
import com.wmt.app.domain.model.SearchResults
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.model.User
import com.wmt.app.util.Resource
import kotlinx.coroutines.flow.Flow

/** Server URL configuration + reachability check (first-launch setup). */
interface SettingsRepository {
    val serverUrl: Flow<String?>
    suspend fun checkHealth(baseUrl: String): Resource<Unit>
    suspend fun saveServerUrl(baseUrl: String)
    suspend fun clearServerUrl()
    /** "system" | "light" | "dark". */
    val themeMode: Flow<String>
    suspend fun setThemeMode(mode: String)
    /** Server-configured general upload cap in MB (videos have a fixed 50MB cap). */
    val maxUploadSizeMb: Flow<Int>
    /** Fetches /api/settings and caches the upload limit locally. */
    suspend fun refreshAppSettings()
}

interface AuthRepository {
    val isLoggedIn: Flow<Boolean>
    val currentUser: Flow<User?>
    suspend fun login(email: String, password: String): Resource<User>
    suspend fun logout(): Resource<Unit>
    suspend fun refreshProfile(): Resource<User>
    /** Changes the signed-in user's password; returns the server's success message. */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
    ): Resource<String>
    /**
     * Revokes every other session's token, keeping this device signed in; returns the message.
     * Requires the current [password] because the backend re-confirms the user.
     */
    suspend fun logoutOtherDevices(password: String): Resource<String>
    /** Epoch millis at which the current token lapses; null when the server did not say. */
    val tokenExpiresAt: Flow<Long?>

    /**
     * Swaps the live token for a freshly minted one. The server deletes the old token as
     * soon as it answers, so a success must be persisted before the next request goes out.
     */
    suspend fun refreshToken(): Resource<Unit>

    /** Registers the current FCM token with the backend. */
    suspend fun registerDeviceToken(token: String): Resource<Unit>
}

interface DashboardRepository {
    /** Emits cached data first (if any), then the refreshed network result. */
    fun dashboard(): Flow<Resource<DashboardData>>
}

interface TaskRepository {
    fun myTasks(): Flow<Resource<List<Task>>>
    suspend fun taskDetail(projectId: Int, taskId: Int): Resource<TaskDetail>
    suspend fun updateStatus(projectId: Int, taskId: Int, status: String): Resource<Task>

    /*
     * The task clock. Each call answers with only the part it changed, so [current] is
     * merged with the reply rather than replaced by it -- the project switch that
     * decided the clock was shown at all is not repeated in these payloads.
     *
     * Project-scoped only: a standalone task has no project to hold the setting.
     */

    /** Starts the clock, which also moves the task to in progress. */
    suspend fun startClock(projectId: Int, taskId: Int, current: TaskClock): Resource<TaskClock>

    /** What pausing now would record, asked before offering the dialog. */
    suspend fun pausePreview(projectId: Int, taskId: Int): Resource<PausePreview>

    /** Pauses and records [minutes] against the day. */
    suspend fun pauseClock(
        projectId: Int,
        taskId: Int,
        minutes: Int,
        note: String? = null,
        current: TaskClock,
    ): Resource<TaskClock>

    suspend fun resumeClock(projectId: Int, taskId: Int, current: TaskClock): Resource<TaskClock>

    /*
     * The timesheet. Read-only apart from removing an entry somebody typed: a figure the
     * clock worked out is changed by asking, not by editing.
     */

    suspend fun timesheet(taskId: Int): Resource<TaskTimesheet>

    /** Refused on an entry the clock wrote; ask for a correction instead. */
    suspend fun deleteTimeLog(timeLogId: Int): Resource<Int>

    /**
     * Asks for an entry to say something else. [duration] is sent as typed -- the server
     * reads "1.5", "1:30" and "90m" alike, and parsing it here would only lose detail.
     */
    suspend fun amendTimeLog(
        timeLogId: Int,
        duration: String,
        reason: String,
    ): Resource<AmendmentOutcome>

    /** Asks for an entry on a day with none. [loggedOn] is yyyy-MM-dd and cannot be future. */
    suspend fun addTimeLogEntry(
        taskId: Int,
        duration: String,
        loggedOn: String,
        reason: String,
    ): Resource<AmendmentOutcome>

    suspend fun decideAmendment(
        amendmentId: Int,
        approve: Boolean,
        note: String? = null,
    ): Resource<AmendmentOutcome>
    /** Posts a comment with optional file attachments (content-resolver URIs as strings). */
    suspend fun addComment(
        projectId: Int,
        taskId: Int,
        body: String,
        attachmentUris: List<String>,
    ): Resource<Unit>
    suspend fun createTask(
        projectId: Int,
        title: String,
        status: String,
        priority: String,
        description: String?,
        assignedTo: Int?,
        sectionId: Int?,
        dueDate: String?,
        isRecurring: Boolean = false,
        recurrenceFrequency: String? = null,
        recurrenceInterval: Int? = null,
        collaboratorIds: List<Int>? = null,
    ): Resource<Task>
    suspend fun updateTask(
        projectId: Int,
        taskId: Int,
        title: String,
        status: String,
        priority: String,
        description: String?,
        assignedTo: Int?,
        dueDate: String?,
        startDate: String?,
        isRecurring: Boolean = false,
        recurrenceFrequency: String? = null,
        recurrenceInterval: Int? = null,
        collaboratorIds: List<Int>? = null,
    ): Resource<Task>
    suspend fun deleteTask(projectId: Int, taskId: Int): Resource<Unit>
    /** Month must be "YYYY-MM"; returns assigned + collaborating tasks due that month. */
    suspend fun calendarTasks(month: String): Resource<List<Task>>
    /** Comments older than [beforeId], newest first, plus whether more remain. */
    suspend fun olderComments(
        projectId: Int,
        taskId: Int,
        beforeId: Int,
    ): Resource<Pair<List<Comment>, Boolean>>
}

interface ProjectRepository {
    fun projects(): Flow<Resource<List<Project>>>
    suspend fun searchProjects(query: String): Resource<List<Project>>
    /** Global search across projects and tasks (GET /api/mobile/search). */
    suspend fun globalSearch(query: String): Resource<SearchResults>
    suspend fun projectDetail(projectId: Int): Resource<ProjectDetail>
    suspend fun createProject(
        name: String,
        description: String?,
        status: String,
        dueDate: String?,
    ): Resource<Project>
    suspend fun updateProject(
        projectId: Int,
        name: String,
        description: String?,
        status: String,
        dueDate: String?,
    ): Resource<Project>
    suspend fun deleteProject(projectId: Int): Resource<Unit>
}

interface NotificationRepository {
    /** [filter]: null/"inbox" (default), "unread", "mentioned", "bookmarked", "archived". */
    fun notifications(filter: String? = null): Flow<Resource<List<Notification>>>
    suspend fun toggleBookmark(id: String): Resource<Unit>
    suspend fun archive(id: String): Resource<Unit>
    suspend fun unarchive(id: String): Resource<Unit>
    val unreadCount: Flow<Int>
    suspend fun refreshUnreadCount(): Resource<Int>
    fun incrementUnreadLocally()
    suspend fun markRead(id: String): Resource<Unit>
    suspend fun markAllRead(): Resource<Unit>

    /**
     * Email notification switches, keyed as the server keys them (`email_task_assigned`,
     * …). Served from the cached session, so it is already populated when Profile opens.
     */
    val preferences: Flow<Map<String, Boolean>>

    /** Re-reads the switches from the server; the cached copy stands if the call fails. */
    suspend fun refreshPreferences(): Resource<Unit>

    /** Flips one switch, rolling the local copy back if the server refuses. */
    suspend fun setPreference(type: String, enabled: Boolean): Resource<Unit>
}

/** Lightweight personal checklist (separate from project/standalone tasks). */
interface TodoRepository {
    suspend fun todos(): Resource<List<PersonalTodo>>
    suspend fun addTodo(title: String): Resource<PersonalTodo>
    suspend fun setCompleted(id: Int, completed: Boolean): Resource<PersonalTodo>
    suspend fun renameTodo(id: Int, title: String): Resource<PersonalTodo>
    suspend fun deleteTodo(id: Int): Resource<Unit>
    suspend fun clearCompleted(): Resource<Unit>
}

/**
 * Approvals: the approver queue, the requestor's own submissions, and the lifecycle of a
 * request. Approval projects are read-only here — configuring one stays on the web.
 *
 * Every list is server-paginated, so callers pass a page and append the result.
 */
interface ApprovalRepository {
    /** The two badge numbers. Deliberately cheap: safe to call on every foreground. */
    suspend fun counts(): Resource<ApprovalCounts>

    /** Requests awaiting this user's decision. */
    suspend fun myApprovals(page: Int = 1, search: String? = null): Resource<MyApprovals>

    /** Decisions this user has recorded, newest first. */
    suspend fun trail(
        page: Int = 1,
        decision: String? = null,
        projectId: Int? = null,
        search: String? = null,
    ): Resource<Page<ApprovalTrailEntry>>

    /** This user own submissions, with the per-status counters the filters use. */
    suspend fun myRequests(
        page: Int = 1,
        status: String? = null,
        projectId: Int? = null,
        search: String? = null,
    ): Resource<MyRequests>

    suspend fun projects(
        page: Int = 1,
        search: String? = null,
        archived: Boolean = false,
    ): Resource<Page<ApprovalProjectSummary>>

    /**
     * Projects a request may be raised against. Separate from [projects] because a pure
     * requestor is refused that list but may still submit, so the New Request flow
     * starts here.
     */
    suspend fun availableProjects(): Resource<List<ApprovalProjectSummary>>

    suspend fun projectRequests(
        projectId: Int,
        page: Int = 1,
        search: String? = null,
        status: String? = null,
        /** A section id, or the literal "none" for unsectioned requests. */
        sectionId: String? = null,
        archived: Boolean = false,
    ): Resource<Page<ApprovalRequest>>

    /** Field definitions and sections the New Request screen renders itself from. */
    suspend fun requestForm(projectId: Int): Resource<ApprovalRequestForm>

    suspend fun request(projectId: Int, itemId: Int): Resource<ApprovalRequestDetail>

    /**
     * Raises a request. [fieldValues] is keyed by custom field id; [attachmentUris] are
     * content URIs, capped at 5 by the server.
     */
    suspend fun createRequest(
        projectId: Int,
        title: String,
        description: String? = null,
        sectionId: Int? = null,
        fieldValues: Map<Int, ApprovalFieldInput> = emptyMap(),
        attachmentUris: List<String> = emptyList(),
    ): Resource<ApprovalRequest>

    suspend fun updateRequest(
        projectId: Int,
        itemId: Int,
        title: String,
        description: String? = null,
        fieldValues: Map<Int, ApprovalFieldInput> = emptyMap(),
    ): Resource<ApprovalRequest>

    /** Records the decision on the active step. */
    suspend fun decide(
        projectId: Int,
        itemId: Int,
        decision: ApprovalDecisionType,
        comment: String? = null,
    ): Resource<ApprovalDecisionResult>

    /** Sends a returned request back out to its chain. */
    suspend fun resubmit(projectId: Int, itemId: Int): Resource<ApprovalRequest>

    suspend fun cancelRequest(projectId: Int, itemId: Int): Resource<Unit>

    suspend fun comments(projectId: Int, itemId: Int, page: Int = 1): Resource<Page<ApprovalComment>>

    suspend fun addComment(
        projectId: Int,
        itemId: Int,
        body: String,
        attachmentUris: List<String> = emptyList(),
    ): Resource<ApprovalComment>
}
