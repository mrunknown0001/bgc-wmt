package com.wmt.app.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.Project
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.OfflineBanner
import com.wmt.app.ui.components.ProjectStatusBadge
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.ui.components.projectDotColor
import com.wmt.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onProjectClick: (projectId: Int) -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-fetch silently whenever this screen comes back into view so project
    // task counts/progress reflect changes made on detail screens.
    LaunchedEffect(Unit) { viewModel.onScreenVisible() }
    var showCreate by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    if (showCreate) {
        CreateProjectDialog(
            creating = state.creating,
            error = state.createError,
            onDismiss = {
                showCreate = false
                viewModel.clearCreateError()
            },
            onCreate = { name, description, status, dueDate ->
                viewModel.createProject(name, description, status, dueDate) { showCreate = false }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { WmtTopAppBar(title = "Projects", scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OfflineBanner(visible = state.offline)

            SearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
            )

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.projects.isEmpty() -> SkeletonList()
                    state.error != null && state.projects.isEmpty() -> ErrorView(
                        message = state.error!!,
                        onRetry = viewModel::refresh,
                    )
                    state.projects.isEmpty() && state.query.isNotBlank() -> EmptyState(
                        title = "No matches",
                        message = "No projects match \"${state.query}\".",
                    )
                    state.projects.isEmpty() -> EmptyState(
                        title = "No projects yet",
                        message = "Create your first project to get started.",
                        actionLabel = "New project",
                        onAction = { showCreate = true },
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(state.projects, key = { it.id }) { project ->
                            ProjectRow(project = project, onClick = { onProjectClick(project.id) })
                            ThinDivider(startIndent = 76.dp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Search projects") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    val accent = projectDotColor(project.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = project.name.trim().take(1).uppercase().ifBlank { "#" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.size(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                ProjectStatusBadge(project.statusEnum)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { project.progress },
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "${project.completedTasksCount}/${project.tasksCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DateUtils.formatDate(project.dueDate)?.let { due ->
                Text(
                    text = "Due $due",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
