package com.wmt.app.ui.approvals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.SharedRequest
import com.wmt.app.domain.repository.ApprovalRepository
import com.wmt.app.ui.common.PagedListController
import com.wmt.app.ui.common.PagedListView
import com.wmt.app.ui.components.ApprovalStatusBadge
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.DateUtils
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Requests other people shared with this user.
 *
 * Its own screen rather than a tab of Approvals, because a share can reach anyone -- an
 * auditor with no approvals rights at all -- and the Approvals tab is only shown to people
 * who have them. Opening a request marks the share read on the server, so the list is
 * re-read on return rather than flipped locally.
 */
@HiltViewModel
class SharedRequestsViewModel @Inject constructor(
    repository: ApprovalRepository,
) : ViewModel() {

    val list = PagedListController<SharedRequest, Int>(viewModelScope) { page ->
        when (val result = repository.sharedRequests(page)) {
            is Resource.Success -> Resource.Success(result.data.page to result.data.unread)
            is Resource.Error -> Resource.Error(result.error)
            is Resource.Loading -> Resource.Loading()
        }
    }

    init {
        list.load()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedRequestsScreen(
    onBack: () -> Unit,
    onOpenRequest: (projectId: Int, requestId: Int) -> Unit,
    viewModel: SharedRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.list.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Re-read on every return: opening a request is what marks it read.
    LaunchedEffect(Unit) { viewModel.list.load(isRefresh = !state.isEmpty) }
    val error = state.error
    if (error != null && !state.isEmpty) {
        LaunchedEffect(error) {
            snackbar.showSnackbar(error)
            viewModel.list.dismissError()
        }
    }

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = state.extra?.takeIf { it > 0 }?.let { "Shared with me ($it new)" } ?: "Shared with me",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PagedListView(
            state = state,
            key = { it.shareId },
            emptyTitle = "Nothing shared with you",
            emptyMessage = "When someone shares an approval request with you, it appears here.",
            onRefresh = { viewModel.list.load(isRefresh = true) },
            onRetry = { viewModel.list.load() },
            onLoadMore = viewModel.list::loadMore,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { share ->
            SharedRequestRow(share, onClick = { onOpenRequest(share.projectId, share.requestId) })
            ThinDivider()
        }
    }
}

@Composable
private fun SharedRequestRow(share: SharedRequest, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = share.title,
                style = MaterialTheme.typography.titleSmall,
                // Unread is said in weight, as the web does, and in words below.
                fontWeight = if (share.isUnread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ApprovalStatusBadge(share.statusEnum)
        }
        Text(
            text = listOfNotNull(share.projectName, share.requester?.let { "from $it" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = buildString {
                if (share.isUnread) append("New · ")
                append("Shared")
                share.sharedBy?.let { append(" by $it") }
                share.sharedAt?.let { append(" ${DateUtils.timeAgo(it)}") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (share.isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
