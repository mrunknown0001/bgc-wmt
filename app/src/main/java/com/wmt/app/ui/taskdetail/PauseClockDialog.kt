package com.wmt.app.ui.taskdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.PausePreview

/**
 * Confirms how much of the time in motion actually counts as work.
 *
 * The suggestion comes from the server, which knows what is already logged today and
 * whose day it lands on. It is editable because a clock left running over lunch is the
 * normal case, not the exception.
 */
@Composable
fun PauseClockDialog(
    preview: PausePreview,
    onConfirm: (minutes: Int, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(preview.suggestedMinutes.toString()) }
    var note by remember { mutableStateOf("") }

    val parsed = minutes.trim().toIntOrNull()
    val error = when {
        minutes.isBlank() -> "Enter the minutes to record."
        parsed == null -> "Minutes must be a whole number."
        parsed < 0 -> "Minutes cannot be negative."
        // The server rejects anything past a day in one go.
        parsed > PausePreview.MAX_PAUSE_MINUTES -> "That is more than a day."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause and record time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                        ?: { Text(formatMinutes(parsed ?: 0)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (preview.alreadyLoggedToday > 0) {
                    Text(
                        text = "${formatMinutes(preview.alreadyLoggedToday)} already logged today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Said out loud, because pausing somebody else's task records their
                // work rather than yours.
                preview.creditedTo?.let {
                    Text(
                        text = "This counts against $it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(255) },
                    label = { Text("Note (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsed ?: 0, note.takeIf { it.isNotBlank() }) },
                enabled = error == null,
            ) {
                Text("Pause")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
