package com.tegar.finance.data.repository

import androidx.room.withTransaction
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.data.local.entity.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*

@Singleton
class LocalBookkeepingRepository @Inject constructor(
    private val database: FinanceDatabase, private val clock: Clock,
) : TransactionRepository, EditableCategoryRepository {
    private val dao get() = database.transactionDao()
    override fun observeCategories() = database.foundationDao().observeCategories()
        .map { rows -> rows.map { it.domain() } }.storageResults()
    override fun observeTransactions(filter: TransactionFilter): Flow<AppResult<List<FinanceTransaction>>> {
        if (!filter.valid) return flowOf(AppResult.Failure(AppError.INVALID_INPUT))
        // Currency has one authority: the singleton ledger, never a display preference.
        return combine(database.foundationDao().observeLedger(),
            dao.observe(filter.from?.toEpochDay(), filter.through?.toEpochDay(), filter.type?.name, filter.categoryId)
        ) { ledger, rows ->
            if (ledger == null) emptyList() else rows.map { it.domain(CurrencyCode(ledger.currencyCode)) }
        }.storageResults()
    }
    override suspend fun saveTransaction(id: String?, draft: TransactionDraft): AppResult<String> = atomic {
        val ledger = database.foundationDao().ledger() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        val previous = id?.let { dao.get(it) }
        if (id != null && previous == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        val currency = CurrencyCode(ledger.currencyCode)
        val account = Ledger(currency, ZoneId.of(ledger.reportingZoneId), LocalDate.ofEpochDay(ledger.trackedFromDate),
            Money(ledger.openingCashMinor, currency), Instant.ofEpochMilli(ledger.createdAt))
        val error = TransactionRules.validate(draft, account, dao.category(draft.categoryId)?.domain(), previous?.domain(currency))
        if (error != null) return@atomic AppResult.Failure(error)
        draft.holdingId?.let { key ->
            val holding = database.investmentDao().holding(key) ?: return@atomic AppResult.Failure(AppError.NOT_FOUND)
            if (draft.date.toEpochDay() < holding.acquiredOn || (!holding.active && previous?.holdingId != key))
                return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        }
        val now = clock.instant().toEpochMilli()
        val row = TransactionEntity(id ?: UUID.randomUUID().toString(), transactionType = draft.type.name,
            amountMinor = draft.amount.minorUnits, categoryId = draft.categoryId, date = draft.date.toEpochDay(),
            note = draft.note.trim(), createdAt = previous?.createdAt ?: now, updatedAt = if (previous == null) null else now,
            investmentAction = draft.investmentAction?.name, holdingId = draft.holdingId)
        if (previous == null) dao.insert(row) else dao.update(row)
        AppResult.Success(row.id)
    }
    override suspend fun deleteTransaction(id: String): AppResult<Unit> = atomic {
        if (dao.delete(id) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    override suspend fun saveCategory(id: String?, name: String, type: TransactionType): AppResult<String> = atomic {
        if (!CategoryNames.valid(name)) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        val previous = id?.let { dao.category(it) }
        if (id != null && previous == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        // Type is immutable; creating a new category is explicit and cannot rewrite history.
        if (previous != null && previous.transactionType != type.name) return@atomic AppResult.Failure(AppError.CATEGORY_MISMATCH)
        val normalized = CategoryNames.normalized(name)
        if (dao.categoryNameExists(type.name, normalized, id)) return@atomic AppResult.Failure(AppError.DUPLICATE_CATEGORY)
        val now = clock.instant().toEpochMilli()
        val row = previous?.copy(name = CategoryNames.clean(name), normalizedName = normalized, updatedAt = now)
            ?: CategoryEntity(UUID.randomUUID().toString(), CategoryNames.clean(name), normalized, type.name,
                sortOrder = Int.MAX_VALUE, seedKey = null, createdAt = now)
        if (previous == null) dao.insertCategory(row) else dao.updateCategory(row)
        AppResult.Success(row.id)
    }
    override suspend fun setCategoryActive(id: String, active: Boolean): AppResult<Unit> = atomic {
        val row = dao.category(id) ?: return@atomic AppResult.Failure(AppError.NOT_FOUND)
        dao.updateCategory(row.copy(active = active, updatedAt = clock.instant().toEpochMilli()))
        AppResult.Success(Unit)
    }
    override suspend fun deleteCategory(id: String): AppResult<Unit> = atomic {
        if (dao.categoryInUse(id) || database.budgetDao().categoryInUse(id)) return@atomic AppResult.Failure(AppError.CATEGORY_IN_USE)
        if (dao.deleteCategory(id) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    private suspend fun <T> atomic(block: suspend () -> AppResult<T>): AppResult<T> =
        when (val outer = storageOperation { database.withTransaction { block() } }) {
            is AppResult.Success -> outer.value
            is AppResult.Failure -> outer
        }
}
private fun CategoryEntity.domain() = Category(id, name, TransactionType.valueOf(transactionType), active)
internal fun TransactionEntity.domain(currency: CurrencyCode) = FinanceTransaction(id,
    TransactionType.valueOf(transactionType), Money(amountMinor, currency), categoryId, LocalDate.ofEpochDay(date),
    note, Instant.ofEpochMilli(createdAt), updatedAt?.let(Instant::ofEpochMilli), investmentAction?.let(InvestmentAction::valueOf), holdingId)
