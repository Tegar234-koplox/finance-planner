package com.tegar.finance.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class MonthlyBudget(val id: String, val month: YearMonth, val allocation: Money)
data class CategoryBudget(val id: String, val month: YearMonth, val categoryId: String, val allocation: Money)
data class BudgetSnapshot(val ledger: Ledger, val monthly: List<MonthlyBudget>,
    val categories: List<CategoryBudget>, val transactions: List<FinanceTransaction>, val goals: List<FinancialGoal> = emptyList())
enum class BudgetCoverage { FULL, PARTIAL, UNTRACKED, FUTURE }
enum class BudgetStatus { NO_BUDGET, NO_DATA, NEUTRAL, SAFE, WATCH, HIGH, OVER_BUDGET }
data class BudgetMetrics(
    val allocation: Money?, val actual: Money?, val remaining: Money?,
    val utilizationPercent: BigDecimal?, val dailyAverage: Money?, val projection: Money?,
    val projectionOverflow: Boolean, val dailyAllowance: Money?,
    val elapsedDays: Int, val remainingDays: Int, val coverage: BudgetCoverage, val status: BudgetStatus,
)
object BudgetMonths {
    fun parse(value: String): YearMonth? = if (value.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")) &&
        !value.startsWith("0000")) YearMonth.parse(value) else null
    fun valid(value: YearMonth) = value.year in 1..9999
}

/** Exact consumption-only calculations. Projection rounds once from the original ratio. */
object BudgetCalculator {
    fun calculate(month: YearMonth, today: LocalDate, trackedFrom: LocalDate, currency: CurrencyCode,
        allocation: Money?, records: List<FinanceTransaction>, categoryId: String? = null): AppResult<BudgetMetrics> {
        if (!BudgetMonths.valid(month) || allocation?.minorUnits?.let { it < 0 } == true)
            return AppResult.Failure(AppError.INVALID_INPUT)
        if (allocation != null && allocation.currency != currency) return AppResult.Failure(AppError.CURRENCY_MISMATCH)
        val first = month.atDay(1); val last = month.atEndOfMonth()
        val coverage = when {
            last < trackedFrom -> BudgetCoverage.UNTRACKED
            first > today -> BudgetCoverage.FUTURE
            first < trackedFrom -> BudgetCoverage.PARTIAL
            else -> BudgetCoverage.FULL
        }
        val asOf = minOf(today, last)
        val elapsed = if (asOf < maxOf(first, trackedFrom)) 0 else
            ChronoUnit.DAYS.between(maxOf(first, trackedFrom), asOf).toInt() + 1
        val daysLeft = if (today > last) 0 else if (today < first) month.lengthOfMonth() else last.dayOfMonth - today.dayOfMonth + 1
        val known = coverage != BudgetCoverage.UNTRACKED && coverage != BudgetCoverage.FUTURE && elapsed > 0
        if (!known) return AppResult.Success(BudgetMetrics(allocation, null, null, null, null, null, false,
            null, elapsed, daysLeft, coverage, if (allocation == null) BudgetStatus.NO_BUDGET else BudgetStatus.NO_DATA))
        var sum = BigInteger.ZERO
        for (row in records) if (row.type == TransactionType.EXPENSE && row.date >= maxOf(first, trackedFrom) &&
            row.date <= asOf && (categoryId == null || row.categoryId == categoryId)) {
            if (row.amount.currency != currency) return AppResult.Failure(AppError.CURRENCY_MISMATCH)
            if (row.amount.minorUnits <= 0) return AppResult.Failure(AppError.INVALID_INPUT)
            sum += BigInteger.valueOf(row.amount.minorUnits)
        }
        // BigInteger.longValueExact is API 31+. Check the nonnegative bound before
        // conversion so the minimum supported Android version also stays exact.
        if (sum > BigInteger.valueOf(Long.MAX_VALUE)) return AppResult.Failure(AppError.OUT_OF_RANGE)
        val actual = Money(sum.toLong(), currency)
        val remaining = allocation?.let { Money(it.minorUnits - actual.minorUnits, currency) }
        val percent = allocation?.takeIf { it.minorUnits > 0 }?.let {
            BigDecimal(sum).multiply(BigDecimal(100)).divide(BigDecimal(it.minorUnits), 2, RoundingMode.DOWN)
        }
        val average = Money(BigDecimal(sum).divide(BigDecimal(elapsed), 0, RoundingMode.HALF_EVEN).longValueExact(), currency)
        var projectionOverflow = false
        val projection = if (coverage != BudgetCoverage.FULL) null else try {
            Money(BigDecimal(sum).multiply(BigDecimal(month.lengthOfMonth()))
                .divide(BigDecimal(elapsed), 0, RoundingMode.HALF_EVEN).longValueExact(), currency)
        } catch (_: ArithmeticException) { projectionOverflow = true; null }
        val daily = if (remaining == null || coverage != BudgetCoverage.FULL) null else
            Money(if (daysLeft <= 0) 0 else maxOf(remaining.minorUnits, 0) / daysLeft, currency)
        val status = when {
            allocation == null -> BudgetStatus.NO_BUDGET
            actual.minorUnits > allocation.minorUnits -> BudgetStatus.OVER_BUDGET
            allocation.minorUnits == 0L -> BudgetStatus.NEUTRAL
            sum * BigInteger.valueOf(100) >= BigInteger.valueOf(allocation.minorUnits) * BigInteger.valueOf(95) -> BudgetStatus.HIGH
            sum * BigInteger.valueOf(100) >= BigInteger.valueOf(allocation.minorUnits) * BigInteger.valueOf(80) -> BudgetStatus.WATCH
            else -> BudgetStatus.SAFE
        }
        return AppResult.Success(BudgetMetrics(allocation, actual, remaining, percent, average, projection,
            projectionOverflow, daily, elapsed, daysLeft, coverage, status))
    }
}
