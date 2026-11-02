package com.tegar.finance.domain.repository

import com.tegar.finance.domain.model.*
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

interface InvestmentRepository {
    fun observePortfolio(): Flow<AppResult<PortfolioSource?>>
    suspend fun saveHolding(id: String?, draft: HoldingDraft, initial: HoldingValueDraft? = null): AppResult<String>
    suspend fun deleteHolding(id: String): AppResult<Unit>
    suspend fun saveValue(id: String?, draft: HoldingValueDraft): AppResult<String>
    suspend fun deleteValue(id: String): AppResult<Unit>
    suspend fun saveTargets(targets: List<AllocationTarget>): AppResult<Unit>
    suspend fun savePlan(month: YearMonth, target: Money?): AppResult<Unit>
    suspend fun saveMovement(id: String?, draft: TransactionDraft, valueAfter: Money?): AppResult<String>
    suspend fun deleteMovement(id: String): AppResult<Unit>
}
