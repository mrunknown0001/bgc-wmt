package com.wmt.app.ui.mytasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.OfflineBanner
import com.wmt.app.ui.components.SectionHeader
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.TaskListItem
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onTaskClick: (projectId: Int, taskId: Int) -> Unit,
    viewModel: MyTasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(title = "My Tasks", scrollBehavior = scrollBehavior)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OfflineBanner(visible = state.offline)

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.groups.isEmpty() -> {
                        SkeletonList()
                    }
                    state.error != null && state.groups.isEmpty() -> {
                        ErrorView(
                            message = state.error!!,
                            onRetry = viewModel::refresh,
                        )
                    }
                    state.groups.isEmpty() -> {
                        EmptyState(
                            title = "You're all caught up",
                            message = "No tasks assigned to you right now.",
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            state.groups.forEach { group ->
                                val expanded = expandedStates[group.title] ?: true
                                item(key = "header-${group.title}") {
                                    SectionHeader(
                                        title = group.title,
                                        count = group.tasks.size,
                                        expanded = expanded,
                                        onToggle = { expandedStates[group.title] = !expanded },
                                    )
                                }
                                if (expanded) {
                                    items(group.tasks, key = { it.id }) { task ->
                                        TaskListItem(
                                            task = task,
                                            onClick = { onTaskClick(task.projectId, task.id) },
                                            onToggleComplete = { viewModel.toggleComplete(task) },
                                        )
                                        ThinDivider(startIndent = 52.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
