package com.tegar.finance.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

enum class ForecastMethod { ROLLING, WEIGHTED }
enum class ForecastEvidence { UNAVAILABLE, LOW, MEDIUM, HIGHER }
data class ForecastOptions(val method: ForecastMethod = ForecastMethod.ROLLING, val window: Int = 6) {
    val valid: Boolean get() = window in listOf(3, 6, 12)
}
data class ForecastSample(val month: YearMonth, val flows: RecordedFlows, val weight: Int)
enum class ForecastUnavailable { NO_SAMPLES, OUT_OF_RANGE, UNSUPPORTED_DATES }
data class ForecastBand(val low: Money?, val high: Money?)
data class ForecastHorizon(val months: Int, val start: LocalDate, val endExclusive: LocalDate,
    val totals: RecordedFlows?, val unavailable: ForecastUnavailable?,
    val incomeBand: ForecastBand?, val consumptionBand: ForecastBand?, val contributionBand: ForecastBand?)
enum class ForecastRuleKind { MORE_HISTORY, HISTORICAL_DEFICIT, CONTRIBUTION_PRESSURE,
    NEXT_BUDGET_MISSING, NEXT_BUDGET_SHORTFALL, CONSUMPTION_RISE }
data class ForecastRule(val kind: ForecastRuleKind, val amounts: List<Money> = emptyList(), val percent: BigDecimal? = null)
data class FinancialForecast(val asOf: LocalDate, val options: ForecastOptions,
    val windowStart: YearMonth, val windowEnd: YearMonth, val samples: List<ForecastSample>,
    val omittedMonths: Int, val currentMonthRecordsExcluded: Int, val futureRecordsExcluded: Int,
    val evidence: ForecastEvidence, val horizons: List<ForecastHorizon>, val rules: List<ForecastRule>)

