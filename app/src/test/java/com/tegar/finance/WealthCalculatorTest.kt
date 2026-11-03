package com.tegar.finance

import com.tegar.finance.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WealthCalculatorTest {
    private val today = LocalDate.of(2028, 3, 10)
    private fun item(id: String = "asset", kind: WealthKind = WealthKind.ASSET, active: Boolean = true, cost: Long? = null) =
        WealthItem(id, "Item $id", kind, if (kind == WealthKind.ASSET) WealthCategory.PROPERTY else WealthCategory.LOAN,
            today.minusYears(1), cost?.let { Money(it) }, "", active, Instant.EPOCH, null)
    private fun value(id: String = "v", item: String = "asset", amount: Long = 200, date: LocalDate = today) =
        WealthValue(id, item, date, Money(amount), "", Instant.EPOCH, null)
    private fun source(cash: Long = 100, items: List<WealthItem> = emptyList(), values: List<WealthValue> = emptyList()) =
        WealthSource(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), today.minusMonths(2), Money(cash), Instant.EPOCH),
            emptyList(), items, values, emptyList())
    private fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
    private fun total(source: WealthSource, date: LocalDate = today) = WealthCalculator.calculate(source, date).ok()
    private fun transaction(type: TransactionType, amount: Long, date: LocalDate = today) =
        FinanceTransaction(type.name, type, Money(amount), type.name, date, "", Instant.EPOCH, null)
    private fun capture(data: WealthSource, date: LocalDate = today, id: String = "capture") = WealthCalculator.capture(data, date, id, Instant.EPOCH).ok()
    @Test fun emptyTrackedScopeHasCashOnceAndNoInventedHistoricalValues() {
        val result = total(source()); assertEquals(Money(100), result.total); assertEquals(Money(0), result.assets)
        assertEquals(Money(0), result.liabilities); assertTrue(result.history.isEmpty()); assertNull(result.historyChange)
    }
    @Test fun assetsAddAndLiabilitiesSubtractWithoutChangingCash() {
        val data = source(items = listOf(item(), item("debt", WealthKind.LIABILITY)), values = listOf(value(), value("d", "debt", 70)))
        val result = total(data); assertEquals(Money(230), result.total); assertEquals(Money(100), result.cash)
        assertEquals(Money(200), result.assets); assertEquals(Money(70), result.liabilities)
    }
    @Test fun costIsNotAddedToValueOrSubstitutedForMissingValue() {
        val data = source(items = listOf(item(cost = 999)))
        val missing = total(data); assertNull(missing.assets); assertNull(missing.total); assertEquals(1, missing.missingAssets)
        assertEquals(Money(300), total(data.copy(values = listOf(value()))).total)
    }
    @Test fun cashPurchaseAndNonCashAssetAreCountedExactlyOnce() {
        val data = source(1000, listOf(item(cost = 200)), listOf(value())).copy(transactions = listOf(transaction(TransactionType.EXPENSE, 200)))
        assertEquals(Money(800), total(data).cash); assertEquals(Money(1000), total(data).total)
    }
    @Test fun contributionsReduceCashWithoutInventingInvestmentHoldings() {
        val data = source(1000).copy(transactions = listOf(transaction(TransactionType.INVESTMENT, 200)))
        assertEquals(Money(800), total(data).total); assertEquals(Money(0), total(data).assets)
    }
    @Test fun goalEarmarksStayInsideTheCashComponent() {
        val data = source(1000, listOf(item()), listOf(value()))
        val goal = FinancialGoal("goal", "Trip", GoalKind.SAVING, Money(900), Money(800), today, today.plusMonths(2), GoalPriority.NORMAL, GoalState.ACTIVE, Instant.EPOCH, null)
        val budget = BudgetSnapshot(data.ledger, emptyList(), emptyList(), emptyList(), listOf(goal))
        val home = HomeDashboardCalculator.calculate(budget, today).ok()
        assertEquals(Money(800), home.reservedForGoals); assertEquals(Money(200), home.unreservedCash)
        assertEquals(Money(1200), total(data).total)
    }
    @Test fun negativeCashAndNegativeNetWorthRemainValid() {
        val data = source(-100, listOf(item("debt", WealthKind.LIABILITY)), listOf(value(item = "debt", amount = 250)))
        assertEquals(Money(-350), total(data).total); assertEquals(Money(-100), total(data).cash)
    }
    @Test fun zeroIsAKnownObservationAndAllowsCapture() {
        val data = source(0, listOf(item()), listOf(value(amount = 0)))
        assertEquals(Money(0), total(data).total); assertEquals(Money(0), capture(data).total)
    }
    @Test fun missingLiabilityDoesNotBecomeZeroOrPartialTotal() {
        val result = total(source(items = listOf(item(), item("debt", WealthKind.LIABILITY)), values = listOf(value())))
        assertEquals(Money(200), result.assets); assertNull(result.liabilities); assertNull(result.total)
        assertEquals(1, result.missingLiabilities)
    }
    @Test fun latestDateWinsAndOlderObservationsRemainAvailable() {
        val result = total(source(items = listOf(item()), values = listOf(value("old", amount = 999, date = today.minusDays(10)), value("now", amount = 150, date = today.minusDays(2)))))
        assertEquals(Money(250), result.total); assertEquals("now", result.items.single().latest!!.id)
        assertEquals(2L, result.items.single().ageDays)
    }
    @Test fun futureValuesAndItemsAreExcludedWhenTheClockMovesBack() {
        val future = item("future").copy(acquiredOn = today.plusDays(1))
        val result = total(source(items = listOf(item(), future), values = listOf(value("past", amount = 120, date = today.minusDays(1)), value("future", amount = 900, date = today.plusDays(1)))))
        assertEquals(Money(220), result.total); assertEquals(1, result.futureValues); assertEquals(1, result.futureItems)
        assertEquals(1L, result.items.first { it.item.id == "asset" }.ageDays)
    }
    @Test fun futureOnlyValueIsUnknownToday() {
        val result = total(source(items = listOf(item()), values = listOf(value(date = today.plusDays(1)))))
        assertNull(result.total); assertEquals(1, result.missingAssets)
    }
    @Test fun archivedAssetsAndLiabilitiesKeepHistoryButDoNotAffectCurrentTotal() {
        val data = source(items = listOf(item(active = false), item("debt", WealthKind.LIABILITY, false)), values = listOf(value(), value("d", "debt", 50)))
        val result = total(data); assertEquals(Money(100), result.total); assertTrue(result.items.none { it.included })
        assertEquals(2, result.items.size); assertTrue(capture(data).lines.isEmpty())
    }
    @Test fun acquisitionBeforeLedgerCoverageIsAllowedButValueBeforeAcquisitionIsRejected() {
        val row = item(); assertNull(WealthRules.validateItem(WealthItemDraft(row.name, row.kind, row.category, row.acquiredOn, null), CurrencyCode.IDR, today))
        val bad = source(items = listOf(row), values = listOf(value(date = row.acquiredOn.minusDays(1))))
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), WealthCalculator.calculate(bad, today))
    }
    @Test fun missingCashCoverageBlocksCaptureEvenWithKnownAssets() {
        val data = source(items = listOf(item()), values = listOf(value())).let { it.copy(ledger = it.ledger.copy(trackedFromDate = today.plusDays(1))) }
        assertNull(total(data).cash); assertNull(total(data).total)
        assertEquals(AppResult.Failure(AppError.WEALTH_INCOMPLETE), WealthCalculator.capture(data, today, "x", Instant.EPOCH))
    }
    @Test fun missingActiveValueBlocksCapture() {
        val data = source(items = listOf(item()))
        assertEquals(AppResult.Failure(AppError.WEALTH_INCOMPLETE), WealthCalculator.capture(data, today, "x", Instant.EPOCH))
    }
    @Test fun snapshotFreezesOriginalNamesValueDatesCashAndAmountsAcrossSourceChanges() {
        val original = source(items = listOf(item(cost = 999)), values = listOf(value(date = today.minusDays(10))))
        val saved = capture(original); val changed = source(500).copy(captures = listOf(saved))
        val result = total(changed); assertEquals(Money(500), result.total)
        assertEquals(Money(300), result.history.single().total); assertEquals("Item asset", saved.lines.single().name)
        assertEquals(today.minusDays(10), saved.lines.single().valuedOn); assertEquals(Money(100), saved.cash)
        assertEquals(saved, result.history.single()); assertEquals(WealthRules.WITH_INVESTMENTS, saved.method)
    }
    @Test fun historicalSnapshotsAreSortedAndChangeUsesOnlyTheLatestTwoDates() {
        val first = capture(source(10), today.minusDays(5), "a"); val second = capture(source(70), today.minusDays(2), "b")
        val third = capture(source(30), today, "c")
        val result = total(source(900).copy(captures = listOf(second, first, third)))
        assertEquals(listOf("c", "b", "a"), result.history.map { it.id }); assertEquals(Money(-40), result.historyChange)
    }
    @Test fun duplicateCaptureDateRequiresExplicitDeletion() {
        val data = source(); val saved = capture(data)
        assertEquals(AppResult.Failure(AppError.SNAPSHOT_EXISTS), WealthCalculator.capture(data.copy(captures = listOf(saved)), today, "new", Instant.EPOCH))
    }
    @Test fun assetAggregateOverflowDoesNotHideCashOrWrapToNegative() {
        val data = source(0, listOf(item(), item("b")), listOf(value(amount = Long.MAX_VALUE), value("b", "b", 1)))
        val result = total(data); assertNull(result.assets); assertNull(result.total); assertTrue(result.assetsOverflow)
        assertEquals(Money(0), result.cash); assertEquals(Money(0), result.liabilities)
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), WealthCalculator.capture(data, today, "x", Instant.EPOCH))
    }
    @Test fun liabilityOverflowIsLocalAndCannotBeCancelledByAssets() {
        val data = source(0, listOf(item(), item("d", WealthKind.LIABILITY), item("e", WealthKind.LIABILITY)),
            listOf(value(amount = Long.MAX_VALUE), value("d", "d", Long.MAX_VALUE), value("e", "e", 1)))
        val result = total(data); assertTrue(result.liabilitiesOverflow); assertEquals(Money(Long.MAX_VALUE), result.assets); assertNull(result.total)
    }
    @Test fun totalOverflowAndCashOverflowAreExplicit() {
        val result = total(source(Long.MAX_VALUE, listOf(item()), listOf(value(amount = 1))))
        assertTrue(result.totalOverflow); assertNull(result.total); assertEquals(Money(1), result.assets)
        val cash = total(source(Long.MAX_VALUE).copy(transactions = listOf(transaction(TransactionType.INCOME, 1))))
        assertTrue(cash.cashOverflow); assertNull(cash.cash); assertNull(cash.total)
    }
    @Test fun exactFinalSumCanFitEvenWhenAnIntermediateAdditionWouldOverflow() {
        val result = total(source(Long.MAX_VALUE, listOf(item(), item("d", WealthKind.LIABILITY)), listOf(value(amount = Long.MAX_VALUE), value("d", "d", Long.MAX_VALUE))))
        assertEquals(Money(Long.MAX_VALUE), result.total); assertFalse(result.totalOverflow)
    }
    @Test fun historicalDeltaOverflowNeverHidesValidStoredCaptures() {
        val low = capture(source(Long.MIN_VALUE), today.minusDays(1), "low"); val high = capture(source(Long.MAX_VALUE))
        val result = total(source().copy(captures = listOf(low, high)))
        assertTrue(result.historyChangeOverflow); assertNull(result.historyChange); assertEquals(2, result.history.size)
    }
    @Test fun corruptedSnapshotEquationAndComponentsAreRejected() {
        val data = source(items = listOf(item()), values = listOf(value())); val saved = capture(data)
        for (bad in listOf(saved.copy(total = Money(1)), saved.copy(method = "INVENTED"), saved.copy(lines = emptyList()),
            saved.copy(lines = saved.lines + saved.lines), saved.copy(lines = listOf(saved.lines.single().copy(valuedOn = today.plusDays(1)))))) {
            assertFalse(WealthRules.validCapture(bad, CurrencyCode.IDR))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), WealthCalculator.calculate(data.copy(captures = listOf(bad)), today))
        }
    }
    @Test fun duplicateItemsValuesAndCaptureDatesAreRejectedAsAmbiguous() {
        val data = source(items = listOf(item()), values = listOf(value())); val saved = capture(data)
        for (bad in listOf(data.copy(items = data.items + data.items), data.copy(values = data.values + data.values), data.copy(captures = listOf(saved, saved.copy(id = "other")))))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), WealthCalculator.calculate(bad, today))
    }
    @Test fun strictItemAndValueRulesValidateCurrencyKindCategoriesDatesAndBounds() {
        val item = item(); val draft = WealthItemDraft(item.name, item.kind, item.category, item.acquiredOn, null)
        for (bad in listOf(draft.copy(name = " "), draft.copy(name = "x".repeat(81)), draft.copy(category = WealthCategory.CARD),
            draft.copy(acquisitionCost = Money(-1)), draft.copy(acquiredOn = today.plusDays(1)), draft.copy(notes = "x".repeat(501)),
            draft.copy(kind = WealthKind.LIABILITY, category = WealthCategory.LOAN, acquisitionCost = Money(1))))
            assertEquals(AppError.INVALID_INPUT, WealthRules.validateItem(bad, CurrencyCode.IDR, today))
        assertEquals(AppError.CURRENCY_MISMATCH, WealthRules.validateItem(draft.copy(acquisitionCost = Money(0, CurrencyCode("USD"))), CurrencyCode.IDR, today))
        val v = WealthValueDraft(item.id, today, Money(0))
        for (bad in listOf(v.copy(itemId = "other"), v.copy(date = today.plusDays(1)), v.copy(amount = Money(-1)), v.copy(notes = "x".repeat(501))))
            assertEquals(AppError.INVALID_INPUT, WealthRules.validateValue(bad, item, CurrencyCode.IDR, today))
        assertNull(WealthRules.validateValue(v, item, CurrencyCode.IDR, today))
    }
}
