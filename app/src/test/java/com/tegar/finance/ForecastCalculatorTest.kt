package com.tegar.finance

import com.tegar.finance.domain.model.*
import java.math.BigDecimal
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ForecastCalculatorTest {
    private val today = LocalDate.of(2028, 3, 10)
    private fun row(amount: Long, date: String = "2028-01-10", type: TransactionType = TransactionType.EXPENSE) =
        FinanceTransaction("$date-$type-$amount", type, Money(amount), type.name, LocalDate.parse(date), "", Instant.EPOCH, null)
    private fun source(rows: List<FinanceTransaction> = emptyList(), tracked: String = "2028-01-01", caps: List<MonthlyBudget> = emptyList()) =
        BudgetSnapshot(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), LocalDate.parse(tracked), Money(90000), Instant.EPOCH), caps, emptyList(), rows)
    private fun forecast(data: BudgetSnapshot = source(), method: ForecastMethod = ForecastMethod.ROLLING, window: Int = 6,
        date: LocalDate = today): FinancialForecast {
        val result = ForecastCalculator.calculate(data, date, ForecastOptions(method, window))
        assertTrue(result.toString(), result is AppResult.Success)
        return (result as AppResult.Success).value
    }
    private fun FinancialForecast.fires(kind: ForecastRuleKind) = rules.any { it.kind == kind }
    private fun cap(amount: Long, month: String = "2028-04") = MonthlyBudget("cap", YearMonth.parse(month), Money(amount))
    private fun twoMonths() = source(listOf(row(100), row(300, "2028-02-10"),
        row(1000, type = TransactionType.INCOME), row(3000, "2028-02-10", TransactionType.INCOME),
        row(50, type = TransactionType.INVESTMENT), row(150, "2028-02-10", TransactionType.INVESTMENT)))

    @Test fun noCompleteMonthsHaveNoForecastOrInventedZero() {
        val data = forecast(source(tracked = "2028-03-01"))
        assertEquals(ForecastEvidence.UNAVAILABLE, data.evidence)
        assertEquals(6, data.omittedMonths)
        assertTrue(data.samples.isEmpty())
        assertEquals(listOf(1, 3), data.horizons.map { it.months })
        data.horizons.forEach { assertNull(it.totals); assertNull(it.consumptionBand); assertEquals(ForecastUnavailable.NO_SAMPLES, it.unavailable) }
        assertEquals(listOf(ForecastRuleKind.MORE_HISTORY), data.rules.map { it.kind })
    }
    @Test fun partialFirstMonthIsExcludedWithoutNormalizationOrZeroFilling() {
        val data = forecast(source(listOf(row(999, "2028-01-15"), row(100, "2028-02-01")), "2028-01-15"))
        assertEquals(listOf(YearMonth.of(2028, 2)), data.samples.map { it.month })
        assertEquals(100L, data.horizons.first().totals!!.consumption.minorUnits)
        assertEquals(ForecastEvidence.LOW, data.evidence)
    }
    @Test fun coverageBeginningOnFirstDayIncludesThatWholeCompletedMonth() {
        assertEquals(listOf(YearMonth.of(2028, 1), YearMonth.of(2028, 2)), forecast().samples.map { it.month })
        assertEquals(4, forecast().omittedMonths)
    }
    @Test fun coveredEmptyMonthsAreRealZeroObservationsWithUndefinedSavingRate() {
        val data = forecast()
        assertEquals(ForecastEvidence.MEDIUM, data.evidence)
        data.horizons.forEach {
            assertEquals(0L, it.totals!!.income.minorUnits); assertEquals(0L, it.totals.consumption.minorUnits)
            assertNull(it.totals.savingRatePercent)
            assertEquals(Money(0), it.consumptionBand!!.low); assertEquals(Money(0), it.consumptionBand.high)
        }
    }
    @Test fun currentMonthNeverBecomesAFullSampleEvenOnItsFinalDay() {
        val data = source(listOf(row(100), row(100, "2028-02-10"), row(Long.MAX_VALUE, "2028-03-01")))
        assertEquals(forecast(data).horizons, forecast(data, date = LocalDate.of(2028, 3, 31)).horizons)
        assertEquals(1, forecast(data).currentMonthRecordsExcluded)
        assertEquals(100L, forecast(data).horizons.first().totals!!.consumption.minorUnits)
    }
    @Test fun futureCoverageAndFutureEntriesCannotBecomeHistoricalSamples() {
        val data = forecast(source(listOf(row(200, "2028-04-10")), "2028-04-01"))
        assertTrue(data.samples.isEmpty()); assertEquals(1, data.futureRecordsExcluded)
        assertEquals(ForecastEvidence.UNAVAILABLE, data.evidence)
    }
    @Test fun futureEntriesWithinCurrentMonthAndNextMonthAreExcluded() {
        val data = forecast(source(listOf(row(10), row(20, "2028-03-11"), row(30, "2028-04-01"))))
        assertEquals(2, data.futureRecordsExcluded); assertEquals(0, data.currentMonthRecordsExcluded)
        assertEquals(5L, data.horizons.first().totals!!.consumption.minorUnits)
    }
    @Test fun rollingMeanForecastsBothHorizonsAndSeparatesInvestmentAccounting() {
        val data = forecast(twoMonths())
        val one = data.horizons.first().totals!!; val three = data.horizons.last().totals!!
        assertEquals(listOf(1, 1), data.samples.map { it.weight })
        assertEquals(2000L, one.income.minorUnits); assertEquals(200L, one.consumption.minorUnits)
        assertEquals(100L, one.contributions.minorUnits); assertEquals(1800L, one.saving.minorUnits)
        assertEquals(1700L, one.netCashFlow!!.minorUnits); assertEquals(BigDecimal("90.00"), one.savingRatePercent)
        assertEquals(6000L, three.income.minorUnits); assertEquals(600L, three.consumption.minorUnits)
        assertEquals(300L, three.contributions.minorUnits)
    }
    @Test fun weightedMeanUsesOldestToNewestWeightsAndRoundsEachHorizonOnlyOnce() {
        val data = forecast(twoMonths().copy(transactions = twoMonths().transactions.reversed()), ForecastMethod.WEIGHTED)
        assertEquals(listOf(1, 2), data.samples.map { it.weight })
        assertEquals(2333L, data.horizons.first().totals!!.income.minorUnits)
        assertEquals(233L, data.horizons.first().totals!!.consumption.minorUnits)
        assertEquals(117L, data.horizons.first().totals!!.contributions.minorUnits)
        assertEquals(7000L, data.horizons.last().totals!!.income.minorUnits)
        assertEquals(700L, data.horizons.last().totals!!.consumption.minorUnits)
        assertEquals(350L, data.horizons.last().totals!!.contributions.minorUnits)
    }
    @Test fun halfEvenRoundingAndDerivedSavingAgreeWithDisplayedGrossAmounts() {
        val data = forecast(source(listOf(row(1), row(2, "2028-02-10"))))
        assertEquals(2L, data.horizons.first().totals!!.consumption.minorUnits)
        assertEquals(-2L, data.horizons.first().totals!!.saving.minorUnits)
        assertEquals(4L, data.horizons.last().totals!!.consumption.minorUnits)
    }
    @Test fun windowsBoundCalendarHistoryAndDoNotBackfillFromEarlierMonths() {
        val data = source(listOf(row(900, "2027-10-01")), "2027-03-01")
        assertEquals(0L, forecast(data, window = 3).horizons.first().totals!!.consumption.minorUnits)
        assertEquals(150L, forecast(data, window = 6).horizons.first().totals!!.consumption.minorUnits)
        assertEquals(75L, forecast(data, window = 12).horizons.first().totals!!.consumption.minorUnits)
        assertEquals(YearMonth.of(2027, 3), forecast(data, window = 12).windowStart)
        assertEquals(YearMonth.of(2028, 2), forecast(data).windowEnd)
    }
    @Test fun evidenceThresholdsDependOnCompleteCoveredMonthCountOnly() {
        for ((months, expected) in listOf(0 to ForecastEvidence.UNAVAILABLE, 1 to ForecastEvidence.LOW,
            2 to ForecastEvidence.MEDIUM, 5 to ForecastEvidence.MEDIUM, 6 to ForecastEvidence.HIGHER, 12 to ForecastEvidence.HIGHER)) {
            val tracked = YearMonth.from(today).minusMonths(months.toLong()).atDay(1).toString()
            val data = forecast(source(tracked = tracked), window = 12)
            assertEquals(months, data.samples.size); assertEquals(expected, data.evidence)
            assertEquals(months < 2, data.fires(ForecastRuleKind.MORE_HISTORY))
        }
    }
    @Test fun historicalRangesUseObservedExtremesScaledByHorizon() {
        val data = forecast(source(listOf(row(10), row(100, "2028-02-10"))))
        assertEquals(ForecastBand(Money(10), Money(100)), data.horizons.first().consumptionBand)
        assertEquals(ForecastBand(Money(30), Money(300)), data.horizons.last().consumptionBand)
        assertEquals(55L, data.horizons.first().totals!!.consumption.minorUnits)
    }
    @Test fun oneSampleHasLowEvidenceAndNoInventedVariation() {
        val data = forecast(source(listOf(row(25, "2028-02-10")), "2028-02-01"))
        assertEquals(ForecastEvidence.LOW, data.evidence)
        assertEquals(ForecastBand(Money(25), Money(25)), data.horizons.first().consumptionBand)
    }
    @Test fun zeroIncomeWithExpensesHasNegativeSavingAndNoRate() {
        val data = forecast(source(listOf(row(100), row(100, "2028-02-10"))))
        assertNull(data.horizons.first().totals!!.savingRatePercent)
        assertEquals(-100L, data.horizons.first().totals!!.saving.minorUnits)
        assertTrue(data.fires(ForecastRuleKind.HISTORICAL_DEFICIT))
    }
    @Test fun contributionsCanPressureCashWithoutBeingConsumptionOrNegativeSaving() {
        val data = forecast(source(listOf(row(200, type = TransactionType.INCOME), row(100),
            row(150, type = TransactionType.INVESTMENT))))
        assertEquals(50L, data.horizons.first().totals!!.saving.minorUnits)
        assertTrue(data.fires(ForecastRuleKind.CONTRIBUTION_PRESSURE))
        assertFalse(data.fires(ForecastRuleKind.HISTORICAL_DEFICIT))
    }
    @Test fun openingCashDoesNotAffectAnyForecast() {
        val original = twoMonths()
        assertEquals(forecast(original), forecast(original.copy(ledger = original.ledger.copy(openingCash = Money(Long.MIN_VALUE)))))
    }
    @Test fun overflowingHistoricalMonthlyGrossTotalsFailWithoutWrapping() {
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), ForecastCalculator.calculate(source(listOf(row(Long.MAX_VALUE), row(1))), today))
    }
    @Test fun oneMonthCanBeValidWhileThreeMonthGrossTotalOverflows() {
        val data = forecast(source(listOf(row(Long.MAX_VALUE, "2028-02-10")), "2028-02-01"))
        assertNotNull(data.horizons.first().totals)
        assertNull(data.horizons.last().totals)
        assertEquals(ForecastUnavailable.OUT_OF_RANGE, data.horizons.last().unavailable)
    }
    @Test fun overflowingRangeEndpointDoesNotHideValidPointEstimate() {
        val data = forecast(source(listOf(row(Long.MAX_VALUE, "2027-12-01")), "2027-12-01"), window = 3)
        assertNotNull(data.horizons.last().totals)
        assertEquals(Long.MAX_VALUE, data.horizons.last().totals!!.consumption.minorUnits)
        assertNull(data.horizons.last().consumptionBand!!.high)
        assertEquals(Money(0), data.horizons.last().consumptionBand!!.low)
    }
    @Test fun weightedNumeratorsCanExceedLongWithoutIntermediateOverflow() {
        val data = forecast(source(listOf(row(Long.MAX_VALUE), row(Long.MAX_VALUE, "2028-02-10"))), ForecastMethod.WEIGHTED)
        assertEquals(Long.MAX_VALUE, data.horizons.first().totals!!.consumption.minorUnits)
    }
    @Test fun netFlowOverflowIsLocalAndContributionRuleStillHasExactInputs() {
        val data = forecast(source(listOf(row(Long.MAX_VALUE), row(Long.MAX_VALUE, type = TransactionType.INVESTMENT)), "2028-01-01"),
            date = LocalDate.of(2028, 2, 1))
        assertEquals(Long.MAX_VALUE, data.horizons.first().totals!!.consumption.minorUnits)
        assertNull(data.horizons.first().totals!!.netCashFlow)
        assertTrue(data.horizons.first().totals!!.netCashFlowOverflow)
        assertTrue(data.fires(ForecastRuleKind.CONTRIBUTION_PRESSURE))
    }
    @Test fun horizonDatesHandleDecemberYearRolloverAndLeapFebruary() {
        val december = forecast(source(tracked = "2027-01-01"), date = LocalDate.of(2027, 12, 31))
        assertEquals(LocalDate.of(2028, 1, 1), december.horizons.first().start)
        assertEquals(LocalDate.of(2028, 4, 1), december.horizons.last().endExclusive)
        val january = forecast(source(tracked = "2027-01-01"), date = LocalDate.of(2028, 1, 1))
        assertEquals(LocalDate.of(2028, 2, 1), january.horizons.first().start)
        assertEquals(LocalDate.of(2028, 3, 1), january.horizons.first().endExclusive)
    }
    @Test fun supportedYearBoundsDoNotCrashOrCreateUnsupportedForecasts() {
        assertTrue(forecast(source(tracked = "0001-01-01"), date = LocalDate.of(1, 1, 1)).samples.isEmpty())
        val end = forecast(source(tracked = "9999-01-01"), date = LocalDate.of(9999, 10, 1))
        assertNotNull(end.horizons.first().totals)
        assertEquals(ForecastUnavailable.UNSUPPORTED_DATES, end.horizons.last().unavailable)
    }
    @Test fun invalidOptionsDatesAmountsAndCoverageAreRejected() {
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), ForecastCalculator.calculate(source(), today, ForecastOptions(window = 0)))
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), ForecastCalculator.calculate(source(), LocalDate.of(10000, 1, 1)))
        for (record in listOf(row(0), row(-1), row(1, "2027-12-31")))
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), ForecastCalculator.calculate(source(listOf(record)), today))
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), ForecastCalculator.calculate(source(caps = listOf(cap(-1))), today))
    }
    @Test fun mixedTransactionOrBudgetCurrenciesAreRejected() {
        val usd = CurrencyCode("USD")
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), ForecastCalculator.calculate(source(listOf(row(1).copy(amount = Money(1, usd)))), today))
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), ForecastCalculator.calculate(source(caps = listOf(cap(1).copy(allocation = Money(1, usd)))), today))
    }
    @Test fun consumptionRiseUsesExactTwentyPercentBoundaryAndPositiveDenominator() {
        for ((before, after, fires) in listOf(Triple(100L, 119L, false), Triple(100L, 120L, true), Triple(5L, 6L, true),
            Triple(0L, 100L, false), Triple(100L, 0L, false))) {
            val data = forecast(source(listOf(row(before.coerceAtLeast(1)), row(after.coerceAtLeast(1), "2028-02-10"))
                .filter { (it.date.monthValue == 1 && before > 0) || (it.date.monthValue == 2 && after > 0) }))
            assertEquals("$before -> $after", fires, data.fires(ForecastRuleKind.CONSUMPTION_RISE))
        }
    }
    @Test fun partialMonthNeverCreatesAConsumptionRiseComparison() {
        val data = forecast(source(listOf(row(100, "2028-01-15"), row(300, "2028-02-10")), "2028-01-15"))
        assertFalse(data.fires(ForecastRuleKind.CONSUMPTION_RISE))
    }
    @Test fun nextBudgetRuleUsesStrictComparisonAndCorrectMonthIncludingZeroCap() {
        for ((amount, expected) in listOf(100L to false, 99L to true, 0L to true)) {
            val data = forecast(source(listOf(row(100), row(100, "2028-02-10")), caps = listOf(cap(amount), cap(0, "2028-03"))))
            assertEquals(expected, data.fires(ForecastRuleKind.NEXT_BUDGET_SHORTFALL))
            assertFalse(data.fires(ForecastRuleKind.NEXT_BUDGET_MISSING))
        }
    }
    @Test fun categoryAllocationsDoNotSubstituteForMissingNextOverallBudget() {
        val data = forecast(source().copy(categories = listOf(CategoryBudget("category", YearMonth.of(2028, 4), "food", Money(100)))))
        assertTrue(data.fires(ForecastRuleKind.NEXT_BUDGET_MISSING))
    }
    @Test fun methodSelectionChangesEstimateAndItsBudgetRuleTogether() {
        val data = source(listOf(row(100), row(300, "2028-02-10")), caps = listOf(cap(210)))
        assertFalse(forecast(data).fires(ForecastRuleKind.NEXT_BUDGET_SHORTFALL))
        assertTrue(forecast(data, ForecastMethod.WEIGHTED).fires(ForecastRuleKind.NEXT_BUDGET_SHORTFALL))
        assertEquals("1", ForecastCalculator.RULE_VERSION)
    }
    @Test fun equalIncomeConsumptionAndZeroContributionsDoNotTriggerDeficitRules() {
        val data = forecast(source(listOf(row(100), row(100, type = TransactionType.INCOME))))
        assertFalse(data.fires(ForecastRuleKind.HISTORICAL_DEFICIT))
        assertFalse(data.fires(ForecastRuleKind.CONTRIBUTION_PRESSURE))
    }
}
