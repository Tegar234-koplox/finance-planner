package com.tegar.finance.data.local.entity

import androidx.room.*

@Entity(tableName = "wealth_item", foreignKeys = [ForeignKey(entity = LedgerEntity::class,
    parentColumns = ["id"], childColumns = ["ledgerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)], indices = [Index("ledgerId")])
data class WealthItemEntity(@PrimaryKey val id: String, val ledgerId: Int = 1, val name: String, val kind: String,
    val category: String, val acquiredOn: Long, val costMinor: Long?, val notes: String, val active: Boolean,
    val createdAt: Long, val updatedAt: Long?)

@Entity(tableName = "wealth_value", foreignKeys = [ForeignKey(entity = WealthItemEntity::class,
    parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index(value = ["itemId", "date"], unique = true)])
data class WealthValueEntity(@PrimaryKey val id: String, val itemId: String, val date: Long, val valueMinor: Long,
    val notes: String, val createdAt: Long, val updatedAt: Long?)

@Entity(tableName = "wealth_capture", foreignKeys = [ForeignKey(entity = LedgerEntity::class,
    parentColumns = ["id"], childColumns = ["ledgerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index(value = ["ledgerId", "date"], unique = true)])
data class WealthCaptureEntity(@PrimaryKey val id: String, val ledgerId: Int = 1, val date: Long, val currencyCode: String,
    val cashMinor: Long, val assetsMinor: Long, val liabilitiesMinor: Long, val totalMinor: Long, val method: String, val capturedAt: Long)

// sourceItemId intentionally has no FK: deleting a current item must not erase captured evidence.
@Entity(tableName = "wealth_capture_line", foreignKeys = [ForeignKey(entity = WealthCaptureEntity::class,
    parentColumns = ["id"], childColumns = ["captureId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index(value = ["captureId", "sourceItemId"], unique = true)])
data class WealthCaptureLineEntity(@PrimaryKey val id: String, val captureId: String, val sourceItemId: String,
    val name: String, val kind: String, val category: String, val valueMinor: Long, val valuedOn: Long)

data class WealthItemWithValues(@Embedded val item: WealthItemEntity,
    @Relation(parentColumn = "id", entityColumn = "itemId") val values: List<WealthValueEntity>)
data class WealthCaptureWithLines(@Embedded val capture: WealthCaptureEntity,
    @Relation(parentColumn = "id", entityColumn = "captureId") val lines: List<WealthCaptureLineEntity>)
data class WealthSourceEntity(@Embedded val ledger: LedgerEntity,
    @Relation(parentColumn = "id", entityColumn = "ledgerId") val transactions: List<TransactionEntity>,
    @Relation(parentColumn = "id", entityColumn = "ledgerId", entity = WealthItemEntity::class) val items: List<WealthItemWithValues>,
    @Relation(parentColumn = "id", entityColumn = "ledgerId", entity = WealthCaptureEntity::class) val captures: List<WealthCaptureWithLines>)
