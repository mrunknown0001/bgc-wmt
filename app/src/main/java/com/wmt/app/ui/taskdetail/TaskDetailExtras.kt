package com.wmt.app.ui.taskdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalFieldType
import com.wmt.app.domain.model.Tag
import com.wmt.app.domain.model.TaskCustomField
import com.wmt.app.domain.model.TaskLink
import com.wmt.app.ui.approvals.ApprovalFieldEditor
import com.wmt.app.ui.components.RestrictionNotice
import com.wmt.app.util.DateUtils

/** The task's custom fields, read-only, with an edit button when the server allows one. */
@Composable
fun CustomFieldsSection(
    fields: List<TaskCustomField>,
    canEdit: Boolean,
    onEdit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Fields",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (canEdit && CustomFieldEdits.editable(fields).isNotEmpty()) {
                TextButton(onClick = onEdit) { Text("Edit fields") }
            }
        }
        fields.forEach { field ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.width(120.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = field.field.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // On every locked field, for managers too: the padlock says the field
                    // is locked, which is true for everybody, even those it does not stop.
                    if (field.isLocked) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = field.value?.displayValue ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (field.value?.displayValue == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        when {
            !canEdit -> RestrictionNotice(
                "Only the task's assignee and the project's managers can change these fields.",
            )
            fields.any { it.lockedForMe } -> RestrictionNotice(
                "Fields with a padlock are locked — only the project's managers can change them.",
            )
        }
    }
}

/**
 * The custom field editor: every field this person may change, in one dialog, saved
 * together. Only the fields that moved are sent (see [CustomFieldEdits.changes]).
 */
@Composable
fun CustomFieldsEditorDialog(
    fields: List<TaskCustomField>,
    saving: Boolean,
    fieldErrors: Map<String, List<String>>,
    onDismiss: () -> Unit,
    onSave: (changes: Map<Int, Any?>) -> Unit,
) {
    val editable = remember(fields) { CustomFieldEdits.editable(fields) }
    val initial = remember(fields) { CustomFieldEdits.initialInputs(fields) }
    var current by remember(fields) { mutableStateOf(initial) }
    val lockedForMe = fields.filter { it.lockedForMe }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit fields") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                editable.forEach { field ->
                    ApprovalFieldEditor(
                        field = field.field,
                        value = current[field.id],
                        isMissing = false,
                        // The server keys a 422 by the path it validated: values.{id}.
                        errors = fieldErrors["values.${field.id}"].orEmpty(),
                        onText = { text -> current = current + (field.id to ApprovalFieldInput.Text(text)) },
                        onOption = { id -> current = current + (field.id to ApprovalFieldInput.Text(id?.toString())) },
                        onToggleOption = { id ->
                            val ids = (current[field.id] as? ApprovalFieldInput.Selection)?.ids.orEmpty()
                            val next = if (id in ids) ids - id else ids + id
                            current = current + (field.id to ApprovalFieldInput.Selection(next))
                        },
                    )
                }
                if (lockedForMe.isNotEmpty()) {
                    RestrictionNotice(
                        "Locked — only the project's managers can change " +
                            lockedForMe.joinToString(", ") { it.field.name } + ".",
                    )
                }
                val webOnly = fields.filter { !it.isEditableType && it.field.typeEnum != ApprovalFieldType.FORMULA }
                if (webOnly.isNotEmpty()) {
                    RestrictionNotice(
                        "${webOnly.joinToString(", ") { it.field.name }} can only be changed on the web.",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = { onSave(CustomFieldEdits.changes(fields, initial, current)) },
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The full text a change replaced. It shows, it does not restore: putting a value back is
 * a decision about somebody else's edit, so the words are handed over and the decision
 * left alone -- the same as the web.
 */
@Composable
fun PreviousValueDialog(sheet: PreviousValueSheet, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val text = sheet.value?.value
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sheet.value?.field?.let { "Previous $it" } ?: "Previous text") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    sheet.loading -> CircularProgressIndicator(Modifier.size(24.dp))
                    sheet.error != null -> Text(sheet.error, color = MaterialTheme.colorScheme.error)
                    else -> {
                        sheet.value?.changedAt?.let {
                            Text(
                                "Replaced ${DateUtils.formatDateTime(it) ?: it}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(text?.ifBlank { null } ?: "(empty)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (!text.isNullOrBlank()) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Copy") }
            }
        },
    )
}

/**
 * What stands in the way of this task, and what it stands in the way of. Open
 * dependencies are what [com.wmt.app.domain.model.TaskRules.isBlocked] is about, so they
 * lead; each opens the task it names.
 */
@Composable
fun DependencyNotices(
    blockedBy: List<TaskLink>,
    blocking: List<TaskLink>,
    isBlocked: Boolean,
    onOpen: (taskId: Int) -> Unit,
) {
    val open = blockedBy.filter { it.isOpen }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isBlocked) {
            RestrictionNotice(
                text = if (open.isEmpty()) {
                    "Blocked by an open dependency — it can't be marked done yet."
                } else {
                    "Blocked by ${open.size} open ${if (open.size == 1) "task" else "tasks"} — it can't be marked done yet:"
                },
                icon = Icons.Outlined.Block,
            )
            open.forEach { LinkRow(it, onOpen) }
        }
        if (blocking.isNotEmpty()) {
            Text(
                text = "Blocking",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            blocking.forEach { LinkRow(it, onOpen) }
        }
    }
}

@Composable
private fun LinkRow(link: TaskLink, onOpen: (Int) -> Unit) {
    Text(
        text = "• ${link.title}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable { onOpen(link.id) }.padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChips(tags: List<Tag>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            AssistChip(onClick = {}, label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) })
        }
    }
}
