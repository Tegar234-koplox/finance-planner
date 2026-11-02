package com.tegar.finance.domain.repository

import com.tegar.finance.domain.model.*
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeBudgets(): Flow<AppResult<BudgetSnapshot?>>
    suspend fun saveMonthly(month: YearMonth, allocation: Money): AppResult<Unit>
    suspend fun saveCategory(month: YearMonth, categoryId: String, allocation: Money): AppResult<Unit>
    suspend fun deleteMonthly(month: YearMonth): AppResult<Unit>
    suspend fun deleteCategory(month: YearMonth, categoryId: String): AppResult<Unit>
}
