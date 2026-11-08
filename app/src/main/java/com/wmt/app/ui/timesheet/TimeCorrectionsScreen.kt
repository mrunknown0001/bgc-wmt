package com.wmt.app.ui.timesheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.CorrectionTab
import com.wmt.app.domain.model.TimeCorrection
import com.wmt.app.domain.model.TimeCorrections
import com.wmt.app.domain.repository.TaskRepository
import com.wmt.app.ui.common.PagedListController
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.common.PagedListView
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.DateUtils
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The statuses the queue filters by, as the web offers them. */
enum class CorrectionStatusFilter(val raw: String, val label: String) {
    PENDING("pending", "Waiting"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    ALL("all", "All"),
}

data class CorrectionsUiState(
    /** What was asked for; the list's extra says what the server settled on. */
    val requestedTab: CorrectionTab? = null,
    val status: CorrectionStatusFilter = CorrectionStatusFilter.PENDING,
    val deciding: Int? = null,
    val message: String? = null,
)

/**
 * The time corrections queue: what is waiting on you to decide, and what you asked for.
 *
 * Corrections used to be reachable on the phone only through a task's timesheet, so one
 * raised on a task nobody opened again simply waited. The server settles the tab:
 * somebody who runs no project asking for "to decide" gets their own requests, and
 * [TimeCorrections.canDecideAny] decides whether that tab is offered at all. Deciding
 * goes through the timesheet's own call, so there is one way to approve or reject.
 */
@HiltViewModel
class TimeCorrectionsViewModel @Inject constructor(
    private val repository: TaskRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CorrectionsUiState())
    val ui = _ui.asStateFlow()

    val list = PagedListController<TimeCorrection, TimeCorrections>(viewModelScope) { page ->
        val current = _ui.value
        when (val result = repository.timeCorrections(current.requestedTab, current.status.raw, page)) {
            is Resource.Success -> Resource.Success(result.data.page to result.data)
            is Resource.Error -> Resource.Error(result.error)
            is Resource.Loading -> Resource.Loading()
        }
    }

    init {
        list.load()
    }

    fun selectTab(tab: CorrectionTab) {
        _ui.update { it.copy(requestedTab = tab) }
        list.load()
    }

    fun selectStatus(status: CorrectionStatusFilter) {
        _ui.update { it.copy(status = status) }
        list.load()
    }

    fun decide(correction: TimeCorrection, approve: Boolean, note: String?) {
        if (_ui.value.deciding != null) return
        _ui.update { it.copy(deciding = correction.id) }
        viewModelScope.launch {
            val result = repository.decideAmendment(correction.id, approve, note?.trim()?.ifBlank { null })
            _ui.update {
                it.copy(
                    deciding = null,
                    message = when (result) {
                        is Resource.Success -> if (approve) "Correction approved." else "Correction rejected."
                        is Resource.Error -> result.error.message
                        is Resource.Loading -> null
                    },
                )
            }
            list.load(isRefresh = true)
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCorrectionsScreen(
    onBack: () -> Unit,
    onOpenTask: (projectId: Int, taskId: Int) -> Unit,
    viewModel: TimeCorrectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.list.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var rejecting by remember { mutableStateOf<TimeCorrection?>(null) }

    val message = ui.message ?: state.error?.takeIf { !state.isEmpty }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
            viewModel.list.dismissError()
        }
    }

    rejecting?.let { correction ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("Reject correction?") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Note for ${correction.requester ?: "them"} (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    rejecting = null
                    viewModel.decide(correction, approve = false, note = note)
                }) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Cancel") } },
        )
    }

    val settled = state.extra
    val tab = settled?.tab ?: CorrectionTab.MINE

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = "Time corrections",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // "To decide" is offered only to someone who decides corrections somewhere.
            if (settled?.canDecideAny == true) {
                TabRow(selectedTabIndex = if (tab == CorrectionTab.TO_DECIDE) 0 else 1) {
                    Tab(
                        selected = tab == CorrectionTab.TO_DECIDE,
                        onClick = { viewModel.selectTab(CorrectionTab.TO_DECIDE) },
                        text = { Text("To decide (${settled.toDecideCount})") },
                    )
                    Tab(
                        selected = tab == CorrectionTab.MINE,
                        onClick = { viewModel.selectTab(CorrectionTab.MINE) },
                        text = { Text("My requests (${settled.mineCount})") },
                    )
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(CorrectionStatusFilter.entries) { filter ->
                    FilterChip(
                        selected = ui.status == filter,
                        onClick = { viewModel.selectStatus(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            PagedListView(
                state = state,
                key = { it.id },
                emptyTitle = if (tab == CorrectionTab.TO_DECIDE) "Nothing to decide" else "No corrections",
                emptyMessage = if (tab == CorrectionTab.TO_DECIDE) {
                    "Corrections people ask for on your projects' time logs appear here."
                } else {
                    "Corrections you ask for on a task's time log appear here."
                },
                onRefresh = { viewModel.list.load(isRefresh = true) },
                onRetry = { viewModel.list.load() },
                onLoadMore = viewModel.list::loadMore,
            ) { correction ->
                CorrectionRow(
                    correction = correction,
                    canDecide = tab == CorrectionTab.TO_DECIDE && correction.isPending,
                    deciding = ui.deciding == correction.id,
                    onOpen = {
                        val taskId = correction.taskId
                        val projectId = correction.projectId
                        if (taskId != null && projectId != null) onOpenTask(projectId, taskId)
                    },
                    onApprove = { viewModel.decide(correction, approve = true, note = null) },
                    onReject = { rejecting = correction },
                )
                ThinDivider()
            }
        }
    }
}

@Composable
private fun CorrectionRow(
    correction: TimeCorrection,
    canDecide: Boolean,
    deciding: Boolean,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(correction.taskTitle ?: "Task", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        correction.projectName?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = buildString {
                append(DateUtils.formatDate(correction.loggedOn) ?: correction.loggedOn ?: "")
                append(" · ")
                if (correction.isAddition) {
                    append("add ${correction.requestedDuration}")
                } else {
                    append("${correction.originalDuration} → ${correction.requestedDuration}")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        correction.reason?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
        Text(
            text = buildString {
                append(correction.requester ?: "Someone")
                correction.requestedAt?.let { append(", ${DateUtils.timeAgo(it)}") }
                if (!correction.isPending) {
                    append(" · ${correction.status.replaceFirstChar { it.uppercase() }}")
                    correction.reviewer?.let { append(" by $it") }
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        correction.reviewNote?.let {
            Text("Note: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canDecide) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = onReject, enabled = !deciding) { Text("Reject") }
                OutlinedButton(onClick = onApprove, enabled = !deciding) { Text("Approve") }
            }
        }
    }
}
