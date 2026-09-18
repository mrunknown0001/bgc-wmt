package com.wmt.app.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wmt.app.domain.model.ApprovalAttachment
import com.wmt.app.domain.model.ApprovalComment
import com.wmt.app.domain.model.ApprovalFieldValue
import com.wmt.app.domain.model.ApprovalStepProgress
import com.wmt.app.ui.components.ApprovalStepStatusBadge
import com.wmt.app.ui.components.HtmlText
import com.wmt.app.ui.components.UserAvatar
import com.wmt.app.domain.model.UserSummary
import com.wmt.app.util.DateUtils

/** Section heading used down the detail screen. */
@Composable
fun DetailSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** One custom field and its value, as the project defined it. */
@Composable
fun FieldValueRow(value: ApprovalFieldValue, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = value.fieldName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            // A field left blank, and a people field the API gives no names for, both
            // read as a dash rather than an empty row.
            text = value.displayValue?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One step of the chain and what happened at it.
 *
 * Every attempt is listed, not just the latest: a resubmitted request keeps the earlier
 * decisions, and an approver looking at it needs to see that history.
 */
@Composable
fun StepRow(step: ApprovalStepProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${step.stepNumber}. ${step.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (step.attemptNumber > 1) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "attempt ${step.attemptNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            ApprovalStepStatusBadge(step.statusEnum)
        }

        val quorum = step.quorumRequired
        if (quorum != null) {
            Text(
                text = "${step.approvalsReceived} of $quorum approved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (step.approverNames.isNotEmpty()) {
            Text(
                text = "Approvers: ${step.approverNames.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        step.decisions.forEach { decision ->
            Text(
                text = listOfNotNull(
                    decision.deciderName,
                    decision.decisionEnum.label.lowercase(),
                    DateUtils.formatDate(decision.decidedAt),
                ).joinToString(" "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            decision.comment?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * One attached file.
 *
 * Images preview inline, which works because every image load goes through the
 * authenticated client. Other types show their name and size: opening a PDF or a
 * spreadsheet needs a download, which this screen does not do yet.
 */
@Composable
fun AttachmentRow(attachment: ApprovalAttachment, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (attachment.isImage && attachment.downloadUrl != null) {
            AsyncImage(
                model = attachment.downloadUrl,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = attachment.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = attachment.readableSize(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ApprovalAttachment.icon(): ImageVector = when {
    isPdf -> Icons.Rounded.PictureAsPdf
    isImage -> Icons.Rounded.Image
    else -> Icons.Rounded.Description
}

/** Size in the units a person reads, not bytes. */
private fun ApprovalAttachment.readableSize(): String = when {
    fileSize <= 0L -> fileType.substringAfterLast('/').uppercase()
    fileSize < 1024 -> "$fileSize B"
    fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
    else -> "%.1f MB".format(fileSize / (1024.0 * 1024.0))
}

/** One comment on the request. Bodies are rich text server-side, so they render as HTML. */
@Composable
fun CommentRow(comment: ApprovalComment, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        UserAvatar(
            user = comment.authorName?.let { UserSummary(id = comment.id, name = it) },
            size = 32.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName ?: "Someone",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                DateUtils.formatDateTime(comment.createdAt)?.let { when_ ->
                    Text(
                        text = when_,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HtmlText(html = comment.body, style = MaterialTheme.typography.bodyMedium)
            comment.attachments.forEach { AttachmentRow(it) }
        }
    }
}
