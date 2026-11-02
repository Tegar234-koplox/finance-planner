package com.tegar.finance.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class AllocationBucket { CASH, STOCK, BOND, FUND, GOLD, OTHER }
data class InvestmentHolding(val id: String, val name: String, val symbol: String, val bucket: AllocationBucket,
    val acquiredOn: LocalDate, val cost: Money?, val notes: String, val active: Boolean, val createdAt: Instant, val updatedAt: Instant?)
data class HoldingDraft(val name: String, val symbol: String, val bucket: AllocationBucket, val acquiredOn: LocalDate,
    val cost: Money? = null, val notes: String = "", val active: Boolean = true)
data class HoldingValue(val id: String, val holdingId: String, val date: LocalDate, val amount: Money,
    val notes: String, val createdAt: Instant, val updatedAt: Instant?)
data class HoldingValueDraft(val holdingId: String, val date: LocalDate, val amount: Money, val notes: String = "")
data class AllocationTarget(val bucket: AllocationBucket, val basisPoints: Int)
data class ContributionPlan(val month: YearMonth, val target: Money)
data class InvestmentCaptureLine(val sourceHoldingId: String, val name: String, val bucket: AllocationBucket,
    val amount: Money, val valuedOn: LocalDate)
data class PortfolioSource(val ledger: Ledger, val transactions: List<FinanceTransaction>, val holdings: List<InvestmentHolding>,
    val values: List<HoldingValue>, val targets: List<AllocationTarget>, val plans: List<ContributionPlan>, val categories: List<Category> = emptyList())
data class HoldingStatus(val holding: InvestmentHolding, val latest: HoldingValue?, val ageDays: Long?, val included: Boolean)
data class InvestmentValuation(val total: Money?, val overflow: Boolean, val missing: Int,
    val statuses: List<HoldingStatus>, val futureCount: Int)
data class AllocationStatus(val bucket: AllocationBucket, val value: Money?, val actualBasisPoints: Int?,
    val targetBasisPoints: Int?, val gap: Money?)
data class ContributionProgress(val month: YearMonth, val coverage: BudgetCoverage, val target: Money?, val contributed: Money?,
    val redeemed: Money?, val net: Money?, val remaining: Money?, val percent: BigDecimal?)
data class PortfolioOverview(val today: LocalDate, val cash: Money?, val valuation: InvestmentValuation,
    val total: Money?, val totalOverflow: Boolean, val allocations: List<AllocationStatus>, val allocationAvailable: Boolean,
    val progress: ContributionProgress, val movements: List<FinanceTransaction>)

object InvestmentRules {
    fun validateHolding(draft: HoldingDraft, currency: CurrencyCode, today: LocalDate): AppError? = when {
        draft.cost?.currency?.let { it != currency } == true -> AppError.CURRENCY_MISMATCH
        !CategoryNames.valid(draft.name) || draft.bucket == AllocationBucket.CASH || draft.symbol.length > 30 ||
            draft.symbol.any(Char::isISOControl) || draft.notes.length > 500 || draft.cost?.minorUnits?.let { it < 0 } == true ||
            draft.acquiredOn.year !in 1..9999 || draft.acquiredOn > today -> AppError.INVALID_INPUT
        else -> null
    }
    fun validateValue(draft: HoldingValueDraft, holding: InvestmentHolding, currency: CurrencyCode, today: LocalDate): AppError? = when {
        draft.amount.currency != currency -> AppError.CURRENCY_MISMATCH
        draft.holdingId != holding.id || draft.amount.minorUnits < 0 || draft.notes.length > 500 ||
            draft.date.year !in 1..9999 || draft.date < holding.acquiredOn || draft.date > today -> AppError.INVALID_INPUT
        else -> null
    }
    fun validateTargets(rows: List<AllocationTarget>): AppError? = when {
        rows.isEmpty() -> null
        rows.map { it.bucket }.toSet().size != AllocationBucket.entries.size || rows.size != AllocationBucket.entries.size ||
            rows.any { it.basisPoints !in 0..10000 } || rows.sumOf { it.basisPoints.toLong() } != 10000L -> AppError.ALLOCATION_TOTAL
        else -> null
    }
    fun percent(text: String): AppResult<Int> {
        val value = text.trim()
        if (!value.matches(Regex("[0-9]{1,3}(,[0-9]{1,2})?"))) return AppResult.Failure(AppError.INVALID_INPUT)
        val bps = BigDecimal(value.replace(',', '.')).movePointRight(2).intValueExact()
        return if (bps in 0..10000) AppResult.Success(bps) else AppResult.Failure(AppError.INVALID_INPUT)
    }
}

