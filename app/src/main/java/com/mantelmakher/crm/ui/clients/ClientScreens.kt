package com.mantelmakher.crm.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mantelmakher.crm.R
import com.mantelmakher.crm.di.AppContainer
import com.mantelmakher.crm.di.LocalAppContainer
import com.mantelmakher.crm.domain.logic.PhoneLinks
import com.mantelmakher.crm.domain.logic.matchesQuery
import com.mantelmakher.crm.domain.model.ActivityEntry
import com.mantelmakher.crm.domain.model.ActivityKind
import com.mantelmakher.crm.domain.model.Client
import com.mantelmakher.crm.domain.model.ClientKind
import com.mantelmakher.crm.domain.model.DealListItem
import com.mantelmakher.crm.domain.model.Ids
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.repository.ActivityRepository
import com.mantelmakher.crm.domain.repository.ClientRepository
import com.mantelmakher.crm.domain.repository.DealRepository
import com.mantelmakher.crm.domain.repository.TaskRepository
import com.mantelmakher.crm.ui.components.AddFab
import com.mantelmakher.crm.ui.components.ChipRow
import com.mantelmakher.crm.ui.components.ConfirmDeleteDialog
import com.mantelmakher.crm.ui.components.CrmDropdown
import com.mantelmakher.crm.ui.components.CrmScaffold
import com.mantelmakher.crm.ui.components.EmptyState
import com.mantelmakher.crm.ui.components.HistoryBlock
import com.mantelmakher.crm.ui.components.InitialsAvatar
import com.mantelmakher.crm.ui.components.LoadingBox
import com.mantelmakher.crm.ui.components.LtrTextField
import com.mantelmakher.crm.ui.components.NoteComposer
import com.mantelmakher.crm.ui.components.SearchField
import com.mantelmakher.crm.ui.components.SectionTitle
import com.mantelmakher.crm.ui.components.StageBadge
import com.mantelmakher.crm.ui.format.dateTimeLabel
import com.mantelmakher.crm.ui.format.dial
import com.mantelmakher.crm.ui.format.email
import com.mantelmakher.crm.ui.format.labelRes
import com.mantelmakher.crm.ui.format.openHttps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientsViewModel(private val repository: ClientRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val kind = MutableStateFlow<ClientKind?>(null)
    val clients = combine(repository.observe("", null), query, kind) { all, q, k ->
        all.filter { k == null || it.kind == k }.filter { it.matchesQuery(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) { query.value = value }
    fun setKind(value: ClientKind?) { kind.value = value }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ClientsViewModel(container.clients) }
        }
    }
}

