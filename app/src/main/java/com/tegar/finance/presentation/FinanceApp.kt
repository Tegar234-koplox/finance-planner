package com.tegar.finance.presentation

import androidx.compose.foundation.layout.*
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

private enum class Destination(val route: String, val label: Int, val title: Int, val empty: Int, val body: Int, val icon: ImageVector) {
    HOME("home", R.string.home, R.string.home_title, R.string.home_empty, R.string.home_body, Icons.Default.Home),
    BUDGET("budget", R.string.budget, R.string.budget_title, R.string.budget_empty, R.string.budget_body, Icons.AutoMirrored.Filled.List),
    PLAN("plan", R.string.plan, R.string.plan_title, R.string.plan_empty, R.string.plan_body, Icons.Default.Star),
    MARKET("market", R.string.market, R.string.market_title, R.string.market_empty, R.string.market_body, Icons.Default.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(state: AppUiState, retry: () -> Unit, onTheme: (com.tegar.finance.domain.model.ThemeMode) -> Unit,
    records: BookkeepingState, bookkeeping: BookkeepingViewModel, budgets: BudgetUiState, budgetModel: BudgetViewModel,
    home: HomeUiState, homeModel: HomeViewModel, reports: ReportsUiState, reportsModel: ReportsViewModel,
    forecast: ForecastUiState, forecastModel: ForecastViewModel,
    onLanguage: (com.tegar.finance.domain.model.AppLanguage) -> Unit,
    goals: GoalsUiState, goalsModel: GoalsViewModel, wealth: WealthUiState, wealthModel: WealthViewModel, portfolio: PortfolioUiState, portfolioModel: PortfolioViewModel,
    market: MarketUiState, marketModel: MarketViewModel) {
    LaunchedEffect(forecast.options) { goalsModel.useForecastOptions(forecast.options) }
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val inSettings = entry?.destination?.route == "settings"
    val inDetail = entry?.destination?.route in listOf("settings", "transactions", "categories", "reports", "goals", "wealth", "investments")
    val ready = state.status is FoundationStatus.Ready
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(if (inSettings) R.string.settings else R.string.app_name)) },
            navigationIcon = {
                if (inDetail) IconButton(onClick = { controller.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }, actions = {
                if (!inSettings) IconButton(onClick = { controller.navigate("settings") { launchSingleTop = true } }, enabled = ready) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                }
            })
    }, bottomBar = {
        if (!inDetail) NavigationBar {
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
                        if (target == Destination.HOME) HomeScreen(home, homeModel,
                            transactions = { controller.navigate("transactions") },
                            categories = { controller.navigate("categories") },
                            reports = { controller.navigate("reports") { launchSingleTop = true } },
                            goals = { controller.navigate("goals") { launchSingleTop = true } },
                            investments = { controller.navigate("investments") { launchSingleTop = true } },
                            wealth = { controller.navigate("wealth") { launchSingleTop = true } },
                            addTransaction = { bookkeeping.openTransaction(); controller.navigate("transactions") },
                            budget = {
                                home.dashboard?.month?.toString()?.let(budgetModel::selectMonth)
                                controller.navigate("budget") {
                                    popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            })
                        else if (target == Destination.BUDGET) BudgetScreen(budgets, budgetModel)
                        else if (target == Destination.PLAN) ForecastScreen(forecast, forecastModel,
                            openGoals = { controller.navigate("goals") { launchSingleTop = true } }) else
                        MarketScreen(market, marketModel::retry, marketModel::refresh, marketModel::recheckAge,
                            portfolio = { controller.navigate("investments") { launchSingleTop = true } })
                    }
                }
                composable("settings") { SettingsScreen(state, onTheme, onLanguage) }
                composable("transactions") { BookkeepingScreen(records, bookkeeping, categoryMode = false) }
                composable("categories") { BookkeepingScreen(records, bookkeeping, categoryMode = true) }
                composable("reports") { ReportsScreen(reports, reportsModel) }
                composable("wealth") { WealthScreen(wealth, wealthModel, openInvestments = { controller.navigate("investments") { launchSingleTop = true } }) }
                composable("investments") { PortfolioScreen(portfolio, portfolioModel) }
                composable("goals") { GoalsScreen(goals, goalsModel) }
            }
        }
    }
}
