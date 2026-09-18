package com.wmt.app.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.ApprovalDecisionType
import com.wmt.app.ui.components.ApprovalStatusBadge
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.HtmlText
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.DateUtils

/**
 * One approval request in full, and the decision on it.
 *
 * Whether the decision bar appears is the server's call, read from canDecide on the
 * payload rather than worked out from the step and the current user here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalDetailScreen(
    onBack: () -> Unit,
    /** Null where editing is not reachable; the action also needs the server to allow it. */
    onEdit: ((projectId: Int, requestId: Int) -> Unit)? = null,
    viewModel: ApprovalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDecision by remember { mutableStateOf<ApprovalDecisionType?>(null) }

    // A cancelled request is soft-deleted, so there is nothing left to look at.
    LaunchedEffect(state.cancelled) { if (state.cancelled) onBack() }

    var confirmCancel by remember { mutableStateOf(false) }
    if (confirmCancel) {
        CancelRequestDialog(
            onConfirm = {
                confirmCancel = false
                viewModel.cancelRequest()
            },
            onDismiss = { confirmCancel = false },
        )
    }

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    // The sheet closes itself once the decision is recorded.
    LaunchedEffect(state.detail?.canDecide, state.deciding) {
        if (!state.deciding && state.detail?.canDecide == false) pendingDecision = null
    }

    pendingDecision?.let { decision ->
        ApprovalDecisionSheet(
            decision = decision,
            submitting = state.deciding,
            onSubmit = { comment -> viewModel.decide(decision, comment) },
            onDismiss = { pendingDecision = null },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = state.request?.reference ?: "Request",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Only while the server says the content is still editable: once an
                    // approver has signed, the record is sealed.
                    if (onEdit != null && state.detail?.canEdit == true) {
                        IconButton(onClick = { onEdit(viewModel.projectId, viewModel.requestId) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit request")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val request = state.request
            when {
                // Deciding takes precedence: an approver looking at a request is here to
                // answer it.
                state.detail?.canDecide == true -> DecisionBar(
                    enabled = !state.deciding,
                    onApprove = { pendingDecision = ApprovalDecisionType.APPROVED },
                    onReject = { pendingDecision = ApprovalDecisionType.REJECTED },
                )
                // The author own controls, each shown only when the server allows it.
                request != null && (request.canResubmit || request.canCancel) -> RequestorBar(
                    canResubmit = request.canResubmit,
                    canCancel = request.canCancel,
                    enabled = !state.submittingAction,
                    onResubmit = viewModel::resubmit,
                    onCancel = { confirmCancel = true },
                )
            }
        },
    ) { innerPadding ->
        val detail = state.detail
        val loadError = state.error
        when {
            state.loading && detail == null -> SkeletonList(rows = 6)
            loadError != null && detail == null -> ErrorView(
                message = loadError,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            detail != null -> ApprovalDetailContent(
                state = state,
                contentPadding = innerPadding,
                onPostComment = viewModel::addComment,
            )
        }
    }
}

@Composable
private fun ApprovalDetailContent(
    state: ApprovalDetailUiState,
    contentPadding: PaddingValues,
    onPostComment: (String) -> Unit,
) {
    val detail = state.detail ?: return
    val request = detail.request
    var draft by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    ApprovalStatusBadge(request.statusEnum)
                    Spacer(Modifier.weight(1f))
                    request.stepProgressLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = listOfNotNull(
                        request.projectName,
                        request.requesterName?.let { "raised by $it" },
                        DateUtils.formatDate(request.submittedAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                request.quorumLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        request.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Column {
                    DetailSectionTitle("Details")
                    HtmlText(html = description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (detail.fieldValues.isNotEmpty()) {
            item { DetailSectionTitle("Fields") }
            items(detail.fieldValues.size) { index ->
                FieldValueRow(detail.fieldValues[index])
            }
        }

        if (detail.attachments.isNotEmpty()) {
            item { DetailSectionTitle("Attachments") }
            items(detail.attachments.size) { index ->
                AttachmentRow(detail.attachments[index])
            }
        }

        if (detail.steps.isNotEmpty()) {
            item { DetailSectionTitle("Approval chain") }
            items(detail.steps.size) { index ->
                StepRow(detail.steps[index])
                if (index < detail.steps.lastIndex) ThinDivider()
            }
        }

        item { DetailSectionTitle("Comments") }
        if (state.comments.isEmpty()) {
            item {
                Text(
                    text = "No comments yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.comments.size) { index ->
                CommentRow(state.comments[index])
            }
        }

        item {
            CommentComposer(
                draft = draft,
                posting = state.postingComment,
                onDraftChange = { draft = it },
                onPost = {
                    onPostComment(draft)
                    draft = ""
                },
            )
        }
    }
}

@Composable
private fun CommentComposer(
    draft: String,
    posting: Boolean,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text("Add a comment") },
            minLines = 2,
            maxLines = 5,
            enabled = !posting,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onPost,
            enabled = !posting && draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (posting) "Posting…" else "Post comment")
        }
    }
}

/**
 * Approve and Reject, shown only when the server says this person may decide.
 *
 * Both open a sheet rather than acting on the tap: a decision cannot be undone from the
 * app, so it gets a deliberate second step.
 */
@Composable
private fun DecisionBar(
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onReject,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text("Reject")
            }
            Button(onClick = onApprove, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("Approve")
            }
        }
    }
}

/**
 * The author's own controls on a request that has come back to them.
 *
 * Both are offered strictly on the server's flags rather than inferred from the status:
 * whether a request may be resubmitted depends on where its chain has got to, which is
 * not something the status alone reveals.
 */
@Composable
private fun RequestorBar(
    canResubmit: Boolean,
    canCancel: Boolean,
    enabled: Boolean,
    onResubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canCancel) {
                TextButton(
                    onClick = onCancel,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Withdraw", color = MaterialTheme.colorScheme.error)
                }
            }
            if (canResubmit) {
                Button(
                    onClick = onResubmit,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Resubmit")
                }
            }
        }
    }
}

/** Withdrawing cannot be undone from the app, so it is confirmed first. */
@Composable
private fun CancelRequestDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdraw this request?") },
        text = {
            Text("It will be cancelled and removed from every approver's queue.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Withdraw", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep it") }
        },
    )
}
