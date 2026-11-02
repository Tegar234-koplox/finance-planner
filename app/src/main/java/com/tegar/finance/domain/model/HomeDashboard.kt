package com.tegar.finance.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

enum class DailyLimitReason { AVAILABLE, CASH_LIMITED, GOAL_RESERVED, NO_OVERALL_BUDGET, PARTIAL_COVERAGE, NO_COVERAGE }
data class CategorySpending(val categoryId: String, val amount: Money)
data class HomeDashboard(
    val date: LocalDate, val month: YearMonth, val trackedFrom: LocalDate,
    val recordedCash: Money?, val monthIncome: Money?, val monthContributions: Money?,
    val monthSaving: Money?, val savingRatePercent: BigDecimal?, val budget: BudgetMetrics,
    val dailyLimit: Money?, val dailyLimitReason: DailyLimitReason,
    val topCategories: List<CategorySpending>, val recent: List<FinanceTransaction>,
    val recordedCount: Int, val futureCount: Int,
    val reservedForGoals: Money? = null, val reservationOverflow: Boolean = false, val unreservedCash: Money? = null,
)

/** No cached counters: all values come from one transactional source snapshot. */
object HomeDashboardCalculator {
    fun calculate(source: BudgetSnapshot, today: LocalDate): AppResult<HomeDashboard> {
        val ledger = source.ledger
        val currency = ledger.currency
        if (ledger.openingCash.currency != currency || source.transactions.any { it.amount.currency != currency })
            return AppResult.Failure(AppError.CURRENCY_MISMATCH)
        if (source.transactions.any { it.amount.minorUnits <= 0 || it.date < ledger.trackedFromDate })
            return AppResult.Failure(AppError.INVALID_INPUT)
        GoalRules.validateRecorded(source.goals, ledger, today)?.let { return AppResult.Failure(it) }
        val month = YearMonth.from(today)
        val cap = source.monthly.firstOrNull { it.month == month }?.allocation
        val calculated = BudgetCalculator.calculate(month, today, ledger.trackedFromDate, currency, cap, source.transactions)
        if (calculated is AppResult.Failure) return calculated
        val budget = (calculated as AppResult.Success).value
        val posted = source.transactions.filter { it.date <= today }
        val futureCount = source.transactions.count { it.date > today }
        if (today < ledger.trackedFromDate) return AppResult.Success(HomeDashboard(today, month, ledger.trackedFromDate,
            null, null, null, null, null, budget, null, DailyLimitReason.NO_COVERAGE, emptyList(), emptyList(), 0, futureCount))
        return try {
            val categoryTotals = mutableMapOf<String, BigInteger>()
            for (row in posted) {
                val amount = BigInteger.valueOf(row.amount.minorUnits)
                if (row.date >= month.atDay(1)) when (row.type) {
                    TransactionType.INCOME, TransactionType.INVESTMENT -> Unit
                    TransactionType.EXPENSE -> categoryTotals[row.categoryId] = (categoryTotals[row.categoryId] ?: BigInteger.ZERO) + amount
                }
            }
            val cashResult = RecordedCashCalculator.calculate(ledger, source.transactions, today)
            if (cashResult is AppResult.Failure) return cashResult
            val recordedCash = (cashResult as AppResult.Success).value!!
            val reservedExact = GoalRules.reservations(source.goals)
            val reserved = try { reservedExact.toMoneyExact(currency) } catch (_: ArithmeticException) { null }
            val unreserved = (BigInteger.valueOf(recordedCash.minorUnits) - reservedExact).max(BigInteger.ZERO).toMoneyExact(currency)
            val calculatedFlows = RecordedFlowCalculator.calculate(posted.filter { it.date >= month.atDay(1) }, currency)
            if (calculatedFlows is AppResult.Failure) return calculatedFlows
            val flows = (calculatedFlows as AppResult.Success).value
            val cashDaily = if (budget.remainingDays <= 0) 0 else unreserved.minorUnits / budget.remainingDays
            val reason = when {
                budget.coverage != BudgetCoverage.FULL -> DailyLimitReason.PARTIAL_COVERAGE
                cap == null -> DailyLimitReason.NO_OVERALL_BUDGET
                cashDaily < budget.dailyAllowance!!.minorUnits ->
                    if (reservedExact.signum() > 0) DailyLimitReason.GOAL_RESERVED else DailyLimitReason.CASH_LIMITED
                else -> DailyLimitReason.AVAILABLE
            }
            val daily = budget.dailyAllowance?.let { Money(minOf(it.minorUnits, cashDaily), currency) }
            val top = categoryTotals.map { CategorySpending(it.key, it.value.toMoneyExact(currency)) }
                .sortedWith(compareByDescending<CategorySpending> { it.amount.minorUnits }.thenBy { it.categoryId }).take(3)
            val recent = posted.sortedWith(compareByDescending<FinanceTransaction> { it.date }
                .thenByDescending { it.createdAt }.thenByDescending { it.id }).take(5)
            AppResult.Success(HomeDashboard(today, month, ledger.trackedFromDate, recordedCash, flows.income,
                flows.contributions, flows.saving, flows.savingRatePercent, budget, daily, reason, top, recent, posted.size, futureCount,
                reserved, reserved == null, Money(unreserved.minorUnits, currency)))
        } catch (_: ArithmeticException) { AppResult.Failure(AppError.OUT_OF_RANGE) }
    }
}