object InvestmentValuations {
    fun calculate(ledger: Ledger, holdings: List<InvestmentHolding>, values: List<HoldingValue>, today: LocalDate): AppResult<InvestmentValuation> {
        val map = holdings.associateBy { it.id }
        if (map.size != holdings.size || values.map { it.holdingId to it.date }.distinct().size != values.size)
            return AppResult.Failure(AppError.INVALID_INPUT)
        for (holding in holdings) InvestmentRules.validateHolding(HoldingDraft(holding.name, holding.symbol, holding.bucket,
            holding.acquiredOn, holding.cost, holding.notes, holding.active), ledger.currency, LocalDate.of(9999, 12, 31))?.let { return AppResult.Failure(it) }
        for (value in values) {
            val holding = map[value.holdingId] ?: return AppResult.Failure(AppError.INVALID_INPUT)
            InvestmentRules.validateValue(HoldingValueDraft(value.holdingId, value.date, value.amount, value.notes), holding,
                ledger.currency, LocalDate.of(9999, 12, 31))?.let { return AppResult.Failure(it) }
        }
        val groups = values.filter { it.date <= today }.groupBy { it.holdingId }
        var sum = BigInteger.ZERO; var missing = 0
        val statuses = holdings.sortedWith(compareByDescending<InvestmentHolding> { it.active }.thenBy { it.name }.thenBy { it.id }).map { holding ->
            val included = holding.active && holding.acquiredOn <= today
            val latest = groups[holding.id]?.maxByOrNull { it.date }
            if (included) { if (latest == null) missing++ else sum += BigInteger.valueOf(latest.amount.minorUnits) }
            HoldingStatus(holding, latest, latest?.let { ChronoUnit.DAYS.between(it.date, today) }, included)
        }
        val money = try { sum.toMoneyExact(ledger.currency) } catch (_: ArithmeticException) { null }
        return AppResult.Success(InvestmentValuation(money.takeIf { missing == 0 }, money == null, missing, statuses,
            values.count { it.date > today } + holdings.count { it.active && it.acquiredOn > today }))
    }
}

