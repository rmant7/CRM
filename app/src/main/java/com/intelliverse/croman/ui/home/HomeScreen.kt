package com.intelliverse.croman.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intelliverse.croman.R
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.ui.Routes
import com.intelliverse.croman.ui.components.Badge
import com.intelliverse.croman.ui.components.CrmScaffold
import com.intelliverse.croman.ui.components.LoadingBox
import com.intelliverse.croman.ui.components.SectionTitle
import com.intelliverse.croman.ui.components.l10n

@Composable
fun HomeScreen(
    workspaceId: String,
    template: TemplateFile,
    onSettings: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onOpenEntity: (String) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val store = LocalAppContainer.current.store
    val summary by store.observeHome(workspaceId, template).collectAsStateWithLifecycle(initialValue = null)
    CrmScaffold(stringResource(R.string.greeting), actions = {
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings)) }
    }) { padding ->
        val data = summary
        if (data == null) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.made_for), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(data.contacts.toString(), stringResource(R.string.dashboard_contacts), { onOpenRoute(Routes.CONTACTS) }, Modifier.weight(1f))
                    Stat(data.activeListings.toString(), stringResource(R.string.dashboard_listings), {
                        val route = template.menu.firstOrNull { it.route.startsWith("entities:") }?.route?.substringAfter(":")
                        if (route != null) onOpenRoute(Routes.entities(route))
                    }, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(data.openDeals.toString(), stringResource(R.string.dashboard_deals), {
                        val route = template.entityTypes.firstOrNull { it.pipeline != null }?.key
                            ?: template.menu.firstOrNull { it.route.startsWith("entities:") }?.route?.substringAfter(":")
                        if (route != null) onOpenRoute(Routes.entities(route))
                    }, Modifier.weight(1f))
                    Stat(
                        data.dueToday.toString(),
                        stringResource(R.string.dashboard_today),
                        { onOpenRoute(Routes.TASKS) },
                        Modifier.weight(1f),
                        if (data.overdue > 0) stringResource(R.string.overdue_count, data.overdue) else null,
                    )
                }
            }
            if (data.contacts == 0 && data.activeListings == 0) {
                item {
                    Text(stringResource(R.string.empty_home_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.empty_home_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (data.stages.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.pipeline)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data.stages, key = { it.key }) { stage ->
                            Card(onClick = { onOpenRoute(Routes.entities("deal")) }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(stage.count.toString(), style = MaterialTheme.typography.titleLarge)
                                    Text(l10n(stage.label), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.upcoming)) }
            if (data.upcoming.isEmpty()) item { Text(stringResource(R.string.no_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(data.upcoming, key = { it.id }) { task ->
                Card(onClick = { onOpenTask(task.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.recent_deals)) }
            if (data.recentDeals.isEmpty()) item { Text(stringResource(R.string.no_recent)) }
            items(data.recentDeals, key = { it.id }) { deal ->
                Card(onClick = { onOpenEntity(deal.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(deal.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (deal.stageKey != null) Badge(deal.stageKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, onClick: () -> Unit, modifier: Modifier, extra: String? = null) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = if (extra != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(label)
            if (extra != null) Text(extra, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
    }
}
