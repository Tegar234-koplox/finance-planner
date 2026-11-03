package com.tegar.finance.data.repository

import androidx.room.withTransaction
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.data.local.entity.GoalEntity
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalGoalRepository @Inject constructor(private val database: FinanceDatabase,
    private val source: BudgetRepository, private val clock: Clock) : GoalRepository {
    override fun observeGoals() = source.observeBudgets()
    override suspend fun saveGoal(id: String?, draft: GoalDraft): AppResult<String> = atomic {
        val row = database.foundationDao().ledger() ?: return@atomic AppResult.Failure(AppError.STORAGE)
        val currency = CurrencyCode(row.currencyCode)
        val ledger = Ledger(currency, ZoneId.of(row.reportingZoneId), LocalDate.ofEpochDay(row.trackedFromDate),
            Money(row.openingCashMinor, currency), Instant.ofEpochMilli(row.createdAt))
        GoalRules.validate(draft, ledger, DatePeriods.today(clock, ledger.reportingZone))?.let {
            return@atomic AppResult.Failure(it)
        }
        val old = id?.let { database.goalDao().get(it) }
        if (id != null && old == null) return@atomic AppResult.Failure(AppError.NOT_FOUND)
        val goalId = old?.id ?: UUID.randomUUID().toString()
        val now = clock.instant().toEpochMilli()
        val entity = GoalEntity(goalId, name = GoalRules.normalizeName(draft.name), kind = draft.kind.name,
            targetMinor = draft.target.minorUnits, earmarkedMinor = draft.earmarked.minorUnits,
            asOfDate = draft.asOf.toEpochDay(), targetDate = draft.targetDate.toEpochDay(), priority = draft.priority.name,
            state = draft.state.name, createdAt = old?.createdAt ?: now, updatedAt = if (old == null) null else now)
        if (old == null) database.goalDao().insert(entity) else database.goalDao().update(entity)
        AppResult.Success(goalId)
    }
    override suspend fun deleteGoal(id: String): AppResult<Unit> = atomic {
        if (database.goalDao().delete(id) == 0) AppResult.Failure(AppError.NOT_FOUND) else AppResult.Success(Unit)
    }
    private suspend fun <T> atomic(block: suspend () -> AppResult<T>): AppResult<T> =
        when (val result = storageOperation { database.withTransaction { block() } }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> result
        }
}
