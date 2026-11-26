package com.mantelmakher.crm.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mantelmakher.crm.R
import com.mantelmakher.crm.ui.clients.ClientDetailScreen
import com.mantelmakher.crm.ui.clients.ClientEditorScreen
import com.mantelmakher.crm.ui.clients.ClientsScreen
import com.mantelmakher.crm.ui.dashboard.DashboardScreen
import com.mantelmakher.crm.ui.deals.DealDetailScreen
import com.mantelmakher.crm.ui.deals.DealEditorScreen
import com.mantelmakher.crm.ui.deals.DealsScreen
import com.mantelmakher.crm.ui.properties.PropertiesScreen
import com.mantelmakher.crm.ui.properties.PropertyDetailScreen
import com.mantelmakher.crm.ui.properties.PropertyEditorScreen
import com.mantelmakher.crm.ui.settings.SettingsScreen
import com.mantelmakher.crm.ui.tasks.TaskEditorScreen
import com.mantelmakher.crm.ui.tasks.TasksScreen

object Routes {
    const val HOME = "home"
    const val CLIENTS = "clients"
    const val CLIENT = "client/{id}"
    const val CLIENT_EDIT = "client_edit?id={id}"
    const val PROPERTIES = "properties"
    const val PROPERTY = "property/{id}"
    const val PROPERTY_EDIT = "property_edit?id={id}"
    const val DEALS = "deals"
    const val DEAL = "deal/{id}"
    const val DEAL_EDIT = "deal_edit?id={id}&clientId={clientId}&propertyId={propertyId}"
    const val TASKS = "tasks"
    const val TASK_EDIT = "task_edit?id={id}&clientId={clientId}&dealId={dealId}&propertyId={propertyId}"
    const val SETTINGS = "settings"

    fun client(id: String) = "client/$id"
    fun clientEdit(id: String? = null) = "client_edit?id=${id.orEmpty()}"
    fun property(id: String) = "property/$id"
    fun propertyEdit(id: String? = null) = "property_edit?id=${id.orEmpty()}"
    fun deal(id: String) = "deal/$id"
    fun dealEdit(id: String? = null, clientId: String? = null, propertyId: String? = null) =
        "deal_edit?id=${id.orEmpty()}&clientId=${clientId.orEmpty()}&propertyId=${propertyId.orEmpty()}"

    fun taskEdit(
        id: String? = null,
        clientId: String? = null,
        dealId: String? = null,
        propertyId: String? = null,
    ) = "task_edit?id=${id.orEmpty()}&clientId=${clientId.orEmpty()}&dealId=${dealId.orEmpty()}&propertyId=${propertyId.orEmpty()}"
}

private val topLevel = setOf(Routes.HOME, Routes.CLIENTS, Routes.PROPERTIES, Routes.DEALS, Routes.TASKS)

