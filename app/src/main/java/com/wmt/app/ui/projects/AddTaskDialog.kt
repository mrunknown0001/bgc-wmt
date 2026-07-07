package com.wmt.app.ui.projects

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
import com.wmt.app.domain.model.ProjectSection
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.domain.model.UserSummary
import com.wmt.app.ui.components.CollaboratorPicker
import com.wmt.app.ui.components.DatePickerField
import com.wmt.app.ui.components.DropdownField
import com.wmt.app.ui.components.RecurrenceFields

@Composable
fun AddTaskDialog(
    creating: Boolean,
    error: String?,
    assignableUsers: List<UserSummary>,
    sections: List<ProjectSection>,
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        status: String,
        priority: String,
        description: String?,
        assignedTo: Int?,
        sectionId: Int?,
        dueDate: String?,
        recurrenceFrequency: String?,
        recurrenceInterval: Int?,
        collaboratorIds: List<Int>,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(TaskStatus.TO_DO) }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var assignee by remember { mutableStateOf<UserSummary?>(null) }
    var section by remember { mutableStateOf<ProjectSection?>(null) }
    var dueDate by remember { mutableStateOf<String?>(null) }
    var recurrenceFrequency by remember { mutableStateOf<String?>(null) }
    var recurrenceInterval by remember { mutableStateOf("1") }
    var collaboratorIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Only real sections are selectable (the synthetic "Other" group has a negative id).
    val realSections = sections.filter { it.id > 0 }
    val assigneeOptions = listOf<UserSummary?>(null) + assignableUsers
    val sectionOptions = listOf<ProjectSection?>(null) + realSections

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text("New Task") },
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
                if (assignableUsers.isNotEmpty()) {
                    DropdownField(
                        label = "Assignee",
                        options = assigneeOptions,
                        selected = assignee,
                        optionLabel = { it?.name ?: "Unassigned" },
                        onSelected = { assignee = it },
                    )
                }
                if (realSections.isNotEmpty()) {
                    DropdownField(
                        label = "Section",
                        options = sectionOptions,
                        selected = section,
                        optionLabel = { it?.name ?: "No section" },
                        onSelected = { section = it },
                    )
                }
                DatePickerField(label = "Due date", value = dueDate, onChange = { dueDate = it })
                RecurrenceFields(
                    frequency = recurrenceFrequency,
                    interval = recurrenceInterval,
                    onFrequencyChange = { recurrenceFrequency = it },
                    onIntervalChange = { recurrenceInterval = it },
                )
                CollaboratorPicker(
                    members = assignableUsers,
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
                    onCreate(
                        title.trim(),
                        status.raw,
                        priority.raw,
                        description.trim().ifBlank { null },
                        assignee?.id,
                        section?.id,
                        dueDate,
                        recurrenceFrequency,
                        recurrenceInterval.toIntOrNull()?.coerceIn(1, 365),
                        collaboratorIds.toList(),
                    )
                },
                enabled = !creating && title.isNotBlank(),
            ) {
                if (creating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("Cancel") }
        },
    )
}
