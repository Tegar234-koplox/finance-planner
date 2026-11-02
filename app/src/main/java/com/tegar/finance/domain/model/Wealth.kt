package com.tegar.finance.domain.model

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class WealthKind { ASSET, LIABILITY }
enum class WealthCategory { PROPERTY, VEHICLE, PERSONAL, LOAN, CARD, OTHER }
data class WealthItem(val id: String, val name: String, val kind: WealthKind, val category: WealthCategory,
    val acquiredOn: LocalDate, val acquisitionCost: Money?, val notes: String, val active: Boolean,
    val createdAt: Instant, val updatedAt: Instant?)
data class WealthItemDraft(val name: String, val kind: WealthKind, val category: WealthCategory,
    val acquiredOn: LocalDate, val acquisitionCost: Money?, val notes: String = "", val active: Boolean = true)
data class WealthValue(val id: String, val itemId: String, val date: LocalDate, val amount: Money,
    val notes: String, val createdAt: Instant, val updatedAt: Instant?)
data class WealthValueDraft(val itemId: String, val date: LocalDate, val amount: Money, val notes: String = "")
data class WealthCaptureLine(val sourceItemId: String, val name: String, val kind: WealthKind,
    val category: WealthCategory, val amount: Money, val valuedOn: LocalDate)
data class WealthCapture(val id: String, val date: LocalDate, val cash: Money, val assets: Money,
    val liabilities: Money, val total: Money, val lines: List<WealthCaptureLine>, val capturedAt: Instant,
    val method: String = WealthRules.METHOD)
data class WealthSource(val ledger: Ledger, val transactions: List<FinanceTransaction>, val items: List<WealthItem>,
    val values: List<WealthValue>, val captures: List<WealthCapture>)
data class WealthItemStatus(val item: WealthItem, val latest: WealthValue?, val ageDays: Long?, val included: Boolean)
data class WealthOverview(val today: LocalDate, val cash: Money?, val cashOverflow: Boolean,
    val assets: Money?, val liabilities: Money?, val total: Money?, val assetsOverflow: Boolean,
    val liabilitiesOverflow: Boolean, val totalOverflow: Boolean, val missingAssets: Int, val missingLiabilities: Int,
    val items: List<WealthItemStatus>, val futureValues: Int, val futureItems: Int,
    val history: List<WealthCapture>, val historyChange: Money?, val historyChangeOverflow: Boolean)

object WealthRules {
    const val METHOD = "TRACKED_V1"
    fun categoryValid(kind: WealthKind, category: WealthCategory) = category == WealthCategory.OTHER || when (kind) {
        WealthKind.ASSET -> category in listOf(WealthCategory.PROPERTY, WealthCategory.VEHICLE, WealthCategory.PERSONAL)
        WealthKind.LIABILITY -> category in listOf(WealthCategory.LOAN, WealthCategory.CARD)
    }
    fun validateItem(draft: WealthItemDraft, currency: CurrencyCode, today: LocalDate): AppError? = when {
        draft.acquisitionCost?.currency?.let { it != currency } == true -> AppError.CURRENCY_MISMATCH
        !CategoryNames.valid(draft.name) || !categoryValid(draft.kind, draft.category) || draft.notes.length > 500 ||
            draft.acquiredOn.year !in 1..9999 || draft.acquiredOn > today ||
            draft.acquisitionCost?.minorUnits?.let { it < 0 } == true ||
            (draft.kind == WealthKind.LIABILITY && draft.acquisitionCost != null) -> AppError.INVALID_INPUT
        else -> null
    }
    fun validateValue(draft: WealthValueDraft, item: WealthItem, currency: CurrencyCode, today: LocalDate): AppError? = when {
        draft.amount.currency != currency -> AppError.CURRENCY_MISMATCH
        draft.itemId != item.id || draft.amount.minorUnits < 0 || draft.notes.length > 500 ||
            draft.date.year !in 1..9999 || draft.date < item.acquiredOn || draft.date > today -> AppError.INVALID_INPUT
        else -> null
    }
    fun validCapture(row: WealthCapture, currency: CurrencyCode): Boolean {
        if (row.method != METHOD || row.date.year !in 1..9999 ||
            listOf(row.cash, row.assets, row.liabilities, row.total).any { it.currency != currency } ||
            row.assets.minorUnits < 0 || row.liabilities.minorUnits < 0 ||
            row.lines.map { it.sourceItemId }.distinct().size != row.lines.size) return false
        var assets = BigInteger.ZERO; var liabilities = BigInteger.ZERO
        for (line in row.lines) {
            if (!CategoryNames.valid(line.name) || !categoryValid(line.kind, line.category) || line.amount.currency != currency ||
                line.amount.minorUnits < 0 || line.valuedOn.year !in 1..9999 || line.valuedOn > row.date) return false
            if (line.kind == WealthKind.ASSET) assets += BigInteger.valueOf(line.amount.minorUnits)
            else liabilities += BigInteger.valueOf(line.amount.minorUnits)
        }
        return assets == BigInteger.valueOf(row.assets.minorUnits) && liabilities == BigInteger.valueOf(row.liabilities.minorUnits) &&
            BigInteger.valueOf(row.cash.minorUnits) + assets - liabilities == BigInteger.valueOf(row.total.minorUnits)
    }
}

