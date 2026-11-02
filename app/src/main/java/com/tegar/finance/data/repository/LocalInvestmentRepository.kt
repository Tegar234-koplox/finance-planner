package com.tegar.finance.data.repository

import androidx.room.withTransaction
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.data.local.entity.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.InvestmentRepository
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class LocalInvestmentRepository @Inject constructor(private val database: FinanceDatabase, private val clock: Clock) : InvestmentRepository {
    private val dao get() = database.investmentDao()
    override fun observePortfolio() = database.invalidationTracker.createFlow("ledger", "category", "finance_transaction",
        "investment_holding", "investment_value", "allocation_target", "contribution_plan").map {
        database.withTransaction { dao.source()?.domain()?.copy(categories = dao.categories().map { row ->
            Category(row.id, row.name, TransactionType.valueOf(row.transactionType), row.active)
        }) }
    }.storageResults()
    private suspend fun ledger() = database.foundationDao().ledger()?.domain()
    override suspend fun saveHolding(id: String?, draft: HoldingDraft, initial: HoldingValueDraft?): AppResult<String> = atomic {
        val ledger = ledger() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        val today = DatePeriods.today(clock, ledger.reportingZone)
        InvestmentRules.validateHolding(draft, ledger.currency, today)?.let { return@atomic AppResult.Failure(it) }
        val old = id?.let { dao.holding(it) }
        if (id != null && old == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        val firstLinked = old?.let { dao.firstLinkedDate(it.id) }
        if (old != null && (initial != null || dao.values(old.id).any { it.date < draft.acquiredOn.toEpochDay() } ||
            (firstLinked != null && firstLinked < draft.acquiredOn.toEpochDay()))) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        val key = old?.id ?: UUID.randomUUID().toString(); val now = clock.instant().toEpochMilli()
        val entity = InvestmentHoldingEntity(key, name = CategoryNames.clean(draft.name), symbol = draft.symbol.trim(), bucket = draft.bucket.name,
            acquiredOn = draft.acquiredOn.toEpochDay(), costMinor = draft.cost?.minorUnits, notes = draft.notes, active = draft.active,
            createdAt = old?.createdAt ?: now, updatedAt = old?.let { now })
        val value = initial?.copy(holdingId = key)
        if (value != null) InvestmentRules.validateValue(value, entity.domain(ledger.currency), ledger.currency, today)?.let { return@atomic AppResult.Failure(it) }
        if (old == null) dao.insertHolding(entity) else dao.updateHolding(entity)
        if (value != null) dao.insertValue(InvestmentValueEntity(UUID.randomUUID().toString(), key, value.date.toEpochDay(), value.amount.minorUnits, value.notes, now, null))
        AppResult.Success(key)
    }
    override suspend fun deleteHolding(id: String): AppResult<Unit> = atomic {
        if (dao.linkedCount(id) > 0) return@atomic AppResult.Failure(AppError.INVESTMENT_IN_USE)
        if (dao.deleteHolding(id) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    override suspend fun saveValue(id: String?, draft: HoldingValueDraft): AppResult<String> = atomic {
        val ledger = ledger() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        val holding = dao.holding(draft.holdingId)?.domain(ledger.currency) ?: return@atomic AppResult.Failure(AppError.NOT_FOUND)
        InvestmentRules.validateValue(draft, holding, ledger.currency, DatePeriods.today(clock, ledger.reportingZone))?.let { return@atomic AppResult.Failure(it) }
        val old = id?.let { dao.value(it) }
        if (id != null && old == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        if (old != null && old.holdingId != draft.holdingId) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        val collision = dao.valueAt(draft.holdingId, draft.date.toEpochDay())
        if (collision != null && collision.id != old?.id) return@atomic AppResult.Failure(AppError.WEALTH_DATE_CONFLICT)
        val now = clock.instant().toEpochMilli(); val key = old?.id ?: UUID.randomUUID().toString()
        val row = InvestmentValueEntity(key, holding.id, draft.date.toEpochDay(), draft.amount.minorUnits, draft.notes, old?.createdAt ?: now, old?.let { now })
        if (old == null) dao.insertValue(row) else dao.updateValue(row)
        AppResult.Success(key)
    }
    override suspend fun deleteValue(id: String): AppResult<Unit> = atomic {
        if (dao.deleteValue(id) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    override suspend fun saveTargets(targets: List<AllocationTarget>): AppResult<Unit> = atomic {
        if (ledger() == null) return@atomic AppResult.Failure(AppError.STORAGE)
        InvestmentRules.validateTargets(targets)?.let { return@atomic AppResult.Failure(it) }
        dao.clearTargets(); dao.insertTargets(targets.map { AllocationTargetEntity(it.bucket.name, basisPoints = it.basisPoints) })
        AppResult.Success(Unit)
    }
    override suspend fun savePlan(month: YearMonth, target: Money?): AppResult<Unit> = atomic {
        val ledger = ledger() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        if (!BudgetMonths.valid(month) || target?.minorUnits?.let { it < 0 } == true) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        if (target != null && target.currency != ledger.currency) return@atomic AppResult.Failure(AppError.CURRENCY_MISMATCH)
        val old = dao.plan(month.toString())
        if (target == null) dao.deletePlan(month.toString()) else {
            val row = ContributionPlanEntity(old?.id ?: UUID.randomUUID().toString(), month = month.toString(), targetMinor = target.minorUnits)
            if (old == null) dao.insertPlan(row) else dao.updatePlan(row)
        }
        AppResult.Success(Unit)
    }
    override suspend fun saveMovement(id: String?, draft: TransactionDraft, valueAfter: Money?): AppResult<String> = atomic {
        val ledger = ledger() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        val today = DatePeriods.today(clock, ledger.reportingZone)
        if (draft.type != TransactionType.INVESTMENT || draft.date > today || (draft.holdingId == null && valueAfter != null))
            return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        val previous = id?.let { database.transactionDao().get(it) }
        if (id != null && previous == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        if (previous != null && previous.transactionType != TransactionType.INVESTMENT.name) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        val category = database.transactionDao().category(draft.categoryId)?.let { Category(it.id, it.name, TransactionType.valueOf(it.transactionType), it.active) }
        TransactionRules.validate(draft, ledger, category, previous?.domain(ledger.currency))?.let { return@atomic AppResult.Failure(it) }
        val holding = draft.holdingId?.let { dao.holding(it) }
        if (draft.holdingId != null && holding == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        var valueDraft: HoldingValueDraft? = null
        if (holding != null) {
            if (!holding.active && previous?.holdingId != holding.id) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
            if (valueAfter == null) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
            valueDraft = HoldingValueDraft(holding.id, draft.date, valueAfter)
            InvestmentRules.validateValue(valueDraft, holding.domain(ledger.currency), ledger.currency, today)?.let { return@atomic AppResult.Failure(it) }
        }
        // All validation precedes either write. Cash and the explicitly supplied observed balance commit together.
        val key = previous?.id ?: UUID.randomUUID().toString(); val now = clock.instant().toEpochMilli()
        val row = TransactionEntity(key, transactionType = draft.type.name, amountMinor = draft.amount.minorUnits, categoryId = draft.categoryId,
            date = draft.date.toEpochDay(), note = draft.note.trim(), createdAt = previous?.createdAt ?: now, updatedAt = previous?.let { now },
            investmentAction = draft.investmentAction?.name, holdingId = draft.holdingId)
        if (previous == null) database.transactionDao().insert(row) else database.transactionDao().update(row)
        if (valueDraft != null) {
            val oldValue = dao.valueAt(valueDraft.holdingId, valueDraft.date.toEpochDay())
            val updated = InvestmentValueEntity(oldValue?.id ?: UUID.randomUUID().toString(), valueDraft.holdingId, valueDraft.date.toEpochDay(),
                valueDraft.amount.minorUnits, oldValue?.notes.orEmpty(), oldValue?.createdAt ?: now, oldValue?.let { now })
            if (oldValue == null) dao.insertValue(updated) else dao.updateValue(updated)
        }
        AppResult.Success(key)
    }
    override suspend fun deleteMovement(id: String): AppResult<Unit> = atomic {
        val row = database.transactionDao().get(id) ?: return@atomic AppResult.Failure(AppError.NOT_FOUND)
        if (row.transactionType != TransactionType.INVESTMENT.name) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        database.transactionDao().delete(id); AppResult.Success(Unit)
    }
    private suspend fun <T> atomic(block: suspend () -> AppResult<T>): AppResult<T> =
        when (val result = storageOperation { database.withTransaction { block() } }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> result
        }
}
internal fun LedgerEntity.domain(): Ledger {
    val currency = CurrencyCode(currencyCode)
    return Ledger(currency, ZoneId.of(reportingZoneId), LocalDate.ofEpochDay(trackedFromDate), Money(openingCashMinor, currency), Instant.ofEpochMilli(createdAt))
}
internal fun InvestmentHoldingEntity.domain(currency: CurrencyCode) = InvestmentHolding(id, name, symbol, AllocationBucket.valueOf(bucket),
    LocalDate.ofEpochDay(acquiredOn), costMinor?.let { Money(it, currency) }, notes, active, Instant.ofEpochMilli(createdAt), updatedAt?.let(Instant::ofEpochMilli))
internal fun InvestmentValueEntity.domain(currency: CurrencyCode) = HoldingValue(id, holdingId, LocalDate.ofEpochDay(date), Money(valueMinor, currency),
    notes, Instant.ofEpochMilli(createdAt), updatedAt?.let(Instant::ofEpochMilli))
internal fun PortfolioSourceEntity.domain(): PortfolioSource {
    val account = ledger.domain(); val currency = account.currency
    return PortfolioSource(account, transactions.map { it.domain(currency) }, holdings.map { it.holding.domain(currency) },
        holdings.flatMap { it.values }.map { it.domain(currency) }, targets.map { AllocationTarget(AllocationBucket.valueOf(it.bucket), it.basisPoints) },
        plans.map { ContributionPlan(YearMonth.parse(it.month), Money(it.targetMinor, currency)) })
}
