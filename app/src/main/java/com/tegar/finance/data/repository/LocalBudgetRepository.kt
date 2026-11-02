package com.tegar.finance.data.repository

import androidx.room.withTransaction
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.data.local.entity.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.BudgetRepository
import java.math.BigInteger
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class LocalBudgetRepository @Inject constructor(private val database: FinanceDatabase, private val clock: Clock) : BudgetRepository {
    private val dao get() = database.budgetDao()
    // Room reads ledger/budgets/transactions in one transaction, avoiding torn totals from combined flows.
    override fun observeBudgets() = dao.observeSnapshot().map { source -> source?.let {
        val currency = CurrencyCode(it.ledger.currencyCode)
        BudgetSnapshot(Ledger(currency, ZoneId.of(it.ledger.reportingZoneId), LocalDate.ofEpochDay(it.ledger.trackedFromDate),
            Money(it.ledger.openingCashMinor, currency), Instant.ofEpochMilli(it.ledger.createdAt)),
            it.monthly.map { row -> MonthlyBudget(row.id, YearMonth.parse(row.month), Money(row.allocationMinor, currency)) },
            it.categories.map { row -> CategoryBudget(row.id, YearMonth.parse(row.month), row.categoryId, Money(row.allocationMinor, currency)) },
            it.transactions.map { row -> FinanceTransaction(row.id, TransactionType.valueOf(row.transactionType),
                Money(row.amountMinor, currency), row.categoryId, LocalDate.ofEpochDay(row.date), row.note,
                Instant.ofEpochMilli(row.createdAt), row.updatedAt?.let(Instant::ofEpochMilli)) },
            it.goals.map { row -> FinancialGoal(row.id, row.name, GoalKind.valueOf(row.kind), Money(row.targetMinor, currency),
                Money(row.earmarkedMinor, currency), LocalDate.ofEpochDay(row.asOfDate), LocalDate.ofEpochDay(row.targetDate),
                GoalPriority.valueOf(row.priority), GoalState.valueOf(row.state), Instant.ofEpochMilli(row.createdAt),
                row.updatedAt?.let(Instant::ofEpochMilli)) })
    } }.storageResults()
    override suspend fun saveMonthly(month: YearMonth, allocation: Money): AppResult<Unit> = atomic {
        validate(month, allocation)?.let { return@atomic AppResult.Failure(it) }
        val key = month.toString()
        if (sum(dao.categories(key)) > BigInteger.valueOf(allocation.minorUnits))
            return@atomic AppResult.Failure(AppError.BUDGET_LIMIT_EXCEEDED)
        val old = dao.monthly(key); val now = clock.instant().toEpochMilli()
        if (old == null) dao.insertMonthly(MonthlyBudgetEntity(UUID.randomUUID().toString(), month = key,
            allocationMinor = allocation.minorUnits, createdAt = now, updatedAt = null))
        else dao.updateMonthly(old.copy(allocationMinor = allocation.minorUnits, updatedAt = now))
        AppResult.Success(Unit)
    }
    override suspend fun saveCategory(month: YearMonth, categoryId: String, allocation: Money): AppResult<Unit> = atomic {
        validate(month, allocation)?.let { return@atomic AppResult.Failure(it) }
        val category = database.transactionDao().category(categoryId)
            ?: return@atomic AppResult.Failure(AppError.CATEGORY_MISMATCH)
        if (category.transactionType != TransactionType.EXPENSE.name) return@atomic AppResult.Failure(AppError.CATEGORY_MISMATCH)
        val key = month.toString(); val rows = dao.categories(key); val old = rows.firstOrNull { it.categoryId == categoryId }
        if (!category.active && old == null) return@atomic AppResult.Failure(AppError.CATEGORY_INACTIVE)
        val cap = dao.monthly(key)
        if (cap != null && sum(rows.filter { it.categoryId != categoryId }) + BigInteger.valueOf(allocation.minorUnits) >
            BigInteger.valueOf(cap.allocationMinor)) return@atomic AppResult.Failure(AppError.BUDGET_LIMIT_EXCEEDED)
        val now = clock.instant().toEpochMilli()
        if (old == null) dao.insertCategory(CategoryBudgetEntity(UUID.randomUUID().toString(), month = key,
            categoryId = categoryId, allocationMinor = allocation.minorUnits, createdAt = now, updatedAt = null))
        else dao.updateCategory(old.copy(allocationMinor = allocation.minorUnits, updatedAt = now))
        AppResult.Success(Unit)
    }
    override suspend fun deleteMonthly(month: YearMonth): AppResult<Unit> = atomic {
        if (!BudgetMonths.valid(month)) AppResult.Failure(AppError.INVALID_INPUT)
        else if (dao.deleteMonthly(month.toString()) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    override suspend fun deleteCategory(month: YearMonth, categoryId: String): AppResult<Unit> = atomic {
        if (!BudgetMonths.valid(month)) AppResult.Failure(AppError.INVALID_INPUT)
        else if (dao.deleteCategory(month.toString(), categoryId) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    private suspend fun validate(month: YearMonth, allocation: Money): AppError? {
        if (!BudgetMonths.valid(month) || allocation.minorUnits < 0) return AppError.INVALID_INPUT
        val ledger = database.foundationDao().ledger() ?: return AppError.STORAGE
        return if (ledger.currencyCode != allocation.currency.value) AppError.CURRENCY_MISMATCH else null
    }
    private fun sum(rows: List<CategoryBudgetEntity>) = rows.fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.allocationMinor) }
    private suspend fun <T> atomic(block: suspend () -> AppResult<T>): AppResult<T> =
        when (val result = storageOperation { database.withTransaction { block() } }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> result
        }
}
