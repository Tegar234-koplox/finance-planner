package com.tegar.finance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.core.database.*
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.presentation.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ForecastContentUiTest {
    @get:Rule val compose = createComposeRule()
    private fun scroll(tag: String) {
        compose.onNodeWithTag("forecast_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun value(tag: String, expected: String) {
        scroll(tag)
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config[SemanticsProperties.Text].joinToString("") { text -> text.text } == expected
        } }
        compose.onNodeWithTag(tag).assertTextEquals(expected)
    }
    @Test fun renderedForecastsUseRoomRecordsAndUpdateMethodRulesAndSourceMonths() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "forecast-ui-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5, InvestmentMigrations.FROM_5_TO_6).addCallback(FinanceMigrations.CALLBACK).build()
        val store = ViewModelStore()
        fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
        val initial = Clock.fixed(Instant.parse("2028-01-01T05:00:00Z"), ZoneOffset.UTC)
        val now = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
        val book = LocalBookkeepingRepository(db, initial); val source = LocalBudgetRepository(db, initial)
        var expenseId = ""; var categoryId = ""
        try {
            runBlocking {
                LocalFoundationRepository(db, initial).initialize().ok()
                val categories = book.observeCategories().first().ok()
                categoryId = categories.first { it.transactionType == TransactionType.EXPENSE }.id
                val income = categories.first { it.transactionType == TransactionType.INCOME }.id
                book.saveTransaction(null, TransactionDraft(TransactionType.INCOME, Money(4000000), income, LocalDate.of(2028, 1, 10))).ok()
                book.saveTransaction(null, TransactionDraft(TransactionType.INCOME, Money(4000000), income, LocalDate.of(2028, 2, 10))).ok()
                book.saveTransaction(null, TransactionDraft(TransactionType.EXPENSE, Money(1000000), categoryId, LocalDate.of(2028, 1, 10))).ok()
                expenseId = book.saveTransaction(null, TransactionDraft(TransactionType.EXPENSE, Money(3000000), categoryId, LocalDate.of(2028, 2, 10))).ok()
                source.saveMonthly(YearMonth.of(2028, 4), Money(2100000)).ok()
            }
            val model = ForecastViewModel(source, now, SavedStateHandle()).also { store.put("forecast", it) }
            val dark = mutableStateOf(false)
            compose.setContent {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                    ForecastScreen(model.state.collectAsStateWithLifecycle().value, model)
                }
            }
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("forecast_1_consumption").fetchSemanticsNodes().isNotEmpty() || model.state.value.forecast != null }
            value("forecast_1_consumption", "Consumption: " + MoneyText.format(Money(2000000)))
            value("forecast_3_consumption", "Consumption: " + MoneyText.format(Money(6000000)))
            scroll("forecast_evidence"); compose.onNodeWithTag("forecast_evidence").assertTextEquals("Evidence: MEDIUM")
            compose.onNodeWithTag("forecast_list").performScrollToIndex(0)
            compose.onNodeWithTag("forecast_method_WEIGHTED").performClick()
            value("forecast_1_consumption", "Consumption: " + MoneyText.format(Money(2333333)))
            value("forecast_3_consumption", "Consumption: " + MoneyText.format(Money(7000000)))
            scroll("forecast_rule_NEXT_BUDGET_SHORTFALL")
            compose.onNodeWithTag("forecast_rule_NEXT_BUDGET_SHORTFALL").assertExists()
            scroll("forecast_source_toggle"); compose.onNodeWithTag("forecast_source_toggle").performClick()
            scroll("forecast_source_2028-02"); compose.onNodeWithText("2028-02 · complete · weight 2").assertIsDisplayed()
            runBlocking { book.saveTransaction(expenseId, TransactionDraft(TransactionType.EXPENSE, Money(6000000), categoryId, LocalDate.of(2028, 2, 10))).ok() }
            value("forecast_1_consumption", "Consumption: " + MoneyText.format(Money(4333333)))
            compose.runOnIdle { dark.value = true }
            value("forecast_3_consumption", "Consumption: " + MoneyText.format(Money(13000000)))
            runBlocking { book.deleteTransaction(expenseId).ok() }
            value("forecast_1_consumption", "Consumption: " + MoneyText.format(Money(333333)))
            compose.onNodeWithTag("forecast_list").performScrollToIndex(0)
            compose.onNodeWithTag("forecast_method_ROLLING").performClick()
            value("forecast_1_consumption", "Consumption: " + MoneyText.format(Money(500000)))
        } finally { compose.runOnIdle { store.clear() }; db.close(); context.deleteDatabase(name) }
    }
}
