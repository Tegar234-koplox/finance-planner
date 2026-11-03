package com.tegar.finance.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class GoalKind { SAVING, PURCHASE }
enum class GoalPriority(val rank: Int) { HIGH(1), NORMAL(2), LOW(3) }
enum class GoalState { ACTIVE, PAUSED }
data class FinancialGoal(val id: String, val name: String, val kind: GoalKind, val target: Money,
    val earmarked: Money, val asOf: LocalDate, val targetDate: LocalDate, val priority: GoalPriority,
    val state: GoalState, val createdAt: Instant, val updatedAt: Instant?)
data class GoalDraft(val name: String, val kind: GoalKind, val target: Money, val earmarked: Money,
    val asOf: LocalDate, val targetDate: LocalDate, val priority: GoalPriority, val state: GoalState)

object GoalRules {
    fun normalizeName(value: String) = value.trim().replace(Regex("\\s+"), " ")
    fun validate(draft: GoalDraft, ledger: Ledger, today: LocalDate): AppError? = when {
        draft.target.currency != ledger.currency || draft.earmarked.currency != ledger.currency -> AppError.CURRENCY_MISMATCH
        normalizeName(draft.name).length !in 1..80 || draft.name.any { it.isISOControl() } ||
            draft.target.minorUnits <= 0 || draft.earmarked.minorUnits < 0 ||
            draft.asOf.year !in 1..9999 || draft.targetDate.year !in 1..9999 || draft.asOf > today -> AppError.INVALID_INPUT
        draft.asOf < ledger.trackedFromDate -> AppError.DATE_BEFORE_COVERAGE
        else -> null
    }
    fun validateRecorded(goals: List<FinancialGoal>, ledger: Ledger, today: LocalDate): AppError? =
        goals.firstNotNullOfOrNull { validate(GoalDraft(it.name, it.kind, it.target, it.earmarked,
            it.asOf, it.targetDate, it.priority, it.state), ledger, today) }
    /** Pausing stops future allocation; it does not release an existing earmark. */
    fun reservations(goals: List<FinancialGoal>): BigInteger = goals.fold(BigInteger.ZERO) { sum, row ->
        sum + BigInteger.valueOf(row.earmarked.minorUnits)
    }
}

enum class GoalFunding { EMPTY, PARTIAL, FUNDED, OVERFUNDED }
enum class GoalDateReason { EARMARKED, CASH_NOW, PROJECTED, PAUSED, NEED_HISTORY, NO_CAPACITY,
    CASH_UNAVAILABLE, RESERVATIONS_EXCEED_CASH, DATE_OUT_OF_RANGE, FORECAST_OUT_OF_RANGE }
enum class PlanningCapacityReason { AVAILABLE, NEED_HISTORY, NO_POSITIVE_FLOW, OUT_OF_RANGE }
data class GoalProgress(val goal: FinancialGoal, val remaining: Money, val percent: BigDecimal,
    val funding: GoalFunding, val overdue: Boolean, val dueToday: Boolean,
    val monthlyNeededAlone: Money?, val contributionDatesBeforeDeadline: Int,
    val cashAssignedNow: Money?, val availableOn: LocalDate?, val dateReason: GoalDateReason,
    val meetsDeadline: Boolean?)
data class GoalPlan(val today: LocalDate, val options: ForecastOptions, val recordedCash: Money?,
    val cashOverflow: Boolean, val reserved: Money?, val reservationOverflow: Boolean,
    val unreservedCash: Money?, val reservationsBacked: Boolean?, val monthlyCapacity: Money?,
    val capacityReason: PlanningCapacityReason, val sampleCount: Int, val evidence: ForecastEvidence,
    val goals: List<GoalProgress>)

