package com.tegar.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ForecastStorageTest {
    @Test fun forecastsFollowDurableEditsBudgetAndReopenWithoutPersistingEstimates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "forecast-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5).addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
        val clock = Clock.fixed(Instant.parse("2028-01-01T05:00:00Z"), ZoneOffset.UTC)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            var book = LocalBookkeepingRepository(db, clock); var source = LocalBudgetRepository(db, clock)
            val categories = book.observeCategories().first().ok()
            val expense = categories.first { it.transactionType == TransactionType.EXPENSE }
            val income = categories.first { it.transactionType == TransactionType.INCOME }
            suspend fun save(type: TransactionType, amount: Long, category: String, date: String) =
                book.saveTransaction(null, TransactionDraft(type, Money(amount), category, LocalDate.parse(date))).ok()
            save(TransactionType.INCOME, 10000, income.id, "2028-01-10")
            save(TransactionType.INCOME, 20000, income.id, "2028-02-10")
            save(TransactionType.EXPENSE, 1000, expense.id, "2028-01-10")
            val id = save(TransactionType.EXPENSE, 3000, expense.id, "2028-02-10")
            save(TransactionType.EXPENSE, 9000, expense.id, "2028-03-10")
            source.saveMonthly(YearMonth.of(2028, 4), Money(2100)).ok()
            suspend fun forecast(method: ForecastMethod = ForecastMethod.ROLLING) = ForecastCalculator.calculate(
                source.observeBudgets().first().ok()!!, LocalDate.of(2028, 3, 15), ForecastOptions(method)).ok()
            val before = source.observeBudgets().first().ok()!!
            assertEquals(2000L, forecast().horizons.first().totals!!.consumption.minorUnits)
            assertEquals(2333L, forecast(ForecastMethod.WEIGHTED).horizons.first().totals!!.consumption.minorUnits)
            assertTrue(forecast(ForecastMethod.WEIGHTED).rules.any { it.kind == ForecastRuleKind.NEXT_BUDGET_SHORTFALL })
            assertEquals(before, source.observeBudgets().first().ok()!!)
            book.saveTransaction(id, TransactionDraft(TransactionType.EXPENSE, Money(5000), expense.id, LocalDate.of(2028, 2, 10))).ok()
            book.saveCategory(expense.id, "Forecast archived expense", TransactionType.EXPENSE).ok()
            book.setCategoryActive(expense.id, false).ok()
            db.close(); db = open(); book = LocalBookkeepingRepository(db, clock); source = LocalBudgetRepository(db, clock)
            assertEquals(3000L, forecast().horizons.first().totals!!.consumption.minorUnits)
            assertEquals(9000L, forecast().horizons.last().totals!!.consumption.minorUnits)
            assertEquals(2, forecast().samples.size)
            assertEquals(5, db.openHelper.readableDatabase.version)
            book.deleteTransaction(id).ok()
            assertEquals(500L, forecast().horizons.first().totals!!.consumption.minorUnits)
            assertEquals(0L, forecast().samples.last().flows.consumption.minorUnits)
            source.saveMonthly(YearMonth.of(2028, 4), Money(500)).ok()
            assertFalse(forecast().rules.any { it.kind == ForecastRuleKind.NEXT_BUDGET_SHORTFALL })
            assertEquals(4, source.observeBudgets().first().ok()!!.transactions.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
