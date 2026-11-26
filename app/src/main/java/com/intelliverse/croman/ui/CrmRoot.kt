package com.intelliverse.croman.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.intelliverse.croman.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.intelliverse.croman.domain.NavLabels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.AppLanguage
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.ui.components.LoadingBox
import com.intelliverse.croman.ui.components.l10n
import com.intelliverse.croman.ui.contacts.ContactDetailScreen
import com.intelliverse.croman.ui.contacts.ContactEditorScreen
import com.intelliverse.croman.ui.contacts.ContactsScreen
import com.intelliverse.croman.ui.entities.CatalogScreen
import com.intelliverse.croman.ui.entities.EntityDetailScreen
import com.intelliverse.croman.ui.entities.EntityEditorScreen
import com.intelliverse.croman.ui.entities.EntityListScreen
import com.intelliverse.croman.ui.home.HomeScreen
import com.intelliverse.croman.ui.settings.SettingsScreen
import com.intelliverse.croman.ui.tasks.TaskEditorScreen
import com.intelliverse.croman.ui.tasks.TasksScreen
import com.intelliverse.croman.ui.theme.LocalLanguage
import java.util.Locale

object Routes {
    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val CONTACT = "contact/{id}"
    const val CONTACT_EDIT = "contact_edit?id={id}"
    const val ENTITIES = "entities/{type}"
    const val ENTITY = "entity/{id}"
    const val ENTITY_EDIT = "entity_edit?type={type}&id={id}"
    const val TASKS = "tasks"
    const val TASK_EDIT = "task_edit?id={id}&contactId={contactId}&entityId={entityId}"
    const val CATALOG = "catalog"
    const val MORE = "more"
    const val SETTINGS = "settings"

    fun contact(id: String) = "contact/$id"
    fun contactEdit(id: String? = null) = "contact_edit?id=${id.orEmpty()}"
    fun entities(type: String) = "entities/$type"
    fun entity(id: String) = "entity/$id"
    fun entityEdit(type: String, id: String? = null) = "entity_edit?type=$type&id=${id.orEmpty()}"
    fun taskEdit(id: String? = null, contactId: String? = null, entityId: String? = null) =
        "task_edit?id=${id.orEmpty()}&contactId=${contactId.orEmpty()}&entityId=${entityId.orEmpty()}"
}