/** One shared priority scenario, not independent promises of the same cash or future income. */
object GoalPlanner {
    fun calculate(source: BudgetSnapshot, today: LocalDate, options: ForecastOptions = ForecastOptions()): AppResult<GoalPlan> {
        if (today.year !in 1..9999 || !options.valid) return AppResult.Failure(AppError.INVALID_INPUT)
        GoalRules.validateRecorded(source.goals, source.ledger, today)?.let { return AppResult.Failure(it) }
        val cashResult = RecordedCashCalculator.calculate(source.ledger, source.transactions, today)
        if (cashResult is AppResult.Failure && cashResult.error != AppError.OUT_OF_RANGE) return cashResult
        val cash = (cashResult as? AppResult.Success)?.value
        val currency = source.ledger.currency
        val reservedExact = GoalRules.reservations(source.goals)
        val reserved = try { reservedExact.toMoneyExact(currency) } catch (_: ArithmeticException) { null }
        val backed = cash?.let { BigInteger.valueOf(it.minorUnits) >= reservedExact }
        val freeExact = cash?.let { (BigInteger.valueOf(it.minorUnits) - reservedExact).max(BigInteger.ZERO) }
        val free = freeExact?.toMoneyExact(currency)
        var cashLeft = if (backed == true) freeExact!! else BigInteger.ZERO
        val forecastResult = ForecastCalculator.calculate(source, today, options)
        if (forecastResult is AppResult.Failure && forecastResult.error != AppError.OUT_OF_RANGE) return forecastResult
        val forecast = (forecastResult as? AppResult.Success)?.value
        val samples = forecast?.samples?.size ?: 0
        val flows = forecast?.horizons?.first()?.totals
        val capacityReason = when {
            forecast == null -> PlanningCapacityReason.OUT_OF_RANGE
            samples < 2 -> PlanningCapacityReason.NEED_HISTORY
            flows == null -> PlanningCapacityReason.OUT_OF_RANGE
            flows.netCashFlow == null || flows.netCashFlow.minorUnits <= 0 -> PlanningCapacityReason.NO_POSITIVE_FLOW
            else -> PlanningCapacityReason.AVAILABLE
        }
        val capacity = when (capacityReason) {
            PlanningCapacityReason.AVAILABLE -> flows!!.netCashFlow
            PlanningCapacityReason.NO_POSITIVE_FLOW -> Money(0, currency)
            else -> null
        }
        val current = YearMonth.from(today)
        var cumulativeNeed = BigInteger.ZERO
        val rows = source.goals.sortedWith(compareBy<FinancialGoal> { it.priority.rank }.thenBy { it.targetDate }
            .thenBy { it.createdAt }.thenBy { it.id }).map { goal ->
            val remainingExact = (BigInteger.valueOf(goal.target.minorUnits) - BigInteger.valueOf(goal.earmarked.minorUnits)).max(BigInteger.ZERO)
            val funding = when {
                goal.earmarked.minorUnits == 0L -> GoalFunding.EMPTY
                goal.earmarked.minorUnits < goal.target.minorUnits -> GoalFunding.PARTIAL
                goal.earmarked.minorUnits == goal.target.minorUnits -> GoalFunding.FUNDED
                else -> GoalFunding.OVERFUNDED
            }
            val targetMonth = YearMonth.from(goal.targetDate)
            val dates = (ChronoUnit.MONTHS.between(current, targetMonth) -
                if (goal.targetDate < targetMonth.atEndOfMonth()) 1 else 0).coerceAtLeast(0).toInt()
            val needed = when {
                remainingExact.signum() == 0 -> Money(0, currency)
                goal.state == GoalState.PAUSED || dates == 0 -> null
                else -> ceil(remainingExact, BigInteger.valueOf(dates.toLong())).toMoneyExact(currency)
            }
            val extra = if (goal.state == GoalState.ACTIVE && backed == true) minOf(cashLeft, remainingExact) else BigInteger.ZERO
            cashLeft -= extra
            if (goal.state == GoalState.ACTIVE) cumulativeNeed += remainingExact - extra
            var date: LocalDate? = null
            val reason = when {
                cash == null -> GoalDateReason.CASH_UNAVAILABLE
                backed == false -> GoalDateReason.RESERVATIONS_EXCEED_CASH
                remainingExact.signum() == 0 -> { date = today; GoalDateReason.EARMARKED }
                goal.state == GoalState.PAUSED -> GoalDateReason.PAUSED
                remainingExact == extra -> { date = today; GoalDateReason.CASH_NOW }
                capacityReason == PlanningCapacityReason.NEED_HISTORY -> GoalDateReason.NEED_HISTORY
                capacity == null -> if (forecast?.horizons?.first()?.unavailable == ForecastUnavailable.UNSUPPORTED_DATES)
                    GoalDateReason.DATE_OUT_OF_RANGE else GoalDateReason.FORECAST_OUT_OF_RANGE
                capacity.minorUnits == 0L -> GoalDateReason.NO_CAPACITY
                else -> {
                    val months = ceil(cumulativeNeed, BigInteger.valueOf(capacity.minorUnits))
                    val supportedMonths = ChronoUnit.MONTHS.between(current, YearMonth.of(9999, 12))
                    if (months > BigInteger.valueOf(supportedMonths)) GoalDateReason.DATE_OUT_OF_RANGE
                    else { date = current.plusMonths(months.toLong()).atEndOfMonth(); GoalDateReason.PROJECTED }
                }
            }
            GoalProgress(goal, remainingExact.toMoneyExact(currency), BigDecimal.valueOf(goal.earmarked.minorUnits)
                .multiply(BigDecimal(100)).divide(BigDecimal.valueOf(goal.target.minorUnits), 2, RoundingMode.DOWN), funding,
                goal.targetDate < today && remainingExact.signum() > 0, goal.targetDate == today && remainingExact.signum() > 0,
                needed, dates, if (backed == true) extra.toMoneyExact(currency) else null, date, reason,
                date?.let { it <= goal.targetDate })
        }
        return AppResult.Success(GoalPlan(today, options, cash, cashResult is AppResult.Failure,
            reserved, reserved == null, free, backed, capacity, capacityReason, samples,
            forecast?.evidence ?: ForecastEvidence.UNAVAILABLE, rows))
    }
    private fun ceil(amount: BigInteger, denominator: BigInteger) =
        (amount + denominator - BigInteger.ONE) / denominator
}
