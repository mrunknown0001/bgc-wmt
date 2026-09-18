package com.wmt.app.ui.timesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.TimeLog
import com.wmt.app.domain.model.TimeLogAmendment

/**
 * Asking for an entry to say something else.
 *
 * The duration box takes whatever is typed. The server reads "1.5", "1:30" and "90m"
 * alike, so normalising it here would only narrow what it accepts, and a value it
 * refuses comes back with the server's own wording.
 */
@Composable
fun AmendTimeLogDialog(
    log: TimeLog,
    durationError: String?,
    submitting: Boolean,
    onConfirm: (duration: String, reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Ask for a correction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This entry says " + (log.duration ?: "nothing") + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.take(20) },
                    label = { Text("It should say") },
                    placeholder = { Text("1.5, 1:30 or 90m") },
                    singleLine = true,
                    isError = durationError != null,
                    supportingText = durationError?.let { { Text(it) } },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(1000) },
                    label = { Text("Why") },
                    minLines = 2,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                // Both are required by the server; a correction without a reason is one
                // nobody can decide.
                onClick = { onConfirm(duration, reason) },
                enabled = !submitting && duration.isNotBlank() && reason.isNotBlank(),
            ) {
                Text("Send")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asking for an entry on a day the clock never ran. */
@Composable
fun AddTimeEntryDialog(
    today: String?,
    durationError: String?,
    dateError: String?,
    submitting: Boolean,
    onConfirm: (duration: String, loggedOn: String, reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by remember { mutableStateOf("") }
    // Prefilled with the server's date rather than the phone's: the two disagree for
    // most of the working day here.
    var day by remember { mutableStateOf(today.orEmpty()) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Add time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.take(20) },
                    label = { Text("How long") },
                    placeholder = { Text("1.5, 1:30 or 90m") },
                    singleLine = true,
                    isError = durationError != null,
                    supportingText = durationError?.let { { Text(it) } },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.take(10) },
                    label = { Text("Which day") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    isError = dateError != null,
                    supportingText = dateError?.let { { Text(it) } }
                        ?: { Text("Today or earlier.") },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(1000) },
                    label = { Text("Why") },
                    minLines = 2,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(duration, day, reason) },
                enabled = !submitting && duration.isNotBlank() &&
                    day.isNotBlank() && reason.isNotBlank(),
            ) {
                Text("Send")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Approving or rejecting somebody's correction, with an optional note back to them. */
@Composable
fun ReviewAmendmentDialog(
    amendment: TimeLogAmendment,
    approve: Boolean,
    submitting: Boolean,
    onConfirm: (note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (approve) "Approve this correction?" else "Reject this correction?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (amendment.isAddition) {
                        "Adds " + amendment.requestedDuration + " on " + amendment.loggedOn + "."
                    } else {
                        "Changes " + amendment.originalDuration +
                            " to " + amendment.requestedDuration + "."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                amendment.reason?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Note (optional)") },
                    minLines = 2,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(note.takeIf { it.isNotBlank() }) },
                enabled = !submitting,
            ) {
                Text(if (approve) "Approve" else "Reject")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
