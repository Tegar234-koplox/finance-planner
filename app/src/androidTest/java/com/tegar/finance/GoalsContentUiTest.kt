package com.tegar.finance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.core.ui.AppLocalization
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

class GoalsContentUiTest {
    @get:Rule val compose = createComposeRule()
    private fun value(tag: String, expected: String) {
        compose.onNodeWithTag("goals_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config[SemanticsProperties.Text].joinToString("") { text -> text.text } == expected
        } }
        compose.onNodeWithTag(tag).assertTextEquals(expected)
    }
    @Test fun completedHistorySharesCapacityAndDatesReactToPriorityCashAndLanguage() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "goal-ui-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5)
            .addCallback(FinanceMigrations.CALLBACK).build()
        val store = ViewModelStore()
        fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
        val initial = Clock.fixed(Instant.parse("2028-01-01T05:00:00Z"), ZoneOffset.UTC)
        val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
        val book = LocalBookkeepingRepository(db, clock); val source = LocalBudgetRepository(db, clock)
        val goals = LocalGoalRepository(db, source, clock)
        val today = LocalDate.of(2028, 3, 10)
        var firstId = ""; var secondId = ""; var incomeCategory = ""
        val first = GoalDraft("Trip funds", GoalKind.SAVING, Money(150000), Money(0), today, LocalDate.of(2028, 5, 31), GoalPriority.HIGH, GoalState.ACTIVE)
        try {
            runBlocking {
                LocalFoundationRepository(db, initial).initialize().ok()
                val categories = book.observeCategories().first().ok()
                fun category(type: TransactionType) = categories.first { it.transactionType == type }.id
                incomeCategory = category(TransactionType.INCOME)
                for (date in listOf(LocalDate.of(2028, 1, 10), LocalDate.of(2028, 2, 10))) {
                    for ((type, amount) in listOf(TransactionType.INCOME to 1000000L, TransactionType.EXPENSE to 800000L, TransactionType.INVESTMENT to 100000L))
                        book.saveTransaction(null, TransactionDraft(type, Money(amount), category(type), date)).ok()
                }
                book.saveTransaction(null, TransactionDraft(TransactionType.EXPENSE, Money(200000), category(TransactionType.EXPENSE), today)).ok()
                firstId = goals.saveGoal(null, first).ok()
                secondId = goals.saveGoal(null, first.copy(name = "Phone", kind = GoalKind.PURCHASE, priority = GoalPriority.NORMAL)).ok()
            }
            val model = GoalsViewModel(goals, clock).also { store.put("goals", it) }
            val language = mutableStateOf(AppLanguage.ENGLISH)
            compose.setContent { AppLocalization(language.value) { MaterialTheme {
                GoalsScreen(model.state.collectAsStateWithLifecycle().value, model)
            } } }
            compose.waitUntil(60_000) { model.state.value.plan != null }
            value("goal_ready_$firstId", "Scenario ready date: 2028-05-31")
            value("goal_ready_$secondId", "Scenario ready date: 2028-06-30")
            runBlocking { goals.saveGoal(firstId, first.copy(priority = GoalPriority.LOW)).ok() }
            compose.waitUntil(60_000) {
                val plan = model.state.value.plan
                plan?.goals?.firstOrNull()?.goal?.id == secondId &&
                    plan.goals.firstOrNull { it.goal.id == firstId }?.goal?.priority == GoalPriority.LOW
            }
            compose.waitForIdle()
            value("goal_ready_$secondId", "Scenario ready date: 2028-05-31")
            value("goal_ready_$firstId", "Scenario ready date: 2028-06-30")
            runBlocking { book.saveTransaction(null, TransactionDraft(TransactionType.INCOME, Money(100000), incomeCategory, today)).ok() }
            compose.waitUntil(60_000) { model.state.value.plan?.recordedCash == Money(100000) }
            compose.waitForIdle()
            value("goal_ready_$secondId", "Scenario ready date: 2028-04-30")
            value("goal_ready_$firstId", "Scenario ready date: 2028-05-31")
            compose.runOnIdle { model.useForecastOptions(ForecastOptions(ForecastMethod.WEIGHTED, 12)); language.value = AppLanguage.INDONESIAN }
            value("goal_ready_$firstId", "Tanggal siap dalam skenario: 2028-05-31")
            value("goal_progress_$firstId", "Progres dana tujuan: 0.00%")
            compose.onNodeWithTag("goals_list").performScrollToIndex(0)
            compose.onNodeWithText("Tujuan dan pembelian").assertIsDisplayed()
            assertEquals(8, runBlocking { (source.observeBudgets().first() as AppResult.Success).value!!.transactions.size })
        } finally { compose.runOnIdle { store.clear() }; db.close(); context.deleteDatabase(name) }
    }
}
