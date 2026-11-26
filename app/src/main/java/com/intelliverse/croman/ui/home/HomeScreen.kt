package com.intelliverse.croman.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intelliverse.croman.R
import com.intelliverse.croman.data.demo.DemoIds
import com.intelliverse.croman.data.demo.DemoSamples
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.Onboarding
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.ui.Routes
import com.intelliverse.croman.ui.components.CrmScaffold
import com.intelliverse.croman.ui.components.LoadingBox
import com.intelliverse.croman.ui.components.l10n
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    workspaceId: String,
    template: TemplateFile,
    onSettings: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onOpenEntity: (String) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val summary by container.store.observeHome(workspaceId, template).collectAsStateWithLifecycle(initialValue = null)
    val storedDemo by container.settings.demoIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val demoIds = DemoIds.idsFor(workspaceId, storedDemo)
    var menu by remember { mutableStateOf(false) }
    CrmScaffold(stringResource(R.string.greeting), actions = {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_settings))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                onClick = { menu = false; onSettings() },
            )
        }
    }) { padding ->
        val data = summary
        if (data == null) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        val empty = data.contacts == 0 && data.activeListings == 0 && data.recentDeals.isEmpty() && data.upcoming.isEmpty()
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text(l10n(Onboarding.homeLine(template.id)), style = MaterialTheme.typography.titleLarge)
            }
            if (demoIds.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.demo_badge), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.demo_banner), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        scope.launch {
                            container.store.eraseOwned(demoIds)
                            container.settings.forgetDemo(workspaceId, demoIds)
                        }
                    }) { Text(stringResource(R.string.delete_demo)) }
                }
            }
            if (empty) {
                item {
                    EmptyDesk(onAdd = { onOpenRoute(Routes.contactEdit()) }, onExample = {
                        scope.launch {
                            val created = DemoSamples.load(container.store, workspaceId, template)
                            container.settings.rememberDemo(workspaceId, created)
                        }
                    })
                }
            } else {
                item {
                    Button(onClick = { onOpenRoute(Routes.contactEdit()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add_first_client))
                    }
                }
                item {
                    Text(
                        stringResource(R.string.dashboard_contacts) + " " + data.contacts + "  ·  " +
                            stringResource(R.string.dashboard_today) + " " + data.dueToday,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (data.stages.isNotEmpty()) {
                item {
                    LazyRow(
                        Modifier.fillMaxWidth().height(88.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(data.stages, key = { it.key }) { stage ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(stage.count.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                Text(l10n(stage.label), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (data.upcoming.isNotEmpty()) {
                items(data.upcoming, key = { it.id }) { task ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenTask(task.id) }) {
                        Text(task.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (task.id in demoIds) Text(stringResource(R.string.demo_badge), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(data.recentDeals, key = { it.id }) { deal ->
                Card(onClick = { onOpenEntity(deal.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Text(deal.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            if (deal.id in demoIds) Text(stringResource(R.string.demo_badge), color = MaterialTheme.colorScheme.primary)
                        }
                        if (deal.subtitle.isNotBlank()) Text(deal.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyDesk(onAdd: () -> Unit, onExample: () -> Unit) {
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Text(stringResource(R.string.add_first_client), style = MaterialTheme.typography.titleMedium)
    }
    TextButton(onClick = onExample, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.see_example)) }
}
