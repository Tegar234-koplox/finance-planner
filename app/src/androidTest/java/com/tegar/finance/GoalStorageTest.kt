package com.tegar.finance

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.data.local.entity.GoalEntity
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class GoalStorageTest {
    @Test fun goalWritesReserveCashWithoutTransactionsAndSurviveReopenWhileSourceEditsStayAuthoritative() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "goal-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5)
            .addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
        val initial = Clock.fixed(Instant.parse("2028-01-01T05:00:00Z"), ZoneOffset.UTC)
        val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
        val today = LocalDate.of(2028, 3, 10)
        try {
            LocalFoundationRepository(db, initial).initialize().ok()
            var book = LocalBookkeepingRepository(db, clock); var source = LocalBudgetRepository(db, clock)
            var goals = LocalGoalRepository(db, source, clock)
            val categories = book.observeCategories().first().ok()
            fun category(type: TransactionType) = categories.first { it.transactionType == type }.id
            suspend fun record(type: TransactionType, amount: Long, date: String) = book.saveTransaction(null,
                TransactionDraft(type, Money(amount), category(type), LocalDate.parse(date), "Keep source note")).ok()
            for (month in listOf("2028-01", "2028-02")) {
                record(TransactionType.INCOME, 1000, "$month-10"); record(TransactionType.EXPENSE, 800, "$month-10")
                record(TransactionType.INVESTMENT, 100, "$month-10")
            }
            val income = record(TransactionType.INCOME, 10000, "2028-03-01")
            source.saveMonthly(YearMonth.of(2028, 3), Money(10000)).ok()
            suspend fun snapshot() = source.observeBudgets().first().ok()!!
            val original = snapshot()
            val draft = GoalDraft("  My   laptop  ", GoalKind.PURCHASE, Money(5000), Money(4000), today,
                LocalDate.of(2028, 6, 30), GoalPriority.HIGH, GoalState.ACTIVE)
            val id = goals.saveGoal(null, draft).ok()
            var saved = snapshot()
            assertEquals("My laptop", saved.goals.single().name)
            assertEquals(original.transactions, saved.transactions); assertEquals(original.ledger, saved.ledger)
            val home = HomeDashboardCalculator.calculate(saved, today).ok()
            assertEquals(Money(10200), home.recordedCash); assertEquals(Money(4000), home.reservedForGoals)
            assertEquals(Money(6200), home.unreservedCash); assertEquals(DailyLimitReason.GOAL_RESERVED, home.dailyLimitReason)
            val creation = saved.goals.single().createdAt
            goals.saveGoal(id, draft.copy(earmarked = Money(7000), state = GoalState.PAUSED)).ok()
            assertEquals(original.transactions, snapshot().transactions)
            db.close(); db = open(); book = LocalBookkeepingRepository(db, clock); source = LocalBudgetRepository(db, clock)
            goals = LocalGoalRepository(db, source, clock); saved = snapshot()
            assertEquals(id, saved.goals.single().id); assertEquals(creation, saved.goals.single().createdAt)
            assertEquals(GoalState.PAUSED, saved.goals.single().state)
            assertEquals(GoalFunding.OVERFUNDED, GoalPlanner.calculate(saved, today).ok().goals.single().funding)
            assertEquals(original.transactions, saved.transactions)
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), goals.saveGoal(id, draft.copy(asOf = today.plusDays(1))))
            assertEquals(AppResult.Failure(AppError.DATE_BEFORE_COVERAGE), goals.saveGoal(id, draft.copy(asOf = LocalDate.of(2027, 12, 31))))
            assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), goals.saveGoal(id, draft.copy(target = Money(1, CurrencyCode("USD")))))
            assertEquals(Money(7000), snapshot().goals.single().earmarked)
            book.saveTransaction(income, TransactionDraft(TransactionType.INCOME, Money(1000), category(TransactionType.INCOME), LocalDate.of(2028, 3, 1), "Keep source note")).ok()
            val changed = snapshot(); val plan = GoalPlanner.calculate(changed, today).ok()
            assertEquals(false, plan.reservationsBacked); assertEquals(Money(0), plan.unreservedCash)
            assertEquals(GoalDateReason.RESERVATIONS_EXCEED_CASH, plan.goals.single().dateReason)
            assertEquals(Money(0), HomeDashboardCalculator.calculate(changed, today).ok().dailyLimit)
            goals.deleteGoal(id).ok(); assertTrue(snapshot().goals.isEmpty())
            assertEquals(7, snapshot().transactions.size)
            assertEquals(AppResult.Failure(AppError.NOT_FOUND), goals.deleteGoal(id))
            assertEquals(AppResult.Failure(AppError.NOT_FOUND), goals.saveGoal(id, draft))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun freshDatabaseGuardsRejectInvalidGoalsEvenOutsideRepositoryAndNeverCreateMoneyRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).addCallback(FinanceMigrations.CALLBACK).build()
        val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
        try {
            LocalFoundationRepository(db, clock).initialize()
            val day = LocalDate.of(2028, 3, 10).toEpochDay()
            val valid = GoalEntity("valid", name = "Test", kind = "PURCHASE", targetMinor = 100, earmarkedMinor = 150,
                asOfDate = day, targetDate = day - 1, priority = "HIGH", state = "ACTIVE", createdAt = 1, updatedAt = null)
            db.goalDao().insert(valid)
            val invalid = listOf(valid.copy(id = "bad-target", targetMinor = 0), valid.copy(id = "bad-earmark", earmarkedMinor = -1),
                valid.copy(id = "bad-kind", kind = "OTHER"), valid.copy(id = "bad-priority", priority = "URGENT"),
                valid.copy(id = "bad-state", state = "DONE"), valid.copy(id = "bad-name", name = " "),
                valid.copy(id = "bad-coverage", asOfDate = day - 1), valid.copy(id = "bad-date", targetDate = 2932897),
                valid.copy(id = "bad-ledger", ledgerId = 2))
            for (row in invalid) try { db.goalDao().insert(row); fail("Must reject ${row.id}") } catch (_: SQLiteException) { }
            try { db.openHelper.writableDatabase.execSQL("UPDATE finance_goal SET targetMinor=-1 WHERE id='valid'"); fail("Invalid update") }
            catch (_: SQLiteException) { }
            try { db.openHelper.writableDatabase.execSQL("DELETE FROM ledger WHERE id=1"); fail("Goal foreign key must protect ledger") }
            catch (_: SQLiteException) { }
            assertEquals(150L, db.goalDao().get("valid")!!.earmarkedMinor)
            val source = LocalBudgetRepository(db, clock)
            val snapshot = (source.observeBudgets().first() as AppResult.Success).value!!
            assertTrue(snapshot.transactions.isEmpty()); assertEquals(Money(0), snapshot.ledger.openingCash)
            // Future as-of is clock/zone validation, enforced by repository and calculator, not SQLite wall time.
            db.goalDao().update(valid.copy(asOfDate = day + 1))
            val corrupted = (source.observeBudgets().first() as AppResult.Success).value!!
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), GoalPlanner.calculate(corrupted, LocalDate.of(2028, 3, 10)))
        } finally { db.close() }
    }
}
