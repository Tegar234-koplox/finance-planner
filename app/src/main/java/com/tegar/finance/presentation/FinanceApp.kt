package com.tegar.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.tegar.finance.R
import com.tegar.finance.core.ui.EmptyState

private enum class Destination(val route: String, val label: Int, val title: Int, val empty: Int, val body: Int, val icon: ImageVector) {
    HOME("home", R.string.home, R.string.home_title, R.string.home_empty, R.string.home_body, Icons.Default.Home),
    BUDGET("budget", R.string.budget, R.string.budget_title, R.string.budget_empty, R.string.budget_body, Icons.AutoMirrored.Filled.List),
    PLAN("plan", R.string.plan, R.string.plan_title, R.string.plan_empty, R.string.plan_body, Icons.Default.Star),
    MARKET("market", R.string.market, R.string.market_title, R.string.market_empty, R.string.market_body, Icons.Default.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(state: AppUiState, retry: () -> Unit, onTheme: (com.tegar.finance.domain.model.ThemeMode) -> Unit) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val inSettings = entry?.destination?.route == "settings"
    val ready = state.status is FoundationStatus.Ready
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(if (inSettings) R.string.settings else R.string.app_name)) },
            navigationIcon = {
                if (inSettings) IconButton(onClick = { controller.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }, actions = {
                if (!inSettings) IconButton(onClick = { controller.navigate("settings") { launchSingleTop = true } }, enabled = ready) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                }
            })
    }, bottomBar = {
        if (!inSettings) NavigationBar {
            Destination.entries.forEach { target ->
                NavigationBarItem(selected = entry?.destination?.route == target.route, enabled = ready,
                    onClick = { controller.navigate(target.route) {
                        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    } }, icon = { Icon(target.icon, null) }, label = { Text(stringResource(target.label)) },
                    modifier = Modifier.testTag("nav_${target.route}"))
            }
        }
    }) { padding ->
        val content = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        when (state.status) {
            FoundationStatus.Loading -> Box(content, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("loading"))
            }
            FoundationStatus.StorageError -> Column(content.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.storage_error), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = retry) { Text(stringResource(R.string.retry)) }
            }
            is FoundationStatus.Ready -> NavHost(controller, startDestination = "home", modifier = content.testTag("app_ready")) {
                Destination.entries.forEach { target ->
                    composable(target.route) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text(stringResource(target.title), style = MaterialTheme.typography.headlineMedium)
                            Card(Modifier.fillMaxWidth()) {
                                EmptyState(target.icon, stringResource(target.empty), stringResource(target.body))
                            }
                            Text(stringResource(R.string.offline_note), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                composable("settings") { SettingsScreen(state, onTheme) }
            }
        }
    }
}
