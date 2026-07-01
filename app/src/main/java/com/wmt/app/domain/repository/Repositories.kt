package com.wmt.app.domain.repository

import com.wmt.app.domain.model.DashboardData
import com.wmt.app.domain.model.Notification
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.model.ProjectDetail
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
    ): Resource<Task>
    suspend fun deleteTask(projectId: Int, taskId: Int): Resource<Unit>
}

interface ProjectRepository {
    fun projects(): Flow<Resource<List<Project>>>
    suspend fun searchProjects(query: String): Resource<List<Project>>
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
    fun notifications(): Flow<Resource<List<Notification>>>
    val unreadCount: Flow<Int>
    suspend fun refreshUnreadCount(): Resource<Int>
    fun incrementUnreadLocally()
    suspend fun markRead(id: String): Resource<Unit>
    suspend fun markAllRead(): Resource<Unit>
    val preferences: Flow<Map<String, Boolean>>
    suspend fun refreshPreferences(): Resource<Unit>
    suspend fun setPreference(type: String, enabled: Boolean): Resource<Unit>
}
