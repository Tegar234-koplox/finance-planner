package com.tegar.finance

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InvestmentStorageTest {
    private val date = LocalDate.of(2028, 3, 10)
    private val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
    private val draft = HoldingDraft("  My   fund  ", "FUND", AllocationBucket.FUND, date.minusYears(1), Money(50000))
    private fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
    @Test fun linkedContributionsAndRedemptionsCommitDeclaredBalancesOnceAndPersistWithoutChangingGoalEarmarks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "investment-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            var repo = LocalInvestmentRepository(db, clock); var wealth = LocalWealthRepository(db, clock)
            val book = LocalBookkeepingRepository(db, clock); val budgets = LocalBudgetRepository(db, clock)
            val categories = book.observeCategories().first().ok()
            fun cat(type: TransactionType) = categories.first { it.transactionType == type }.id
            book.saveTransaction(null, TransactionDraft(TransactionType.INCOME, Money(10000), cat(TransactionType.INCOME), date)).ok()
            LocalGoalRepository(db, budgets, clock).saveGoal(null, GoalDraft("Trip", GoalKind.SAVING, Money(5000), Money(3000), date, date.plusMonths(2), GoalPriority.NORMAL, GoalState.ACTIVE)).ok()
            val goal = budgets.observeBudgets().first().ok()!!.goals.single()
            val id = repo.saveHolding(null, draft, HoldingValueDraft("", date, Money(10000))).ok()
            suspend fun source() = repo.observePortfolio().first().ok()!!
            assertEquals("My fund", source().holdings.single().name)
            val contributed = repo.saveMovement(null, TransactionDraft(TransactionType.INVESTMENT, Money(2000), cat(TransactionType.INVESTMENT), date,
                "Deposit", InvestmentAction.CONTRIBUTION, id), Money(12000)).ok()
            val redeemed = repo.saveMovement(null, TransactionDraft(TransactionType.INVESTMENT, Money(3000), cat(TransactionType.INVESTMENT), date,
                "Redeem", InvestmentAction.REDEMPTION, id), Money(9000)).ok()
            val p = PortfolioCalculator.calculate(source(), date).ok()
            assertEquals(Money(11000), p.cash); assertEquals(Money(9000), p.valuation.total); assertEquals(Money(20000), p.total)
            assertEquals(Money(20000), WealthCalculator.calculate(wealth.observeWealth().first().ok()!!, date).ok().total)
            assertEquals(goal, budgets.observeBudgets().first().ok()!!.goals.single())
            val actual = HomeDashboardCalculator.calculate(budgets.observeBudgets().first().ok()!!, date).ok()
            assertEquals(Money(11000), actual.recordedCash); assertEquals(Money(10000), actual.monthIncome)
            val targets = AllocationBucket.entries.map { AllocationTarget(it, if (it == AllocationBucket.CASH) 5500 else if (it == AllocationBucket.FUND) 4500 else 0) }
            repo.saveTargets(targets).ok(); repo.savePlan(YearMonth.from(date), Money(4000)).ok()
            val capture = wealth.captureToday().ok(); val frozen = wealth.observeWealth().first().ok()!!.captures.single()
            assertEquals(Money(9000), frozen.investments); assertEquals(1, frozen.investmentLines.size)
            db.close(); db = open(); repo = LocalInvestmentRepository(db, clock); wealth = LocalWealthRepository(db, clock)
            val reopened = PortfolioCalculator.calculate(source(), date).ok()
            assertEquals(Money(20000), reopened.total); assertEquals(6, source().targets.size)
            assertEquals(Money(4000), reopened.progress.target); assertEquals(Money(2000), reopened.progress.contributed); assertEquals(Money(3000), reopened.progress.redeemed)
            assertEquals(AppResult.Failure(AppError.INVESTMENT_IN_USE), repo.deleteHolding(id))
            repo.saveHolding(id, draft.copy(name = "Renamed", active = false)).ok()
            assertEquals(Money(11000), PortfolioCalculator.calculate(source(), date).ok().total)
            assertEquals(frozen, wealth.observeWealth().first().ok()!!.captures.single())
            repo.deleteMovement(contributed).ok(); repo.deleteMovement(redeemed).ok()
            assertEquals(Money(9000), source().values.single().amount) // A cash correction never fabricates a valuation reversal.
            repo.deleteHolding(id).ok(); assertTrue(source().values.isEmpty())
            assertEquals(frozen, wealth.observeWealth().first().ok()!!.captures.single())
            wealth.deleteCapture(capture).ok(); assertEquals(0, db.openHelper.readableDatabase.query("SELECT * FROM wealth_capture_investment").use { it.count })
            assertEquals(0, db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { it.count })
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun invalidFlowAndAllocationWritesPreserveBothCashAndValuationsAtomically() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).addCallback(FinanceMigrations.CALLBACK).build()
        try {
            LocalFoundationRepository(db, clock).initialize().ok(); val repo = LocalInvestmentRepository(db, clock)
            suspend fun source() = repo.observePortfolio().first().ok()!!
            val id = repo.saveHolding(null, draft, HoldingValueDraft("", date, Money(0))).ok()
            val before = source(); val category = before.categories.first { it.transactionType == TransactionType.INVESTMENT }.id
            val cashDraft = TransactionDraft(TransactionType.INVESTMENT, Money(1000), category, date, investmentAction = InvestmentAction.REDEMPTION, holdingId = id)
            for ((bad, after) in listOf(cashDraft to Money(-1), cashDraft to null, cashDraft.copy(date = date.plusDays(1)) to Money(0),
                cashDraft.copy(amount = Money(0)) to Money(0), cashDraft.copy(type = TransactionType.INCOME) to Money(0))) {
                assertTrue(repo.saveMovement(null, bad, after) is AppResult.Failure); assertEquals(before, source())
            }
            assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), repo.saveMovement(null, cashDraft, Money(1, CurrencyCode("USD")))); assertEquals(before, source())
            val targets = AllocationBucket.entries.map { AllocationTarget(it, if (it == AllocationBucket.CASH) 10000 else 0) }
            repo.saveTargets(targets).ok()
            assertEquals(AppResult.Failure(AppError.ALLOCATION_TOTAL), repo.saveTargets(targets.map { if (it.bucket == AllocationBucket.FUND) it.copy(basisPoints = 1) else it }))
            assertEquals(targets.toSet(), source().targets.toSet())
            assertEquals(AppResult.Failure(AppError.WEALTH_DATE_CONFLICT), repo.saveValue(null, HoldingValueDraft(id, date, Money(5))))
            val value = source().values.single(); repo.deleteValue(value.id).ok()
            assertNull(PortfolioCalculator.calculate(source(), date).ok().total)
            assertEquals(AppResult.Failure(AppError.WEALTH_INCOMPLETE), LocalWealthRepository(db, clock).captureToday())
            repo.saveHolding(id, draft.copy(active = false)).ok(); assertEquals(Money(0), PortfolioCalculator.calculate(source(), date).ok().total)
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveMovement(null, cashDraft, Money(0)))
            repo.savePlan(YearMonth.from(date), Money(0)).ok(); assertNull(PortfolioCalculator.calculate(source(), date).ok().progress.percent)
            repo.saveTargets(emptyList()).ok(); repo.savePlan(YearMonth.from(date), null).ok()
            assertTrue(source().targets.isEmpty()); assertTrue(source().plans.isEmpty())
        } finally { db.close() }
    }
    @Test fun sqlGuardsProtectActionOwnershipAmountsAndFrozenInvestmentComponents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).addCallback(FinanceMigrations.CALLBACK).build()
        try {
            LocalFoundationRepository(db, clock).initialize().ok(); val repo = LocalInvestmentRepository(db, clock)
            val id = repo.saveHolding(null, draft, HoldingValueDraft("", date, Money(1000))).ok()
            val category = repo.observePortfolio().first().ok()!!.categories.first { it.transactionType == TransactionType.INVESTMENT }.id
            repo.saveMovement(null, TransactionDraft(TransactionType.INVESTMENT, Money(100), category, date, investmentAction = InvestmentAction.CONTRIBUTION, holdingId = id), Money(1100)).ok()
            val snapshot = LocalWealthRepository(db, clock).captureToday().ok()
            val sql = db.openHelper.writableDatabase
            val invalid = listOf("UPDATE investment_holding SET bucket='CASH'", "UPDATE investment_holding SET costMinor=-1", "UPDATE investment_holding SET acquiredOn=${date.plusDays(1).toEpochDay()}",
                "UPDATE investment_value SET valueMinor=-1", "UPDATE investment_value SET valueMinor=1.5", "UPDATE investment_value SET date=${draft.acquiredOn.minusDays(1).toEpochDay()}",
                "UPDATE finance_transaction SET investmentAction='DIVIDEND'", "UPDATE finance_transaction SET holdingId='missing'", "UPDATE wealth_capture_investment SET valueMinor=0", "UPDATE wealth_capture SET investmentsMinor=0")
            for (statement in invalid) { try { sql.execSQL(statement); fail(statement) } catch (_: SQLiteException) { } }
            LocalWealthRepository(db, clock).deleteCapture(snapshot).ok()
            assertEquals(0, sql.query("SELECT * FROM wealth_capture_investment").use { it.count })
            assertEquals(0, sql.query("PRAGMA foreign_key_check").use { it.count })
        } finally { db.close() }
    }
}
