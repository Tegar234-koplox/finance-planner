package com.tegar.finance.data.local.entity

import androidx.room.*

@Entity(tableName = "monthly_budget", foreignKeys = [
    ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledgerId"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
], indices = [Index(value = ["ledgerId", "month"], unique = true)])
data class MonthlyBudgetEntity(@PrimaryKey val id: String, val ledgerId: Int = 1, val month: String,
    val allocationMinor: Long, val createdAt: Long, val updatedAt: Long?)

@Entity(tableName = "category_budget", foreignKeys = [
    ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledgerId"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id", "transactionType"],
        childColumns = ["categoryId", "transactionType"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
], indices = [Index(value = ["ledgerId", "categoryId", "month"], unique = true),
    Index(value = ["categoryId", "transactionType"])])
data class CategoryBudgetEntity(@PrimaryKey val id: String, val ledgerId: Int = 1, val month: String,
    val categoryId: String, val transactionType: String = "EXPENSE",
    val allocationMinor: Long, val createdAt: Long, val updatedAt: Long?)

data class BudgetSnapshotEntity(
    @Embedded val ledger: LedgerEntity,
    @Relation(parentColumn = "id", entityColumn = "ledgerId") val monthly: List<MonthlyBudgetEntity>,
    @Relation(parentColumn = "id", entityColumn = "ledgerId") val categories: List<CategoryBudgetEntity>,
    @Relation(parentColumn = "id", entityColumn = "ledgerId") val transactions: List<TransactionEntity>,
    @Relation(parentColumn = "id", entityColumn = "ledgerId") val goals: List<GoalEntity>,
)
