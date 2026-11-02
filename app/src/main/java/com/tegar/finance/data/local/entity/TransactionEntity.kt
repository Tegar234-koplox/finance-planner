package com.tegar.finance.data.local.entity

import androidx.room.*

@Entity(tableName = "finance_transaction", foreignKeys = [
    ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledgerId"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id", "transactionType"],
        childColumns = ["categoryId", "transactionType"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
], indices = [Index("ledgerId"), Index(value = ["categoryId", "transactionType"]),
    Index(value = ["date", "createdAt"])])
data class TransactionEntity(
    @PrimaryKey val id: String, val ledgerId: Int = 1, val transactionType: String,
    val amountMinor: Long, val categoryId: String, val date: Long, val note: String,
    val createdAt: Long, val updatedAt: Long?,
)
