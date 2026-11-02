package com.tegar.finance.data.local.entity

import androidx.room.*

@Entity(tableName = "investment_holding", foreignKeys = [ForeignKey(entity = LedgerEntity::class,
    parentColumns = ["id"], childColumns = ["ledgerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)], indices = [Index("ledgerId")])
data class InvestmentHoldingEntity(@PrimaryKey val id: String, val ledgerId: Int = 1, val name: String, val symbol: String,
    val bucket: String, val acquiredOn: Long, val costMinor: Long?, val notes: String, val active: Boolean,
    val createdAt: Long, val updatedAt: Long?)
@Entity(tableName = "investment_value", foreignKeys = [ForeignKey(entity = InvestmentHoldingEntity::class,
    parentColumns = ["id"], childColumns = ["holdingId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index(value = ["holdingId", "date"], unique = true)])
data class InvestmentValueEntity(@PrimaryKey val id: String, val holdingId: String, val date: Long, val valueMinor: Long,
    val notes: String, val createdAt: Long, val updatedAt: Long?)
@Entity(tableName = "allocation_target", foreignKeys = [ForeignKey(entity = LedgerEntity::class,
    parentColumns = ["id"], childColumns = ["ledgerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)], indices = [Index("ledgerId")])
data class AllocationTargetEntity(@PrimaryKey val bucket: String, val ledgerId: Int = 1, val basisPoints: Int)
@Entity(tableName = "contribution_plan", foreignKeys = [ForeignKey(entity = LedgerEntity::class,
    parentColumns = ["id"], childColumns = ["ledgerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index(value = ["ledgerId", "month"], unique = true)])
data class ContributionPlanEntity(@PrimaryKey val id: String, val ledgerId: Int = 1, val month: String, val targetMinor: Long)
// No holding FK: current holding deletion cannot remove a saved historical component.
@Entity(tableName = "wealth_capture_investment", foreignKeys = [ForeignKey(entity = WealthCaptureEntity::class,
    parentColumns = ["id"], childColumns = ["captureId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index(value = ["captureId", "sourceHoldingId"], unique = true)])
data class InvestmentCaptureEntity(@PrimaryKey val id: String, val captureId: String, val sourceHoldingId: String,
    val name: String, val bucket: String, val valueMinor: Long, val valuedOn: Long)
data class HoldingWithValues(@Embedded val holding: InvestmentHoldingEntity,
    @Relation(parentColumn = "id", entityColumn = "holdingId") val values: List<InvestmentValueEntity>)
data class PortfolioSourceEntity(@Embedded val ledger: LedgerEntity,
    @Relation(parentColumn = "id", entityColumn = "ledgerId") val transactions: List<TransactionEntity>,
    @Relation(parentColumn = "id", entityColumn = "ledgerId", entity = InvestmentHoldingEntity::class) val holdings: List<HoldingWithValues>,
    @Relation(parentColumn = "id", entityColumn = "ledgerId") val targets: List<AllocationTargetEntity>,
    @Relation(parentColumn = "id", entityColumn = "ledgerId") val plans: List<ContributionPlanEntity>)
