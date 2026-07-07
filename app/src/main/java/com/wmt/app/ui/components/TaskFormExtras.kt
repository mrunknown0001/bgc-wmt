package com.wmt.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.UserSummary

/** Human labels for the API's recurrence_frequency values; null = not recurring. */
val RECURRENCE_FREQUENCIES: List<Pair<String?, String>> = listOf(
    null to "Doesn't repeat",
    "daily" to "Daily",
    "weekly" to "Weekly",
    "monthly" to "Monthly",
    "yearly" to "Yearly",
)

/** "Repeat" dropdown + interval field, shared by the create/edit task dialogs. */
@Composable
fun RecurrenceFields(
    frequency: String?,
    interval: String,
    onFrequencyChange: (String?) -> Unit,
    onIntervalChange: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            DropdownField(
                label = "Repeat",
                options = RECURRENCE_FREQUENCIES,
                selected = RECURRENCE_FREQUENCIES.firstOrNull { it.first == frequency }
                    ?: RECURRENCE_FREQUENCIES.first(),
                optionLabel = { it.second },
                onSelected = { onFrequencyChange(it.first) },
            )
        }
        if (frequency != null) {
            OutlinedTextField(
                value = interval,
                onValueChange = { new -> onIntervalChange(new.filter { it.isDigit() }.take(3)) },
                label = { Text("Every") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp),
            )
        }
    }
}

/** Multi-select member chips for task collaborators. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollaboratorPicker(
    members: List<UserSummary>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    if (members.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Collaborators",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            members.forEach { member ->
                val selected = member.id in selectedIds
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(member.id) },
                    label = { Text(member.name) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