/** Current observation only. Historical totals come exclusively from immutable stored captures. */
object WealthCalculator {
    fun calculate(source: WealthSource, today: LocalDate): AppResult<WealthOverview> {
        if (today.year !in 1..9999) return AppResult.Failure(AppError.INVALID_INPUT)
        val currency = source.ledger.currency
        for (item in source.items) {
            WealthRules.validateItem(WealthItemDraft(item.name, item.kind, item.category, item.acquiredOn,
                item.acquisitionCost, item.notes, item.active), currency, LocalDate.of(9999, 12, 31))?.let { return AppResult.Failure(it) }
        }
        val itemMap = source.items.associateBy { it.id }
        if (itemMap.size != source.items.size || source.values.map { it.itemId to it.date }.distinct().size != source.values.size ||
            source.captures.map { it.date }.distinct().size != source.captures.size) return AppResult.Failure(AppError.INVALID_INPUT)
        for (value in source.values) {
            val item = itemMap[value.itemId] ?: return AppResult.Failure(AppError.INVALID_INPUT)
            WealthRules.validateValue(WealthValueDraft(value.itemId, value.date, value.amount, value.notes), item,
                currency, LocalDate.of(9999, 12, 31))?.let { return AppResult.Failure(it) }
        }
        if (source.captures.any { !WealthRules.validCapture(it, currency) }) return AppResult.Failure(AppError.INVALID_INPUT)
        val cashResult = RecordedCashCalculator.calculate(source.ledger, source.transactions, today)
        if (cashResult is AppResult.Failure && cashResult.error != AppError.OUT_OF_RANGE) return cashResult
        val cash = (cashResult as? AppResult.Success)?.value
        val groups = source.values.filter { it.date <= today }.groupBy { it.itemId }
        var assetSum = BigInteger.ZERO; var liabilitySum = BigInteger.ZERO
        var missingAssets = 0; var missingLiabilities = 0
        val rows = source.items.sortedWith(compareByDescending<WealthItem> { it.active }.thenBy { it.name }.thenBy { it.id }).map { item ->
            val included = item.active && item.acquiredOn <= today
            val latest = groups[item.id]?.maxWithOrNull(compareBy<WealthValue> { it.date }.thenBy { it.id })
            if (included) {
                if (latest == null) { if (item.kind == WealthKind.ASSET) missingAssets++ else missingLiabilities++ }
                else if (item.kind == WealthKind.ASSET) assetSum += BigInteger.valueOf(latest.amount.minorUnits)
                else liabilitySum += BigInteger.valueOf(latest.amount.minorUnits)
            }
            WealthItemStatus(item, latest, latest?.let { ChronoUnit.DAYS.between(it.date, today) }, included)
        }
        fun money(value: BigInteger) = try { value.toMoneyExact(currency) } catch (_: ArithmeticException) { null }
        val assetsKnown = money(assetSum); val liabilitiesKnown = money(liabilitySum)
        val assets = assetsKnown.takeIf { missingAssets == 0 }; val liabilities = liabilitiesKnown.takeIf { missingLiabilities == 0 }
        val total = if (cash != null && assets != null && liabilities != null)
            money(BigInteger.valueOf(cash.minorUnits) + assetSum - liabilitySum) else null
        val history = source.captures.sortedWith(compareByDescending<WealthCapture> { it.date }.thenBy { it.id })
        val change = if (history.size >= 2) money(BigInteger.valueOf(history[0].total.minorUnits) - BigInteger.valueOf(history[1].total.minorUnits)) else null
        return AppResult.Success(WealthOverview(today, cash, cashResult is AppResult.Failure, assets, liabilities, total,
            assetsKnown == null, liabilitiesKnown == null, cash != null && assets != null && liabilities != null && total == null,
            missingAssets, missingLiabilities, rows, source.values.count { it.date > today }, source.items.count { it.active && it.acquiredOn > today },
            history, change, history.size >= 2 && change == null))
    }
    fun capture(source: WealthSource, today: LocalDate, id: String, at: Instant): AppResult<WealthCapture> {
        if (source.captures.any { it.date == today }) return AppResult.Failure(AppError.SNAPSHOT_EXISTS)
        val result = calculate(source, today)
        if (result is AppResult.Failure) return result
        val current = (result as AppResult.Success).value
        if (current.total == null) return AppResult.Failure(if (current.cashOverflow || current.assetsOverflow ||
            current.liabilitiesOverflow || current.totalOverflow) AppError.OUT_OF_RANGE else AppError.WEALTH_INCOMPLETE)
        return AppResult.Success(WealthCapture(id, today, current.cash!!, current.assets!!, current.liabilities!!, current.total,
            current.items.filter { it.included }.map { row -> WealthCaptureLine(row.item.id, row.item.name, row.item.kind,
                row.item.category, row.latest!!.amount, row.latest.date) }, at))
    }
}