@Composable
fun CromanRoot(initialTaskId: String?) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val languageTag by container.settings.languageTag.collectAsStateWithLifecycle(initialValue = AppLanguage.SYSTEM)
    val workspaceId by container.settings.workspaceId.collectAsStateWithLifecycle(initialValue = "")
    val workspaces by container.store.observeWorkspaces().collectAsStateWithLifecycle(initialValue = null)
    val language = if (languageTag == AppLanguage.SYSTEM) Locale.getDefault().language else languageTag
    val known = workspaces
    val workspace = known?.firstOrNull { it.id == workspaceId } ?: known?.firstOrNull()
    val template = workspace?.let { runCatching { TemplateCatalog.effective(context, it) }.getOrNull() }
    CompositionLocalProvider(LocalLanguage provides AppLanguage.normalize(language).let { if (it == AppLanguage.SYSTEM) "en" else it }) {
        if (known == null) {
            LoadingBox()
            return@CompositionLocalProvider
        }
        if (workspace == null || template == null) {
            OnboardingScreen()
            return@CompositionLocalProvider
        }
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val primary = template.menu.take(3)
        val overflow = template.menu.drop(3)
        LaunchedEffect(initialTaskId) {
            val id = initialTaskId ?: return@LaunchedEffect
            nav.navigate(Routes.taskEdit(id)) { launchSingleTop = true }
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (route in setOf(Routes.HOME, Routes.CONTACTS, Routes.TASKS, Routes.CATALOG, Routes.MORE) || route?.startsWith("entities/") == true) {
                    NavigationBar {
                        primary.forEach { item ->
                            val target = item.destination()
                            val selected = route == target
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.goTop(target) },
                                icon = { Icon(iconFor(item.route), contentDescription = l10n(item.label)) },
                                alwaysShowLabel = true,
                                label = {
                                    Text(
                                        l10n(item.label),
                                        maxLines = NavLabels.MAX_LINES,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                                    )
                                },
                            )
                        }
                        if (overflow.isNotEmpty()) {
                            NavigationBarItem(
                                selected = route == Routes.MORE || overflow.any { route == it.destination() },
                                onClick = { nav.goTop(Routes.MORE) },
                                icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.cd_more)) },
                                label = { Text(stringResource(R.string.nav_more), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, Routes.HOME, Modifier.padding(padding)) {
                composable(Routes.HOME) {
                    HomeScreen(workspace.id, template, { nav.navigate(Routes.SETTINGS) }, { nav.goTop(it) }, { nav.navigate(Routes.entity(it)) }, { nav.navigate(Routes.taskEdit(it)) })
                }
                composable(Routes.CONTACTS) {
                    ContactsScreen(workspace.id, template, { nav.navigate(Routes.contact(it)) }, { nav.navigate(Routes.contactEdit()) }, com.intelliverse.croman.data.demo.DemoIds.idsFor(workspace.id, container.settings.demoIds.collectAsStateWithLifecycle(initialValue = emptySet()).value))
                }
                composable(Routes.CONTACT, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    ContactDetailScreen(
                        workspace.id, template, entry.arguments?.getString("id").orEmpty(),
                        { nav.popBackStack() },
                        { nav.navigate(Routes.contactEdit(it)) },
                        { nav.navigate(Routes.entity(it)) },
                        { nav.navigate(Routes.taskEdit(contactId = it)) },
                    )
                }
                composable(Routes.CONTACT_EDIT, listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) { entry ->
                    ContactEditorScreen(workspace.id, template, entry.arguments.arg("id"), { nav.popBackStack() }) { id, created ->
                        nav.popBackStack(); if (created) nav.navigate(Routes.contact(id))
                    }
                }
                composable(Routes.ENTITIES, listOf(navArgument("type") { type = NavType.StringType })) { entry ->
                    val type = entry.arguments?.getString("type").orEmpty()
                    EntityListScreen(workspace.id, template, type, { nav.navigate(Routes.entity(it)) }, { nav.navigate(Routes.entityEdit(type)) })
                }
                composable(Routes.ENTITY, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    EntityDetailScreen(
                        workspace.id, template, entry.arguments?.getString("id").orEmpty(),
                        { nav.popBackStack() },
                        { type, id -> nav.navigate(Routes.entityEdit(type, id)) },
                        { nav.navigate(Routes.contact(it)) },
                        { nav.navigate(Routes.entity(it)) },
                        { nav.navigate(Routes.taskEdit(entityId = it)) },
                    )
                }
                composable(
                    Routes.ENTITY_EDIT,
                    listOf(
                        navArgument("type") { type = NavType.StringType; defaultValue = "" },
                        navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    EntityEditorScreen(workspace.id, template, entry.arguments?.getString("type").orEmpty(), entry.arguments.arg("id"), { nav.popBackStack() }) { id, created ->
                        nav.popBackStack(); if (created) nav.navigate(Routes.entity(id))
                    }
                }
                composable(Routes.TASKS) {
                    TasksScreen(workspace.id, { nav.navigate(Routes.taskEdit(it)) }, { nav.navigate(Routes.taskEdit()) })
                }
                composable(
                    Routes.TASK_EDIT,
                    listOf(
                        navArgument("id") { type = NavType.StringType; defaultValue = "" },
                        navArgument("contactId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("entityId") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    TaskEditorScreen(
                        workspace.id,
                        entry.arguments.arg("id"),
                        entry.arguments.arg("contactId"),
                        entry.arguments.arg("entityId"),
                        { nav.popBackStack() },
                        { nav.popBackStack() },
                    )
                }
                composable(Routes.CATALOG) { CatalogScreen(workspace.id) }
                composable(Routes.MORE) {
                    MoreScreen(overflow.map { l10n(it.label) to it.destination() } + (stringResource(R.string.settings) to Routes.SETTINGS)) {
                        nav.navigate(it)
                    }
                }
                composable(Routes.SETTINGS) { SettingsScreen(workspace.id, template, { nav.popBackStack() }) }
            }
        }
    }
}

private fun android.os.Bundle?.arg(name: String): String? = this?.getString(name).orEmpty().ifBlank { null }

private fun NavHostController.goTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun com.intelliverse.croman.domain.MenuItem.destination(): String = when {
    route == "home" -> Routes.HOME
    route == "contacts" -> Routes.CONTACTS
    route == "tasks" -> Routes.TASKS
    route == "catalog" -> Routes.CATALOG
    route.startsWith("entities:") -> Routes.entities(route.substringAfter(":"))
    else -> Routes.HOME
}

@Composable
private fun MoreScreen(items: List<Pair<String, String>>, onOpen: (String) -> Unit) {
    com.intelliverse.croman.ui.components.CrmScaffold(stringResource(R.string.nav_more)) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 8.dp, vertical = 12.dp)) {
            items.forEach { (label, route) ->
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(route) }.padding(horizontal = 16.dp, vertical = 18.dp),
                )
            }
        }
    }
}

private fun iconFor(route: String): ImageVector = when {
    route == "home" -> Icons.Filled.Home
    route == "contacts" -> Icons.Filled.People
    route == "tasks" -> Icons.Filled.Checklist
    route == "catalog" -> Icons.Filled.School
    route.substringAfter(":") == "deal" -> Icons.Filled.Handshake
    route.substringAfter(":") == "lesson" -> Icons.Filled.School
    else -> Icons.Filled.Apartment
}

@Suppress("unused")
private val keepTemplate: TemplateFile? = null
