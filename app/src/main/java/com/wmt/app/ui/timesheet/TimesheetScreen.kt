package com.wmt.app.ui.timesheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.TimeLog
import com.wmt.app.domain.model.TimeLogAmendment
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.ui.taskdetail.formatMinutes

/**
 * A task's timesheet: what has been recorded against it, and anything waiting to be
 * decided about those figures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesheetScreen(
    onBack: () -> Unit,
    viewModel: TimesheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    var amending by remember { mutableStateOf<TimeLog?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf<Pair<TimeLogAmendment, Boolean>?>(null) }

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }
    // A refused duration comes back against the field, so the dialog stays open to be
    // corrected rather than closing and losing what was typed.
    val durationError = state.fieldErrors["duration"]?.firstOrNull()
    val dateError = state.fieldErrors["logged_on"]?.firstOrNull()
    LaunchedEffect(state.busy, durationError) {
        if (!state.busy && durationError == null && dateError == null) {
            amending = null
            addingEntry = false
            reviewing = null
        }
    }

    amending?.let { log ->
        AmendTimeLogDialog(
            log = log,
            durationError = durationError,
            submitting = state.busy,
            onConfirm = { duration, reason -> viewModel.amend(log.id, duration, reason) },
            onDismiss = { amending = null },
        )
    }
    if (addingEntry) {
        AddTimeEntryDialog(
            today = state.sheet.today,
            durationError = durationError,
            dateError = dateError,
            submitting = state.busy,
            onConfirm = { duration, day, reason -> viewModel.addEntry(duration, day, reason) },
            onDismiss = { addingEntry = false },
        )
    }
    reviewing?.let { (amendment, approve) ->
        ReviewAmendmentDialog(
            amendment = amendment,
            approve = approve,
            submitting = state.busy,
            onConfirm = { note -> viewModel.decide(amendment.id, approve, note) },
            onDismiss = { reviewing = null },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = "Time log",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Only where a correction has somewhere to go: a standalone task has no
            // project, so nobody to decide one.
            if (state.sheet.amendmentsAvailable) {
                ExtendedFloatingActionButton(
                    onClick = { addingEntry = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Add time") },
                )
            }
        },
    ) { innerPadding ->
        val loadError = state.error
        when {
            state.loading -> SkeletonList(rows = 5)
            loadError != null -> ErrorView(
                message = loadError,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                TimesheetList(
                    state = state,
                    onAmend = { amending = it },
                    onReview = { amendment, approve -> reviewing = amendment to approve },
                    onDelete = { viewModel.deleteLog(it.id) },
                )
            }
        }
    }
}

@Composable
private fun TimesheetList(
    state: TimesheetUiState,
    onAmend: (TimeLog) -> Unit,
    onReview: (TimeLogAmendment, Boolean) -> Unit,
    onDelete: (TimeLog) -> Unit,
) {
    val sheet = state.sheet
    if (sheet.isEmpty) {
        EmptyState(
            title = "No time recorded",
            message = "Effort appears here once the task clock has run.",
            icon = Icons.Rounded.Schedule,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item { TotalsHeader(state) }

        // Requests for a day with no entry sit above the sheet: they belong to no row,
        // and a reviewer would otherwise never find them.
        if (sheet.pendingAdditions.isNotEmpty()) {
            item { SectionLabel("Waiting for a decision") }
            items(sheet.pendingAdditions, key = { "add-" + it.id }) { amendment ->
                AmendmentRow(
                    amendment = amendment,
                    canReview = sheet.canReview,
                    busy = state.busy,
                    onReview = onReview,
                )
                ThinDivider()
            }
        }

        item { SectionLabel("Entries") }
        items(sheet.logs, key = { it.id }) { log ->
            TimeLogRow(
                log = log,
                canAmend = sheet.amendmentsAvailable,
                canReview = sheet.canReview,
                busy = state.busy,
                onAmend = onAmend,
                onDelete = onDelete,
                onReview = onReview,
            )
            ThinDivider()
        }
    }
}

@Composable
private fun TotalsHeader(state: TimesheetUiState) {
    val sheet = state.sheet
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column {
            Text(
                text = formatMinutes(sheet.totalMinutes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Logged",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sheet.estimatedMinutes?.takeIf { it > 0 }?.let { estimate ->
            Column {
                Text(
                    text = formatMinutes(estimate),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Estimated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun TimeLogRow(
    log: TimeLog,
    canAmend: Boolean,
    canReview: Boolean,
    busy: Boolean,
    onAmend: (TimeLog) -> Unit,
    onDelete: (TimeLog) -> Unit,
    onReview: (TimeLogAmendment, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    text = log.duration ?: formatMinutes(log.minutes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        log.loggedOn,
                        log.userName,
                        log.recordedBy?.let { "recorded by $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                // Where the figure came from. An amended one is not the clock's any
                // more, and should not read as though it were.
                text = when {
                    log.isAmended -> "Corrected"
                    log.isGenerated -> "From the clock"
                    else -> "Entered"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        log.note?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }

        log.pendingAmendment?.let { amendment ->
            AmendmentRow(
                amendment = amendment,
                canReview = canReview,
                busy = busy,
                onReview = onReview,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (!busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (canAmend && log.canAmend) {
                    TextAction(text = "Ask for a correction") { onAmend(log) }
                }
                // The clock's own entries are corrected, never removed: the server
                // refuses the delete and says so.
                if (log.canDelete) {
                    TextAction(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    ) { onDelete(log) }
                }
            }
        }
    }
}

@Composable
private fun AmendmentRow(
    amendment: TimeLogAmendment,
    canReview: Boolean,
    busy: Boolean,
    onReview: (TimeLogAmendment, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (modifier == Modifier) 16.dp else 0.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (amendment.isAddition) {
                "Asks to add " + amendment.requestedDuration + " on " + amendment.loggedOn
            } else {
                "Asks to change " + amendment.originalDuration +
                    " to " + amendment.requestedDuration
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = listOfNotNull(amendment.requesterName, amendment.reason)
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canReview && amendment.isPending && !busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextAction(text = "Approve") { onReview(amendment, true) }
                TextAction(
                    text = "Reject",
                    color = MaterialTheme.colorScheme.error,
                ) { onReview(amendment, false) }
            }
        } else if (amendment.isPending) {
            Text(
                text = "Waiting for a decision",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextAction(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}
