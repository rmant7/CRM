package com.mantelmakher.crm.ui.deals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.mantelmakher.crm.domain.logic.MoneyText
import com.mantelmakher.crm.domain.logic.Numbers
import com.mantelmakher.crm.domain.logic.matchesQuery
import com.mantelmakher.crm.domain.model.ActivityEntry
import com.mantelmakher.crm.domain.model.ActivityKind
import com.mantelmakher.crm.domain.model.Client
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.model.Deal
import com.mantelmakher.crm.domain.model.DealListItem
import com.mantelmakher.crm.domain.model.DealStage
import com.mantelmakher.crm.domain.model.Ids
import com.mantelmakher.crm.domain.model.Property
import com.mantelmakher.crm.domain.repository.ActivityRepository
import com.mantelmakher.crm.domain.repository.ClientRepository
import com.mantelmakher.crm.domain.repository.DealRepository
import com.mantelmakher.crm.domain.repository.PropertyRepository
import com.mantelmakher.crm.domain.repository.TaskRepository
import com.mantelmakher.crm.domain.repository.UserSettings
import com.mantelmakher.crm.ui.components.AddFab
import com.mantelmakher.crm.ui.components.ChipRow
import com.mantelmakher.crm.ui.components.ConfirmDeleteDialog
import com.mantelmakher.crm.ui.components.CrmDropdown
import com.mantelmakher.crm.ui.components.CrmScaffold
import com.mantelmakher.crm.ui.components.EmptyState
import com.mantelmakher.crm.ui.components.HistoryBlock
import com.mantelmakher.crm.ui.components.LoadingBox
import com.mantelmakher.crm.ui.components.LtrTextField
import com.mantelmakher.crm.ui.components.NoteComposer
import com.mantelmakher.crm.ui.components.PickerField
import com.mantelmakher.crm.ui.components.SearchField
import com.mantelmakher.crm.ui.components.SectionTitle
import com.mantelmakher.crm.ui.components.StageBadge
import com.mantelmakher.crm.ui.components.rememberDateTimePicker
import com.mantelmakher.crm.ui.format.dateTimeLabel
import com.mantelmakher.crm.ui.format.labelRes
import com.mantelmakher.crm.ui.format.moneyLabel
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DealsViewModel(private val repository: DealRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val stage = MutableStateFlow<DealStage?>(null)
    val board = MutableStateFlow(false)
    val deals = combine(repository.observe("", null), query, stage) { all, q, selected ->
        all.filter { selected == null || it.deal.stage == selected }
            .filter { it.deal.matchesQuery(q, it.clientName, it.propertyTitle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) { query.value = value }
    fun setStage(value: DealStage?) { stage.value = value }
    fun setBoard(value: Boolean) { board.value = value }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { DealsViewModel(container.deals) }
        }
    }
}

@Composable
fun DealsScreen(onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: DealsViewModel = viewModel(factory = DealsViewModel.factory(container))
    val rows by vm.deals.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val stage by vm.stage.collectAsStateWithLifecycle()
    val board by vm.board.collectAsStateWithLifecycle()
    CrmScaffold(title = stringResource(R.string.nav_deals), floatingActionButton = { if (!board) AddFab(onCreate) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, vm::setQuery, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            ChipRow(
                options = listOf<DealStage?>(null) + DealStage.entries,
                selected = stage,
                label = { option -> if (option == null) stringResource(R.string.filter_all) else stringResource(option.labelRes()) },
                onSelect = vm::setStage,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !board, onClick = { vm.setBoard(false) }, label = { Text(stringResource(R.string.list_view)) })
                FilterChip(selected = board, onClick = { vm.setBoard(true) }, label = { Text(stringResource(R.string.board_view)) })
            }
            when {
                rows == null -> LoadingBox()
                board -> DealBoard(rows!!, onOpen)
                rows!!.isEmpty() -> EmptyState(
                    title = stringResource(if (query.isBlank() && stage == null) R.string.empty_deals_title else R.string.no_results),
                    body = stringResource(if (query.isBlank() && stage == null) R.string.empty_deals_body else R.string.no_results_body),
                    actionLabel = if (query.isBlank() && stage == null) stringResource(R.string.add_deal) else null,
                    onAction = if (query.isBlank() && stage == null) onCreate else null,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(rows!!, key = { it.deal.id }) { item -> DealCard(item) { onOpen(item.deal.id) } }
                }
            }
        }
    }
}

