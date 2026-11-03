package com.tegar.finance

import com.tegar.finance.domain.model.*
import java.math.BigDecimal
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PortfolioCalculatorTest {
    private val today = LocalDate.of(2028, 3, 10)
    private fun holding(id: String = "fund", bucket: AllocationBucket = AllocationBucket.FUND, active: Boolean = true, cost: Long? = null) =
        InvestmentHolding(id, "Holding $id", "CODE", bucket, today.minusYears(1), cost?.let { Money(it) }, "", active, Instant.EPOCH, null)
    private fun value(id: String = "v", holding: String = "fund", amount: Long = 10000, date: LocalDate = today) =
        HoldingValue(id, holding, date, Money(amount), "", Instant.EPOCH, null)
    private fun source(cash: Long = 10000, holdings: List<InvestmentHolding> = emptyList(), values: List<HoldingValue> = emptyList()) =
        PortfolioSource(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), today.minusMonths(2), Money(cash), Instant.EPOCH),
            emptyList(), holdings, values, emptyList(), emptyList())
    private fun targets(vararg entries: Pair<AllocationBucket, Int>) = AllocationBucket.entries.map { AllocationTarget(it, entries.toMap()[it] ?: 0) }
    private fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
    private fun result(source: PortfolioSource) = PortfolioCalculator.calculate(source, today).ok()
    private fun movement(amount: Long, action: InvestmentAction = InvestmentAction.CONTRIBUTION, date: LocalDate = today) =
        FinanceTransaction("$action-$date", TransactionType.INVESTMENT, Money(amount), "invest", date, "", Instant.EPOCH, null, action)
    private fun wealth(source: PortfolioSource) = WealthSource(source.ledger, source.transactions, emptyList(), emptyList(), emptyList(), source.holdings, source.values)
    @Test fun cashIsTheOnlyCashBucketAndNeverADuplicateHolding() {
        val p = result(source()); assertEquals(Money(10000), p.total); assertEquals(Money(0), p.valuation.total)
        assertEquals(10000, p.allocations.first().actualBasisPoints); assertEquals(10000, p.allocations.sumOf { it.actualBasisPoints!! })
        assertNull(p.allocations.first().targetBasisPoints)
        assertEquals(AppError.INVALID_INPUT, InvestmentRules.validateHolding(HoldingDraft("Cash duplicate", "", AllocationBucket.CASH, today), CurrencyCode.IDR, today))
    }
    @Test fun largestRemainderSharesSumToOneHundredPercentWithDeterministicTies() {
        val data = source(1, listOf(holding("s", AllocationBucket.STOCK), holding("b", AllocationBucket.BOND)), listOf(value("s", "s", 1), value("b", "b", 1)))
        val p = result(data); assertEquals(10000, p.allocations.sumOf { it.actualBasisPoints!! })
        assertEquals(listOf(3334, 3333, 3333), p.allocations.take(3).map { it.actualBasisPoints })
    }
    @Test fun exactTargetAmountsAndGapsPreserveTheWholePortfolio() {
        val data = source(1, listOf(holding("s", AllocationBucket.STOCK), holding("b", AllocationBucket.BOND)), listOf(value("s", "s", 1), value("b", "b", 1)))
            .copy(targets = targets(AllocationBucket.CASH to 10000))
        val p = result(data); assertEquals(listOf(2L, -1L, -1L), p.allocations.take(3).map { it.gap!!.minorUnits })
        assertEquals(0L, p.allocations.sumOf { it.gap!!.minorUnits })
    }
    @Test fun aTargetPlanRequiresEveryBucketAndExactlyTenThousandBasisPoints() {
        assertNull(InvestmentRules.validateTargets(emptyList())); assertNull(InvestmentRules.validateTargets(targets(AllocationBucket.CASH to 10000)))
        for (bad in listOf(targets(AllocationBucket.CASH to 9999), targets(AllocationBucket.CASH to 10001),
            targets(AllocationBucket.CASH to 5000, AllocationBucket.FUND to 5001), listOf(AllocationTarget(AllocationBucket.CASH, 10000)),
            AllocationBucket.entries.map { AllocationTarget(AllocationBucket.CASH, 0) }))
            assertEquals(AppError.ALLOCATION_TOTAL, InvestmentRules.validateTargets(bad))
    }
    @Test fun percentInputIsExactAndRejectsExcessPrecisionSymbolsOrOutOfRangeValues() {
        assertEquals(AppResult.Success(1250), InvestmentRules.percent("12,50")); assertEquals(AppResult.Success(10000), InvestmentRules.percent("100"))
        for (text in listOf("", "-1", "100,01", "12.5", "12,345", "25%", "1e2")) assertTrue(text, InvestmentRules.percent(text) is AppResult.Failure)
    }
    @Test fun zeroDenominatorKeepsTargetsButDoesNotInventActualPercentages() {
        val p = result(source(0).copy(targets = targets(AllocationBucket.CASH to 10000)))
        assertEquals(Money(0), p.total); assertFalse(p.allocationAvailable)
        assertTrue(p.allocations.all { it.actualBasisPoints == null && it.gap == null }); assertEquals(10000, p.allocations.first().targetBasisPoints)
    }
    @Test fun negativeCashNeverProducesNegativeOrMisleadingAllocationShares() {
        val p = result(source(-1, listOf(holding()), listOf(value(amount = 100))))
        assertEquals(Money(99), p.total); assertFalse(p.allocationAvailable); assertTrue(p.allocations.all { it.actualBasisPoints == null })
    }
    @Test fun unknownHoldingsDoNotFallBackToCostOrZero() {
        val p = result(source(100, listOf(holding(cost = 99999))))
        assertEquals(Money(100), p.cash); assertNull(p.total); assertEquals(1, p.valuation.missing); assertNull(p.valuation.total)
        assertFalse(p.allocationAvailable)
    }
    @Test fun costAndContributionsAreNotAddedToAManualHoldingValue() {
        val data = source(10000, listOf(holding(cost = 30000)), listOf(value())).copy(transactions = listOf(movement(2000)))
        val p = result(data); assertEquals(Money(8000), p.cash); assertEquals(Money(10000), p.valuation.total); assertEquals(Money(18000), p.total)
    }
    @Test fun knownZeroHoldingAllowsKnownTotalsAndHasNoFakeWeight() {
        val p = result(source(100, listOf(holding()), listOf(value(amount = 0))))
        assertEquals(Money(100), p.total); assertEquals(0, p.allocations.first { it.bucket == AllocationBucket.FUND }.actualBasisPoints)
    }
    @Test fun excludedHoldingsKeepRecordsButDoNotInflateCurrentWealthOrAllocation() {
        val p = result(source(100, listOf(holding(active = false))))
        assertEquals(Money(100), p.total); assertEquals(0, p.valuation.missing); assertEquals(1, p.valuation.statuses.size)
        assertFalse(p.valuation.statuses.single().included)
    }
    @Test fun latestDatedObservationWinsAndFutureValuesAreExcludedAfterClockRollback() {
        val data = source(0, listOf(holding()), listOf(value("old", amount = 500, date = today.minusDays(3)), value("now", amount = 700, date = today.minusDays(1)), value("future", amount = 900, date = today.plusDays(1))))
        val p = result(data); assertEquals(Money(700), p.valuation.total); assertEquals(1L, p.valuation.statuses.single().ageDays)
        assertEquals(1, p.valuation.futureCount)
    }
    @Test fun futureAcquisitionIsNotAnActiveValueForToday() {
        val p = result(source(100, listOf(holding().copy(acquiredOn = today.plusDays(1)))))
        assertEquals(Money(100), p.total); assertEquals(1, p.valuation.futureCount); assertEquals(0, p.valuation.missing)
    }
    @Test fun contributionAndRedemptionWithExplicitBalancesPreserveCashPlusWealth() {
        val data = source(10000, listOf(holding()), listOf(value(amount = 12000))).copy(transactions = listOf(movement(2000)))
        assertEquals(Money(20000), result(data).total)
        val sold = data.copy(values = listOf(value(amount = 9000)), transactions = data.transactions + movement(3000, InvestmentAction.REDEMPTION))
        val p = result(sold); assertEquals(Money(11000), p.cash); assertEquals(Money(9000), p.valuation.total); assertEquals(Money(20000), p.total)
        assertEquals(Money(20000), WealthCalculator.calculate(wealth(sold), today).ok().total)
    }
    @Test fun redemptionsAreCashInflowsAndNeverIncomeOrConsumption() {
        val rows = listOf(movement(2000), movement(3000, InvestmentAction.REDEMPTION))
        val flows = RecordedFlowCalculator.calculate(rows, CurrencyCode.IDR).ok()
        assertEquals(Money(0), flows.income); assertEquals(Money(0), flows.consumption); assertEquals(Money(0), flows.saving)
        assertNull(flows.savingRatePercent); assertEquals(Money(2000), flows.contributions); assertEquals(Money(3000), flows.redemptions)
        assertEquals(Money(1000), flows.netCashFlow)
    }
    @Test fun legacyInvestmentRowsRemainContributionsAndFutureRedemptionsCannotFundCash() {
        val legacy = movement(2000).copy(investmentAction = null)
        val data = source(10000).copy(transactions = listOf(legacy, movement(5000, InvestmentAction.REDEMPTION, today.plusDays(1))))
        assertEquals(Money(8000), result(data).cash); assertEquals(Money(2000), result(data).progress.contributed); assertEquals(Money(0), result(data).progress.redeemed)
    }
    @Test fun monthlyTargetUsesGrossContributionsAndSeparatesNetRedemptions() {
        val data = source().copy(transactions = listOf(movement(600), movement(900, InvestmentAction.REDEMPTION)),
            plans = listOf(ContributionPlan(YearMonth.from(today), Money(1000))))
        val p = result(data).progress; assertEquals(Money(600), p.contributed); assertEquals(Money(900), p.redeemed)
        assertEquals(Money(-300), p.net); assertEquals(Money(400), p.remaining); assertEquals(BigDecimal("60.00"), p.percent)
    }
    @Test fun noPlanZeroTargetAndOverTargetAreDifferentStates() {
        assertNull(result(source()).progress.target)
        val zero = result(source().copy(plans = listOf(ContributionPlan(YearMonth.from(today), Money(0))))).progress
        assertEquals(Money(0), zero.target); assertNull(zero.percent); assertEquals(Money(0), zero.remaining)
        val over = result(source().copy(transactions = listOf(movement(1250)), plans = listOf(ContributionPlan(YearMonth.from(today), Money(1000))))).progress
        assertEquals(BigDecimal("125.00"), over.percent); assertEquals(Money(0), over.remaining)
    }
    @Test fun futureAndUntrackedMonthsHaveUnknownActualsWhileCoveredEmptyMonthsHaveZero() {
        val data = source()
        val future = PortfolioCalculator.calculate(data, today, YearMonth.from(today).plusMonths(1)).ok().progress
        assertEquals(BudgetCoverage.FUTURE, future.coverage); assertNull(future.contributed)
        val earlier = PortfolioCalculator.calculate(data, today, YearMonth.of(2027, 1)).ok().progress
        assertEquals(BudgetCoverage.UNTRACKED, earlier.coverage); assertNull(earlier.redeemed)
        assertEquals(Money(0), result(data).progress.contributed)
    }
    @Test fun partialCoverageStaysExplicitWithoutInventingEarlierContributions() {
        val data = source().let { it.copy(ledger = it.ledger.copy(trackedFromDate = today)) }
        val p = result(data).progress; assertEquals(BudgetCoverage.PARTIAL, p.coverage); assertEquals(Money(0), p.contributed)
    }
    @Test fun largeSumsNeverWrapAndCashRemainsIndependentlyKnown() {
        val data = source(10, listOf(holding("a"), holding("b")), listOf(value("a", "a", Long.MAX_VALUE), value("b", "b", 1)))
        val p = result(data); assertTrue(p.valuation.overflow); assertNull(p.total); assertEquals(Money(10), p.cash)
        assertTrue(p.allocations.all { it.actualBasisPoints == null })
    }
    @Test fun combinedOverflowIsDifferentFromUnknownValuation() {
        val p = result(source(Long.MAX_VALUE, listOf(holding()), listOf(value(amount = 1))))
        assertTrue(p.totalOverflow); assertEquals(0, p.valuation.missing); assertEquals(Money(1), p.valuation.total); assertNull(p.total)
    }
    @Test fun invalidCurrencyDuplicateDatesAndCashClassHoldingsAreRejected() {
        val data = source(100, listOf(holding()), listOf(value()))
        for (bad in listOf(data.copy(values = data.values + data.values), data.copy(holdings = data.holdings + data.holdings),
            data.copy(holdings = listOf(holding(bucket = AllocationBucket.CASH))))) assertEquals(AppResult.Failure(AppError.INVALID_INPUT), PortfolioCalculator.calculate(bad, today))
        val wrongCurrency = data.copy(values = listOf(value().copy(amount = Money(1, CurrencyCode("USD")))))
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), PortfolioCalculator.calculate(wrongCurrency, today))
    }
    @Test fun v2SnapshotsFreezeHoldingsAndOldV1CapturesStayUnchangedWithoutInventedCrossScopeDelta() {
        val data = source(100, listOf(holding()), listOf(value(amount = 200, date = today.minusDays(2))))
        val old = WealthCapture("old", today.minusDays(1), Money(100), Money(0), Money(0), Money(100), emptyList(), Instant.EPOCH)
        assertTrue(WealthRules.validCapture(old, CurrencyCode.IDR))
        val saved = WealthCalculator.capture(wealth(data), today, "new", Instant.EPOCH).ok()
        assertEquals(WealthRules.WITH_INVESTMENTS, saved.method); assertEquals(Money(300), saved.total)
        assertEquals(today.minusDays(2), saved.investmentLines.single().valuedOn)
        val changed = wealth(source(999)).copy(captures = listOf(old, saved))
        val current = WealthCalculator.calculate(changed, today).ok()
        assertEquals(saved, current.history.first()); assertTrue(current.historyScopeChanged); assertNull(current.historyChange)
        assertFalse(WealthRules.validCapture(saved.copy(investments = Money(201)), CurrencyCode.IDR))
        assertFalse(WealthRules.validCapture(old.copy(investments = Money(1)), CurrencyCode.IDR))
    }
    @Test fun forecastsDoNotExtrapolateHistoricalRedemptionsAsIncomeOrFutureCapacity() {
        val data = source(0).copy(transactions = listOf(movement(5000, InvestmentAction.REDEMPTION, LocalDate.of(2028, 1, 15)), movement(7000, InvestmentAction.REDEMPTION, LocalDate.of(2028, 2, 15))))
        val budget = BudgetSnapshot(data.ledger.copy(trackedFromDate = LocalDate.of(2028, 1, 1)), emptyList(), emptyList(), data.transactions)
        val forecast = ForecastCalculator.calculate(budget, today).ok()
        assertEquals(Money(6000), forecast.samples.first().flows.redemptions.let { Money((it.minorUnits + forecast.samples.last().flows.redemptions.minorUnits) / 2) })
        assertEquals(Money(0), forecast.horizons.first().totals!!.income); assertEquals(Money(0), forecast.horizons.first().totals!!.netCashFlow)
        assertEquals(Money(12000), HomeDashboardCalculator.calculate(budget, today).ok().recordedCash)
    }
}