object PortfolioCalculator {
    private fun money(value: BigInteger, currency: CurrencyCode) = try { value.toMoneyExact(currency) } catch (_: ArithmeticException) { null }
    /** Largest remainder with stable enum-order ties; both weights and target cents add up exactly. */
    private fun distribute(weights: List<BigInteger>, units: BigInteger): List<BigInteger> {
        val denominator = weights.fold(BigInteger.ZERO, BigInteger::add)
        val divided = weights.map { (it * units).divideAndRemainder(denominator) }
        val amounts = divided.map { it[0] }.toMutableList()
        val remainder = units - amounts.fold(BigInteger.ZERO, BigInteger::add)
        check(remainder >= BigInteger.ZERO && remainder < BigInteger.valueOf(weights.size.toLong()))
        val leftover = remainder.toInt()
        val order = weights.indices.sortedWith(compareByDescending<Int> { divided[it][1] }.thenBy { it })
        for (index in order.take(leftover)) amounts[index] += BigInteger.ONE
        return amounts
    }
    fun calculate(source: PortfolioSource, today: LocalDate, month: YearMonth = YearMonth.from(today)): AppResult<PortfolioOverview> {
        if (today.year !in 1..9999 || !BudgetMonths.valid(month) || source.plans.map { it.month }.distinct().size != source.plans.size)
            return AppResult.Failure(AppError.INVALID_INPUT)
        val currency = source.ledger.currency
        InvestmentRules.validateTargets(source.targets)?.let { return AppResult.Failure(it) }
        for (plan in source.plans) {
            if (plan.target.currency != currency) return AppResult.Failure(AppError.CURRENCY_MISMATCH)
            if (!BudgetMonths.valid(plan.month) || plan.target.minorUnits < 0) return AppResult.Failure(AppError.INVALID_INPUT)
        }
        val valued = InvestmentValuations.calculate(source.ledger, source.holdings, source.values, today)
        if (valued is AppResult.Failure) return valued
        val valuation = (valued as AppResult.Success).value
        val cashResult = RecordedCashCalculator.calculate(source.ledger, source.transactions, today)
        if (cashResult is AppResult.Failure && cashResult.error != AppError.OUT_OF_RANGE) return cashResult
        val cash = (cashResult as? AppResult.Success)?.value
        val gross = valuation.total
        val combined = if (cash != null && gross != null) money(BigInteger.valueOf(cash.minorUnits) + BigInteger.valueOf(gross.minorUnits), currency) else null
        val bucketTotals = AllocationBucket.entries.map { bucket ->
            if (bucket == AllocationBucket.CASH) BigInteger.valueOf(cash?.minorUnits ?: 0) else
                valuation.statuses.filter { it.included && it.holding.bucket == bucket }.fold(BigInteger.ZERO) { sum, row -> sum + BigInteger.valueOf(row.latest?.amount?.minorUnits ?: 0) }
        }
        val available = combined != null && combined.minorUnits > 0 && cash!!.minorUnits >= 0
        val actualBps = if (available) distribute(bucketTotals, BigInteger.valueOf(10000)) else null
        val targetMap = source.targets.associate { it.bucket to it.basisPoints }
        val targets = if (available && targetMap.isNotEmpty()) distribute(AllocationBucket.entries.map { BigInteger.valueOf(targetMap.getValue(it).toLong()) }, BigInteger.valueOf(combined!!.minorUnits)) else null
        val allocations = AllocationBucket.entries.mapIndexed { index, bucket ->
            val current = if (bucket == AllocationBucket.CASH) cash else if (valuation.missing == 0 && !valuation.overflow) money(bucketTotals[index], currency) else null
            AllocationStatus(bucket, current, actualBps?.get(index)?.let { check(it in BigInteger.ZERO..BigInteger.valueOf(10000)); it.toInt() }, targetMap[bucket],
                if (targets != null) money(targets[index] - bucketTotals[index], currency) else null)
        }
        val start = month.atDay(1); val end = month.atEndOfMonth()
        val coverage = when {
            start > today -> BudgetCoverage.FUTURE
            end < source.ledger.trackedFromDate || today < source.ledger.trackedFromDate -> BudgetCoverage.UNTRACKED
            source.ledger.trackedFromDate > start -> BudgetCoverage.PARTIAL
            else -> BudgetCoverage.FULL
        }
        val movements = source.transactions.filter { it.type == TransactionType.INVESTMENT && YearMonth.from(it.date) == month }
            .sortedWith(compareByDescending<FinanceTransaction> { it.date }.thenByDescending { it.createdAt }.thenBy { it.id })
        val flow = if (coverage in listOf(BudgetCoverage.FULL, BudgetCoverage.PARTIAL))
            RecordedFlowCalculator.calculate(movements.filter { it.date <= today }, currency) else null
        if (flow is AppResult.Failure) return flow
        val totals = (flow as? AppResult.Success)?.value
        val target = source.plans.firstOrNull { it.month == month }?.target
        val contributed = totals?.contributions; val redeemed = totals?.redemptions
        val net = if (contributed != null && redeemed != null) money(BigInteger.valueOf(contributed.minorUnits) - BigInteger.valueOf(redeemed.minorUnits), currency) else null
        val remaining = if (target != null && contributed != null) Money(maxOf(0, target.minorUnits - contributed.minorUnits), currency) else null
        val percent = if (target != null && target.minorUnits > 0 && contributed != null)
            BigDecimal.valueOf(contributed.minorUnits).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(target.minorUnits), 2, RoundingMode.DOWN) else null
        return AppResult.Success(PortfolioOverview(today, cash, valuation, combined,
            cashResult is AppResult.Failure || valuation.overflow || (cash != null && gross != null && combined == null), allocations, available,
            ContributionProgress(month, coverage, target, contributed, redeemed, net, remaining, percent), movements))
    }
}
