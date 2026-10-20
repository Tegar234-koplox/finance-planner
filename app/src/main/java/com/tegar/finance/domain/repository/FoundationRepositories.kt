package com.tegar.finance.domain.repository

import com.tegar.finance.domain.model.*
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    suspend fun initialize(): AppResult<Unit>
    fun observeLedger(): Flow<AppResult<Ledger?>>
}
interface CategoryRepository { fun observeCategories(): Flow<AppResult<List<Category>>> }
interface SettingsRepository {
    fun observeSettings(): Flow<AppResult<AppSettings>>
    suspend fun setTheme(mode: ThemeMode): AppResult<Unit>
}
