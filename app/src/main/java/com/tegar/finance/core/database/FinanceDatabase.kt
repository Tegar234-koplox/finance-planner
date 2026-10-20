package com.tegar.finance.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tegar.finance.data.local.dao.FoundationDao
import com.tegar.finance.data.local.entity.CategoryEntity
import com.tegar.finance.data.local.entity.LedgerEntity

@Database(entities = [LedgerEntity::class, CategoryEntity::class], version = 1, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun foundationDao(): FoundationDao
}
