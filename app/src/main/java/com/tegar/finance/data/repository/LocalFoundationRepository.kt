package com.tegar.finance.data.repository

import androidx.room.withTransaction
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.data.local.DefaultCategories
import com.tegar.finance.data.local.entity.LedgerEntity
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class LocalFoundationRepository @Inject constructor(
    private val database: FinanceDatabase,
    private val clock: Clock,
) : LedgerRepository, CategoryRepository {
    override suspend fun initialize(): AppResult<Unit> = storageOperation {
        database.withTransaction {
            val dao = database.foundationDao()
            if (dao.ledger() == null) {
                val now = clock.instant().toEpochMilli()
                dao.insertLedger(LedgerEntity(currencyCode = "IDR", reportingZoneId = DatePeriods.DefaultZone.id,
                    trackedFromDate = DatePeriods.today(clock).toEpochDay(), openingCashMinor = 0, createdAt = now))
                dao.insertCategories(DefaultCategories.create(now))
            }
        }
    }
    override fun observeLedger() = database.foundationDao().observeLedger().map { row ->
        row?.let {
            val currency = CurrencyCode(it.currencyCode)
            Ledger(currency, ZoneId.of(it.reportingZoneId), LocalDate.ofEpochDay(it.trackedFromDate),
                Money(it.openingCashMinor, currency), Instant.ofEpochMilli(it.createdAt))
        }
    }.storageResults()
    override fun observeCategories() = database.foundationDao().observeCategories().map { rows ->
        rows.map { Category(it.id, it.name, TransactionType.valueOf(it.transactionType), it.active) }
    }.storageResults()
}
