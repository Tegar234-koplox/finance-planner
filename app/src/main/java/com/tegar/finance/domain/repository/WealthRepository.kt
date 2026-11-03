package com.tegar.finance.domain.repository

import com.tegar.finance.domain.model.*
import kotlinx.coroutines.flow.Flow

interface WealthRepository {
    fun observeWealth(): Flow<AppResult<WealthSource?>>
    suspend fun saveItem(id: String?, draft: WealthItemDraft, initial: WealthValueDraft? = null): AppResult<String>
    suspend fun deleteItem(id: String): AppResult<Unit>
    suspend fun saveValue(id: String?, draft: WealthValueDraft): AppResult<String>
    suspend fun deleteValue(id: String): AppResult<Unit>
    suspend fun captureToday(): AppResult<String>
    suspend fun deleteCapture(id: String): AppResult<Unit>
}
