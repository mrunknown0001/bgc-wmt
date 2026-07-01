package com.wmt.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.model.ProjectStatus
import com.wmt.app.ui.components.DatePickerField
import com.wmt.app.ui.components.DropdownField

@Composable
fun EditProjectDialog(
    project: Project,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, status: String, dueDate: String?) -> Unit,
) {
    var name by remember { mutableStateOf(project.name) }
    var description by remember { mutableStateOf(project.description ?: "") }
    var status by remember { mutableStateOf(project.statusEnum) }
    var dueDate by remember { mutableStateOf(project.dueDate?.take(10)) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
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
                    options = ProjectStatus.entries,
                    selected = status,
                    optionLabel = { it.label },
                    onSelected = { status = it },
                )
                DatePickerField(label = "Due date", value = dueDate, onChange = { dueDate = it })
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), description.trim().ifBlank { null }, status.raw, dueDate) },
                enabled = !saving && name.isNotBlank(),
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
