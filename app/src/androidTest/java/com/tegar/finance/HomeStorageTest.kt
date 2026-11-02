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

class HomeStorageTest {
    @Test fun dashboardFollowsPersistedRecordsBudgetEditsAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "home-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5).addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
        val clock = Clock.fixed(Instant.parse("2028-02-01T05:00:00Z"), ZoneOffset.UTC)
        val month = YearMonth.of(2028, 2); val today = month.atDay(1)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            var book = LocalBookkeepingRepository(db, clock); var source = LocalBudgetRepository(db, clock)
            val cats = book.observeCategories().first().ok()
            val food = cats.first { it.transactionType == TransactionType.EXPENSE }
            val income = cats.first { it.transactionType == TransactionType.INCOME }
            val investment = cats.first { it.transactionType == TransactionType.INVESTMENT }
            book.saveTransaction(null, TransactionDraft(TransactionType.INCOME, Money(10000), income.id, today)).ok()
            val expense = book.saveTransaction(null, TransactionDraft(TransactionType.EXPENSE, Money(2000), food.id, today)).ok()
            book.saveTransaction(null, TransactionDraft(TransactionType.INVESTMENT, Money(3000), investment.id, today)).ok()
            source.saveMonthly(month, Money(4000)).ok()
            suspend fun home() = HomeDashboardCalculator.calculate(source.observeBudgets().first().ok()!!, today).ok()
            val before = home()
            assertEquals(5000L, before.recordedCash!!.minorUnits); assertEquals(8000L, before.monthSaving!!.minorUnits)
            assertEquals(2000L / 29, before.dailyLimit!!.minorUnits)
            source.saveMonthly(month, Money(20000)).ok()
            assertEquals(DailyLimitReason.CASH_LIMITED, home().dailyLimitReason)
            book.saveTransaction(expense, TransactionDraft(TransactionType.EXPENSE, Money(4000), food.id, today)).ok()
            book.saveCategory(food.id, "Edited food", TransactionType.EXPENSE).ok()
            book.setCategoryActive(food.id, false).ok()
            assertEquals(3000L, home().recordedCash!!.minorUnits); assertEquals(4000L, home().budget.actual!!.minorUnits)
            db.close(); db = open(); book = LocalBookkeepingRepository(db, clock); source = LocalBudgetRepository(db, clock)
            assertEquals(3000L, home().recordedCash!!.minorUnits)
            assertEquals("Edited food", book.observeCategories().first().ok().first { it.id == food.id }.name)
            book.deleteTransaction(expense).ok()
            assertEquals(7000L, home().recordedCash!!.minorUnits); assertEquals(0L, home().budget.actual!!.minorUnits)
            source.deleteMonthly(month).ok()
            assertNull(home().dailyLimit)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