@Composable
fun CrmRoot(initialTaskId: String?) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    LaunchedEffect(initialTaskId) {
        val id = initialTaskId ?: return@LaunchedEffect
        nav.navigate(Routes.taskEdit(id)) { launchSingleTop = true }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route in topLevel) {
                CrmBottomBar(route, nav::goTop)
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                DashboardScreen(
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onClients = { nav.goTop(Routes.CLIENTS) },
                    onProperties = { nav.goTop(Routes.PROPERTIES) },
                    onDeals = { nav.goTop(Routes.DEALS) },
                    onTasks = { nav.goTop(Routes.TASKS) },
                    onOpenDeal = { nav.navigate(Routes.deal(it)) },
                    onOpenTask = { nav.navigate(Routes.taskEdit(it)) },
                    onAddClient = { nav.navigate(Routes.clientEdit()) },
                    onAddProperty = { nav.navigate(Routes.propertyEdit()) },
                    onAddDeal = { nav.navigate(Routes.dealEdit()) },
                    onAddTask = { nav.navigate(Routes.taskEdit()) },
                )
            }
            composable(Routes.CLIENTS) {
                ClientsScreen(
                    onOpen = { nav.navigate(Routes.client(it)) },
                    onCreate = { nav.navigate(Routes.clientEdit()) },
                )
            }
            composable(
                route = Routes.CLIENT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                ClientDetailScreen(
                    clientId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.clientEdit(it)) },
                    onOpenDeal = { nav.navigate(Routes.deal(it)) },
                    onOpenTask = { nav.navigate(Routes.taskEdit(it)) },
                    onAddDeal = { nav.navigate(Routes.dealEdit(clientId = it)) },
                    onAddTask = { nav.navigate(Routes.taskEdit(clientId = it)) },
                )
            }
            composable(
                route = Routes.CLIENT_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                ClientEditorScreen(
                    clientId = entry.arguments.arg("id"),
                    onBack = { nav.popBackStack() },
                    onSaved = { id, created ->
                        nav.popBackStack()
                        if (created) nav.navigate(Routes.client(id))
                    },
                )
            }
            composable(Routes.PROPERTIES) {
                PropertiesScreen(
                    onOpen = { nav.navigate(Routes.property(it)) },
                    onCreate = { nav.navigate(Routes.propertyEdit()) },
                )
            }
            composable(
                route = Routes.PROPERTY,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                PropertyDetailScreen(
                    propertyId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.propertyEdit(it)) },
                    onOpenDeal = { nav.navigate(Routes.deal(it)) },
                    onOpenTask = { nav.navigate(Routes.taskEdit(it)) },
                    onAddDeal = { nav.navigate(Routes.dealEdit(propertyId = it)) },
                    onAddTask = { nav.navigate(Routes.taskEdit(propertyId = it)) },
                )
            }
            composable(
                route = Routes.PROPERTY_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                PropertyEditorScreen(
                    propertyId = entry.arguments.arg("id"),
                    onBack = { nav.popBackStack() },
                    onSaved = { id, created ->
                        nav.popBackStack()
                        if (created) nav.navigate(Routes.property(id))
                    },
                )
            }
            composable(Routes.DEALS) {
                DealsScreen(
                    onOpen = { nav.navigate(Routes.deal(it)) },
                    onCreate = { nav.navigate(Routes.dealEdit()) },
                )
            }
            composable(
                route = Routes.DEAL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                DealDetailScreen(
                    dealId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.dealEdit(id = it)) },
                    onOpenClient = { nav.navigate(Routes.client(it)) },
                    onOpenProperty = { nav.navigate(Routes.property(it)) },
                    onOpenTask = { nav.navigate(Routes.taskEdit(it)) },
                    onAddTask = { dealId, clientId, propertyId ->
                        nav.navigate(Routes.taskEdit(clientId = clientId, dealId = dealId, propertyId = propertyId))
                    },
                )
            }
            composable(
                route = Routes.DEAL_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("clientId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("propertyId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                DealEditorScreen(
                    dealId = entry.arguments.arg("id"),
                    presetClientId = entry.arguments.arg("clientId"),
                    presetPropertyId = entry.arguments.arg("propertyId"),
                    onBack = { nav.popBackStack() },
                    onSaved = { id, created ->
                        nav.popBackStack()
                        if (created) nav.navigate(Routes.deal(id))
                    },
                )
            }
            composable(Routes.TASKS) {
                TasksScreen(
                    onOpen = { nav.navigate(Routes.taskEdit(it)) },
                    onCreate = { nav.navigate(Routes.taskEdit()) },
                )
            }
            composable(
                route = Routes.TASK_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("clientId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("dealId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("propertyId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                TaskEditorScreen(
                    taskId = entry.arguments.arg("id"),
                    presetClientId = entry.arguments.arg("clientId"),
                    presetDealId = entry.arguments.arg("dealId"),
                    presetPropertyId = entry.arguments.arg("propertyId"),
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
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

@Composable
private fun CrmBottomBar(route: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        BottomItem(Routes.HOME, route, Icons.Filled.Home, R.string.nav_home, onSelect)
        BottomItem(Routes.CLIENTS, route, Icons.Filled.People, R.string.nav_clients, onSelect)
        BottomItem(Routes.PROPERTIES, route, Icons.Filled.Apartment, R.string.nav_properties, onSelect)
        BottomItem(Routes.DEALS, route, Icons.Filled.Handshake, R.string.nav_deals, onSelect)
        BottomItem(Routes.TASKS, route, Icons.Filled.TaskAlt, R.string.nav_tasks, onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomItem(
    target: String,
    current: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    onSelect: (String) -> Unit,
) {
    NavigationBarItem(
        selected = current == target,
        onClick = { onSelect(target) },
        icon = { Icon(icon, contentDescription = stringResource(label)) },
        label = { Text(stringResource(label), maxLines = 1) },
    )
}
