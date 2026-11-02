package com.tegar.finance.core.database

import com.tegar.finance.data.local.entity.*
import com.tegar.finance.data.local.dao.InvestmentDao
import androidx.room.Database
import androidx.room.RoomDatabase
import com.tegar.finance.data.local.dao.FoundationDao
import com.tegar.finance.data.local.dao.BudgetDao
import com.tegar.finance.data.local.entity.MonthlyBudgetEntity
import com.tegar.finance.data.local.entity.CategoryBudgetEntity
import com.tegar.finance.data.local.dao.TransactionDao
import com.tegar.finance.data.local.entity.TransactionEntity
import com.tegar.finance.data.local.entity.CategoryEntity
import com.tegar.finance.data.local.entity.LedgerEntity
import com.tegar.finance.data.local.entity.GoalEntity
import com.tegar.finance.data.local.dao.GoalDao
import com.tegar.finance.data.local.dao.WealthDao
import com.tegar.finance.data.local.entity.WealthItemEntity
import com.tegar.finance.data.local.entity.WealthValueEntity
import com.tegar.finance.data.local.entity.WealthCaptureEntity
import com.tegar.finance.data.local.entity.WealthCaptureLineEntity

@Database(entities = [LedgerEntity::class, CategoryEntity::class, TransactionEntity::class,
    MonthlyBudgetEntity::class, CategoryBudgetEntity::class, GoalEntity::class, WealthItemEntity::class,
    WealthValueEntity::class, WealthCaptureEntity::class, WealthCaptureLineEntity::class,
    InvestmentHoldingEntity::class, InvestmentValueEntity::class, AllocationTargetEntity::class,
    ContributionPlanEntity::class, InvestmentCaptureEntity::class], version = 6, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun foundationDao(): FoundationDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun wealthDao(): WealthDao
}
