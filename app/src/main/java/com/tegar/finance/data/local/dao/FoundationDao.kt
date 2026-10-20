package com.tegar.finance.data.local.dao

import androidx.room.*
import com.tegar.finance.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FoundationDao {
    @Query("SELECT * FROM ledger WHERE id = 1")
    suspend fun ledger(): LedgerEntity?
    @Query("SELECT * FROM ledger WHERE id = 1")
    fun observeLedger(): Flow<LedgerEntity?>
    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    fun observeCategories(): Flow<List<CategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedger(ledger: LedgerEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategories(categories: List<CategoryEntity>)
}
