package com.wmt.app.ui.approvals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar

/**
 * Which approval project a new request is raised against.
 *
 * The list comes from the available endpoint rather than the project list, because a
 * requestor without approver access is refused the latter while still being allowed to
 * submit against these.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRequestProjectScreen(
    onBack: () -> Unit,
    onProjectChosen: (projectId: Int) -> Unit,
    viewModel: NewRequestProjectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = "Choose a project",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val error = state.error
        when {
            state.loading -> SkeletonList(rows = 5)
            error != null -> ErrorView(
                message = error,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            state.projects.isEmpty() -> EmptyState(
                title = "No projects open for requests",
                message = "Nothing is currently accepting new approval requests.",
                icon = Icons.AutoMirrored.Outlined.FactCheck,
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(state.projects, key = { it.id }) { project ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProjectChosen(project.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        project.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ThinDivider()
                }
            }
        }
    }
}
