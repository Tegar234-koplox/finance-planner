package com.tegar.finance.domain.model

import com.tegar.finance.core.datetime.DatePeriod
import com.tegar.finance.core.datetime.DatePeriods
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.*
import java.time.temporal.ChronoUnit

enum class ReportKind { WEEK, MONTH, YEAR }
data class ReportSelection(val kind: ReportKind, val date: LocalDate) {
    val valid: Boolean get() = date.year in 1..9999
    fun period(): DatePeriod = when (kind) {
        ReportKind.WEEK -> DatePeriods.week(date)
        ReportKind.MONTH -> DatePeriods.month(YearMonth.from(date))
        ReportKind.YEAR -> DatePeriods.year(date.year)
    }
    fun move(offset: Long): ReportSelection = copy(date = when (kind) {
        ReportKind.WEEK -> period().start.plusWeeks(offset)
        ReportKind.MONTH -> period().start.plusMonths(offset)
        ReportKind.YEAR -> period().start.plusYears(offset)
    })
    companion object {
        fun parseDate(text: String): LocalDate? = try {
            if (!text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) null
            else LocalDate.parse(text).takeIf { it.year in 1..9999 }
        } catch (_: DateTimeException) { null }
    }
}
enum class ReportCoverage { COMPLETE, IN_PROGRESS, PARTIAL_HISTORY, UNTRACKED, FUTURE }
data class ReportSlice(val period: DatePeriod, val observed: DatePeriod?, val coverage: ReportCoverage,
    val totals: RecordedFlows?, val count: Int)
enum class ComparisonUnavailable { NO_CURRENT_HISTORY, PARTIAL_HISTORY, MISSING_PREVIOUS_HISTORY, PREVIOUS_SHORTER, OUT_OF_RANGE, UNSUPPORTED_PREVIOUS }
data class ReportComparison(val previous: ReportSlice, val alignedDays: Boolean,
    val incomeDelta: Money, val expenseDelta: Money, val savingDelta: Money,
    val incomeChangePercent: BigDecimal?, val expenseChangePercent: BigDecimal?)
data class ReportCategory(val categoryId: String, val amount: Money, val sharePercent: BigDecimal)
data class FinanceReport(val selection: ReportSelection, val asOf: LocalDate, val current: ReportSlice,
    val comparison: ReportComparison?, val comparisonUnavailable: ComparisonUnavailable?,
    val buckets: List<ReportSlice>, val categories: List<ReportCategory>, val futureExcludedCount: Int)