@Composable
private fun DealCard(item: DealListItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.deal.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StageBadge(item.deal.stage)
            }
            val who = listOfNotNull(item.clientName, item.propertyTitle).joinToString(" · ")
            if (who.isNotBlank()) Text(who, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(moneyLabel(item.deal.amount, item.deal.currency))
        }
    }
}

@Composable
private fun DealBoard(rows: List<DealListItem>, onOpen: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(DealStage.entries, key = { it.name }) { stage ->
            val column = rows.filter { it.deal.stage == stage }
            Column(
                Modifier
                    .fillParentMaxHeight()
                    .width(240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(stage.labelRes()) + " · " + column.size, style = MaterialTheme.typography.titleSmall)
                if (column.isEmpty()) {
                    Text(stringResource(R.string.board_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                column.forEach { item ->
                    Card(onClick = { onOpen(item.deal.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.deal.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            item.clientName?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

data class DealDetailState(
    val loaded: Boolean = false,
    val deal: Deal? = null,
    val clientName: String? = null,
    val propertyTitle: String? = null,
    val tasks: List<CrmTask> = emptyList(),
    val history: List<ActivityEntry> = emptyList(),
)

class DealDetailViewModel(
    private val deals: DealRepository,
    private val clients: ClientRepository,
    private val properties: PropertyRepository,
    private val tasks: TaskRepository,
    private val activities: ActivityRepository,
    private val dealId: String,
) : ViewModel() {
    val state = combine(
        deals.observeById(dealId),
        clients.observe("", null),
        properties.observe("", null, null, null),
        tasks.observeForDeal(dealId),
        activities.observeForDeal(dealId),
    ) { deal, clientRows, propertyRows, taskRows, history ->
        val live = deal?.takeIf { it.deletedAt == null }
        DealDetailState(
            loaded = true,
            deal = live,
            clientName = clientRows.firstOrNull { it.id == live?.clientId }?.fullName,
            propertyTitle = propertyRows.firstOrNull { it.id == live?.propertyId }?.title,
            tasks = taskRows,
            history = history,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DealDetailState())

    fun addNote(body: String) {
        val deal = state.value.deal ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            activities.add(ActivityEntry(Ids.new(), ActivityKind.NOTE, body.trim(), deal.clientId, deal.id, deal.propertyId, now, now))
        }
    }

    fun moveTo(stage: DealStage, message: String) {
        val deal = state.value.deal ?: return
        viewModelScope.launch {
            deals.save(deal.copy(stage = stage))
            val now = System.currentTimeMillis()
            activities.add(ActivityEntry(Ids.new(), ActivityKind.NOTE, message, deal.clientId, deal.id, deal.propertyId, now, now))
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            deals.softDelete(dealId)
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, dealId: String) = viewModelFactory {
            initializer {
                DealDetailViewModel(container.deals, container.clients, container.properties, container.tasks, container.activities, dealId)
            }
        }
    }
}

@Composable
fun DealDetailScreen(
    dealId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenClient: (String) -> Unit,
    onOpenProperty: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onAddTask: (String, String?, String?) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: DealDetailViewModel = viewModel(factory = DealDetailViewModel.factory(container, dealId))
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val deal = state.deal
    val stageLabels = DealStage.entries.associateWith { stringResource(it.labelRes()) }
    val stagePattern = stringResource(R.string.activity_stage)
    CrmScaffold(
        title = deal?.title ?: stringResource(R.string.nav_deals),
        onBack = onBack,
        actions = {
            if (deal != null) {
                IconButton(onClick = { onEdit(deal.id) }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit)) }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete)) }
            }
        },
    ) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        if (deal == null) {
            EmptyState(stringResource(R.string.not_found), stringResource(R.string.no_results_body))
            return@CrmScaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { StageBadge(deal.stage) }
            item { Text(moneyLabel(deal.amount, deal.currency), style = MaterialTheme.typography.headlineSmall) }
            item {
                state.clientName?.let { name ->
                    TextButton(onClick = { deal.clientId?.let(onOpenClient) }) { Text(name) }
                }
                state.propertyTitle?.let { title ->
                    TextButton(onClick = { deal.propertyId?.let(onOpenProperty) }) { Text(title) }
                }
                if (deal.expectedCloseAt != null) Text(stringResource(R.string.deal_close) + ": " + dateTimeLabel(deal.expectedCloseAt))
                if (deal.note.isNotBlank()) Text(deal.note)
            }
            item {
                val next = deal.stage.next()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (next != null) {
                        FilledTonalButton(onClick = {
                            vm.moveTo(next, stagePattern.format(stageLabels[next].orEmpty()))
                        }) { Text(stringResource(R.string.move_to, stageLabels[next].orEmpty())) }
                    }
                    if (deal.stage != DealStage.WON) {
                        TextButton(onClick = { vm.moveTo(DealStage.WON, stagePattern.format(stageLabels[DealStage.WON].orEmpty())) }) {
                            Text(stringResource(R.string.mark_won))
                        }
                    }
                    if (deal.stage != DealStage.LOST) {
                        TextButton(onClick = { vm.moveTo(DealStage.LOST, stagePattern.format(stageLabels[DealStage.LOST].orEmpty())) }) {
                            Text(stringResource(R.string.mark_lost))
                        }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.linked_tasks))
                TextButton(onClick = { onAddTask(deal.id, deal.clientId, deal.propertyId) }) { Text(stringResource(R.string.add_task)) }
            }
            items(state.tasks, key = { it.id }) { task ->
                TextButton(onClick = { onOpenTask(task.id) }) { Text(task.title + " · " + dateTimeLabel(task.dueAt)) }
            }
            item { SectionTitle(stringResource(R.string.history)) }
            item { NoteComposer(vm::addNote) }
            item { HistoryBlock(state.history) }
        }
    }
    if (confirm && deal != null) {
        ConfirmDeleteDialog(deal.title, onConfirm = { confirm = false; vm.delete(onBack) }, onDismiss = { confirm = false })
    }
}

private data class LinkChoice(val id: String?, val label: String)

class DealEditorViewModel(
    private val deals: DealRepository,
    private val clients: ClientRepository,
    private val properties: PropertyRepository,
    private val activities: ActivityRepository,
    private val settings: UserSettings,
    private val dealId: String?,
    presetClientId: String?,
    presetPropertyId: String?,
) : ViewModel() {
    var title by mutableStateOf("")
    var clientId by mutableStateOf(presetClientId)
    var propertyId by mutableStateOf(presetPropertyId)
    var stage by mutableStateOf(DealStage.LEAD)
    var amount by mutableStateOf("")
    var currency by mutableStateOf("ILS")
    var expectedCloseAt by mutableStateOf<Long?>(null)
    var note by mutableStateOf("")
    var clientsOptions by mutableStateOf<List<Client>>(emptyList())
    var propertyOptions by mutableStateOf<List<Property>>(emptyList())
    var titleError by mutableStateOf(false)
    var clientError by mutableStateOf(false)
    var numberError by mutableStateOf(false)
    var ready by mutableStateOf(dealId == null)
    private val draftId = dealId ?: Ids.new()
    private var createdAt = 0L
    private var existed = false
    private var originalStage = DealStage.LEAD

    init {
        viewModelScope.launch { clients.observe("", null).collect { clientsOptions = it } }
        viewModelScope.launch { properties.observe("", null, null, null).collect { propertyOptions = it } }
        viewModelScope.launch {
            if (dealId == null) {
                val pref = settings.currencyCode.first()
                currency = pref.ifBlank { MoneyText.suggested(Locale.getDefault()) }
                ready = true
            } else {
                val existing = deals.get(dealId)
                if (existing != null && existing.deletedAt == null) {
                    existed = true
                    createdAt = existing.createdAt
                    title = existing.title
                    clientId = existing.clientId
                    propertyId = existing.propertyId
                    stage = existing.stage
                    originalStage = existing.stage
                    amount = if (existing.amount == 0L) "" else existing.amount.toString()
                    currency = existing.currency
                    expectedCloseAt = existing.expectedCloseAt
                    note = existing.note
                }
                ready = true
            }
        }
    }

    fun save(createdMessage: String, stageMessage: (String) -> String, stageLabel: (DealStage) -> String, onDone: (String, Boolean) -> Unit) {
        val parsed = Numbers.parseWhole(amount)
        if (title.isBlank()) {
            titleError = true
            return
        }
        if (clientId.isNullOrBlank()) {
            clientError = true
            return
        }
        if (parsed == null) {
            numberError = true
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val created = !existed
            deals.save(
                Deal(
                    id = draftId,
                    title = title.trim(),
                    clientId = clientId,
                    propertyId = propertyId,
                    stage = stage,
                    amount = parsed,
                    currency = currency,
                    expectedCloseAt = expectedCloseAt,
                    note = note.trim(),
                    createdAt = if (existed) createdAt else now,
                    updatedAt = now,
                ),
            )
            if (created) {
                activities.add(ActivityEntry(Ids.new(), ActivityKind.NOTE, createdMessage, clientId, draftId, propertyId, now, now))
            } else if (stage != originalStage) {
                activities.add(ActivityEntry(Ids.new(), ActivityKind.NOTE, stageMessage(stageLabel(stage)), clientId, draftId, propertyId, now, now))
                originalStage = stage
            }
            existed = true
            onDone(draftId, created)
        }
    }

    companion object {
        fun factory(container: AppContainer, dealId: String?, clientId: String?, propertyId: String?) = viewModelFactory {
            initializer {
                DealEditorViewModel(container.deals, container.clients, container.properties, container.activities, container.settings, dealId, clientId, propertyId)
            }
        }
    }
}

@Composable
fun DealEditorScreen(
    dealId: String?,
    presetClientId: String?,
    presetPropertyId: String?,
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: DealEditorViewModel = viewModel(
        factory = DealEditorViewModel.factory(container, dealId, presetClientId, presetPropertyId),
    )
    val createdMessage = stringResource(R.string.activity_created_deal)
    val stagePattern = stringResource(R.string.activity_stage)
    val stageLabels = DealStage.entries.associateWith { stringResource(it.labelRes()) }
    val noneProperty = stringResource(R.string.no_property)
    val pickClose = rememberDateTimePicker { vm.expectedCloseAt = it }
    CrmScaffold(
        title = stringResource(if (dealId == null) R.string.new_deal else R.string.edit_deal),
        onBack = onBack,
        actions = {
            TextButton(onClick = {
                vm.save(createdMessage, { stagePattern.format(it) }, { stageLabels[it].orEmpty() }, onSaved)
            }) { Text(stringResource(R.string.save)) }
        },
    ) { padding ->
        if (!vm.ready) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        val clientChoices = vm.clientsOptions.map { LinkChoice(it.id, it.fullName) }
        val propertyChoices = listOf(LinkChoice(null, noneProperty)) + vm.propertyOptions.map { LinkChoice(it.id, it.title) }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    vm.title,
                    { vm.title = it; vm.titleError = false },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.deal_title)) },
                    isError = vm.titleError,
                    supportingText = if (vm.titleError) { { Text(stringResource(R.string.title_required)) } } else null,
                    singleLine = true,
                )
            }
            item {
                CrmDropdown(
                    label = stringResource(R.string.deal_client),
                    valueLabel = clientChoices.firstOrNull { it.id == vm.clientId }?.label ?: stringResource(R.string.pick_client),
                    options = clientChoices,
                    optionLabel = { it.label },
                    onSelect = { vm.clientId = it.id; vm.clientError = false },
                )
                if (vm.clientError) Text(stringResource(R.string.client_required), color = MaterialTheme.colorScheme.error)
            }
            item {
                CrmDropdown(
                    label = stringResource(R.string.deal_property),
                    valueLabel = propertyChoices.firstOrNull { it.id == vm.propertyId }?.label ?: noneProperty,
                    options = propertyChoices,
                    optionLabel = { it.label },
                    onSelect = { vm.propertyId = it.id },
                )
            }
            item {
                CrmDropdown(stringResource(R.string.deal_stage), stageLabels[vm.stage].orEmpty(), DealStage.entries.toList(), { stageLabels[it].orEmpty() }, { vm.stage = it })
            }
            item {
                LtrTextField(
                    vm.amount,
                    { vm.amount = it.filter { ch -> ch.isDigit() }.take(12); vm.numberError = false },
                    stringResource(R.string.deal_amount),
                    isError = vm.numberError,
                    supportingText = if (vm.numberError) stringResource(R.string.number_invalid) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            item { CrmDropdown(stringResource(R.string.currency), vm.currency, MoneyText.currencies, { it }, { vm.currency = it }) }
            item {
                PickerField(
                    label = stringResource(R.string.deal_close),
                    value = dateTimeLabel(vm.expectedCloseAt),
                    onClick = pickClose,
                    onClear = if (vm.expectedCloseAt != null) { { vm.expectedCloseAt = null } } else null,
                )
            }
            item {
                OutlinedTextField(vm.note, { vm.note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.deal_note)) }, minLines = 3)
            }
        }
    }
}
