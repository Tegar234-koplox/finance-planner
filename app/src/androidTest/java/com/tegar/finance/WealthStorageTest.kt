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

class WealthStorageTest {
    private val today = LocalDate.of(2028, 3, 10)
    private val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
    private val draft = WealthItemDraft("  My   vehicle  ", WealthKind.ASSET, WealthCategory.VEHICLE, today.minusYears(1), Money(200000))
    private fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
    @Test fun sourceEditsArchiveAndDeletionNeverRewriteCapturedComponentsOrMoveCashAndGoals() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "wealth-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        try {
            LocalFoundationRepository(db, Clock.fixed(clock.instant().minusSeconds(86400 * 90), ZoneOffset.UTC)).initialize().ok()
            val book = LocalBookkeepingRepository(db, clock); val budgets = LocalBudgetRepository(db, clock)
            val category = book.observeCategories().first().ok().first { it.transactionType == TransactionType.INCOME }.id
            val income = book.saveTransaction(null, TransactionDraft(TransactionType.INCOME, Money(100000), category, today)).ok()
            budgets.saveMonthly(YearMonth.from(today), Money(100000)).ok()
            LocalGoalRepository(db, budgets, clock).saveGoal(null, GoalDraft("Trip", GoalKind.SAVING, Money(90000), Money(50000), today, today.plusMonths(1), GoalPriority.NORMAL, GoalState.ACTIVE)).ok()
            val baseline = budgets.observeBudgets().first().ok()!!
            var repo = LocalWealthRepository(db, clock)
            val id = repo.saveItem(null, draft, WealthValueDraft("", today.minusDays(5), Money(150000))).ok()
            val liability = repo.saveItem(null, draft.copy(name = "Loan", kind = WealthKind.LIABILITY, category = WealthCategory.LOAN, acquisitionCost = null), WealthValueDraft("", today, Money(20000))).ok()
            suspend fun source() = repo.observeWealth().first().ok()!!
            assertEquals("My vehicle", source().items.first { it.id == id }.name)
            val capture = repo.captureToday().ok(); val frozen = source().captures.single()
            assertEquals(Money(230000), frozen.total); assertEquals(2, frozen.lines.size)
            assertEquals(AppResult.Failure(AppError.SNAPSHOT_EXISTS), repo.captureToday())
            val old = source().values.first { it.itemId == id }
            repo.saveValue(old.id, WealthValueDraft(id, old.date, Money(120000), "Correction")).ok()
            repo.saveValue(null, WealthValueDraft(id, today, Money(100000))).ok()
            repo.saveItem(id, draft.copy(name = "Renamed", active = false)).ok()
            assertEquals(Money(80000), WealthCalculator.calculate(source(), today).ok().total)
            assertEquals(frozen, source().captures.single())
            assertEquals(baseline, budgets.observeBudgets().first().ok())
            repo.deleteItem(id).ok(); repo.deleteItem(liability).ok()
            assertTrue(source().values.isEmpty()); assertEquals(frozen, source().captures.single())
            book.saveTransaction(income, TransactionDraft(TransactionType.INCOME, Money(200000), category, today)).ok()
            assertEquals(Money(200000), WealthCalculator.calculate(source(), today).ok().total)
            assertEquals(Money(100000), source().captures.single().cash)
            db.close(); db = open(); repo = LocalWealthRepository(db, clock)
            assertEquals(frozen, source().captures.single()); assertTrue(source().items.isEmpty())
            repo.deleteCapture(capture).ok(); assertTrue(source().captures.isEmpty())
            repo.captureToday().ok(); assertEquals(Money(200000), source().captures.single().total)
            assertTrue(db.openHelper.readableDatabase.query("SELECT * FROM wealth_capture_line").use { it.count == 0 })
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun invalidAndConflictingMutationsAreAtomicAndDeletingLastValueMakesTotalUnknown() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).addCallback(FinanceMigrations.CALLBACK).build()
        try {
            LocalFoundationRepository(db, clock).initialize().ok(); val repo = LocalWealthRepository(db, clock)
            suspend fun source() = repo.observeWealth().first().ok()!!
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveItem(null, draft, WealthValueDraft("", today.plusDays(1), Money(1))))
            assertTrue(source().items.isEmpty()); assertTrue(source().values.isEmpty())
            val id = repo.saveItem(null, draft, WealthValueDraft("", today, Money(0))).ok()
            val initial = source().values.single()
            assertEquals(Money(0), WealthCalculator.calculate(source(), today).ok().total)
            assertEquals(AppResult.Failure(AppError.WEALTH_DATE_CONFLICT), repo.saveValue(null, WealthValueDraft(id, today, Money(1))))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveValue(initial.id, WealthValueDraft(id, today.plusDays(1), Money(1))))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveValue(initial.id, WealthValueDraft(id, draft.acquiredOn.minusDays(1), Money(1))))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveItem(id, draft.copy(kind = WealthKind.LIABILITY, category = WealthCategory.LOAN, acquisitionCost = null)))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveItem(id, draft.copy(acquiredOn = today.plusDays(1))))
            assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), repo.saveValue(null, WealthValueDraft(id, today.minusDays(1), Money(1, CurrencyCode("USD")))))
            val previous = repo.saveValue(null, WealthValueDraft(id, today.minusDays(1), Money(500))).ok()
            assertEquals(AppResult.Failure(AppError.WEALTH_DATE_CONFLICT), repo.saveValue(previous, WealthValueDraft(id, today, Money(1))))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveItem(id, draft.copy(acquiredOn = today)))
            assertEquals(initial, source().values.first { it.id == initial.id })
            repo.deleteValue(initial.id).ok(); assertEquals(Money(500), WealthCalculator.calculate(source(), today).ok().total)
            repo.deleteValue(previous).ok(); assertNull(WealthCalculator.calculate(source(), today).ok().total)
            assertEquals(AppResult.Failure(AppError.WEALTH_INCOMPLETE), repo.captureToday())
            assertEquals(AppResult.Failure(AppError.NOT_FOUND), repo.deleteValue(previous))
            assertEquals(AppResult.Failure(AppError.NOT_FOUND), repo.saveItem("missing", draft))
        } finally { db.close() }
    }
    @Test fun sqlGuardsRejectInvalidValuesAndProtectCapturedRowsWhileSourceDeletionCascadesOnlyValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).addCallback(FinanceMigrations.CALLBACK).build()
        try {
            LocalFoundationRepository(db, clock).initialize().ok(); val repo = LocalWealthRepository(db, clock)
            val id = repo.saveItem(null, draft, WealthValueDraft("", today, Money(100))).ok()
            val capture = repo.captureToday().ok(); val sql = db.openHelper.writableDatabase
            val invalid = listOf("UPDATE wealth_item SET kind='LIABILITY',category='LOAN',costMinor=NULL", "UPDATE wealth_item SET category='CARD'",
                "UPDATE wealth_item SET name=''", "UPDATE wealth_item SET costMinor=-1", "UPDATE wealth_item SET acquiredOn=${today.plusDays(1).toEpochDay()}",
                "UPDATE wealth_value SET valueMinor=-1", "UPDATE wealth_value SET valueMinor=1.5", "UPDATE wealth_value SET date=${draft.acquiredOn.minusDays(1).toEpochDay()}",
                "UPDATE wealth_capture SET totalMinor=0", "UPDATE wealth_capture_line SET name='Rewritten'")
            for (statement in invalid) { try { sql.execSQL(statement); fail(statement) } catch (_: SQLiteException) { } }
            repo.deleteItem(id).ok()
            val remaining = repo.observeWealth().first().ok()!!
            assertTrue(remaining.items.isEmpty()); assertTrue(remaining.values.isEmpty()); assertEquals(1, remaining.captures.single().lines.size)
            repo.deleteCapture(capture).ok()
            assertEquals(0, sql.query("SELECT * FROM wealth_capture_line").use { it.count })
            assertEquals(0, sql.query("PRAGMA foreign_key_check").use { it.count })
        } finally { db.close() }
    }
}
