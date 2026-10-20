package com.tegar.finance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ledger")
data class LedgerEntity(
    @PrimaryKey val id: Int = 1,
    val currencyCode: String,
    val reportingZoneId: String,
    val trackedFromDate: Long,
    val openingCashMinor: Long,
    val createdAt: Long,
    val updatedAt: Long? = null,
)

@Entity(tableName = "category", indices = [
    Index(value = ["transactionType", "normalizedName"], unique = true),
    Index(value = ["seedKey"], unique = true),
    Index(value = ["id", "transactionType"], unique = true),
])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val transactionType: String,
    val active: Boolean = true,
    val sortOrder: Int,
    val seedKey: String?,
    val createdAt: Long,
    val updatedAt: Long? = null,
)
