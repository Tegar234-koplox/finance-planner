package com.tegar.finance

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.data.local.entity.*
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class BudgetStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2028-02-01T05:00:00Z"), ZoneOffset.UTC)
    private val month = YearMonth.of(2028, 2)
    private fun open(name: String) = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
        .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5).addCallback(FinanceMigrations.CALLBACK).build()
    private fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("Expected database constraint rejection") } catch (_: SQLiteException) { }
    }
    @Test fun allocationsAreDurableAndCapEditsAreAtomic() = runBlocking {
        val name = "budget-crud-${UUID.randomUUID()}.db"; var db = open(name)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            var repo = LocalBudgetRepository(db, clock)
            val book = LocalBookkeepingRepository(db, clock)
            val cats = book.observeCategories().first().ok().filter { it.transactionType == TransactionType.EXPENSE }
            repo.saveMonthly(month, Money(10000)).ok()
            repo.saveCategory(month, cats[0].id, Money(6000)).ok()
            assertEquals(AppResult.Failure(AppError.BUDGET_LIMIT_EXCEEDED), repo.saveCategory(month, cats[1].id, Money(4001)))
            repo.saveCategory(month, cats[1].id, Money(4000)).ok()
            assertEquals(AppResult.Failure(AppError.BUDGET_LIMIT_EXCEEDED), repo.saveMonthly(month, Money(9999)))
            val before = repo.observeBudgets().first().ok()!!
            val firstId = before.categories.first { it.categoryId == cats[0].id }.id
            repo.saveCategory(month, cats[0].id, Money(5000)).ok()
            assertEquals(firstId, repo.observeBudgets().first().ok()!!.categories.first { it.categoryId == cats[0].id }.id)
            assertEquals(AppResult.Failure(AppError.CATEGORY_IN_USE), book.deleteCategory(cats[0].id))
            book.setCategoryActive(cats[0].id, false).ok()
            repo.saveCategory(month, cats[0].id, Money(4500)).ok()
            assertEquals(AppResult.Failure(AppError.CATEGORY_INACTIVE), repo.saveCategory(month.plusMonths(1), cats[0].id, Money(1)))
            db.close(); db = open(name); repo = LocalBudgetRepository(db, clock)
            val reopened = repo.observeBudgets().first().ok()!!
            assertEquals(10000L, reopened.monthly.single().allocation.minorUnits)
            assertEquals(2, reopened.categories.size)
            repo.deleteMonthly(month).ok()
            assertEquals(2, repo.observeBudgets().first().ok()!!.categories.size)
            repo.deleteCategory(month, cats[0].id).ok()
            repo.deleteCategory(month, cats[1].id).ok()
            assertTrue(repo.observeBudgets().first().ok()!!.categories.isEmpty())
            repo.saveMonthly(month, Money(0)).ok()
            assertEquals(AppResult.Failure(AppError.NOT_FOUND), repo.deleteCategory(month, "missing"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun consumptionRecomputesAfterTransactionCreateEditDeleteAndCategoryArchive() = runBlocking {
        val name = "budget-actuals-${UUID.randomUUID()}.db"; val db = open(name)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            val repo = LocalBudgetRepository(db, clock); val book = LocalBookkeepingRepository(db, clock)
            val cats = book.observeCategories().first().ok()
            val food = cats.first { it.transactionType == TransactionType.EXPENSE }
            val other = cats.last { it.transactionType == TransactionType.EXPENSE }
            val income = cats.first { it.transactionType == TransactionType.INCOME }
            val investment = cats.first { it.transactionType == TransactionType.INVESTMENT }
            repo.saveMonthly(month, Money(1000)).ok(); repo.saveCategory(month, food.id, Money(500)).ok()
            val draft = TransactionDraft(TransactionType.EXPENSE, Money(200), food.id, month.atDay(1))
            val tx = book.saveTransaction(null, draft).ok()
            book.saveTransaction(null, draft.copy(categoryId = other.id, amount = Money(100))).ok()
            book.saveTransaction(null, draft.copy(type = TransactionType.INCOME, categoryId = income.id, amount = Money(9999))).ok()
            book.saveTransaction(null, draft.copy(type = TransactionType.INVESTMENT, categoryId = investment.id, amount = Money(9999))).ok()
            fun metrics(s: BudgetSnapshot, id: String? = null) = BudgetCalculator.calculate(month, month.atDay(1), s.ledger.trackedFromDate,
                s.ledger.currency, s.monthly.single().allocation, s.transactions, id).ok()
            assertEquals(300L, metrics(repo.observeBudgets().first().ok()!!).actual!!.minorUnits)
            book.saveTransaction(tx, draft.copy(amount = Money(400))).ok()
            book.setCategoryActive(food.id, false).ok()
            assertEquals(500L, metrics(repo.observeBudgets().first().ok()!!).actual!!.minorUnits)
            book.deleteTransaction(tx).ok()
            assertEquals(100L, metrics(repo.observeBudgets().first().ok()!!).actual!!.minorUnits)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun concurrentAllocationsAndVeryLargeSumsNeverBypassTheCap() = runBlocking {
        val name = "budget-limits-${UUID.randomUUID()}.db"; val db = open(name)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            val repo = LocalBudgetRepository(db, clock)
            val cats = LocalBookkeepingRepository(db, clock).observeCategories().first().ok()
            val expenses = cats.filter { it.transactionType == TransactionType.EXPENSE }
            repo.saveMonthly(month, Money(100)).ok()
            val writes = coroutineScope { expenses.take(2).map { async { repo.saveCategory(month, it.id, Money(60)) } }.awaitAll() }
            assertEquals(1, writes.count { it is AppResult.Success })
            assertEquals(1, writes.count { it == AppResult.Failure(AppError.BUDGET_LIMIT_EXCEEDED) })
            repo.deleteMonthly(month).ok()
            for (cat in expenses.take(2)) repo.saveCategory(month, cat.id, Money(Long.MAX_VALUE)).ok()
            assertEquals(AppResult.Failure(AppError.BUDGET_LIMIT_EXCEEDED), repo.saveMonthly(month, Money(Long.MAX_VALUE)))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveMonthly(month, Money(-1)))
            assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), repo.saveMonthly(month, Money(1, CurrencyCode("USD"))))
            assertEquals(AppResult.Failure(AppError.CATEGORY_MISMATCH), repo.saveCategory(month,
                cats.first { it.transactionType == TransactionType.INCOME }.id, Money(1)))
            repo.saveMonthly(month.plusMonths(1), Money(10)).ok()
            assertEquals(2, repo.observeBudgets().first().ok()!!.categories.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun databaseGuardsProtectMonthsAmountsExpenseTypesAndOverallCap() = runBlocking {
        val name = "budget-guards-${UUID.randomUUID()}.db"; val db = open(name)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            val cats = LocalBookkeepingRepository(db, clock).observeCategories().first().ok()
            val expense = cats.first { it.transactionType == TransactionType.EXPENSE }
            val income = cats.first { it.transactionType == TransactionType.INCOME }
            val cap = MonthlyBudgetEntity("overall", month = month.toString(), allocationMinor = 100, createdAt = 1, updatedAt = null)
            reject { db.budgetDao().insertMonthly(cap.copy(month = "2028-13")) }
            reject { db.budgetDao().insertMonthly(cap.copy(allocationMinor = -1)) }
            db.budgetDao().insertMonthly(cap)
            val category = CategoryBudgetEntity("cat", month = month.toString(), categoryId = expense.id,
                allocationMinor = 60, createdAt = 1, updatedAt = null)
            reject { db.budgetDao().insertCategory(category.copy(categoryId = income.id, transactionType = "INCOME")) }
            reject { db.budgetDao().insertCategory(category.copy(allocationMinor = 101)) }
            db.budgetDao().insertCategory(category)
            reject { db.budgetDao().updateMonthly(cap.copy(allocationMinor = 59)) }
            reject { db.budgetDao().updateCategory(category.copy(allocationMinor = 101)) }
            reject { db.transactionDao().deleteCategory(expense.id) }
            assertEquals(60L, db.budgetDao().categories(month.toString()).single().allocationMinor)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
