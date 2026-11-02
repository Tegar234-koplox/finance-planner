package com.tegar.finance.domain.repository

import com.tegar.finance.domain.model.*
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeGoals(): Flow<AppResult<BudgetSnapshot?>>
    suspend fun saveGoal(id: String?, draft: GoalDraft): AppResult<String>
    suspend fun deleteGoal(id: String): AppResult<Unit>
}
