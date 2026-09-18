package com.wmt.app.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.ApprovalStatus
import com.wmt.app.domain.model.MyRequestsStats
import com.wmt.app.domain.model.UserSummary
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.OfflineBanner
import com.wmt.app.ui.components.ProfileAvatarAction
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.WmtTopAppBar

/**
 * The Approvals section: requests waiting on this person, newest submission first.
 *
 * Search and paging both run on the server, so this screen keeps the accumulated pages
 * and asks for the next one as the list nears its end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(
    currentUser: UserSummary?,
    onOpenProfile: () -> Unit,
    onRequestClick: ((projectId: Int, requestId: Int) -> Unit)? = null,
    onNewRequest: (() -> Unit)? = null,
    onOpenTrail: (() -> Unit)? = null,
    viewModel: ApprovalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onScreenVisible() }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchVisible by remember { mutableStateOf(false) }

    // A page that fails to load is reported without clearing what is already shown.
    val errorMessage = state.error
    if (errorMessage != null && !state.isEmpty) {
        LaunchedEffect(errorMessage) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.dismissError()
        }
    }

    // Ask for the next page a little before the end, so scrolling does not stall.
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
                title = "Approvals",
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.visibleTabs.isNotEmpty()) {
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
                                    "Search requests"
                                },
                            )
                        }
                    }
                    if (onOpenTrail != null && state.capabilities.canAccessApprovals) {
                        IconButton(onClick = onOpenTrail) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "Decision trail",
                            )
                        }
                    }
                    ProfileAvatarAction(user = currentUser, onClick = onOpenProfile)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (onNewRequest != null &&
                state.tab == ApprovalsTab.MY_REQUESTS &&
                state.capabilities.canRequest
            ) {
                FloatingActionButton(
                    onClick = onNewRequest,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New request")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OfflineBanner(visible = state.offline)

            if (searchVisible && state.visibleTabs.isNotEmpty()) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = { Text("Search requests") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (state.visibleTabs.size > 1) {
                TabRow(selectedTabIndex = state.visibleTabs.indexOf(state.tab).coerceAtLeast(0)) {
                    state.visibleTabs.forEach { tab ->
                        Tab(
                            selected = tab == state.tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }

            if (state.tab == ApprovalsTab.MY_REQUESTS && state.capabilitiesResolved) {
                StatusFilterRow(
                    stats = state.requestStats,
                    selected = state.statusFilter,
                    onSelect = viewModel::setStatusFilter,
                )
            }

            ApprovalsBody(
                state = state,
                listState = listState,
                onRequestClick = onRequestClick,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApprovalsBody(
    state: ApprovalsUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRequestClick: ((projectId: Int, requestId: Int) -> Unit)?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        // Before capabilities are known, an empty set looks exactly like an account with
        // no approvals rights, so wait rather than render that verdict.
        !state.capabilitiesResolved || state.loading -> SkeletonList(rows = 5)

        // Neither list is available to this account.
        state.visibleTabs.isEmpty() -> EmptyState(
            title = "No approvals for you",
            message = "This account cannot raise or decide approval requests.",
            icon = Icons.AutoMirrored.Outlined.FactCheck,
        )

        state.error != null && state.isEmpty -> ErrorView(
            message = state.error,
            onRetry = onRetry,
        )

        else -> PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isEmpty && state.isFiltered -> EmptyState(
                    title = "No matches",
                    message = "No requests match the current search or filter.",
                    icon = Icons.AutoMirrored.Outlined.FactCheck,
                )
                state.isEmpty && state.tab == ApprovalsTab.TO_APPROVE -> EmptyState(
                    title = "Nothing to approve",
                    message = "Requests waiting on your decision appear here.",
                    icon = Icons.AutoMirrored.Outlined.FactCheck,
                )
                state.isEmpty -> EmptyState(
                    title = "No requests yet",
                    message = "Requests you raise appear here with where they have got to.",
                    icon = Icons.AutoMirrored.Outlined.FactCheck,
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { ListStats(state) }
                    items(state.page.items, key = { it.id }) { request ->
                        ApprovalRequestCard(
                            request = request,
                            onClick = onRequestClick?.let { open ->
                                { open(request.projectId, request.id) }
                            },
                        )
                    }
                    if (state.loadingMore) {
                        item { LoadingMoreRow() }
                    }
                }
            }
        }
    }
}

/** The figures each endpoint reports alongside its page. */
@Composable
private fun ListStats(state: ApprovalsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.tab == ApprovalsTab.TO_APPROVE) {
            StatText(value = state.queueStats.pending, label = "Awaiting you")
            StatText(value = state.queueStats.decidedThisWeek, label = "Decided this week")
        } else {
            StatText(value = state.requestStats.total, label = "Raised")
            StatText(value = state.requestStats.pending, label = "In progress")
            StatText(value = state.requestStats.needsAction, label = "Need you")
        }
    }
}

@Composable
private fun StatText(value: Int, label: String) {
    Column {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingMoreRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}


private const val PREFETCH_DISTANCE = 3

/**
 * Status chips for My requests, labelled with the counters the endpoint reports so the
 * numbers come from the server rather than from counting the page on screen, which only
 * ever holds the first twenty.
 */
@Composable
private fun StatusFilterRow(
    stats: MyRequestsStats,
    selected: ApprovalStatus?,
    onSelect: (ApprovalStatus?) -> Unit,
) {
    val options = listOf(
        null to "All ${stats.total}",
        ApprovalStatus.PENDING to "Pending ${stats.pending}",
        ApprovalStatus.CHANGES_REQUESTED to "Changes ${stats.changesRequested}",
        ApprovalStatus.REJECTED to "Rejected ${stats.rejected}",
        ApprovalStatus.APPROVED to "Approved ${stats.approved}",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val (status, label) = options[index]
            FilterChip(
                selected = status == selected,
                onClick = { onSelect(status) },
                label = { Text(label) },
            )
        }
    }
}