object ReportCalculator {
    fun calculate(source: BudgetSnapshot, selection: ReportSelection, today: LocalDate): AppResult<FinanceReport> {
        if (!selection.valid || today.year !in 1..9999) return AppResult.Failure(AppError.INVALID_INPUT)
        if (source.transactions.any { it.amount.currency != source.ledger.currency })
            return AppResult.Failure(AppError.CURRENCY_MISMATCH)
        if (source.transactions.any { it.amount.minorUnits <= 0 || it.date < source.ledger.trackedFromDate })
            return AppResult.Failure(AppError.INVALID_INPUT)
        return try {
            val period = selection.period()
            val current = slice(source, period, today)
            val buckets = buildList {
                var date = period.start
                while (date < period.endExclusive) {
                    val next = if (selection.kind == ReportKind.YEAR) date.plusMonths(1) else date.plusDays(1)
                    add(slice(source, DatePeriod(date, next), today)); date = next
                }
            }
            var unavailable: ComparisonUnavailable? = null
            var comparison: ReportComparison? = null
            when {
                current.totals == null -> unavailable = ComparisonUnavailable.NO_CURRENT_HISTORY
                current.coverage == ReportCoverage.PARTIAL_HISTORY -> unavailable = ComparisonUnavailable.PARTIAL_HISTORY
                else -> {
                    val previousSelection = selection.move(-1)
                    if (!previousSelection.valid) unavailable = ComparisonUnavailable.UNSUPPORTED_PREVIOUS
                    else {
                        val previousFull = previousSelection.period()
                        val aligned = current.coverage == ReportCoverage.IN_PROGRESS
                        val days = ChronoUnit.DAYS.between(current.observed!!.start, current.observed.endExclusive)
                        if (aligned && days > ChronoUnit.DAYS.between(previousFull.start, previousFull.endExclusive))
                            unavailable = ComparisonUnavailable.PREVIOUS_SHORTER
                        else {
                            val previousPeriod = if (aligned) DatePeriod(previousFull.start, previousFull.start.plusDays(days)) else previousFull
                            val previous = try { slice(source, previousPeriod, today) }
                                catch (_: ReportCalculationError) { null }
                            if (previous == null) unavailable = ComparisonUnavailable.OUT_OF_RANGE
                            else if (previous.coverage != ReportCoverage.COMPLETE) unavailable = ComparisonUnavailable.MISSING_PREVIOUS_HISTORY
                            else {
                                val currentFlows = current.totals
                                val prior = previous.totals!!
                                try {
                                    fun delta(a: Money, b: Money) = (BigInteger.valueOf(a.minorUnits) - BigInteger.valueOf(b.minorUnits)).toMoneyExact(a.currency)
                                    comparison = ReportComparison(previous, aligned, delta(currentFlows.income, prior.income),
                                        delta(currentFlows.consumption, prior.consumption), delta(currentFlows.saving, prior.saving),
                                        change(currentFlows.income, prior.income), change(currentFlows.consumption, prior.consumption))
                                } catch (_: ArithmeticException) { unavailable = ComparisonUnavailable.OUT_OF_RANGE }
                            }
                        }
                    }
                }
            }
            val categoryTotals = mutableMapOf<String, BigInteger>()
            if (current.observed != null) for (row in source.transactions) {
                if (row.type == TransactionType.EXPENSE && row.date in current.observed)
                    categoryTotals[row.categoryId] = (categoryTotals[row.categoryId] ?: BigInteger.ZERO) + BigInteger.valueOf(row.amount.minorUnits)
            }
            val categories = categoryTotals.map { (id, amount) -> ReportCategory(id, amount.toMoneyExact(source.ledger.currency),
                BigDecimal(amount).multiply(BigDecimal(100)).divide(BigDecimal(current.totals!!.consumption.minorUnits), 2, RoundingMode.DOWN))
            }.sortedWith(compareByDescending<ReportCategory> { it.amount.minorUnits }.thenBy { it.categoryId })
            AppResult.Success(FinanceReport(selection, today, current, comparison, unavailable, buckets, categories,
                source.transactions.count { it.date in period && it.date > today }))
        } catch (error: ReportCalculationError) { AppResult.Failure(error.error) }
        catch (_: ArithmeticException) { AppResult.Failure(AppError.OUT_OF_RANGE) }
    }
    private fun slice(source: BudgetSnapshot, period: DatePeriod, today: LocalDate): ReportSlice {
        val start = maxOf(period.start, source.ledger.trackedFromDate)
        val end = minOf(period.endExclusive, today.plusDays(1))
        if (start >= end) return ReportSlice(period, null,
            if (period.start > today) ReportCoverage.FUTURE else ReportCoverage.UNTRACKED, null, 0)
        val observed = DatePeriod(start, end)
        val rows = source.transactions.filter { it.date in observed }
        val calculated = RecordedFlowCalculator.calculate(rows, source.ledger.currency)
        if (calculated is AppResult.Failure) throw ReportCalculationError(calculated.error)
        val coverage = when {
            start > period.start -> ReportCoverage.PARTIAL_HISTORY
            end < period.endExclusive -> ReportCoverage.IN_PROGRESS
            else -> ReportCoverage.COMPLETE
        }
        return ReportSlice(period, observed, coverage, (calculated as AppResult.Success).value, rows.size)
    }
    private fun change(current: Money, previous: Money): BigDecimal? = if (previous.minorUnits == 0L) null else
        BigDecimal.valueOf(current.minorUnits).subtract(BigDecimal.valueOf(previous.minorUnits)).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(previous.minorUnits), 2, RoundingMode.HALF_EVEN)
    private class ReportCalculationError(val error: AppError) : RuntimeException()
}
