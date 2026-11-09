package com.wmt.app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.PersonOverview
import com.wmt.app.domain.model.PersonTask
import com.wmt.app.domain.repository.PeopleRepository
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.PriorityBadge
import com.wmt.app.ui.components.RestrictionNotice
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.DateUtils
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The three lists the web's "What they're carrying" card toggles between. */
enum class WorkBucket(val label: String) { OVERDUE("Overdue"), WEEK("This week"), MONTH("This month") }

/** What one bucket shows: its rows, the uncapped count, and whether the rows are a sample. */
data class BucketView(val tasks: List<PersonTask>, val count: Int, val capped: Boolean)

/**
 * The rows and count for [bucket]. Week and month are one list on the server -- the month
 * is a superset of the week -- so the week is the rows it marks as in the week, and the
 * count is always the server's, which is not capped the way the rows are.
 */
fun PersonOverview.bucket(bucket: WorkBucket): BucketView {
    val (rows, count) = when (bucket) {
        WorkBucket.OVERDUE -> overdue to overdueCount
        WorkBucket.WEEK -> upcoming.filter { it.inWeek } to weekCount
        WorkBucket.MONTH -> upcoming to monthCount
    }
    return BucketView(rows, count, capped = rows.size < count)
}

/** Opens on overdue work when there is any, since that is what the page is for. */
fun PersonOverview.defaultBucket(): WorkBucket = if (overdueCount > 0) WorkBucket.OVERDUE else WorkBucket.WEEK

data class PersonUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val person: PersonOverview? = null,
    /** Null until the first load picks one. */
    val bucket: WorkBucket? = null,
    val error: String? = null,
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repository: PeopleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: Int = savedStateHandle["userId"] ?: 0

    private val _state = MutableStateFlow(PersonUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load(isRefresh: Boolean = false) {
        _state.update { it.copy(loading = !isRefresh && it.person == null, refreshing = isRefresh, error = null) }
        viewModelScope.launch {
            when (val result = repository.personOverview(userId)) {
                is Resource.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        person = result.data,
                        bucket = it.bucket ?: result.data.defaultBucket(),
                    )
                }
                is Resource.Error -> _state.update { it.copy(loading = false, refreshing = false, error = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    fun selectBucket(bucket: WorkBucket) = _state.update { it.copy(bucket = bucket) }
}

/**
 * One person from My people: their figures, what they are carrying, and what they have
 * been doing -- the web's person page, built by the same server method. Arranging cover
 * and handing work over stay on the web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    onBack: () -> Unit,
    onOpenTask: (projectId: Int, taskId: Int) -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val person = state.person

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = person?.name ?: "Person",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val error = state.error
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.load(isRefresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> SkeletonList()
                person == null -> ErrorView(error ?: "Couldn't load this person.", { viewModel.load() })
                else -> PersonContent(
                    person = person,
                    bucket = state.bucket ?: person.defaultBucket(),
                    onBucket = viewModel::selectBucket,
                    onOpenTask = { task -> onOpenTask(task.projectId ?: 0, task.id) },
                )
            }
        }
    }
}

@Composable
private fun PersonContent(
    person: PersonOverview,
    bucket: WorkBucket,
    onBucket: (WorkBucket) -> Unit,
    onOpenTask: (PersonTask) -> Unit,
) {
    val view = person.bucket(bucket)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                person.position?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                person.placement?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!person.isActive) RestrictionNotice("This account is deactivated.")
                // Read before the numbers: an away person's queue is somebody else's for now.
                person.cover?.let {
                    Text(
                        if (person.coverRunning) "Away — covered by $it" else "Cover arranged: $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item(key = "figures") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Figure("Open", person.openTasks.toString(), Modifier.weight(1f))
                Figure(
                    "Overdue",
                    person.overdueTasks.toString(),
                    Modifier.weight(1f),
                    emphasis = if (person.overdueTasks > 0) MaterialTheme.colorScheme.error else null,
                )
                Figure("Completed", "${person.completionRate}%", Modifier.weight(1f))
                Figure("On time", "${person.onTimeRate}%", Modifier.weight(1f))
            }
            Text(
                text = listOfNotNull(
                    "${person.completedTasks} of ${person.totalTasks} tasks done",
                    "${person.projectsInvolved} ${if (person.projectsInvolved == 1) "project" else "projects"}",
                    "${person.activity30} actions in 30 days",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "buckets") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(WorkBucket.entries, key = { it.name }) { b ->
                    FilterChip(
                        selected = bucket == b,
                        onClick = { onBucket(b) },
                        label = { Text("${b.label} (${person.bucket(b).count})") },
                    )
                }
            }
        }
        if (view.tasks.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = when (bucket) {
                        WorkBucket.OVERDUE -> "Nothing overdue."
                        WorkBucket.WEEK -> "Nothing due this week."
                        WorkBucket.MONTH -> "Nothing due this month."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(view.tasks, key = { "task-${it.id}" }) { task ->
            TaskRow(task, onClick = { onOpenTask(task) })
            ThinDivider(startIndent = 16.dp)
        }
        // Say when the list is a sample, or it reads as the whole.
        if (view.capped) {
            item(key = "capped") {
                Text(
                    "Showing the first ${view.tasks.size} of ${view.count}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (person.delegatedAway.isNotEmpty()) {
            item(key = "away-heading") { Heading("With a stand-in") }
            items(person.delegatedAway, key = { "away-${it.task.id}" }) { away ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenTask(away.task) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(away.task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = listOfNotNull(
                            away.delegate?.let { "With $it" },
                            away.endsOn?.let { DateUtils.formatDate(it)?.let { d -> "until $d" } },
                        ).joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ThinDivider(startIndent = 16.dp)
            }
        }
        if (person.recentActivity.isNotEmpty()) {
            item(key = "activity-heading") { Heading("Recent activity") }
            items(person.recentActivity.withIndex().toList(), key = { "activity-${it.index}" }) { (_, activity) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(activity.description, style = MaterialTheme.typography.bodyMedium)
                    activity.createdAt?.let {
                        Text(DateUtils.timeAgo(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier, emphasis: Color? = null) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = emphasis ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun TaskRow(task: PersonTask, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            PriorityBadge(task.priorityEnum)
        }
        val late = task.daysLate?.takeIf { it > 0 }
        Text(
            text = listOfNotNull(
                task.projectName ?: "Personal task",
                task.statusEnum.label,
                late?.let { "$it ${if (it == 1) "day" else "days"} late" }
                    ?: task.dueDate?.let { DateUtils.formatDate(it)?.let { d -> "due $d" } },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (late != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
