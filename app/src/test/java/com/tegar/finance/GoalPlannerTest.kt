package com.tegar.finance

import com.tegar.finance.domain.model.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class GoalPlannerTest {
    private val today = LocalDate.of(2028, 3, 10)
    private fun goal(id: String = "goal", target: Long = 1000, earmark: Long = 0, date: String = "2028-12-31",
        priority: GoalPriority = GoalPriority.NORMAL, state: GoalState = GoalState.ACTIVE, asOf: LocalDate = today,
        created: Instant = Instant.EPOCH, kind: GoalKind = GoalKind.SAVING) =
        FinancialGoal(id, "Goal $id", kind, Money(target), Money(earmark), asOf, LocalDate.parse(date), priority, state, created, null)
    private fun row(amount: Long, type: TransactionType, date: LocalDate) = FinanceTransaction("$type-$date-$amount",
        type, Money(amount), type.name, date, "", Instant.EPOCH, null)
    private fun source(cash: Long = 0, goals: List<FinancialGoal> = emptyList(), at: LocalDate = today): BudgetSnapshot {
        val month = YearMonth.from(at)
        val first = month.minusMonths(2).atDay(1); val second = month.minusMonths(1).atDay(1)
        val records = listOf(first, second).flatMap { listOf(row(1000, TransactionType.INCOME, it),
            row(800, TransactionType.EXPENSE, it), row(100, TransactionType.INVESTMENT, it)) }
        return BudgetSnapshot(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), first, Money(cash - 200), Instant.EPOCH),
            emptyList(), emptyList(), records, goals)
    }
    private fun plan(source: BudgetSnapshot, at: LocalDate = today, options: ForecastOptions = ForecastOptions()): GoalPlan {
        val result = GoalPlanner.calculate(source, at, options); assertTrue(result.toString(), result is AppResult.Success)
        return (result as AppResult.Success).value
    }
    private fun GoalPlan.byId(id: String) = goals.first { it.goal.id == id }

    @Test fun emptyPlanUsesExactRecordedCashAndHistoricalNetCapacity() {
        val data = source(345); val result = plan(data)
        assertEquals(Money(345), result.recordedCash); assertEquals(Money(0), result.reserved)
        assertEquals(Money(345), result.unreservedCash); assertEquals(Money(100), result.monthlyCapacity)
        assertEquals(2, result.sampleCount); assertEquals(ForecastEvidence.MEDIUM, result.evidence)
        assertTrue(result.goals.isEmpty()); assertEquals(data, source(345))
    }
    @Test fun progressDistinguishesEmptyPartialFundedAndOverfundedWithoutNegativeRemaining() {
        val result = plan(source(10000, listOf(goal("empty", 1000), goal("part", 1000, 250),
            goal("full", 1000, 1000), goal("over", 1000, 1250))))
        assertEquals(GoalFunding.EMPTY, result.byId("empty").funding)
        assertEquals(GoalFunding.PARTIAL, result.byId("part").funding)
        assertEquals(GoalFunding.FUNDED, result.byId("full").funding)
        assertEquals(GoalFunding.OVERFUNDED, result.byId("over").funding)
        assertEquals(Money(750), result.byId("part").remaining)
        assertEquals(Money(0), result.byId("over").remaining)
        assertEquals(BigDecimal("125.00"), result.byId("over").percent)
        assertEquals(Money(2500), result.reserved) // Excess earmark stays reserved.
    }
    @Test fun progressTruncatesAndCannotRoundAnUnfundedGoalToOneHundredPercent() {
        val row = plan(source(0, listOf(goal(target = Long.MAX_VALUE, earmark = Long.MAX_VALUE - 1)))).goals.single()
        assertEquals(BigDecimal("99.99"), row.percent); assertEquals(Money(1), row.remaining)
        assertEquals(GoalFunding.PARTIAL, row.funding)
    }
    @Test fun overdueDueTodayAndFundedDatesAreSeparateFacts() {
        val result = plan(source(100, listOf(goal("late", 10, date = "2028-03-09"),
            goal("due", 10, date = "2028-03-10"), goal("full", 10, 10, "2028-03-01"))))
        assertTrue(result.byId("late").overdue); assertFalse(result.byId("late").dueToday)
        assertTrue(result.byId("due").dueToday); assertFalse(result.byId("due").overdue)
        assertFalse(result.byId("full").overdue); assertEquals(today, result.byId("full").availableOn)
    }
    @Test fun freeCashIsAssignedOnceAccordingToPriorityNotIndependentlyToEveryGoal() {
        val result = plan(source(100, listOf(goal("low", 80, priority = GoalPriority.LOW), goal("high", 80, priority = GoalPriority.HIGH))))
        assertEquals(listOf("high", "low"), result.goals.map { it.goal.id })
        assertEquals(Money(80), result.byId("high").cashAssignedNow)
        assertEquals(Money(20), result.byId("low").cashAssignedNow)
        assertEquals(GoalDateReason.CASH_NOW, result.byId("high").dateReason)
        assertEquals(LocalDate.of(2028, 4, 30), result.byId("low").availableOn)
        assertEquals(100L, result.goals.sumOf { it.cashAssignedNow!!.minorUnits })
    }
    @Test fun earlierDeadlineThenOlderCreationThenIdBreakPriorityTiesDeterministically() {
        val result = plan(source(10, listOf(goal("z", 10, date = "2028-06-30", created = Instant.EPOCH.plusSeconds(1)),
            goal("a", 10, date = "2028-06-30"), goal("b", 10, date = "2028-06-30"), goal("early", 10, date = "2028-04-30"))))
        assertEquals(listOf("early", "a", "b", "z"), result.goals.map { it.goal.id })
        assertEquals(GoalDateReason.CASH_NOW, result.byId("early").dateReason)
    }
    @Test fun priorityGoalsShareOneMonthlyCapacityAndCarryUnusedCapacityWithinAMonth() {
        val result = plan(source(0, listOf(goal("first", 150, priority = GoalPriority.HIGH), goal("second", 150))))
        assertEquals(LocalDate.of(2028, 5, 31), result.byId("first").availableOn)
        assertEquals(LocalDate.of(2028, 6, 30), result.byId("second").availableOn)
        val small = plan(source(0, listOf(goal("first", 50, priority = GoalPriority.HIGH), goal("second", 50))))
        assertEquals(LocalDate.of(2028, 4, 30), small.byId("first").availableOn)
        assertEquals(LocalDate.of(2028, 4, 30), small.byId("second").availableOn)
    }
    @Test fun pausedGoalsKeepEarmarksButReceiveNeitherFreeCashNorFutureCapacity() {
        val result = plan(source(100, listOf(goal("paused", 100, 40, priority = GoalPriority.HIGH, state = GoalState.PAUSED), goal("active", 80))))
        assertEquals(Money(40), result.reserved); assertEquals(Money(60), result.unreservedCash)
        assertEquals(GoalDateReason.PAUSED, result.byId("paused").dateReason)
        assertEquals(Money(0), result.byId("paused").cashAssignedNow); assertNull(result.byId("paused").availableOn)
        assertEquals(Money(60), result.byId("active").cashAssignedNow)
        assertEquals(LocalDate.of(2028, 4, 30), result.byId("active").availableOn)
    }
    @Test fun fundedGoalsDoNotConsumeFutureCapacityButKeepTheirWholeReservation() {
        val result = plan(source(125, listOf(goal("over", 100, 125, priority = GoalPriority.HIGH), goal("other", 150))))
        assertEquals(Money(0), result.unreservedCash); assertEquals(GoalDateReason.EARMARKED, result.byId("over").dateReason)
        assertEquals(LocalDate.of(2028, 5, 31), result.byId("other").availableOn)
    }
    @Test fun zeroOrNegativeHistoricalFlowIsKnownZeroCapacityNotMissingHistory() {
        val data = source().let { it.copy(transactions = it.transactions.filter { row -> row.type != TransactionType.INCOME },
            ledger = it.ledger.copy(openingCash = Money(1800)), goals = listOf(goal())) }
        val result = plan(data)
        assertEquals(Money(0), result.recordedCash); assertEquals(Money(0), result.monthlyCapacity)
        assertEquals(PlanningCapacityReason.NO_POSITIVE_FLOW, result.capacityReason)
        assertEquals(GoalDateReason.NO_CAPACITY, result.goals.single().dateReason); assertNull(result.goals.single().availableOn)
    }
    @Test fun incompleteAndSingleMonthHistoryWithholdFutureDatesButNotCashNow() {
        for (tracked in listOf("2028-03-01", "2028-02-01", "2028-01-15")) {
            val data = source().let { original ->
                val start = LocalDate.parse(tracked)
                original.copy(ledger = original.ledger.copy(trackedFromDate = start, openingCash = Money(50)),
                    transactions = emptyList(), goals = listOf(goal("now", 50, priority = GoalPriority.HIGH), goal("later", 100)))
            }
            val result = plan(data)
            assertNull(result.monthlyCapacity); assertEquals(PlanningCapacityReason.NEED_HISTORY, result.capacityReason)
            assertEquals(GoalDateReason.CASH_NOW, result.byId("now").dateReason)
            assertEquals(GoalDateReason.NEED_HISTORY, result.byId("later").dateReason)
        }
    }
    @Test fun equalCashAndEarmarksIsFullyBackedWithNoFreeCash() {
        val result = plan(source(100, listOf(goal(target = 100, earmark = 100))))
        assertEquals(true, result.reservationsBacked); assertEquals(Money(0), result.unreservedCash)
        assertEquals(GoalDateReason.EARMARKED, result.goals.single().dateReason)
    }
    @Test fun overReservedOrNegativeCashWithholdsDatesEvenForFundedEarmarks() {
        for (cash in listOf(99L, -1L)) {
            val result = plan(source(cash, listOf(goal(target = 100, earmark = 100))))
            assertEquals(false, result.reservationsBacked); assertEquals(Money(0), result.unreservedCash)
            assertEquals(GoalFunding.FUNDED, result.goals.single().funding)
            assertEquals(GoalDateReason.RESERVATIONS_EXCEED_CASH, result.goals.single().dateReason)
            assertNull(result.goals.single().availableOn); assertNull(result.goals.single().cashAssignedNow)
        }
    }
    @Test fun changingMethodsChangesSharedCapacityAndDatesTogether() {
        val original = source(goals = listOf(goal(target = 115)))
        val records = original.transactions.map { if (it.type == TransactionType.EXPENSE)
            it.copy(amount = Money(if (it.date.monthValue == 1) 850 else 750)) else it }
        val rolling = plan(original.copy(transactions = records))
        val weighted = plan(original.copy(transactions = records), options = ForecastOptions(ForecastMethod.WEIGHTED, 6))
        assertEquals(Money(100), rolling.monthlyCapacity); assertEquals(Money(117), weighted.monthlyCapacity)
        assertEquals(LocalDate.of(2028, 5, 31), rolling.goals.single().availableOn)
        assertEquals(LocalDate.of(2028, 4, 30), weighted.goals.single().availableOn)
    }
    @Test fun currentIncomeCanFundCashNowWhileCurrentMonthNeverEntersHistoricalCapacity() {
        val original = source(goals = listOf(goal(target = 500)))
        val result = plan(original.copy(transactions = original.transactions + row(500, TransactionType.INCOME, today)))
        assertEquals(Money(500), result.recordedCash); assertEquals(Money(100), result.monthlyCapacity)
        assertEquals(today, result.goals.single().availableOn)
    }
    @Test fun futureRecordsCannotFundGoalCashOrPlanningCapacity() {
        val original = source(goals = listOf(goal(target = 500)))
        val result = plan(original.copy(transactions = original.transactions + row(99999, TransactionType.INCOME, today.plusDays(1))))
        assertEquals(plan(original), result)
    }
    @Test fun contributionsReduceCapacitySeparatelyFromConsumptionAndSaving() {
        val result = plan(source(goals = listOf(goal(target = 150))))
        assertEquals(Money(100), result.monthlyCapacity) // income 1000 - expenses 800 - contributions 100
        assertEquals(LocalDate.of(2028, 5, 31), result.goals.single().availableOn)
    }
    @Test fun nextFullMonthEndIsFirstContributionEvenAtCurrentMonthsFirstOrLastDay() {
        for (at in listOf(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 1, 31))) {
            val result = plan(source(goals = listOf(goal(target = 100, asOf = at)), at = at), at)
            assertEquals(LocalDate.of(2028, 2, 29), result.goals.single().availableOn)
        }
    }
    @Test fun decemberAndLeapDayRollToCorrectNextMonthEnds() {
        for ((at, expected) in listOf(LocalDate.of(2027, 12, 31) to LocalDate.of(2028, 1, 31),
            LocalDate.of(2028, 2, 29) to LocalDate.of(2028, 3, 31))) {
            val result = plan(source(goals = listOf(goal(target = 100, asOf = at)), at = at), at)
            assertEquals(expected, result.goals.single().availableOn)
        }
    }
    @Test fun onlyMonthEndDatesBeforeOrOnDeadlineCountTowardMonthlyRequirement() {
        val at = LocalDate.of(2028, 1, 15)
        for ((deadline, count) in listOf("2028-02-28" to 0, "2028-02-29" to 1, "2028-03-01" to 1, "2028-03-31" to 2)) {
            val row = plan(source(goals = listOf(goal(target = 101, date = deadline, asOf = at)), at = at), at).goals.single()
            assertEquals(count, row.contributionDatesBeforeDeadline)
            assertEquals(if (count == 0) null else Money(if (count == 1) 101 else 51), row.monthlyNeededAlone)
        }
    }
    @Test fun targetTodayOrPastHasNoFutureContributionDateAndZeroRemainingNeedsZero() {
        val result = plan(source(10, listOf(goal("due", target = 10, date = today.toString()),
            goal("old", target = 10, date = today.minusDays(1).toString()), goal("full", 10, 10, today.minusDays(1).toString()))))
        assertEquals(0, result.byId("due").contributionDatesBeforeDeadline)
        assertNull(result.byId("due").monthlyNeededAlone); assertNull(result.byId("old").monthlyNeededAlone)
        assertEquals(Money(0), result.byId("full").monthlyNeededAlone)
    }
    @Test fun scenarioDeadlineEqualityPassesButOneDayEarlierDoesNot() {
        for ((date, expected) in listOf("2028-04-30" to true, "2028-04-29" to false)) {
            val row = plan(source(goals = listOf(goal(target = 100, date = date)))).goals.single()
            assertEquals(expected, row.meetsDeadline)
        }
    }
    @Test fun cumulativeAmountsBeyondLongCannotWrapIntoAnEarlyCompletionDate() {
        val result = plan(source(goals = listOf(goal("one", Long.MAX_VALUE), goal("two", Long.MAX_VALUE))))
        result.goals.forEach { assertEquals(GoalDateReason.DATE_OUT_OF_RANGE, it.dateReason); assertNull(it.availableOn) }
    }
    @Test fun aggregateEarmarkOverflowDoesNotHideValidProgressOrInventSpendableCash() {
        val result = plan(source(Long.MAX_VALUE, listOf(goal("one", Long.MAX_VALUE, Long.MAX_VALUE), goal("two", Long.MAX_VALUE, Long.MAX_VALUE))))
        assertTrue(result.reservationOverflow); assertNull(result.reserved); assertEquals(Money(0), result.unreservedCash)
        assertEquals(false, result.reservationsBacked)
        result.goals.forEach { assertEquals(GoalFunding.FUNDED, it.funding); assertNull(it.availableOn) }
    }
    @Test fun recordedCashOverflowWithholdsCashDatesButRetainsDeclaredProgress() {
        val original = source(goals = listOf(goal(target = 1000, earmark = 500)))
        val result = plan(original.copy(ledger = original.ledger.copy(openingCash = Money(Long.MAX_VALUE))))
        assertTrue(result.cashOverflow); assertNull(result.recordedCash); assertNull(result.unreservedCash)
        assertEquals(BigDecimal("50.00"), result.goals.single().percent)
        assertEquals(GoalDateReason.CASH_UNAVAILABLE, result.goals.single().dateReason)
    }
    @Test fun overflowingHistoricalGrossAmountsOnlyDisableFutureCapacityWhenCashFits() {
        val original = source(goals = listOf(goal()))
        val day = LocalDate.of(2028, 2, 1)
        val records = listOf(row(Long.MAX_VALUE, TransactionType.INCOME, day), row(1, TransactionType.INCOME, day),
            row(Long.MAX_VALUE, TransactionType.EXPENSE, day), row(1, TransactionType.EXPENSE, day))
        val result = plan(original.copy(ledger = original.ledger.copy(openingCash = Money(0)), transactions = records))
        assertEquals(Money(0), result.recordedCash); assertNull(result.monthlyCapacity)
        assertEquals(PlanningCapacityReason.OUT_OF_RANGE, result.capacityReason)
        assertEquals(GoalDateReason.FORECAST_OUT_OF_RANGE, result.goals.single().dateReason)
    }
    @Test fun unsupportedFutureYearsDoNotCrashOrProduceInvalidDates() {
        val at = LocalDate.of(9999, 12, 1)
        val row = plan(source(goals = listOf(goal(target = 1, asOf = at, date = "9999-12-31")), at = at), at).goals.single()
        assertEquals(GoalDateReason.DATE_OUT_OF_RANGE, row.dateReason); assertNull(row.availableOn)
    }
    @Test fun purchasePlanningDoesNotAlterCashRecordsOrCreateAnAssetOrExpense() {
        val original = source(100, listOf(goal(target = 100, kind = GoalKind.PURCHASE)))
        val result = plan(original)
        assertEquals(GoalDateReason.CASH_NOW, result.goals.single().dateReason)
        assertEquals(Money(100), original.ledger.openingCash.add(Money(200)).let { (it as AppResult.Success).value })
        assertEquals(6, original.transactions.size); assertEquals(0L, original.goals.single().earmarked.minorUnits)
    }
    @Test fun invalidNamesTargetsEarmarksDatesAndCurrenciesAreRejected() {
        val original = source(); val valid = goal()
        val invalid = listOf(valid.copy(name = " "), valid.copy(name = "a".repeat(81)), valid.copy(name = "a\u0000b"),
            valid.copy(target = Money(0)), valid.copy(earmarked = Money(-1)), valid.copy(asOf = today.plusDays(1)),
            valid.copy(targetDate = LocalDate.of(0, 1, 1)))
        for (item in invalid) assertEquals(AppResult.Failure(AppError.INVALID_INPUT), GoalPlanner.calculate(original.copy(goals = listOf(item)), today))
        assertEquals(AppResult.Failure(AppError.DATE_BEFORE_COVERAGE), GoalPlanner.calculate(original.copy(goals = listOf(valid.copy(asOf = LocalDate.of(2027, 1, 1)))), today))
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), GoalPlanner.calculate(original.copy(goals = listOf(valid.copy(target = Money(1, CurrencyCode("USD"))))), today))
    }
    @Test fun invalidSourceCurrencyRecordsAndOptionsNeverProduceAPlan() {
        val original = source()
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), GoalPlanner.calculate(original, today, ForecastOptions(window = 5)))
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), GoalPlanner.calculate(original, LocalDate.of(0, 1, 1)))
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), GoalPlanner.calculate(original.copy(transactions = listOf(row(0, TransactionType.EXPENSE, today))), today))
        val bad = MonthlyBudget("bad", YearMonth.from(today), Money(1, CurrencyCode("USD")))
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), GoalPlanner.calculate(original.copy(monthly = listOf(bad)), today))
    }
    @Test fun homeDeductsGoalReservationsWithoutChangingRecordedCashSavingOrBudgetActuals() {
        val original = source(1000).copy(monthly = listOf(MonthlyBudget("cap", YearMonth.from(today), Money(1000))))
        val before = (HomeDashboardCalculator.calculate(original, today) as AppResult.Success).value
        val withGoal = original.copy(goals = listOf(goal(target = 500, earmark = 400, state = GoalState.PAUSED)))
        val after = (HomeDashboardCalculator.calculate(withGoal, today) as AppResult.Success).value
        assertEquals(before.recordedCash, after.recordedCash); assertEquals(before.monthSaving, after.monthSaving)
        assertEquals(before.budget, after.budget); assertEquals(Money(400), after.reservedForGoals)
        assertEquals(Money(600), after.unreservedCash); assertEquals(GoalDateReason.PAUSED, plan(withGoal).goals.single().dateReason)
        assertEquals(DailyLimitReason.GOAL_RESERVED, after.dailyLimitReason)
        assertEquals(Money(600L / after.budget.remainingDays), after.dailyLimit)
    }
    @Test fun homeFloorsSpendableCashAtZeroForOverReservationIncludingAggregateOverflow() {
        val original = source(100).copy(monthly = listOf(MonthlyBudget("cap", YearMonth.from(today), Money(1000))),
            goals = listOf(goal("one", Long.MAX_VALUE, Long.MAX_VALUE), goal("two", Long.MAX_VALUE, Long.MAX_VALUE)))
        val result = (HomeDashboardCalculator.calculate(original, today) as AppResult.Success).value
        assertEquals(Money(100), result.recordedCash); assertTrue(result.reservationOverflow)
        assertNull(result.reservedForGoals); assertEquals(Money(0), result.unreservedCash); assertEquals(Money(0), result.dailyLimit)
    }
}
