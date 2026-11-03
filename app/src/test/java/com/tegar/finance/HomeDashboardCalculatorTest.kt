package com.tegar.finance

import com.tegar.finance.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class HomeDashboardCalculatorTest {
    private val month = YearMonth.of(2028, 2)
    private val first = month.atDay(1)
    private fun row(amount: Long, type: TransactionType = TransactionType.EXPENSE, date: LocalDate = first,
        category: String = "food", id: String = "$type-$amount-$date", created: Long = 1) =
        FinanceTransaction(id, type, Money(amount), category, date, "", Instant.ofEpochMilli(created), null)
    private fun snapshot(rows: List<FinanceTransaction> = emptyList(), opening: Long = 0, cap: Long? = 10000,
        start: LocalDate = first): BudgetSnapshot {
        val ledger = Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), start, Money(opening), Instant.EPOCH)
        return BudgetSnapshot(ledger, cap?.let { listOf(MonthlyBudget("cap", month, Money(it))) } ?: emptyList(), emptyList(), rows)
    }
    private fun calculate(source: BudgetSnapshot = snapshot(), today: LocalDate = first) = HomeDashboardCalculator.calculate(source, today)
    private fun AppResult<HomeDashboard>.ok(): HomeDashboard {
        assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value
    }
    @Test fun emptyCoveredLedgerHasRealZeroAmountsButNoSavingRate() {
        val home = calculate().ok()
        assertEquals(0L, home.recordedCash!!.minorUnits); assertEquals(0L, home.monthIncome!!.minorUnits)
        assertEquals(0L, home.budget.actual!!.minorUnits); assertNull(home.savingRatePercent)
        assertEquals(0L, home.dailyLimit!!.minorUnits); assertTrue(home.recent.isEmpty())
    }
    @Test fun cashIncludesOpeningAndAllCoveredHistoryButMonthlyTotalsDoNot() {
        val january = first.minusDays(1)
        val source = snapshot(listOf(row(20000, TransactionType.INCOME, january), row(4000, date = january),
            row(2000, TransactionType.INVESTMENT, january), row(1000, TransactionType.INCOME), row(300),
            row(200, TransactionType.INVESTMENT)), opening = 10000, start = first.minusMonths(1))
        val home = calculate(source).ok()
        assertEquals(24500L, home.recordedCash!!.minorUnits)
        assertEquals(1000L, home.monthIncome!!.minorUnits); assertEquals(300L, home.budget.actual!!.minorUnits)
        assertEquals(200L, home.monthContributions!!.minorUnits); assertEquals(700L, home.monthSaving!!.minorUnits)
        assertEquals("70.00", home.savingRatePercent!!.toPlainString())
    }
    @Test fun investmentContributionsReduceCashWithoutInflatingConsumptionOrReducingSaving() {
        val home = calculate(snapshot(listOf(row(1000, TransactionType.INCOME), row(200), row(300, TransactionType.INVESTMENT)))).ok()
        assertEquals(500L, home.recordedCash!!.minorUnits)
        assertEquals(200L, home.budget.actual!!.minorUnits)
        assertEquals(800L, home.monthSaving!!.minorUnits)
    }
    @Test fun futureDatedEntriesNeverFundTodaysCashOrInflateActualsOrRecentActivity() {
        val source = snapshot(listOf(row(10, TransactionType.INCOME), row(999, TransactionType.INCOME, first.plusDays(1)),
            row(888, date = first.plusDays(1)), row(777, TransactionType.INVESTMENT, first.plusDays(1))))
        val home = calculate(source).ok()
        assertEquals(10L, home.recordedCash!!.minorUnits); assertEquals(10L, home.monthIncome!!.minorUnits)
        assertEquals(0L, home.budget.actual!!.minorUnits); assertEquals(3, home.futureCount)
        assertEquals(1, home.recordedCount); assertEquals(1, home.recent.size)
    }
    @Test fun dailyLimitIsCashBoundedAndFloorsIncludingToday() {
        val home = calculate(snapshot(opening = 100, cap = 10000)).ok()
        assertEquals(3L, home.dailyLimit!!.minorUnits)
        assertEquals(DailyLimitReason.CASH_LIMITED, home.dailyLimitReason)
        assertTrue(home.dailyLimit.minorUnits <= home.budget.dailyAllowance!!.minorUnits)
    }
    @Test fun dailyLimitIsBudgetBoundedEvenWithAbundantCash() {
        val home = calculate(snapshot(opening = 100000, cap = 100)).ok()
        assertEquals(3L, home.dailyLimit!!.minorUnits)
        assertEquals(DailyLimitReason.AVAILABLE, home.dailyLimitReason)
    }
    @Test fun negativeCashNeverProducesANegativeOrPositiveSpendingLimit() {
        val home = calculate(snapshot(listOf(row(1)), opening = 0)).ok()
        assertEquals(-1L, home.recordedCash!!.minorUnits); assertEquals(0L, home.dailyLimit!!.minorUnits)
    }
    @Test fun overspendingClampsLimitToZeroAndKeepsNegativeRemaining() {
        val home = calculate(snapshot(listOf(row(200)), opening = 10000, cap = 100)).ok()
        assertEquals(-100L, home.budget.remaining!!.minorUnits); assertEquals(0L, home.dailyLimit!!.minorUnits)
        assertEquals(BudgetStatus.OVER_BUDGET, home.budget.status)
    }
    @Test fun zeroBudgetDiffersFromMissingBudget() {
        val zero = calculate(snapshot(opening = 1000, cap = 0)).ok()
        assertEquals(0L, zero.dailyLimit!!.minorUnits); assertNull(zero.budget.utilizationPercent)
        val missing = calculate(snapshot(opening = 1000, cap = null)).ok()
        assertNull(missing.dailyLimit); assertEquals(DailyLimitReason.NO_OVERALL_BUDGET, missing.dailyLimitReason)
    }
    @Test fun categoryAllocationsAloneCannotInventAnOverallLimit() {
        val source = snapshot(cap = null).copy(categories = listOf(CategoryBudget("food-budget", month, "food", Money(999))))
        val home = calculate(source).ok()
        assertNull(home.dailyLimit); assertNull(home.budget.allocation)
    }
    @Test fun incompleteMonthKeepsRecordedCashButWithholdsDailyLimitAndProjection() {
        val today = first.plusDays(10)
        val home = calculate(snapshot(listOf(row(200, TransactionType.INCOME, today)), start = today), today).ok()
        assertEquals(200L, home.recordedCash!!.minorUnits); assertEquals(200L, home.monthIncome!!.minorUnits)
        assertNull(home.dailyLimit); assertNull(home.budget.projection)
        assertEquals(DailyLimitReason.PARTIAL_COVERAGE, home.dailyLimitReason)
    }
    @Test fun dateBeforeTrackingShowsUnknownAmountsRatherThanZero() {
        val home = calculate(snapshot(start = first.plusDays(1))).ok()
        assertNull(home.recordedCash); assertNull(home.monthIncome); assertNull(home.monthSaving)
        assertNull(home.dailyLimit); assertEquals(DailyLimitReason.NO_COVERAGE, home.dailyLimitReason)
    }
    @Test fun zeroIncomeHasNoSavingRateAndNegativeSavingRemainsVisible() {
        val home = calculate(snapshot(listOf(row(100)), opening = 1000)).ok()
        assertEquals(-100L, home.monthSaving!!.minorUnits); assertNull(home.savingRatePercent)
    }
    @Test fun savingRateCanBeNegativeAndUsesExplicitHalfEvenRounding() {
        val home = calculate(snapshot(listOf(row(3, TransactionType.INCOME), row(4)))).ok()
        assertEquals("-33.33", home.savingRatePercent!!.toPlainString())
    }
    @Test fun topCategoriesIncludeUnallocatedConsumptionOnlyAndHaveStableTies() {
        val home = calculate(snapshot(listOf(row(100, category = "a"), row(20, category = "a", id = "second"),
            row(120, category = "b"), row(999, TransactionType.INVESTMENT, category = "investment"),
            row(1, category = "c"), row(1, category = "d")))).ok()
        assertEquals(listOf("a", "b", "c"), home.topCategories.map { it.categoryId })
        assertEquals(listOf(120L, 120L, 1L), home.topCategories.map { it.amount.minorUnits })
    }
    @Test fun recentActivityUsesDateCreationAndIdRatherThanInputOrderAndIsBounded() {
        val rows = (1..7).map { row(it.toLong(), id = "id$it", created = it.toLong()) }
        val home = calculate(snapshot(rows.reversed())).ok()
        assertEquals(listOf("id7", "id6", "id5", "id4", "id3"), home.recent.map { it.id })
    }
    @Test fun recentDatesOutrankCreationTimesAndEqualTimesUseStableIds() {
        val rows = listOf(row(1, id = "a", created = 100), row(2, id = "b", created = 100),
            row(3, date = first.plusDays(1), id = "next-day", created = 1))
        assertEquals(listOf("next-day", "b", "a"), calculate(snapshot(rows), first.plusDays(1)).ok().recent.map { it.id })
    }
    @Test fun finalLeapDayHasOneDayLeftAndUsesExistingBudgetFormula() {
        val home = calculate(snapshot(listOf(row(100)), opening = 1000, cap = 600), month.atEndOfMonth()).ok()
        assertEquals(1, home.budget.remainingDays); assertEquals(500L, home.dailyLimit!!.minorUnits)
        val shared = BudgetCalculator.calculate(month, month.atEndOfMonth(), first, CurrencyCode.IDR, Money(600), listOf(row(100))) as AppResult.Success
        assertEquals(shared.value, home.budget)
    }
    @Test fun priorMonthBudgetDoesNotBecomeCurrentBudget() {
        val source = snapshot(cap = null).copy(monthly = listOf(MonthlyBudget("old", month.minusMonths(1), Money(1000))))
        assertNull(calculate(source).ok().budget.allocation)
    }
    @Test fun signedCashBoundsAreCheckedWithoutWrapping() {
        assertEquals(Long.MIN_VALUE, calculate(snapshot(opening = Long.MIN_VALUE)).ok().recordedCash!!.minorUnits)
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), calculate(snapshot(listOf(row(1)), opening = Long.MIN_VALUE)))
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), calculate(snapshot(listOf(row(1, TransactionType.INCOME)), opening = Long.MAX_VALUE)))
    }
    @Test fun largeHistoricalInflowsAndOutflowsCanCancelExactlyWithoutIntermediateOverflow() {
        val old = first.minusDays(1)
        val source = snapshot(listOf(row(Long.MAX_VALUE, TransactionType.INCOME, old), row(Long.MAX_VALUE, date = old)),
            start = first.minusMonths(1))
        assertEquals(0L, calculate(source).ok().recordedCash!!.minorUnits)
    }
    @Test fun monthlyAggregateOverflowFailsClosedEvenWhenNetCashWouldFit() {
        val source = snapshot(listOf(row(Long.MAX_VALUE, TransactionType.INCOME), row(1, TransactionType.INCOME), row(1)))
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), calculate(source))
    }
    @Test fun invalidOrMixedCurrencySourceIsRejected() {
        val wrong = row(1).copy(amount = Money(1, CurrencyCode("USD")))
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), calculate(snapshot(listOf(wrong))))
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), calculate(snapshot(listOf(row(0)))))
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), calculate(snapshot(listOf(row(1, date = first.minusDays(1))))))
    }
    @Test fun sharedFlowRefactorPreservesCashWhenPeriodNetFlowOverflowsButOpeningCashOffsetsIt() {
        val home=calculate(snapshot(listOf(row(Long.MAX_VALUE),row(Long.MAX_VALUE,type=TransactionType.INVESTMENT)),
            opening=Long.MAX_VALUE,cap=Long.MAX_VALUE)).ok()
        assertEquals(-Long.MAX_VALUE,home.recordedCash!!.minorUnits)
        assertEquals(-Long.MAX_VALUE,home.monthSaving!!.minorUnits)
    }
    @Test fun homeAndReportsShareTheSameRecordedFlowAccounting() {
        val source=snapshot(listOf(row(1000,TransactionType.INCOME),row(200),row(300,TransactionType.INVESTMENT)))
        val home=calculate(source).ok()
        val report=(ReportCalculator.calculate(source,ReportSelection(ReportKind.MONTH,first),first) as AppResult.Success).value
        assertEquals(home.monthIncome,report.current.totals!!.income)
        assertEquals(home.budget.actual,report.current.totals.consumption)
        assertEquals(home.monthContributions,report.current.totals.contributions)
        assertEquals(home.monthSaving,report.current.totals.saving)
        assertEquals(home.savingRatePercent,report.current.totals.savingRatePercent)
    }
}
