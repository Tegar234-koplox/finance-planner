package com.tegar.finance.data.local.dao

import androidx.room.*
import com.tegar.finance.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Transaction @Query("SELECT * FROM ledger WHERE id = 1")
    fun observeSnapshot(): Flow<BudgetSnapshotEntity?>
    @Query("SELECT * FROM monthly_budget WHERE ledgerId = 1 AND month = :month")
    suspend fun monthly(month: String): MonthlyBudgetEntity?
    @Query("SELECT * FROM category_budget WHERE ledgerId = 1 AND month = :month")
    suspend fun categories(month: String): List<CategoryBudgetEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM category_budget WHERE categoryId = :id)")
    suspend fun categoryInUse(id: String): Boolean
    @Insert suspend fun insertMonthly(row: MonthlyBudgetEntity)
    @Update suspend fun updateMonthly(row: MonthlyBudgetEntity)
    @Insert suspend fun insertCategory(row: CategoryBudgetEntity)
    @Update suspend fun updateCategory(row: CategoryBudgetEntity)
    @Query("DELETE FROM monthly_budget WHERE ledgerId = 1 AND month = :month")
    suspend fun deleteMonthly(month: String): Int
    @Query("DELETE FROM category_budget WHERE ledgerId = 1 AND month = :month AND categoryId = :categoryId")
    suspend fun deleteCategory(month: String, categoryId: String): Int
}
