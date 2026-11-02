package com.tegar.finance

import com.tegar.finance.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ReportCalculatorTest {
    private val february = YearMonth.of(2028, 2)
    private fun row(amount: Long, date: String = "2028-02-10", type: TransactionType = TransactionType.EXPENSE,
        category: String = "food") = FinanceTransaction("$type-$amount-$date-$category", type, Money(amount), category,
        LocalDate.parse(date), "", Instant.EPOCH, null)
    private fun source(rows: List<FinanceTransaction> = emptyList(), start: String = "2027-01-01") =
        BudgetSnapshot(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), LocalDate.parse(start), Money(999), Instant.EPOCH),
            emptyList(), emptyList(), rows)
    private fun report(rows: List<FinanceTransaction> = emptyList(), date: String = "2028-02-15", today: String = "2028-03-01",
        kind: ReportKind = ReportKind.MONTH, start: String = "2027-01-01") =
        ReportCalculator.calculate(source(rows, start), ReportSelection(kind, LocalDate.parse(date)), LocalDate.parse(today))
    private fun AppResult<FinanceReport>.ok(): FinanceReport {
        assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value
    }
    @Test fun weeklyReportsUseMondayAndHalfOpenBoundaries() {
        val data = report(listOf(row(1,"2028-03-05"),row(2,"2028-03-06"),row(3,"2028-03-12"),row(4,"2028-03-13")),
            date="2028-03-08",today="2028-03-13",kind=ReportKind.WEEK).ok()
        assertEquals(LocalDate.of(2028,3,6),data.current.period.start)
        assertEquals(LocalDate.of(2028,3,13),data.current.period.endExclusive)
        assertEquals(5L,data.current.totals!!.consumption.minorUnits); assertEquals(7,data.buckets.size)
    }
    @Test fun leapMonthIncludesFebruaryTwentyNineButNotAdjacentMonths() {
        val data = report(listOf(row(1,"2028-01-31"),row(2,"2028-02-29"),row(3,"2028-03-01"))).ok()
        assertEquals(2L,data.current.totals!!.consumption.minorUnits); assertEquals(29,data.buckets.size)
        assertEquals(ReportCoverage.COMPLETE,data.current.coverage)
    }
    @Test fun nonLeapMonthsAndDecemberRolloverHaveExactBucketCounts() {
        assertEquals(28,report(date="2027-02-10",today="2027-03-01").ok().buckets.size)
        val data = report(listOf(row(1,"2028-12-31"),row(2,"2029-01-01")),date="2028-12-15",today="2029-01-01").ok()
        assertEquals(31,data.buckets.size); assertEquals(1L,data.current.totals!!.consumption.minorUnits)
    }
    @Test fun yearlyReportsHaveTwelveCalendarMonthsIncludingLeapFebruary() {
        val data = report(listOf(row(1,"2028-01-01"),row(2,"2028-02-29"),row(3,"2028-12-31"),row(4,"2029-01-01")),
            date="2028-06-01",today="2029-01-01",kind=ReportKind.YEAR).ok()
        assertEquals(12,data.buckets.size); assertEquals(6L,data.current.totals!!.consumption.minorUnits)
        assertEquals(2L,data.buckets[1].totals!!.consumption.minorUnits)
    }
    @Test fun incomeConsumptionContributionsAndSavingAreDistinctAndOpeningCashExcluded() {
        val data = report(listOf(row(1000,type=TransactionType.INCOME),row(200),row(300,type=TransactionType.INVESTMENT))).ok()
        val totals=data.current.totals!!
        assertEquals(1000L,totals.income.minorUnits); assertEquals(200L,totals.consumption.minorUnits)
        assertEquals(300L,totals.contributions.minorUnits); assertEquals(800L,totals.saving.minorUnits)
        assertEquals(500L,totals.netCashFlow!!.minorUnits); assertEquals("80.00",totals.savingRatePercent!!.toPlainString())
    }
    @Test fun currentPeriodComparesEqualElapsedDaysInsteadOfPreviousFullMonth() {
        val data=report(listOf(row(20),row(10,"2028-01-10"),row(999,"2028-01-30")),today="2028-02-10").ok()
        val comparison=data.comparison!!
        assertTrue(comparison.alignedDays); assertEquals(LocalDate.of(2028,1,11),comparison.previous.period.endExclusive)
        assertEquals(10L,comparison.expenseDelta.minorUnits); assertEquals("100.00",comparison.expenseChangePercent!!.toPlainString())
    }
    @Test fun completePeriodsCompareWholeCalendarPeriodsWithDifferentLengths() {
        val data=report(listOf(row(20),row(10,"2028-01-31"))).ok()
        assertFalse(data.comparison!!.alignedDays)
        assertEquals(LocalDate.of(2028,2,1),data.comparison.previous.period.endExclusive)
        assertEquals(10L,data.comparison.expenseDelta.minorUnits)
    }
    @Test fun incompleteCurrentPeriodWithLongerElapsedSpanThanPriorPeriodCannotCompare() {
        val data=report(date="2028-03-15",today="2028-03-30").ok()
        assertNull(data.comparison); assertEquals(ComparisonUnavailable.PREVIOUS_SHORTER,data.comparisonUnavailable)
    }
    @Test fun partialTrackingDoesNotPretendEarlierDaysWereZero() {
        val data=report(listOf(row(10,"2028-02-20")),start="2028-02-15").ok()
        assertEquals(ReportCoverage.PARTIAL_HISTORY,data.current.coverage)
        assertEquals(10L,data.current.totals!!.consumption.minorUnits)
        assertEquals(ComparisonUnavailable.PARTIAL_HISTORY,data.comparisonUnavailable)
        assertNull(data.buckets.first().totals)
        assertEquals(0L,data.buckets[14].totals!!.consumption.minorUnits)
    }
    @Test fun missingPreviousHistoryMakesComparisonUnavailableNotZero() {
        val data=report(start="2028-01-15").ok()
        assertEquals(ReportCoverage.COMPLETE,data.current.coverage)
        assertNull(data.comparison); assertEquals(ComparisonUnavailable.MISSING_PREVIOUS_HISTORY,data.comparisonUnavailable)
    }
    @Test fun untrackedPeriodHasUnknownTotalsAndUnknownBuckets() {
        val data=report(date="2020-02-10").ok()
        assertEquals(ReportCoverage.UNTRACKED,data.current.coverage)
        assertNull(data.current.totals); assertTrue(data.buckets.all { it.totals==null })
    }
    @Test fun futureTransactionsAreCountedButNeverDrawnAsActuals() {
        val data=report(listOf(row(500,"2099-02-10")),date="2099-02-01").ok()
        assertEquals(ReportCoverage.FUTURE,data.current.coverage); assertNull(data.current.totals)
        assertEquals(1,data.futureExcludedCount); assertTrue(data.buckets.all { it.totals==null })
    }
    @Test fun inProgressPeriodExcludesFutureEntriesWithinSameCalendarWindow() {
        val data=report(listOf(row(1,"2028-02-10"),row(2,"2028-02-11")),today="2028-02-10").ok()
        assertEquals(1L,data.current.totals!!.consumption.minorUnits); assertEquals(1,data.futureExcludedCount)
        assertEquals(ReportCoverage.FUTURE,data.buckets[10].coverage); assertNull(data.buckets[10].totals)
    }
    @Test fun coveredZeroActivityIsZeroButSavingAndGrowthRatesRemainUndefinedAtZeroIncome() {
        val data=report().ok()
        assertEquals(0L,data.current.totals!!.income.minorUnits); assertEquals(0L,data.current.totals.consumption.minorUnits)
        assertNull(data.current.totals.savingRatePercent); assertNull(data.comparison!!.expenseChangePercent)
        assertTrue(data.buckets.all { it.totals!!.consumption.minorUnits==0L })
    }
    @Test fun zeroIncomeWithExpensesShowsNegativeSavingAndNoSavingRate() {
        val totals=report(listOf(row(100))).ok().current.totals!!
        assertEquals(-100L,totals.saving.minorUnits); assertNull(totals.savingRatePercent)
    }
    @Test fun zeroPreviousAmountsHaveAbsoluteChangesWithoutInfinitePercentage() {
        val comparison=report(listOf(row(100))).ok().comparison!!
        assertEquals(100L,comparison.expenseDelta.minorUnits); assertNull(comparison.expenseChangePercent)
    }
    @Test fun decreasesAndNegativeSavingChangesKeepTheirSigns() {
        val comparison=report(listOf(row(200,type=TransactionType.INCOME),row(400),
            row(500,"2028-01-10",TransactionType.INCOME),row(100,"2028-01-10"))).ok().comparison!!
        assertEquals(-300L,comparison.incomeDelta.minorUnits); assertEquals("-60.00",comparison.incomeChangePercent!!.toPlainString())
        assertEquals(-600L,comparison.savingDelta.minorUnits)
    }
    @Test fun bucketsReconcileExactlyWithSelectedTotalsForAllThreeSeries() {
        val data=report(listOf(row(1,"2028-02-01",TransactionType.INCOME),row(2,"2028-02-10"),
            row(3,"2028-02-29",TransactionType.INVESTMENT))).ok()
        assertEquals(data.current.totals!!.income.minorUnits,data.buckets.sumOf { it.totals!!.income.minorUnits })
        assertEquals(data.current.totals.consumption.minorUnits,data.buckets.sumOf { it.totals!!.consumption.minorUnits })
        assertEquals(data.current.totals.contributions.minorUnits,data.buckets.sumOf { it.totals!!.contributions.minorUnits })
    }
    @Test fun categorySharesIncludeUnallocatedCategoriesAndHaveStableTies() {
        val data=report(listOf(row(1,category="b"),row(1,category="a"),row(1,category="c"),
            row(999,type=TransactionType.INVESTMENT,category="investment"))).ok()
        assertEquals(listOf("a","b","c"),data.categories.map { it.categoryId })
        assertTrue(data.categories.all { it.sharePercent.toPlainString()=="33.33" })
    }
    @Test fun selectedTotalOverflowFailsClosed() {
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE),report(listOf(row(Long.MAX_VALUE),row(1))))
    }
    @Test fun netCashFlowOverflowDoesNotHideSupportedGrossTotals() {
        val totals=report(listOf(row(Long.MAX_VALUE),row(Long.MAX_VALUE,type=TransactionType.INVESTMENT))).ok().current.totals!!
        assertEquals(Long.MAX_VALUE,totals.consumption.minorUnits); assertTrue(totals.netCashFlowOverflow); assertNull(totals.netCashFlow)
    }
    @Test fun overflowingPriorTotalsDoNotHideValidCurrentReport() {
        val data=report(listOf(row(1),row(Long.MAX_VALUE,"2028-01-10"),row(1,"2028-01-11"))).ok()
        assertEquals(1L,data.current.totals!!.consumption.minorUnits)
        assertEquals(ComparisonUnavailable.OUT_OF_RANGE,data.comparisonUnavailable); assertNull(data.comparison)
    }
    @Test fun overflowingSignedComparisonDeltaIsUnavailableWithoutWrapping() {
        val data=report(listOf(row(Long.MAX_VALUE,type=TransactionType.INCOME),row(Long.MAX_VALUE,"2028-01-10"))).ok()
        assertEquals(Long.MAX_VALUE,data.current.totals!!.saving.minorUnits)
        assertEquals(ComparisonUnavailable.OUT_OF_RANGE,data.comparisonUnavailable)
    }
    @Test fun invalidAndMixedCurrencySourceRecordsAreRejected() {
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT),report(listOf(row(0))))
        val bad=source(listOf(row(1).copy(amount=Money(1,CurrencyCode("USD")))))
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH),
            ReportCalculator.calculate(bad,ReportSelection(ReportKind.MONTH,february.atDay(1)),LocalDate.of(2028,3,1)))
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT),report(listOf(row(1,"2026-01-01"))))
    }
    @Test fun strictDateParsingAndPeriodNavigationHandleYearBounds() {
        for (date in listOf("2028-02-30","2028-2-01","0000-01-01","10000-01-01"," 2028-02-01")) assertNull(ReportSelection.parseDate(date))
        assertEquals(LocalDate.of(2028,2,29),ReportSelection.parseDate("2028-02-29"))
        val selection=ReportSelection(ReportKind.MONTH,LocalDate.of(2028,12,31))
        assertEquals(LocalDate.of(2029,1,1),selection.move(1).date)
        val data=report(date="0001-01-01",today="0001-02-01",start="0001-01-01").ok()
        assertEquals(ComparisonUnavailable.UNSUPPORTED_PREVIOUS,data.comparisonUnavailable)
    }
}
