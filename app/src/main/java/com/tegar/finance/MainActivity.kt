package com.tegar.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.tegar.finance.core.ui.FinanceTheme
import com.tegar.finance.core.ui.AppLocalization
import com.tegar.finance.presentation.FinanceApp
import com.tegar.finance.presentation.FoundationViewModel
import com.tegar.finance.presentation.BookkeepingViewModel
import com.tegar.finance.presentation.BudgetViewModel
import com.tegar.finance.presentation.HomeViewModel
import com.tegar.finance.presentation.ReportsViewModel
import com.tegar.finance.presentation.ForecastViewModel
import com.tegar.finance.presentation.PortfolioViewModel
import com.tegar.finance.presentation.WealthViewModel
import com.tegar.finance.presentation.GoalsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: FoundationViewModel by viewModels()
    private val bookkeeping: BookkeepingViewModel by viewModels()
    private val budgets: BudgetViewModel by viewModels()
    private val home: HomeViewModel by viewModels()
    private val reports: ReportsViewModel by viewModels()
    private val forecast: ForecastViewModel by viewModels()
    private val portfolio: PortfolioViewModel by viewModels()
    private val wealth: WealthViewModel by viewModels()
    private val goals: GoalsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val records by bookkeeping.state.collectAsStateWithLifecycle()
            val budgetState by budgets.state.collectAsStateWithLifecycle()
            val homeState by home.state.collectAsStateWithLifecycle()
            val reportsState by reports.state.collectAsStateWithLifecycle()
            val forecastState by forecast.state.collectAsStateWithLifecycle()
            val portfolioState by portfolio.state.collectAsStateWithLifecycle()
            val wealthState by wealth.state.collectAsStateWithLifecycle()
            val goalsState by goals.state.collectAsStateWithLifecycle()
            AppLocalization(state.language) { FinanceTheme(state.theme, onDarkTheme = { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }) { FinanceApp(state, viewModel::retry, viewModel::setTheme, records, bookkeeping, budgetState, budgets, homeState, home, reportsState, reports, forecastState, forecast, viewModel::setLanguage, goalsState, goals, wealthState, wealth, portfolioState, portfolio) } }
        }
    }
}
