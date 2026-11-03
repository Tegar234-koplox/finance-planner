package com.tegar.finance.domain.repository

import com.tegar.finance.domain.model.*
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeTransactions(filter: TransactionFilter = TransactionFilter()): Flow<AppResult<List<FinanceTransaction>>>
    suspend fun saveTransaction(id: String?, draft: TransactionDraft): AppResult<String>
    suspend fun deleteTransaction(id: String): AppResult<Unit>
}
interface EditableCategoryRepository : CategoryRepository {
    suspend fun saveCategory(id: String?, name: String, type: TransactionType): AppResult<String>
    suspend fun setCategoryActive(id: String, active: Boolean): AppResult<Unit>
    suspend fun deleteCategory(id: String): AppResult<Unit>
}
