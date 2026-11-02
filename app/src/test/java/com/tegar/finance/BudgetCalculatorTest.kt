package com.tegar.finance

import com.tegar.finance.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class BudgetCalculatorTest {
    private val month = YearMonth.of(2028, 2)
    private val first = month.atDay(1)
    private fun row(amount: Long, day: LocalDate = first, type: TransactionType = TransactionType.EXPENSE, category: String = "food",
        currency: CurrencyCode = CurrencyCode.IDR) = FinanceTransaction("tx-$amount-$day-$category", type, Money(amount, currency),
        category, day, "", Instant.EPOCH, null)
    private fun result(rows: List<FinanceTransaction> = emptyList(), cap: Long? = 10000, today: LocalDate = first,
        start: LocalDate = first, target: YearMonth = month, category: String? = null): AppResult<BudgetMetrics> =
        BudgetCalculator.calculate(target, today, start, CurrencyCode.IDR, cap?.let { Money(it) }, rows, category)
    private fun AppResult<BudgetMetrics>.ok(): BudgetMetrics {
        assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value
    }
    @Test fun noAllocationIsDistinctFromZero() {
        val metrics = result(cap = null).ok()
        assertEquals(0L, metrics.actual!!.minorUnits); assertNull(metrics.allocation)
        assertNull(metrics.remaining); assertNull(metrics.utilizationPercent); assertNull(metrics.dailyAllowance)
        assertEquals(BudgetStatus.NO_BUDGET, metrics.status)
    }
    @Test fun zeroAllocationAndZeroUsageAreNeutralWithoutRatio() {
        val metrics = result(cap = 0).ok()
        assertEquals(BudgetStatus.NEUTRAL, metrics.status); assertNull(metrics.utilizationPercent)
        assertEquals(0L, metrics.dailyAllowance!!.minorUnits)
    }
    @Test fun zeroAllocationWithExpenseIsOverBudget() {
        val metrics = result(listOf(row(1)), cap = 0).ok()
        assertEquals(-1L, metrics.remaining!!.minorUnits); assertNull(metrics.utilizationPercent)
        assertEquals(BudgetStatus.OVER_BUDGET, metrics.status); assertEquals(0L, metrics.dailyAllowance!!.minorUnits)
    }
    @Test fun negativeRemainingNeverBecomesPositiveAllowance() {
        val metrics = result(listOf(row(20000))).ok()
        assertEquals(-10000L, metrics.remaining!!.minorUnits); assertEquals(0L, metrics.dailyAllowance!!.minorUnits)
    }
    @Test fun firstDayIncludesTodayAndProjectsFullLeapMonth() {
        val metrics = result(listOf(row(100))).ok()
        assertEquals(1, metrics.elapsedDays); assertEquals(29, metrics.remainingDays)
        assertEquals(2900L, metrics.projection!!.minorUnits); assertEquals(9900L / 29, metrics.dailyAllowance!!.minorUnits)
    }
    @Test fun finalDayHasOneDayLeftAndProjectionEqualsActual() {
        val metrics = result(listOf(row(1000)), today = month.atEndOfMonth()).ok()
        assertEquals(29, metrics.elapsedDays); assertEquals(1, metrics.remainingDays)
        assertEquals(1000L, metrics.projection!!.minorUnits); assertEquals(9000L, metrics.dailyAllowance!!.minorUnits)
    }
    @Test fun closedMonthHasZeroDailyAllowance() {
        val metrics = result(listOf(row(1000)), today = month.plusMonths(1).atDay(1)).ok()
        assertEquals(0, metrics.remainingDays); assertEquals(0L, metrics.dailyAllowance!!.minorUnits)
        assertEquals(1000L, metrics.projection!!.minorUnits)
    }
    @Test fun nonLeapFebruaryAndThirtyOneDayMonthUseActualCalendarLengths() {
        for ((target, days) in listOf(YearMonth.of(2027, 2) to 28, YearMonth.of(2028, 1) to 31)) {
            val metrics = result(listOf(row(10, target.atDay(1))), target = target,
                today = target.atDay(1), start = target.atDay(1)).ok()
            assertEquals(days, metrics.remainingDays); assertEquals(days * 10L, metrics.projection!!.minorUnits)
        }
    }
    @Test fun projectionsRoundOnceFromUnroundedDailyPace() {
        val target = YearMonth.of(2028, 1)
        val metrics = result(listOf(row(1, target.atDay(1))), target = target, today = target.atDay(3), start = target.atDay(1)).ok()
        assertEquals(0L, metrics.dailyAverage!!.minorUnits)
        assertEquals(10L, metrics.projection!!.minorUnits)
    }
    @Test fun projectionUsesHalfEvenAtExactTies() {
        val target = YearMonth.of(2028, 1)
        for ((minor, expected) in listOf(1L to 16L, 3L to 46L)) {
            assertEquals(expected, result(listOf(row(minor, target.atDay(1))), target = target,
                today = target.atDay(2), start = target.atDay(1)).ok().projection!!.minorUnits)
        }
    }
    @Test fun thresholdsUseExactAmountsAndEqualityBoundaries() {
        for ((spent, expected) in listOf(7999L to BudgetStatus.SAFE, 8000L to BudgetStatus.WATCH,
            9499L to BudgetStatus.WATCH, 9500L to BudgetStatus.HIGH, 10000L to BudgetStatus.HIGH, 10001L to BudgetStatus.OVER_BUDGET)) {
            assertEquals(expected, result(listOf(row(spent))).ok().status)
        }
    }
    @Test fun displayedPercentDoesNotRoundUpAcrossAStatusThreshold() {
        val metrics = result(listOf(row(799999)), cap = 1000000).ok()
        assertEquals("79.99", metrics.utilizationPercent!!.toPlainString())
        assertEquals(BudgetStatus.SAFE, metrics.status)
    }
    @Test fun incomeInvestmentsFutureAndAdjacentMonthsDoNotInflateConsumption() {
        val rows = listOf(row(100), row(200, type = TransactionType.INCOME), row(300, type = TransactionType.INVESTMENT),
            row(400, first.plusDays(1)), row(500, first.minusDays(1)), row(600, month.plusMonths(1).atDay(1)))
        assertEquals(100L, result(rows).ok().actual!!.minorUnits)
    }
    @Test fun overallIncludesUnallocatedCategoriesWhileCategoryFilterIsExact() {
        val rows = listOf(row(100), row(200, category = "unallocated"))
        assertEquals(300L, result(rows).ok().actual!!.minorUnits)
        assertEquals(100L, result(rows, category = "food").ok().actual!!.minorUnits)
    }
    @Test fun partialCoverageDoesNotInventEarlierHistoryOrSafeSpending() {
        val metrics = result(listOf(row(100, first.plusDays(9))), today = first.plusDays(10), start = first.plusDays(9)).ok()
        assertEquals(BudgetCoverage.PARTIAL, metrics.coverage); assertEquals(2, metrics.elapsedDays)
        assertEquals(100L, metrics.actual!!.minorUnits); assertEquals(9900L, metrics.remaining!!.minorUnits)
        assertNull(metrics.projection); assertNull(metrics.dailyAllowance)
    }
    @Test fun untrackedAndFutureMonthsHaveUnknownActuals() {
        for (metrics in listOf(result(start = month.plusMonths(1).atDay(1)).ok(),
            result(today = first.minusDays(1)).ok())) {
            assertNull(metrics.actual); assertNull(metrics.remaining); assertNull(metrics.dailyAllowance)
            assertEquals(BudgetStatus.NO_DATA, metrics.status)
        }
    }
    @Test fun zeroSpendingIsKnownWhenTrackingCoversThePeriod() {
        val metrics = result(today = first.plusDays(14)).ok()
        assertEquals(0L, metrics.actual!!.minorUnits); assertEquals(0L, metrics.projection!!.minorUnits)
        assertEquals(10000L / 15, metrics.dailyAllowance!!.minorUnits)
    }
    @Test fun exactLongMaximumIsSupportedButAggregateOverflowIsVisible() {
        assertEquals(Long.MAX_VALUE, result(listOf(row(Long.MAX_VALUE)), cap = Long.MAX_VALUE,
            today = month.atEndOfMonth()).ok().actual!!.minorUnits)
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), result(listOf(row(Long.MAX_VALUE), row(1))))
    }
    @Test fun projectionOverflowDoesNotHideValidRecordedActuals() {
        val metrics = result(listOf(row(Long.MAX_VALUE)), cap = Long.MAX_VALUE).ok()
        assertEquals(Long.MAX_VALUE, metrics.actual!!.minorUnits)
        assertTrue(metrics.projectionOverflow); assertNull(metrics.projection)
    }
    @Test fun invalidMoneyAndCurrencyAreRejected() {
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), result(cap = -1))
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), result(listOf(row(1, currency = CurrencyCode("USD")))))
    }
    @Test fun strictMonthKeysRejectInvalidAndOutOfRangeYears() {
        for (key in listOf("2028-2", "2028-13", "0000-01", "-001-01", "2028-01-01", " 2028-01")) assertNull(BudgetMonths.parse(key))
        assertEquals(month, BudgetMonths.parse("2028-02"))
        assertFalse(BudgetMonths.valid(YearMonth.of(10000, 1)))
    }
}
