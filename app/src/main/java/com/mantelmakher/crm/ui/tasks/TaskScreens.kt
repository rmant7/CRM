package com.mantelmakher.crm.ui.tasks

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mantelmakher.crm.R
import com.mantelmakher.crm.di.AppContainer
import com.mantelmakher.crm.di.LocalAppContainer
import com.mantelmakher.crm.domain.logic.DayWindow
import com.mantelmakher.crm.domain.logic.matchesFilter
import com.mantelmakher.crm.domain.logic.matchesQuery
import com.mantelmakher.crm.domain.model.Client
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.model.Deal
import com.mantelmakher.crm.domain.model.Ids
import com.mantelmakher.crm.domain.model.Property
import com.mantelmakher.crm.domain.model.TaskFilter
import com.mantelmakher.crm.domain.repository.ClientRepository
import com.mantelmakher.crm.domain.repository.DealRepository
import com.mantelmakher.crm.domain.repository.PropertyRepository
import com.mantelmakher.crm.domain.repository.TaskRepository
import com.mantelmakher.crm.ui.components.AddFab
import com.mantelmakher.crm.ui.components.ChipRow
import com.mantelmakher.crm.ui.components.ConfirmDeleteDialog
import com.mantelmakher.crm.ui.components.CrmDropdown
import com.mantelmakher.crm.ui.components.CrmScaffold
import com.mantelmakher.crm.ui.components.EmptyState
import com.mantelmakher.crm.ui.components.LoadingBox
import com.mantelmakher.crm.ui.components.PickerField
import com.mantelmakher.crm.ui.components.SearchField
import com.mantelmakher.crm.ui.components.rememberDateTimePicker
import com.mantelmakher.crm.ui.format.dateTimeLabel
import com.mantelmakher.crm.ui.format.labelRes
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(private val repository: TaskRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(TaskFilter.OPEN)
    private val zone = ZoneId.systemDefault()
    val tasks = combine(repository.observe(TaskFilter.ALL, ""), query, filter) { all, q, selected ->
        val now = System.currentTimeMillis()
        all.filter { it.matchesFilter(selected, now, zone) }
            .filter { it.matchesQuery(q) }
            .sortedWith(comparator(selected, now))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: TaskFilter) { filter.value = value }
    fun setCompleted(id: String, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(id, completed) }
    }

    private fun comparator(selected: TaskFilter, now: Long): Comparator<CrmTask> {
        if (selected == TaskFilter.DONE) return compareByDescending { it.completedAt ?: 0L }
        return compareBy<CrmTask> { if (DayWindow.isOverdue(it.dueAt, it.completedAt, now, zone)) 0 else 1 }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { TasksViewModel(container.tasks) }
        }
    }
}

