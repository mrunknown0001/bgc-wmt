package com.wmt.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level navigation graph routes. */
object Routes {
    const val SETUP = "setup"
    const val LOGIN = "login"
    const val MAIN = "main"
}

/** Routes inside the bottom-navigation host. */
object MainRoutes {
    const val DASHBOARD = "dashboard"
    const val MY_TASKS = "my_tasks"
    const val PROJECTS = "projects"
    const val INBOX = "inbox"
    const val PROFILE = "profile"

    const val PROJECT_DETAIL = "project/{projectId}"
    const val TASK_DETAIL = "task/{projectId}/{taskId}"
    const val SEARCH = "search"
    const val TODOS = "todos"
    const val APPROVALS = "approvals"
    const val APPROVAL_DETAIL = "approval/{projectId}/{requestId}"
    const val APPROVAL_NEW = "approval/new"
    const val APPROVAL_TRAIL = "approval/trail"

    /** One route for both raising and editing; requestId 0 means a new request. */
    const val APPROVAL_FORM = "approval/form/{projectId}?requestId={requestId}"

    fun projectDetail(projectId: Int) = "project/$projectId"
    fun taskDetail(projectId: Int, taskId: Int) = "task/$projectId/$taskId"
    fun approvalDetail(projectId: Int, requestId: Int) = "approval/$projectId/$requestId"

    fun approvalForm(projectId: Int, requestId: Int = 0) =
        "approval/form/$projectId?requestId=$requestId"
}

enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    DASHBOARD(MainRoutes.DASHBOARD, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    MY_TASKS(MainRoutes.MY_TASKS, "My Tasks", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    PROJECTS(MainRoutes.PROJECTS, "Projects", Icons.Outlined.FolderOpen, Icons.Filled.Folder),
    INBOX(MainRoutes.INBOX, "Inbox", Icons.Outlined.Notifications, Icons.Filled.Notifications),

    /**
     * Shown only to people the server says may use approvals, so the bar carries four
     * items for everyone else rather than a tab that would 403 on tap.
     */
    APPROVALS(
        MainRoutes.APPROVALS,
        "Approvals",
        Icons.AutoMirrored.Outlined.FactCheck,
        Icons.AutoMirrored.Filled.FactCheck,
    ),
}
