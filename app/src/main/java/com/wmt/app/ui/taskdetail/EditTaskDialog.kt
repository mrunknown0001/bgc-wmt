package com.wmt.app.ui.taskdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.domain.model.UserSummary
import com.wmt.app.ui.components.CollaboratorPicker
import com.wmt.app.ui.components.DatePickerField
import com.wmt.app.ui.components.DropdownField
import com.wmt.app.ui.components.RecurrenceFields

@Composable
fun EditTaskDialog(
    task: Task,
    members: List<UserSummary>,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        status: String,
        priority: String,
        description: String?,
        assignedTo: Int?,
        dueDate: String?,
        startDate: String?,
        recurrenceFrequency: String?,
        recurrenceInterval: Int?,
        collaboratorIds: List<Int>,
    ) -> Unit,
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var status by remember { mutableStateOf(task.statusEnum) }
    var priority by remember { mutableStateOf(task.priorityEnum) }
    var assignee by remember { mutableStateOf(task.assignee) }
    var dueDate by remember { mutableStateOf(task.dueDate?.take(10)) }
    var startDate by remember { mutableStateOf(task.startDate?.take(10)) }
    var recurrenceFrequency by remember {
        mutableStateOf(if (task.isRecurring) task.recurrenceFrequency else null)
    }
    var recurrenceInterval by remember {
        mutableStateOf((task.recurrenceInterval ?: 1).toString())
    }
    var collaboratorIds by remember {
        mutableStateOf(task.collaborators.map { it.id }.toSet())
    }

    // Collaborators may include users outside the member list — keep them selectable.
    val collaboratorOptions = (members + task.collaborators).distinctBy { it.id }

    // Always include the current assignee even if they aren't in the member list.
    val assigneeOptions = (listOf<UserSummary?>(null) + members + listOfNotNull(task.assignee))
        .distinctBy { it?.id }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit Task") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = "Status",
                    options = TaskStatus.entries,
                    selected = status,
                    optionLabel = { it.label },
                    onSelected = { status = it },
                )
                DropdownField(
                    label = "Priority",
                    options = TaskPriority.entries,
                    selected = priority,
                    optionLabel = { it.label },
                    onSelected = { priority = it },
                )
                if (members.isNotEmpty() || task.assignee != null) {
                    DropdownField(
                        label = "Assignee",
                        options = assigneeOptions,
                        selected = assignee,
                        optionLabel = { it?.name ?: "Unassigned" },
                        onSelected = { assignee = it },
                    )
                }
                DatePickerField(label = "Due date", value = dueDate, onChange = { dueDate = it })
                DatePickerField(label = "Start date", value = startDate, onChange = { startDate = it })
                RecurrenceFields(
                    frequency = recurrenceFrequency,
                    interval = recurrenceInterval,
                    onFrequencyChange = { recurrenceFrequency = it },
                    onIntervalChange = { recurrenceInterval = it },
                )
                CollaboratorPicker(
                    members = collaboratorOptions,
                    selectedIds = collaboratorIds,
                    onToggle = { id ->
                        collaboratorIds =
                            if (id in collaboratorIds) collaboratorIds - id else collaboratorIds + id
                    },
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title.trim(),
                        status.raw,
                        priority.raw,
                        description.trim().ifBlank { null },
                        assignee?.id,
                        dueDate,
                        startDate,
                        recurrenceFrequency,
                        recurrenceInterval.toIntOrNull()?.coerceIn(1, 365),
                        collaboratorIds.toList(),
                    )
                },
                enabled = !saving && title.isNotBlank(),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}
