package com.tegar.finance.data.repository

import androidx.room.withTransaction
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.data.local.entity.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.WealthRepository
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class LocalWealthRepository @Inject constructor(private val database: FinanceDatabase, private val clock: Clock) : WealthRepository {
    private val dao get() = database.wealthDao()
    override fun observeWealth() = dao.observeSource().map { it?.domain() }.storageResults()
    private suspend fun ledger(): Ledger? = database.foundationDao().ledger()?.let { row ->
        Ledger(CurrencyCode(row.currencyCode), ZoneId.of(row.reportingZoneId), LocalDate.ofEpochDay(row.trackedFromDate),
            Money(row.openingCashMinor, CurrencyCode(row.currencyCode)), Instant.ofEpochMilli(row.createdAt))
    }
    override suspend fun saveItem(id: String?, draft: WealthItemDraft, initial: WealthValueDraft?): AppResult<String> = atomic {
        val ledger = ledger() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        val today = DatePeriods.today(clock, ledger.reportingZone)
        WealthRules.validateItem(draft, ledger.currency, today)?.let { return@atomic AppResult.Failure(it) }
        val old = id?.let { dao.item(it) }
        if (id != null && old == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        if (old != null && (old.kind != draft.kind.name || initial != null ||
            dao.values(old.id).any { it.date < draft.acquiredOn.toEpochDay() })) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        val key = old?.id ?: UUID.randomUUID().toString(); val now = clock.instant()
        val item = WealthItem(key, CategoryNames.clean(draft.name), draft.kind, draft.category, draft.acquiredOn,
            draft.acquisitionCost, draft.notes, draft.active, old?.createdAt?.let(Instant::ofEpochMilli) ?: now, old?.let { now })
        val initialDraft = initial?.copy(itemId = key)
        if (initialDraft != null) WealthRules.validateValue(initialDraft, item, ledger.currency, today)?.let { return@atomic AppResult.Failure(it) }
        val entity = WealthItemEntity(key, name = item.name, kind = item.kind.name, category = item.category.name,
            acquiredOn = item.acquiredOn.toEpochDay(), costMinor = item.acquisitionCost?.minorUnits, notes = item.notes,
            active = item.active, createdAt = item.createdAt.toEpochMilli(), updatedAt = item.updatedAt?.toEpochMilli())
        if (old == null) dao.insertItem(entity) else dao.updateItem(entity)
        if (initialDraft != null) dao.insertValue(WealthValueEntity(UUID.randomUUID().toString(), key,
            initialDraft.date.toEpochDay(), initialDraft.amount.minorUnits, initialDraft.notes, now.toEpochMilli(), null))
        AppResult.Success(key)
    }
    override suspend fun deleteItem(id: String): AppResult<Unit> = atomic {
        if (dao.deleteItem(id) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    override suspend fun saveValue(id: String?, draft: WealthValueDraft): AppResult<String> = atomic {
        val ledger = ledger() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        val item = dao.item(draft.itemId)?.domain(ledger.currency) ?: return@atomic AppResult.Failure(AppError.NOT_FOUND)
        WealthRules.validateValue(draft, item, ledger.currency, DatePeriods.today(clock, ledger.reportingZone))?.let { return@atomic AppResult.Failure(it) }
        val old = id?.let { dao.value(it) }
        if (id != null && old == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        if (old != null && old.itemId != draft.itemId) return@atomic AppResult.Failure(AppError.INVALID_INPUT)
        val collision = dao.valueAt(draft.itemId, draft.date.toEpochDay())
        if (collision != null && collision.id != old?.id) return@atomic AppResult.Failure(AppError.WEALTH_DATE_CONFLICT)
        val key = old?.id ?: UUID.randomUUID().toString(); val now = clock.instant().toEpochMilli()
        val entity = WealthValueEntity(key, draft.itemId, draft.date.toEpochDay(), draft.amount.minorUnits, draft.notes,
            old?.createdAt ?: now, if (old == null) null else now)
        if (old == null) dao.insertValue(entity) else dao.updateValue(entity)
        AppResult.Success(key)
    }
    override suspend fun deleteValue(id: String): AppResult<Unit> = atomic {
        if (dao.deleteValue(id) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    override suspend fun captureToday(): AppResult<String> = atomic {
        val source = dao.loadSource()?.domain() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        val id = UUID.randomUUID().toString()
        when (val result = WealthCalculator.capture(source, DatePeriods.today(clock, source.ledger.reportingZone), id, clock.instant())) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val capture = result.value
                dao.insertCapture(WealthCaptureEntity(capture.id, date = capture.date.toEpochDay(), currencyCode = capture.cash.currency.value,
                    cashMinor = capture.cash.minorUnits, assetsMinor = capture.assets.minorUnits, liabilitiesMinor = capture.liabilities.minorUnits,
                    totalMinor = capture.total.minorUnits, method = capture.method, capturedAt = capture.capturedAt.toEpochMilli(), investmentsMinor = capture.investments.minorUnits))
                dao.insertLines(capture.lines.map { line -> WealthCaptureLineEntity(UUID.randomUUID().toString(), capture.id,
                    line.sourceItemId, line.name, line.kind.name, line.category.name, line.amount.minorUnits, line.valuedOn.toEpochDay()) })
                database.investmentDao().insertCaptureInvestments(capture.investmentLines.map { line ->
                    InvestmentCaptureEntity(UUID.randomUUID().toString(), capture.id, line.sourceHoldingId, line.name,
                        line.bucket.name, line.amount.minorUnits, line.valuedOn.toEpochDay())
                })
                AppResult.Success(id)
            }
        }
    }
    override suspend fun deleteCapture(id: String): AppResult<Unit> = atomic {
        if (dao.deleteCapture(id) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    private suspend fun <T> atomic(block: suspend () -> AppResult<T>): AppResult<T> =
        when (val result = storageOperation { database.withTransaction { block() } }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> result
        }
}

internal fun WealthItemEntity.domain(currency: CurrencyCode) = WealthItem(id, name, WealthKind.valueOf(kind), WealthCategory.valueOf(category),
    LocalDate.ofEpochDay(acquiredOn), costMinor?.let { Money(it, currency) }, notes, active, Instant.ofEpochMilli(createdAt), updatedAt?.let(Instant::ofEpochMilli))
internal fun WealthSourceEntity.domain(): WealthSource {
    val currency = CurrencyCode(ledger.currencyCode)
    return WealthSource(Ledger(currency, ZoneId.of(ledger.reportingZoneId), LocalDate.ofEpochDay(ledger.trackedFromDate),
        Money(ledger.openingCashMinor, currency), Instant.ofEpochMilli(ledger.createdAt)),
        transactions.map { row -> row.domain(currency) },
        items.map { it.item.domain(currency) }, items.flatMap { it.values }.map { row -> WealthValue(row.id, row.itemId,
            LocalDate.ofEpochDay(row.date), Money(row.valueMinor, currency), row.notes, Instant.ofEpochMilli(row.createdAt), row.updatedAt?.let(Instant::ofEpochMilli)) },
        captures.map { source ->
            val row = source.capture; val recordedCurrency = CurrencyCode(row.currencyCode)
            WealthCapture(row.id, LocalDate.ofEpochDay(row.date), Money(row.cashMinor, recordedCurrency), Money(row.assetsMinor, recordedCurrency),
                Money(row.liabilitiesMinor, recordedCurrency), Money(row.totalMinor, recordedCurrency), source.lines.map { line ->
                    WealthCaptureLine(line.sourceItemId, line.name, WealthKind.valueOf(line.kind), WealthCategory.valueOf(line.category),
                        Money(line.valueMinor, recordedCurrency), LocalDate.ofEpochDay(line.valuedOn))
                }, Instant.ofEpochMilli(row.capturedAt), row.method, Money(row.investmentsMinor, recordedCurrency),
                source.investments.map { line -> InvestmentCaptureLine(line.sourceHoldingId, line.name, AllocationBucket.valueOf(line.bucket),
                    Money(line.valueMinor, recordedCurrency), LocalDate.ofEpochDay(line.valuedOn)) })
        }, holdings.map { it.holding.domain(currency) }, holdings.flatMap { it.values }.map { it.domain(currency) })
}
