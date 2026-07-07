package com.wmt.app.domain.repository

import com.wmt.app.domain.model.Comment
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
    val preferences: Flow<Map<String, Boolean>>
    suspend fun refreshPreferences(): Resource<Unit>
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