@Composable
fun ClientsScreen(onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ClientsViewModel = viewModel(factory = ClientsViewModel.factory(container))
    val clients by vm.clients.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    CrmScaffold(
        title = stringResource(R.string.nav_clients),
        floatingActionButton = { AddFab(onCreate) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, vm::setQuery, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            ChipRow(
                options = listOf<ClientKind?>(null) + ClientKind.entries,
                selected = kind,
                label = { option ->
                    if (option == null) stringResource(R.string.filter_all) else stringResource(option.labelRes())
                },
                onSelect = vm::setKind,
            )
            when {
                clients == null -> LoadingBox()
                clients!!.isEmpty() -> EmptyState(
                    title = stringResource(if (query.isBlank() && kind == null) R.string.empty_clients_title else R.string.no_results),
                    body = stringResource(if (query.isBlank() && kind == null) R.string.empty_clients_body else R.string.no_results_body),
                    actionLabel = if (query.isBlank() && kind == null) stringResource(R.string.add_client) else null,
                    onAction = if (query.isBlank() && kind == null) onCreate else null,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(clients!!, key = { it.id }) { client ->
                        ClientCard(client, onClick = { onOpen(client.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientCard(client: Client, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialsAvatar(client.fullName)
            Column(Modifier.weight(1f)) {
                Text(client.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (client.phone.isNotBlank()) {
                    Text(client.phone, style = TextStyle(textDirection = TextDirection.Ltr), maxLines = 1)
                }
                Text(
                    stringResource(client.kind.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

data class ClientDetailState(
    val loaded: Boolean = false,
    val client: Client? = null,
    val deals: List<DealListItem> = emptyList(),
    val tasks: List<CrmTask> = emptyList(),
    val history: List<ActivityEntry> = emptyList(),
)

class ClientDetailViewModel(
    private val clients: ClientRepository,
    private val deals: DealRepository,
    private val tasks: TaskRepository,
    private val activities: ActivityRepository,
    private val clientId: String,
) : ViewModel() {
    val state = combine(
        clients.observeById(clientId),
        deals.observeForClient(clientId),
        tasks.observeForClient(clientId),
        activities.observeForClient(clientId),
    ) { client, dealRows, taskRows, history ->
        ClientDetailState(
            loaded = true,
            client = client?.takeIf { it.deletedAt == null },
            deals = dealRows,
            tasks = taskRows,
            history = history,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientDetailState())

    fun addActivity(kind: ActivityKind, body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            activities.add(ActivityEntry(Ids.new(), kind, text, clientId, null, null, now, now))
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            clients.softDelete(clientId)
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, clientId: String) = viewModelFactory {
            initializer {
                ClientDetailViewModel(
                    container.clients,
                    container.deals,
                    container.tasks,
                    container.activities,
                    clientId,
                )
            }
        }
    }
}

@Composable
fun ClientDetailScreen(
    clientId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenDeal: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onAddDeal: (String) -> Unit,
    onAddTask: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: ClientDetailViewModel = viewModel(factory = ClientDetailViewModel.factory(container, clientId))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.action_failed)
    val missingPhone = stringResource(R.string.phone_missing)
    val missingEmail = stringResource(R.string.email_missing)
    val callLog = stringResource(R.string.activity_call)
    val whatsAppLog = stringResource(R.string.activity_whatsapp)
    val emailLog = stringResource(R.string.activity_email)
    val whatsAppMissing = stringResource(R.string.whatsapp_unavailable)
    val subject = stringResource(R.string.email_subject)
    val meetingLog = stringResource(R.string.log_meeting)
    val viewingLog = stringResource(R.string.log_viewing)
    val client = state.client
    CrmScaffold(
        title = client?.fullName ?: stringResource(R.string.nav_clients),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (client != null) {
                androidx.compose.material3.IconButton(onClick = { onEdit(client.id) }) {
                    androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit))
                }
                androidx.compose.material3.IconButton(onClick = { confirm = true }) {
                    androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete))
                }
            }
        },
    ) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        if (client == null) {
            EmptyState(stringResource(R.string.not_found), stringResource(R.string.no_results_body))
            return@CrmScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(client.kind.labelRes()), color = MaterialTheme.colorScheme.primary)
                if (client.company.isNotBlank()) Text(client.company)
                if (client.phone.isNotBlank()) Text(client.phone, style = TextStyle(textDirection = TextDirection.Ltr))
                if (client.email.isNotBlank()) Text(client.email, style = TextStyle(textDirection = TextDirection.Ltr))
                if (client.source.isNotBlank()) Text(client.source, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (client.note.isNotBlank()) Text(client.note)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = {
                        val url = PhoneLinks.dialUrl(client.phone)
                        if (url == null) scope.launch { snackbar.showSnackbar(missingPhone) }
                        else if (context.dial(url)) vm.addActivity(ActivityKind.CALL, callLog)
                        else scope.launch { snackbar.showSnackbar(failed) }
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.call)) }
                    FilledTonalButton(onClick = {
                        val url = PhoneLinks.whatsAppUrl(client.phone)
                        if (url == null) scope.launch { snackbar.showSnackbar(missingPhone) }
                        else if (context.openHttps(url)) vm.addActivity(ActivityKind.MESSAGE, whatsAppLog)
                        else scope.launch { snackbar.showSnackbar(whatsAppMissing) }
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.whatsapp)) }
                    FilledTonalButton(onClick = {
                        val url = PhoneLinks.mailto(client.email, subject)
                        if (url == null) scope.launch { snackbar.showSnackbar(missingEmail) }
                        else if (context.email(url)) vm.addActivity(ActivityKind.MESSAGE, emailLog)
                        else scope.launch { snackbar.showSnackbar(failed) }
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.email_action)) }
                }
            }
            item {
                SectionTitle(stringResource(R.string.linked_deals))
                TextButton(onClick = { onAddDeal(client.id) }) { Text(stringResource(R.string.add_deal)) }
            }
            if (state.deals.isEmpty()) {
                item { Text(stringResource(R.string.empty_deals_title), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.deals, key = { it.deal.id }) { item ->
                    Card(onClick = { onOpenDeal(item.deal.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.deal.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                item.propertyTitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                            }
                            StageBadge(item.deal.stage)
                        }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.linked_tasks))
                TextButton(onClick = { onAddTask(client.id) }) { Text(stringResource(R.string.add_task)) }
            }
            items(state.tasks.take(8), key = { it.id }) { task ->
                TextButton(onClick = { onOpenTask(task.id) }) {
                    Text(task.title + " · " + dateTimeLabel(task.dueAt), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item { SectionTitle(stringResource(R.string.history)) }
            item { NoteComposer { vm.addActivity(ActivityKind.NOTE, it) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.addActivity(ActivityKind.MEETING, meetingLog) }) {
                        Text(meetingLog)
                    }
                    TextButton(onClick = { vm.addActivity(ActivityKind.VIEWING, viewingLog) }) {
                        Text(viewingLog)
                    }
                }
            }
            item { HistoryBlock(state.history) }
        }
    }
    if (confirm && client != null) {
        ConfirmDeleteDialog(client.fullName, onConfirm = { confirm = false; vm.delete(onBack) }, onDismiss = { confirm = false })
    }
}

class ClientEditorViewModel(
    private val clients: ClientRepository,
    private val activities: ActivityRepository,
    private val clientId: String?,
) : ViewModel() {
    var fullName by mutableStateOf("")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var company by mutableStateOf("")
    var source by mutableStateOf("")
    var note by mutableStateOf("")
    var kind by mutableStateOf(ClientKind.BUYER)
    var nameError by mutableStateOf(false)
    var ready by mutableStateOf(clientId == null)
    private val draftId = clientId ?: Ids.new()
    private var createdAt = 0L
    private var existed = false

    init {
        if (clientId != null) {
            viewModelScope.launch {
                val existing = clients.get(clientId)
                if (existing != null && existing.deletedAt == null) {
                    existed = true
                    createdAt = existing.createdAt
                    fullName = existing.fullName
                    phone = existing.phone
                    email = existing.email
                    company = existing.company
                    source = existing.source
                    note = existing.note
                    kind = existing.kind
                }
                ready = true
            }
        }
    }

    fun save(createdMessage: String, onDone: (String, Boolean) -> Unit) {
        if (fullName.isBlank()) {
            nameError = true
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val created = !existed
            clients.save(
                Client(
                    id = draftId,
                    fullName = fullName.trim(),
                    phone = phone.trim(),
                    email = email.trim(),
                    company = company.trim(),
                    kind = kind,
                    source = source.trim(),
                    note = note.trim(),
                    createdAt = if (existed) createdAt else now,
                    updatedAt = now,
                ),
            )
            if (created) {
                activities.add(ActivityEntry(Ids.new(), ActivityKind.NOTE, createdMessage, draftId, null, null, now, now))
            }
            existed = true
            onDone(draftId, created)
        }
    }

    companion object {
        fun factory(container: AppContainer, clientId: String?) = viewModelFactory {
            initializer { ClientEditorViewModel(container.clients, container.activities, clientId) }
        }
    }
}

@Composable
fun ClientEditorScreen(clientId: String?, onBack: () -> Unit, onSaved: (String, Boolean) -> Unit) {
    val container = LocalAppContainer.current
    val vm: ClientEditorViewModel = viewModel(factory = ClientEditorViewModel.factory(container, clientId))
    val createdMessage = stringResource(R.string.activity_created_client)
    CrmScaffold(
        title = stringResource(if (clientId == null) R.string.new_client else R.string.edit_client),
        onBack = onBack,
        actions = {
            TextButton(onClick = { vm.save(createdMessage, onSaved) }) { Text(stringResource(R.string.save)) }
        },
    ) { padding ->
        if (!vm.ready) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = vm.fullName,
                    onValueChange = { vm.fullName = it; vm.nameError = false },
                    label = { Text(stringResource(R.string.client_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = vm.nameError,
                    supportingText = if (vm.nameError) { { Text(stringResource(R.string.name_required)) } } else null,
                    singleLine = true,
                )
            }
            item { LtrTextField(vm.phone, { vm.phone = it }, stringResource(R.string.client_phone), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) }
            item { LtrTextField(vm.email, { vm.email = it }, stringResource(R.string.client_email), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)) }
            item {
                OutlinedTextField(vm.company, { vm.company = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.client_company)) }, singleLine = true)
            }
            item {
                CrmDropdown(
                    label = stringResource(R.string.client_kind),
                    valueLabel = stringResource(vm.kind.labelRes()),
                    options = ClientKind.entries,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { vm.kind = it },
                )
            }
            item {
                OutlinedTextField(vm.source, { vm.source = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.client_source)) }, singleLine = true)
            }
            item {
                OutlinedTextField(vm.note, { vm.note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.client_note)) }, minLines = 3)
            }
        }
    }
}
