package com.wmt.app.ui.approvals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.ApprovalRequest
import com.wmt.app.ui.components.ApprovalStatusBadge
import com.wmt.app.util.DateUtils

/**
 * One request in a list.
 *
 * The figures along the bottom come from the server rather than being counted here: the
 * queue endpoint reports quorum progress and step position per row, because the active
 * step, its approvers and its recorded decisions are all needed to work them out.
 */
@Composable
fun ApprovalRequestCard(
    request: ApprovalRequest,
    /** Null until the request detail screen exists, so the card does not offer a tap
     *  that goes nowhere. */
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = request.reference,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                ApprovalStatusBadge(request.statusEnum)
            }

            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Project and who raised it: the two things an approver reads before opening.
            Text(
                text = listOfNotNull(request.projectName, request.requesterName)
                    .joinToString(" · ")
                    .ifBlank { "No project" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val progress = listOfNotNull(request.stepProgressLabel, request.quorumLabel)
            if (progress.isNotEmpty() || request.stepDueAt != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (progress.isNotEmpty()) {
                        Text(
                            text = progress.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    request.stepDueAt?.let { due ->
                        Spacer(Modifier.weight(1f))
                        DueLabel(due = due, isOverdue = request.isOverdue)
                    }
                }
            }
        }
    }
}

/**
 * When this step is due. Overdue is the server's own verdict, not a local clock
 * comparison, so the list agrees with the decision screen and with the web app.
 */
@Composable
private fun DueLabel(due: String, isOverdue: Boolean) {
    val color = if (isOverdue) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = DateUtils.formatDate(due)?.let { if (isOverdue) "Overdue · $it" else it }
                ?: if (isOverdue) "Overdue" else "",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
