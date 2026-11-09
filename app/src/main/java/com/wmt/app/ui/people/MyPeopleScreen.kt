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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.OverdueTask
import com.wmt.app.domain.model.PersonnelMember
import com.wmt.app.domain.model.PersonnelOverdue
import com.wmt.app.domain.model.PersonnelUnit
import com.wmt.app.domain.repository.PeopleRepository
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.PriorityBadge
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PeopleTab { PEOPLE, OVERDUE }

data class MyPeopleUiState(
    val tab: PeopleTab = PeopleTab.PEOPLE,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val units: List<PersonnelUnit> = emptyList(),
    val overdue: PersonnelOverdue? = null,
    val bucket: String? = null,
    val error: String? = null,
)

/** One line of the flattened tree: a unit heading, or a member under it. */
sealed interface PeopleRow {
    data class Unit(val unit: PersonnelUnit, val depth: Int) : PeopleRow
    data class Member(val member: PersonnelMember, val unitKey: String) : PeopleRow
}

/** The tree as rows, a unit's own people before the units inside it, as the web lays it out. */
fun flatten(units: List<PersonnelUnit>, depth: Int = 0): List<PeopleRow> = units.flatMap { unit ->
    val key = "${unit.type}-${unit.id}"
    listOf<PeopleRow>(PeopleRow.Unit(unit, depth)) +
        unit.members.map { PeopleRow.Member(it, key) } +
        flatten(unit.children, depth + 1)
}

/**
 * The people a division head, department head or team leader is responsible for, laid out
 * as the org chart is, and every overdue task among them. Scope is the server's
 * (OrgScope, from who heads what); the app never works it out.
 */
@HiltViewModel
class MyPeopleViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyPeopleUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load(isRefresh: Boolean = false) {
        _state.update { it.copy(loading = !isRefresh && it.units.isEmpty(), refreshing = isRefresh, error = null) }
        viewModelScope.launch {
            val people = repository.myPersonnel()
            val overdue = repository.personnelOverdue()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    units = (people as? Resource.Success)?.data ?: it.units,
                    overdue = (overdue as? Resource.Success)?.data ?: it.overdue,
                    error = (people as? Resource.Error)?.error?.message ?: (overdue as? Resource.Error)?.error?.message,
                )
            }
        }
    }

    fun selectTab(tab: PeopleTab) = _state.update { it.copy(tab = tab) }

    fun selectBucket(key: String?) = _state.update { it.copy(bucket = if (it.bucket == key) null else key) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPeopleScreen(
    onBack: () -> Unit,
    onOpenTask: (projectId: Int, taskId: Int) -> Unit,
    onOpenPerson: (userId: Int) -> Unit,
    viewModel: MyPeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = "My people",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(selected = state.tab == PeopleTab.PEOPLE, onClick = { viewModel.selectTab(PeopleTab.PEOPLE) }, text = { Text("People") })
                Tab(
                    selected = state.tab == PeopleTab.OVERDUE,
                    onClick = { viewModel.selectTab(PeopleTab.OVERDUE) },
                    text = { Text(state.overdue?.total?.let { "Overdue ($it)" } ?: "Overdue") },
                )
            }
            val error = state.error
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { viewModel.load(isRefresh = true) }) {
                when {
                    state.loading -> SkeletonList()
                    error != null && state.units.isEmpty() -> ErrorView(error, { viewModel.load() })
                    state.tab == PeopleTab.PEOPLE -> PeopleList(state.units, onOpenPerson)
                    else -> OverdueList(
                        overdue = state.overdue,
                        bucket = state.bucket,
                        onBucket = viewModel::selectBucket,
                        onOpen = { task -> onOpenTask(task.projectId ?: 0, task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleList(units: List<PersonnelUnit>, onOpenPerson: (Int) -> Unit) {
    val rows = flatten(units)
    if (rows.isEmpty()) {
        EmptyState(title = "Nobody here yet", message = "The units you head or lead have no people in them.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(rows, key = { row ->
            when (row) {
                is PeopleRow.Unit -> "unit-${row.unit.type}-${row.unit.id}"
                is PeopleRow.Member -> "member-${row.unitKey}-${row.member.id}"
            }
        }) { row ->
            when (row) {
                is PeopleRow.Unit -> Text(
                    text = "${row.unit.type.replaceFirstChar { it.uppercase() }} · ${row.unit.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp + (row.depth * 12).dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                is PeopleRow.Member -> {
                    MemberRow(row.member, onClick = { onOpenPerson(row.member.id) })
                    ThinDivider(startIndent = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: PersonnelMember, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.bodyLarge)
            member.position?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Read before the numbers: an away person's queue is somebody else's for now.
            member.coveredBy?.let {
                Text("Away — covered by $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${member.openTasks} open", style = MaterialTheme.typography.labelMedium)
            if (member.overdueTasks > 0) {
                Text(
                    "${member.overdueTasks} overdue",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OverdueList(
    overdue: PersonnelOverdue?,
    bucket: String?,
    onBucket: (String?) -> Unit,
    onOpen: (OverdueTask) -> Unit,
) {
    if (overdue == null || overdue.total == 0) {
        EmptyState(title = "Nothing overdue", message = "Nobody you are responsible for has an overdue task.")
        return
    }
    val shown = overdue.tasks.filter { bucket == null || it.bucket == bucket }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "buckets") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(overdue.buckets, key = { it.key }) { b ->
                    FilterChip(selected = bucket == b.key, onClick = { onBucket(b.key) }, label = { Text("${b.label} (${b.count})") })
                }
            }
        }
        item(key = "summary") {
            Text(
                text = "${overdue.total} overdue across ${overdue.people} ${if (overdue.people == 1) "person" else "people"}" +
                    // Say when the list is a sample, or it reads as the whole.
                    if (overdue.capped) " — showing the first ${overdue.limit}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(shown, key = { it.id }) { task ->
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(task) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    PriorityBadge(task.priorityEnum)
                }
                Text(
                    text = listOfNotNull(
                        task.assigneeName,
                        task.projectName,
                        "${task.daysLate} ${if (task.daysLate == 1) "day" else "days"} late",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ThinDivider(startIndent = 16.dp)
        }
    }
}