@Composable
fun TasksScreen(onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: TasksViewModel = viewModel(factory = TasksViewModel.factory(container))
    val rows by vm.tasks.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    CrmScaffold(title = stringResource(R.string.nav_tasks), floatingActionButton = { AddFab(onCreate) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, vm::setQuery, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            ChipRow(
                options = TaskFilter.entries.map { it as TaskFilter? },
                selected = filter,
                label = { option -> stringResource((option ?: TaskFilter.ALL).labelRes()) },
                onSelect = { vm.setFilter(it ?: TaskFilter.ALL) },
            )
            when {
                rows == null -> LoadingBox()
                rows!!.isEmpty() -> EmptyState(
                    title = stringResource(if (query.isBlank() && filter == TaskFilter.OPEN) R.string.empty_tasks_title else R.string.no_results),
                    body = stringResource(if (query.isBlank() && filter == TaskFilter.OPEN) R.string.empty_tasks_body else R.string.no_results_body),
                    actionLabel = if (query.isBlank() && filter == TaskFilter.OPEN) stringResource(R.string.add_task) else null,
                    onAction = if (query.isBlank() && filter == TaskFilter.OPEN) onCreate else null,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(rows!!, key = { it.id }) { task ->
                        TaskCard(task, { vm.setCompleted(task.id, it) }) { onOpen(task.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: CrmTask, onChecked: (Boolean) -> Unit, onClick: () -> Unit) {
    val overdue = DayWindow.isOverdue(task.dueAt, task.completedAt, System.currentTimeMillis(), ZoneId.systemDefault())
    Card(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.completedAt != null, onCheckedChange = onChecked)
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dateTimeLabel(task.dueAt),
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class LinkChoice(val id: String?, val label: String)

class TaskEditorViewModel(
    private val tasks: TaskRepository,
    private val clients: ClientRepository,
    private val deals: DealRepository,
    private val properties: PropertyRepository,
    private val taskId: String?,
    presetClientId: String?,
    presetDealId: String?,
    presetPropertyId: String?,
) : ViewModel() {
    var title by mutableStateOf("")
    var details by mutableStateOf("")
    var dueAt by mutableStateOf<Long?>(null)
    var reminderAt by mutableStateOf<Long?>(null)
    var completed by mutableStateOf(false)
    var clientId by mutableStateOf(presetClientId)
    var dealId by mutableStateOf(presetDealId)
    var propertyId by mutableStateOf(presetPropertyId)
    var clientOptions by mutableStateOf<List<Client>>(emptyList())
    var dealOptions by mutableStateOf<List<Deal>>(emptyList())
    var propertyOptions by mutableStateOf<List<Property>>(emptyList())
    var titleError by mutableStateOf(false)
    var ready by mutableStateOf(taskId == null)
    var missing by mutableStateOf(false)
    private val draftId = taskId ?: Ids.new()
    private var createdAt = 0L
    private var existed = false
    private var completedAt: Long? = null

    init {
        viewModelScope.launch { clients.observe("", null).collect { clientOptions = it } }
        viewModelScope.launch { deals.observe("", null).collect { dealOptions = it.map { row -> row.deal } } }
        viewModelScope.launch { properties.observe("", null, null, null).collect { propertyOptions = it } }
        if (taskId != null) {
            viewModelScope.launch {
                val existing = tasks.get(taskId)
                if (existing == null || existing.deletedAt != null) {
                    missing = true
                } else {
                    existed = true
                    createdAt = existing.createdAt
                    title = existing.title
                    details = existing.details
                    dueAt = existing.dueAt
                    reminderAt = existing.reminderAt
                    completed = existing.completedAt != null
                    completedAt = existing.completedAt
                    clientId = existing.clientId
                    dealId = existing.dealId
                    propertyId = existing.propertyId
                }
                ready = true
            }
        }
    }

    fun save(onDone: () -> Unit) {
        if (title.isBlank()) {
            titleError = true
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val doneAt = when {
                completed && completedAt != null -> completedAt
                completed -> now
                else -> null
            }
            tasks.save(
                CrmTask(
                    id = draftId,
                    title = title.trim(),
                    details = details.trim(),
                    dueAt = dueAt,
                    reminderAt = reminderAt,
                    completedAt = doneAt,
                    clientId = clientId,
                    dealId = dealId,
                    propertyId = propertyId,
                    createdAt = if (existed) createdAt else now,
                    updatedAt = now,
                ),
            )
            existed = true
            completedAt = doneAt
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (!existed) {
            onDone()
            return
        }
        viewModelScope.launch {
            tasks.softDelete(draftId)
            onDone()
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            taskId: String?,
            clientId: String?,
            dealId: String?,
            propertyId: String?,
        ) = viewModelFactory {
            initializer {
                TaskEditorViewModel(container.tasks, container.clients, container.deals, container.properties, taskId, clientId, dealId, propertyId)
            }
        }
    }
}

@Composable
fun TaskEditorScreen(
    taskId: String?,
    presetClientId: String?,
    presetDealId: String?,
    presetPropertyId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: TaskEditorViewModel = viewModel(
        factory = TaskEditorViewModel.factory(container, taskId, presetClientId, presetDealId, presetPropertyId),
    )
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val denied = stringResource(R.string.notifications_denied)
    val past = stringResource(R.string.reminder_past)
    val none = stringResource(R.string.none_option)
    var confirm by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snackbar.showSnackbar(denied) }
    }
    fun ensureNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val pickDue = rememberDateTimePicker { vm.dueAt = it }
    val pickReminder = rememberDateTimePicker { picked ->
        vm.reminderAt = picked
        ensureNotifications()
        if (picked <= System.currentTimeMillis()) scope.launch { snackbar.showSnackbar(past) }
    }
    CrmScaffold(
        title = stringResource(if (taskId == null) R.string.new_task else R.string.edit_task),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (taskId != null) {
                androidx.compose.material3.IconButton(onClick = { confirm = true }) {
                    androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete))
                }
            }
            TextButton(onClick = { vm.save(onSaved) }) { Text(stringResource(R.string.save)) }
        },
    ) { padding ->
        if (!vm.ready) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        if (vm.missing) {
            EmptyState(stringResource(R.string.not_found), stringResource(R.string.no_results_body))
            return@CrmScaffold
        }
        val clientChoices = listOf(LinkChoice(null, none)) + vm.clientOptions.map { LinkChoice(it.id, it.fullName) }
        val dealChoices = listOf(LinkChoice(null, none)) + vm.dealOptions.map { LinkChoice(it.id, it.title) }
        val propertyChoices = listOf(LinkChoice(null, none)) + vm.propertyOptions.map { LinkChoice(it.id, it.title) }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    vm.title,
                    { vm.title = it; vm.titleError = false },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.task_title)) },
                    isError = vm.titleError,
                    supportingText = if (vm.titleError) { { Text(stringResource(R.string.title_required)) } } else null,
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(vm.details, { vm.details = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.task_details)) }, minLines = 3)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.completed_label), modifier = Modifier.weight(1f))
                    Switch(checked = vm.completed, onCheckedChange = { vm.completed = it })
                }
            }
            item {
                PickerField(
                    stringResource(R.string.task_due),
                    dateTimeLabel(vm.dueAt),
                    pickDue,
                    if (vm.dueAt != null) { { vm.dueAt = null } } else null,
                )
            }
            item {
                PickerField(
                    stringResource(R.string.task_reminder),
                    dateTimeLabel(vm.reminderAt),
                    pickReminder,
                    if (vm.reminderAt != null) { { vm.reminderAt = null } } else null,
                )
                Text(stringResource(R.string.reminder_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                CrmDropdown(stringResource(R.string.link_client), clientChoices.firstOrNull { it.id == vm.clientId }?.label ?: none, clientChoices, { it.label }, { vm.clientId = it.id })
            }
            item {
                CrmDropdown(stringResource(R.string.link_deal), dealChoices.firstOrNull { it.id == vm.dealId }?.label ?: none, dealChoices, { it.label }, { vm.dealId = it.id })
            }
            item {
                CrmDropdown(stringResource(R.string.link_property), propertyChoices.firstOrNull { it.id == vm.propertyId }?.label ?: none, propertyChoices, { it.label }, { vm.propertyId = it.id })
            }
        }
    }
    if (confirm) {
        ConfirmDeleteDialog(vm.title.ifBlank { stringResource(R.string.task_title) }, onConfirm = { confirm = false; vm.delete(onSaved) }, onDismiss = { confirm = false })
    }
}
