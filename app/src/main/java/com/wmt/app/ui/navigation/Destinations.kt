package com.wmt.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
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

    fun projectDetail(projectId: Int) = "project/$projectId"
    fun taskDetail(projectId: Int, taskId: Int) = "task/$projectId/$taskId"
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
    PROFILE(MainRoutes.PROFILE, "Profile", Icons.Outlined.Person, Icons.Filled.Person),
}
