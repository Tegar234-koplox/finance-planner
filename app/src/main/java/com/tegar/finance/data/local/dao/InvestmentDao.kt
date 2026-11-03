package com.tegar.finance.data.local.dao

import androidx.room.*
import com.tegar.finance.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentDao {
    @Transaction @Query("SELECT * FROM ledger WHERE id=1") fun observeSource(): Flow<PortfolioSourceEntity?>
    @Transaction @Query("SELECT * FROM ledger WHERE id=1") suspend fun source(): PortfolioSourceEntity?
    @Query("SELECT * FROM category ORDER BY sortOrder,name") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM investment_holding WHERE id=:id AND ledgerId=1") suspend fun holding(id: String): InvestmentHoldingEntity?
    @Query("SELECT * FROM investment_value WHERE id=:id") suspend fun value(id: String): InvestmentValueEntity?
    @Query("SELECT * FROM investment_value WHERE holdingId=:id") suspend fun values(id: String): List<InvestmentValueEntity>
    @Query("SELECT * FROM investment_value WHERE holdingId=:id AND date=:date") suspend fun valueAt(id: String, date: Long): InvestmentValueEntity?
    @Query("SELECT COUNT(*) FROM finance_transaction WHERE holdingId=:id") suspend fun linkedCount(id: String): Int
    @Query("SELECT MIN(date) FROM finance_transaction WHERE holdingId=:id") suspend fun firstLinkedDate(id: String): Long?
    @Insert suspend fun insertHolding(row: InvestmentHoldingEntity)
    @Update suspend fun updateHolding(row: InvestmentHoldingEntity): Int
    @Insert suspend fun insertValue(row: InvestmentValueEntity)
    @Update suspend fun updateValue(row: InvestmentValueEntity): Int
    @Query("DELETE FROM investment_holding WHERE id=:id AND ledgerId=1") suspend fun deleteHolding(id: String): Int
    @Query("DELETE FROM investment_value WHERE id=:id") suspend fun deleteValue(id: String): Int
    @Query("DELETE FROM allocation_target WHERE ledgerId=1") suspend fun clearTargets()
    @Insert suspend fun insertTargets(rows: List<AllocationTargetEntity>)
    @Query("SELECT * FROM contribution_plan WHERE ledgerId=1 AND month=:month") suspend fun plan(month: String): ContributionPlanEntity?
    @Insert suspend fun insertPlan(row: ContributionPlanEntity)
    @Update suspend fun updatePlan(row: ContributionPlanEntity): Int
    @Query("DELETE FROM contribution_plan WHERE ledgerId=1 AND month=:month") suspend fun deletePlan(month: String): Int
    @Insert suspend fun insertCaptureInvestments(rows: List<InvestmentCaptureEntity>)
}
