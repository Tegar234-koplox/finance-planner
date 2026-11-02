package com.tegar.finance.data.local.entity

import androidx.room.*

@Entity(tableName = "finance_goal", foreignKeys = [ForeignKey(entity = LedgerEntity::class,
    parentColumns = ["id"], childColumns = ["ledgerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index("ledgerId")])
data class GoalEntity(@PrimaryKey val id: String, val ledgerId: Int = 1, val name: String, val kind: String,
    val targetMinor: Long, val earmarkedMinor: Long, val asOfDate: Long, val targetDate: Long,
    val priority: String, val state: String, val createdAt: Long, val updatedAt: Long?)
