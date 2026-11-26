package com.mantelmakher.crm.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.mantelmakher.crm.domain.logic.DashboardCalculator
import com.mantelmakher.crm.domain.model.DashboardSummary
import com.mantelmakher.crm.domain.model.TaskFilter
import com.mantelmakher.crm.domain.repository.ClientRepository
import com.mantelmakher.crm.domain.repository.DealRepository
import com.mantelmakher.crm.domain.repository.PropertyRepository
import com.mantelmakher.crm.domain.repository.TaskRepository
import com.mantelmakher.crm.ui.components.CrmScaffold
import com.mantelmakher.crm.ui.components.LoadingBox
import com.mantelmakher.crm.ui.components.SectionTitle
import com.mantelmakher.crm.ui.components.StageBadge
import com.mantelmakher.crm.ui.format.dateTimeLabel
import com.mantelmakher.crm.ui.format.labelRes
import com.mantelmakher.crm.ui.format.moneyLabel
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    clients: ClientRepository,
    properties: PropertyRepository,
    deals: DealRepository,
    tasks: TaskRepository,
) : ViewModel() {
    val summary: StateFlow<DashboardSummary?> = combine(
        clients.observe("", null),
        properties.observe("", null, null, null),
        deals.observe("", null),
        tasks.observe(TaskFilter.ALL, ""),
    ) { clientRows, propertyRows, dealRows, taskRows ->
        DashboardCalculator.summarize(
            clientRows,
            propertyRows,
            dealRows,
            taskRows,
            System.currentTimeMillis(),
            ZoneId.systemDefault(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                DashboardViewModel(container.clients, container.properties, container.deals, container.tasks)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    onSettings: () -> Unit,
    onClients: () -> Unit,
    onProperties: () -> Unit,
    onDeals: () -> Unit,
    onTasks: () -> Unit,
    onOpenDeal: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onAddClient: () -> Unit,
    onAddProperty: () -> Unit,
    onAddDeal: () -> Unit,
    onAddTask: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val summary by vm.summary.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    CrmScaffold(
        title = stringResource(R.string.greeting),
        actions = {
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (menuOpen) {
                    ExtendedFloatingActionButton(
                        onClick = { menuOpen = false; onAddClient() },
                        text = { Text(stringResource(R.string.add_client)) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                    ExtendedFloatingActionButton(
                        onClick = { menuOpen = false; onAddProperty() },
                        text = { Text(stringResource(R.string.add_property)) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                    ExtendedFloatingActionButton(
                        onClick = { menuOpen = false; onAddDeal() },
                        text = { Text(stringResource(R.string.add_deal)) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                    ExtendedFloatingActionButton(
                        onClick = { menuOpen = false; onAddTask() },
                        text = { Text(stringResource(R.string.add_task)) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
                FloatingActionButton(onClick = { menuOpen = !menuOpen }) {
                    Icon(
                        if (menuOpen) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = stringResource(if (menuOpen) R.string.cd_close else R.string.cd_add),
                    )
                }
            }
        },
    ) { padding ->
        val data = summary
        if (data == null) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.made_for),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(data.clientCount.toString(), stringResource(R.string.dashboard_clients), false, onClients, Modifier.weight(1f))
                    StatCard(data.activeListings.toString(), stringResource(R.string.dashboard_listings), false, onProperties, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(data.openDeals.toString(), stringResource(R.string.dashboard_deals), false, onDeals, Modifier.weight(1f))
                    StatCard(
                        data.dueToday.toString(),
                        stringResource(R.string.dashboard_today),
                        data.overdue > 0,
                        onTasks,
                        Modifier.weight(1f),
                        supporting = if (data.overdue > 0) stringResource(R.string.overdue_count, data.overdue) else null,
                    )
                }
            }
            if (data.clientCount == 0 && data.activeListings == 0 && data.openDeals == 0) {
                item {
                    Text(
                        stringResource(R.string.empty_dashboard_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.empty_dashboard_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionTitle(stringResource(R.string.pipeline)) }
            item {
                if (data.openDeals == 0 && data.stages.all { it.count == 0 }) {
                    Text(stringResource(R.string.pipeline_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data.stages, key = { it.stage.name }) { stage ->
                            Card(onClick = onDeals) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                    Text(stage.count.toString(), style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(stage.stage.labelRes()), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.upcoming)) }
            if (data.upcoming.isEmpty()) {
                item { Text(stringResource(R.string.no_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(data.upcoming, key = { it.id }) { task ->
                    Card(onClick = { onOpenTask(task.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(dateTimeLabel(task.dueAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.recent_deals)) }
            if (data.recentDeals.isEmpty()) {
                item { Text(stringResource(R.string.no_recent), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(data.recentDeals, key = { it.deal.id }) { item ->
                    Card(onClick = { onOpenDeal(item.deal.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(item.deal.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                StageBadge(item.deal.stage)
                            }
                            val subtitle = listOfNotNull(item.clientName, item.propertyTitle).joinToString(" · ")
                            if (subtitle.isNotBlank()) {
                                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(moneyLabel(item.deal.amount, item.deal.currency))
                        }
                    }
                }
            }
            item { TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) } }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    emphasis: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (emphasis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (supporting != null) {
                Text(supporting, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
