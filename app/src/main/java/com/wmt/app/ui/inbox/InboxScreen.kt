package com.wmt.app.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.Notification
import com.wmt.app.domain.model.UserSummary
import kotlinx.coroutines.delay
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.OfflineBanner
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.ProfileAvatarAction
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onNotificationClick: (projectId: Int?, taskId: Int?) -> Unit,
    currentUser: UserSummary? = null,
    onOpenProfile: () -> Unit = {},
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Near-real-time: silently refresh while the Inbox is on screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            viewModel.poll()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = "Inbox",
                scrollBehavior = scrollBehavior,
                actions = {
                    TextButton(onClick = viewModel::markAllRead) {
                        Text("Mark All Read")
                    }
                    ProfileAvatarAction(user = currentUser, onClick = onOpenProfile)
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OfflineBanner(visible = state.offline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InboxFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.notifications.isEmpty() -> SkeletonList()
                    state.error != null && state.notifications.isEmpty() -> ErrorView(
                        message = state.error!!,
                        onRetry = viewModel::refresh,
                    )
                    state.notifications.isEmpty() -> EmptyState(
                        title = "No notifications",
                        message = when (state.filter) {
                            InboxFilter.ALL -> "You have no notifications yet."
                            InboxFilter.UNREAD -> "You're all caught up."
                            InboxFilter.MENTIONED -> "No mentions yet."
                            InboxFilter.BOOKMARKED -> "Bookmark notifications to pin them here."
                            InboxFilter.ARCHIVED -> "Archived notifications will appear here."
                        },
                        icon = Icons.Default.NotificationsNone,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.notifications, key = { it.id }) { notification ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value != SwipeToDismissBoxValue.Settled && notification.isUnread) {
                                        viewModel.markRead(notification)
                                    }
                                    false // keep the row (mark-read only), snap back
                                },
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = notification.isUnread,
                                enableDismissFromEndToStart = notification.isUnread,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                            Spacer(Modifier.size(8.dp))
                                            Text(
                                                "Mark read",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }
                                },
                            ) {
                                NotificationRow(
                                    notification = notification,
                                    onClick = {
                                        viewModel.onNotificationClick(notification, onNotificationClick)
                                    },
                                    onToggleBookmark = { viewModel.toggleBookmark(notification) },
                                    onToggleArchive = { viewModel.toggleArchive(notification) },
                                )
                            }
                            ThinDivider(startIndent = 68.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleArchive: () -> Unit,
) {
    val background = if (notification.isUnread) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForType(notification.data.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                notification.data.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (notification.isUnread) FontWeight.Bold else FontWeight.SemiBold,
            )
            Text(
                notification.data.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                DateUtils.timeAgo(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row {
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (notification.isBookmarked) {
                            Icons.Rounded.Bookmark
                        } else {
                            Icons.Rounded.BookmarkBorder
                        },
                        contentDescription = if (notification.isBookmarked) "Remove bookmark" else "Bookmark",
                        modifier = Modifier.size(18.dp),
                        tint = if (notification.isBookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onToggleArchive, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (notification.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                        contentDescription = if (notification.isArchived) "Unarchive" else "Archive",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (notification.isUnread) {
                Spacer(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 12.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private fun iconForType(type: String): ImageVector {
    val lower = type.lowercase()
    return when {
        lower.contains("task") -> Icons.Default.CheckCircle
        lower.contains("project") -> Icons.Default.Folder
        lower.contains("comment") -> Icons.AutoMirrored.Filled.Comment
        lower.contains("mention") -> Icons.Default.AlternateEmail
        else -> Icons.Default.Notifications
    }
}
