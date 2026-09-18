package com.wmt.app.ui.approvals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.ApprovalDecisionType
import com.wmt.app.domain.model.ApprovalTrailEntry
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.DateUtils

/**
 * Decisions this person has recorded, newest first.
 *
 * Read-only: nothing in the API takes a decision back, so an entry leads to the request
 * it was made on rather than offering an action here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalTrailScreen(
    onBack: () -> Unit,
    onEntryClick: ((projectId: Int, requestId: Int) -> Unit)? = null,
    viewModel: ApprovalTrailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchVisible by remember { mutableStateOf(false) }

    val errorMessage = state.error
    if (errorMessage != null && !state.isEmpty) {
        LaunchedEffect(errorMessage) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - PREFETCH_DISTANCE
        }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = "Decision trail",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) viewModel.clearSearch()
                    }) {
                        Icon(
                            imageVector = if (searchVisible) {
                                Icons.Rounded.Close
                            } else {
                                Icons.Rounded.Search
                            },
                            contentDescription = if (searchVisible) {
                                "Close search"
                            } else {
                                "Search decisions"
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (searchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = { Text("Search decisions") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            DecisionFilterRow(
                selected = state.decisionFilter,
                onSelect = viewModel::setDecisionFilter,
            )

            TrailBody(
                state = state,
                listState = listState,
                onEntryClick = onEntryClick,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrailBody(
    state: ApprovalTrailUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEntryClick: ((projectId: Int, requestId: Int) -> Unit)?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    val loadError = state.error
    when {
        state.loading -> SkeletonList(rows = 6)
        loadError != null && state.isEmpty -> ErrorView(message = loadError, onRetry = onRetry)
        else -> PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isEmpty && state.isFiltered -> EmptyState(
                    title = "No matches",
                    message = "No decisions match the current search or filter.",
                    icon = Icons.Rounded.History,
                )
                state.isEmpty -> EmptyState(
                    title = "No decisions yet",
                    message = "Requests you approve or reject are recorded here.",
                    icon = Icons.Rounded.History,
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.entries.items, key = { it.id }) { entry ->
                        TrailRow(entry = entry, onClick = onEntryClick)
                        ThinDivider()
                    }
                    if (state.loadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionFilterRow(
    selected: ApprovalDecisionType?,
    onSelect: (ApprovalDecisionType?) -> Unit,
) {
    // The endpoint accepts only these two, so there is no third chip to offer.
    val options = listOf(
        null to "All",
        ApprovalDecisionType.APPROVED to "Approved",
        ApprovalDecisionType.REJECTED to "Rejected",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val (decision, label) = options[index]
            FilterChip(
                selected = decision == selected,
                onClick = { onSelect(decision) },
                label = { Text(label) },
            )
        }
    }
}

/**
 * One recorded decision.
 *
 * The trail flattens its request to a few strings server-side, so this shows what it was
 * given rather than loading the request to say more.
 */
@Composable
private fun TrailRow(
    entry: ApprovalTrailEntry,
    onClick: ((projectId: Int, requestId: Int) -> Unit)?,
) {
    val openable = onClick != null && entry.projectId != null && entry.requestId != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (openable) {
                    Modifier.clickable { onClick!!(entry.projectId!!, entry.requestId!!) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.requestTitle ?: "Request",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DecisionPill(entry.decisionEnum)
        }
        Text(
            text = listOfNotNull(
                entry.projectName,
                entry.stepName,
                entry.requesterName?.let { "raised by $it" },
                DateUtils.formatDate(entry.decidedAt),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.comment?.takeIf { it.isNotBlank() }?.let { comment ->
            Text(
                text = comment,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Reuses the decision colours the rest of the section uses. */
@Composable
private fun DecisionPill(decision: ApprovalDecisionType) {
    val colors = when (decision) {
        ApprovalDecisionType.APPROVED -> MaterialTheme.colorScheme.primary
        ApprovalDecisionType.REJECTED -> MaterialTheme.colorScheme.error
    }
    Text(
        text = decision.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = colors,
    )
}

private const val PREFETCH_DISTANCE = 3
