package com.wmt.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wmt.app.fcm.InAppMessage
import com.wmt.app.ui.auth.LoginScreen
import com.wmt.app.ui.auth.ServerSetupScreen
import com.wmt.app.ui.dashboard.DashboardScreen
import com.wmt.app.ui.inbox.InboxScreen
import com.wmt.app.ui.mytasks.MyTasksScreen
import com.wmt.app.ui.profile.ProfileScreen
import com.wmt.app.ui.projects.ProjectDetailScreen
import com.wmt.app.ui.projects.ProjectsScreen
import com.wmt.app.ui.search.SearchScreen
import com.wmt.app.ui.taskdetail.TaskDetailScreen
import com.wmt.app.ui.todos.TodosScreen
import kotlinx.coroutines.flow.Flow

/** Top-level switch between setup, login and the main app based on [RootViewModel] state. */
@Composable
fun WmtApp(
    intentDeepLinks: Flow<DeepLink>,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.booting -> SplashScreen()
        !state.hasServer -> ServerSetupScreen(onConnected = {})
        !state.isLoggedIn -> LoginScreen(onChangeServer = viewModel::changeServer)
        else -> MainScaffold(
            unreadCount = state.unreadCount,
            deepLinks = viewModel.deepLinks,
            intentDeepLinks = intentDeepLinks,
            inAppMessages = viewModel.inAppMessages,
            onDeepLinkConsumed = viewModel::onDeepLink,
        )
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MainScaffold(
    unreadCount: Int,
    deepLinks: Flow<DeepLink>,
    intentDeepLinks: Flow<DeepLink>,
    inAppMessages: Flow<InAppMessage>,
    onDeepLinkConsumed: (Int?, Int?) -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }

    // Route notification taps (both in-app FCM events and launch intents) to detail screens.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        intentDeepLinks.collect { onDeepLinkConsumed(it.projectId, it.taskId) }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        deepLinks.collect { link ->
            when {
                link.projectId != null && link.taskId != null ->
                    navController.navigate(MainRoutes.taskDetail(link.projectId, link.taskId))
                link.projectId != null ->
                    navController.navigate(MainRoutes.projectDetail(link.projectId))
            }
        }
    }
    // Foreground FCM messages surface as an in-app snackbar with a "View" action.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        inAppMessages.collect { msg ->
            val result = snackbarHostState.showSnackbar(
                message = if (msg.body.isBlank()) msg.title else "${msg.title}: ${msg.body}",
                actionLabel = if (msg.projectId != null || msg.taskId != null) "View" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onDeepLinkConsumed(msg.projectId, msg.taskId)
            }
        }
    }

    Scaffold(
        // Each destination supplies its own TopAppBar, which applies the status-bar
        // inset itself. Zero the outer content insets so the top inset isn't added
        // twice (which left a gap above every screen's app bar).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { WmtBottomBar(navController, unreadCount) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        MainNavHost(
            navController = navController,
            unreadCount = unreadCount,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun WmtBottomBar(navController: NavHostController, unreadCount: Int) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Column {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            BottomTab.entries.forEach { tab ->
                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        val icon = if (selected) tab.selectedIcon else tab.icon
                        if (tab == BottomTab.INBOX && unreadCount > 0) {
                            BadgedBox(badge = { Badge { Text(unreadCount.coerceAtMost(99).toString()) } }) {
                                Icon(icon, contentDescription = tab.label)
                            }
                        } else {
                            Icon(icon, contentDescription = tab.label)
                        }
                    },
                    label = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MainNavHost(
    navController: NavHostController,
    unreadCount: Int,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = MainRoutes.DASHBOARD,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeIn(tween(250))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeOut(tween(250))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeIn(tween(250))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeOut(tween(250))
        },
    ) {
        composable(MainRoutes.DASHBOARD) {
            fun openTab(route: String) = navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            DashboardScreen(
                onTaskClick = { p, t -> navController.navigate(MainRoutes.taskDetail(p, t)) },
                onProjectClick = { navController.navigate(MainRoutes.projectDetail(it)) },
                onOpenMyTasks = { openTab(MainRoutes.MY_TASKS) },
                onOpenProjects = { openTab(MainRoutes.PROJECTS) },
                onOpenInbox = { openTab(MainRoutes.INBOX) },
                onOpenProfile = { openTab(MainRoutes.PROFILE) },
                onOpenSearch = { navController.navigate(MainRoutes.SEARCH) },
                onOpenTodos = { navController.navigate(MainRoutes.TODOS) },
                unreadCount = unreadCount,
            )
        }
        composable(MainRoutes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onProjectClick = { navController.navigate(MainRoutes.projectDetail(it)) },
                onTaskClick = { p, t -> navController.navigate(MainRoutes.taskDetail(p, t)) },
            )
        }
        composable(MainRoutes.TODOS) {
            TodosScreen(onBack = { navController.popBackStack() })
        }
        composable(MainRoutes.MY_TASKS) {
            MyTasksScreen(
                onTaskClick = { p, t -> navController.navigate(MainRoutes.taskDetail(p, t)) },
            )
        }
        composable(MainRoutes.PROJECTS) {
            ProjectsScreen(
                onProjectClick = { navController.navigate(MainRoutes.projectDetail(it)) },
            )
        }
        composable(MainRoutes.INBOX) {
            InboxScreen(
                onNotificationClick = { projectId, taskId ->
                    when {
                        projectId != null && taskId != null ->
                            navController.navigate(MainRoutes.taskDetail(projectId, taskId))
                        projectId != null ->
                            navController.navigate(MainRoutes.projectDetail(projectId))
                    }
                },
            )
        }
        composable(MainRoutes.PROFILE) {
            ProfileScreen(onChangeServer = {}, onLoggedOut = {})
        }

        composable(
            route = MainRoutes.PROJECT_DETAIL,
            arguments = listOf(navArgument("projectId") { type = NavType.IntType }),
        ) {
            ProjectDetailScreen(
                onBack = { navController.popBackStack() },
                onTaskClick = { p, t -> navController.navigate(MainRoutes.taskDetail(p, t)) },
            )
        }
        composable(
            route = MainRoutes.TASK_DETAIL,
            arguments = listOf(
                navArgument("projectId") { type = NavType.IntType },
                navArgument("taskId") { type = NavType.IntType },
            ),
        ) {
            TaskDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenTask = { p, t -> navController.navigate(MainRoutes.taskDetail(p, t)) },
            )
        }
    }
}