/** Complete calendar months only. Estimates repeat a monthly baseline; no trend or return is invented. */
object ForecastCalculator {
    const val RULE_VERSION = "1"
    fun calculate(source: BudgetSnapshot, today: LocalDate, options: ForecastOptions = ForecastOptions()): AppResult<FinancialForecast> {
        if (!options.valid || today.year !in 1..9999 || source.ledger.trackedFromDate.year !in 1..9999)
            return AppResult.Failure(AppError.INVALID_INPUT)
        val currency = source.ledger.currency
        if (source.transactions.any { it.amount.currency != currency } || source.monthly.any { it.allocation.currency != currency })
            return AppResult.Failure(AppError.CURRENCY_MISMATCH)
        if (source.transactions.any { it.amount.minorUnits <= 0 || it.date < source.ledger.trackedFromDate } ||
            source.monthly.any { !BudgetMonths.valid(it.month) || it.allocation.minorUnits < 0 })
            return AppResult.Failure(AppError.INVALID_INPUT)
        val current = YearMonth.from(today)
        val samples = mutableListOf<ForecastSample>()
        // Group once; unknown months never become implicit zero observations.
        val grouped = source.transactions.filter { it.date < current.atDay(1) && it.date <= today }
            .groupBy { YearMonth.from(it.date) }
        for (offset in options.window downTo 1) {
            val month = current.minusMonths(offset.toLong())
            if (!BudgetMonths.valid(month) || month.atDay(1) < source.ledger.trackedFromDate) continue
            val flows = RecordedFlowCalculator.calculate(grouped[month].orEmpty(), currency)
            if (flows is AppResult.Failure) return flows
            samples += ForecastSample(month, (flows as AppResult.Success).value,
                if (options.method == ForecastMethod.ROLLING) 1 else samples.size + 1)
        }
        val evidence = when (samples.size) {
            0 -> ForecastEvidence.UNAVAILABLE
            1 -> ForecastEvidence.LOW
            in 2..5 -> ForecastEvidence.MEDIUM
            else -> ForecastEvidence.HIGHER
        }
        fun numerator(amount: (RecordedFlows) -> Money): BigInteger = samples.fold(BigInteger.ZERO) { sum, sample ->
            sum + BigInteger.valueOf(amount(sample.flows).minorUnits) * BigInteger.valueOf(sample.weight.toLong())
        }
        val denominator = BigInteger.valueOf(samples.sumOf { it.weight }.toLong())
        fun estimate(numerator: BigInteger, months: Int) = BigDecimal(numerator * BigInteger.valueOf(months.toLong()))
            .divide(BigDecimal(denominator), 0, RoundingMode.HALF_EVEN).toBigIntegerExact()
        fun band(months: Int, amount: (RecordedFlows) -> Money): ForecastBand {
            val values = samples.map { BigInteger.valueOf(amount(it.flows).minorUnits) * BigInteger.valueOf(months.toLong()) }
            fun money(value: BigInteger) = try { value.toMoneyExact(currency) } catch (_: ArithmeticException) { null }
            return ForecastBand(money(values.minOrNull()!!), money(values.maxOrNull()!!))
        }
        val horizons = listOf(1, 3).map { months ->
            val start = current.plusMonths(1).atDay(1)
            val end = current.plusMonths(months.toLong() + 1).atDay(1)
            when {
                start.year !in 1..9999 || end.minusDays(1).year !in 1..9999 ->
                    ForecastHorizon(months, start, end, null, ForecastUnavailable.UNSUPPORTED_DATES, null, null, null)
                samples.isEmpty() -> ForecastHorizon(months, start, end, null, ForecastUnavailable.NO_SAMPLES, null, null, null)
                else -> {
                    val result = RecordedFlowCalculator.fromTotals(estimate(numerator { it.income }, months),
                        estimate(numerator { it.consumption }, months), estimate(numerator { it.contributions }, months), currency)
                    ForecastHorizon(months, start, end, (result as? AppResult.Success)?.value,
                        if (result is AppResult.Failure) ForecastUnavailable.OUT_OF_RANGE else null,
                        band(months) { it.income }, band(months) { it.consumption }, band(months) { it.contributions })
                }
            }
        }
        val rules = buildList {
            if (samples.size < 2) add(ForecastRule(ForecastRuleKind.MORE_HISTORY))
            val next = horizons.first().totals
            if (next != null) {
                if (next.consumption.minorUnits > next.income.minorUnits)
                    add(ForecastRule(ForecastRuleKind.HISTORICAL_DEFICIT, listOf(next.consumption, next.income)))
                // BigInteger preserves the rule even when a signed net flow cannot be displayed.
                if (BigInteger.valueOf(next.income.minorUnits) - BigInteger.valueOf(next.consumption.minorUnits) -
                    BigInteger.valueOf(next.contributions.minorUnits) < BigInteger.ZERO && next.contributions.minorUnits > 0)
                    add(ForecastRule(ForecastRuleKind.CONTRIBUTION_PRESSURE, listOf(next.income, next.consumption, next.contributions)))
                val cap = source.monthly.firstOrNull { it.month == current.plusMonths(1) }?.allocation
                if (cap == null) add(ForecastRule(ForecastRuleKind.NEXT_BUDGET_MISSING))
                else if (next.consumption.minorUnits > cap.minorUnits)
                    add(ForecastRule(ForecastRuleKind.NEXT_BUDGET_SHORTFALL, listOf(next.consumption, cap)))
            }
            if (samples.size >= 2) {
                val previous = samples[samples.lastIndex - 1].flows.consumption
                val latest = samples.last().flows.consumption
                val prior = BigInteger.valueOf(previous.minorUnits); val recent = BigInteger.valueOf(latest.minorUnits)
                if (prior.signum() > 0 && recent * BigInteger.valueOf(100) >= prior * BigInteger.valueOf(120))
                    add(ForecastRule(ForecastRuleKind.CONSUMPTION_RISE, listOf(latest, previous),
                        BigDecimal(recent - prior).multiply(BigDecimal(100)).divide(BigDecimal(prior), 2, RoundingMode.DOWN)))
            }
        }
        return AppResult.Success(FinancialForecast(today, options, maxOf(current.minusMonths(options.window.toLong()), YearMonth.of(1, 1)),
            current.minusMonths(1), samples, options.window - samples.size,
            source.transactions.count { YearMonth.from(it.date) == current && it.date <= today },
            source.transactions.count { it.date > today }, evidence, horizons, rules))
    }
}
