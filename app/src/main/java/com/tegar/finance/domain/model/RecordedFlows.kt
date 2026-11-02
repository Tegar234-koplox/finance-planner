package com.tegar.finance.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate

object RecordedCashCalculator {
    fun calculate(ledger: Ledger, rows: List<FinanceTransaction>, today: LocalDate): AppResult<Money?> {
        if (ledger.openingCash.currency != ledger.currency || rows.any { it.amount.currency != ledger.currency })
            return AppResult.Failure(AppError.CURRENCY_MISMATCH)
        if (rows.any { it.amount.minorUnits <= 0 || it.date < ledger.trackedFromDate })
            return AppResult.Failure(AppError.INVALID_INPUT)
        if (today < ledger.trackedFromDate) return AppResult.Success(null)
        return try {
            var cash = BigInteger.valueOf(ledger.openingCash.minorUnits)
            for (row in rows) if (row.date <= today) cash += BigInteger.valueOf(row.amount.minorUnits).let {
                if (row.type == TransactionType.INCOME) it else -it
            }
            AppResult.Success(cash.toMoneyExact(ledger.currency))
        } catch (_: ArithmeticException) { AppResult.Failure(AppError.OUT_OF_RANGE) }
    }
}

data class RecordedFlows(val income: Money, val consumption: Money, val contributions: Money,
    val saving: Money, val savingRatePercent: BigDecimal?, val netCashFlow: Money?, val netCashFlowOverflow: Boolean)

/** Shared accounting owner for Home and reports; investments are not consumption. */
object RecordedFlowCalculator {
    fun calculate(rows: List<FinanceTransaction>, currency: CurrencyCode): AppResult<RecordedFlows> {
        if (rows.any { it.amount.currency != currency }) return AppResult.Failure(AppError.CURRENCY_MISMATCH)
        if (rows.any { it.amount.minorUnits <= 0 }) return AppResult.Failure(AppError.INVALID_INPUT)
        var income = BigInteger.ZERO; var expense = BigInteger.ZERO; var investments = BigInteger.ZERO
        for (row in rows) when (row.type) {
            TransactionType.INCOME -> income += BigInteger.valueOf(row.amount.minorUnits)
            TransactionType.EXPENSE -> expense += BigInteger.valueOf(row.amount.minorUnits)
            TransactionType.INVESTMENT -> investments += BigInteger.valueOf(row.amount.minorUnits)
        }
        return fromTotals(income, expense, investments, currency)
    }
    internal fun fromTotals(income: BigInteger, expense: BigInteger, investments: BigInteger,
        currency: CurrencyCode): AppResult<RecordedFlows> {
        if (income.signum() < 0 || expense.signum() < 0 || investments.signum() < 0)
            return AppResult.Failure(AppError.INVALID_INPUT)
        return try {
            val saving = income - expense
            val net = try { (saving - investments).toMoneyExact(currency) } catch (_: ArithmeticException) { null }
            AppResult.Success(RecordedFlows(income.toMoneyExact(currency), expense.toMoneyExact(currency),
                investments.toMoneyExact(currency), saving.toMoneyExact(currency),
                if (income.signum() == 0) null else BigDecimal(saving).multiply(BigDecimal(100))
                    .divide(BigDecimal(income), 2, RoundingMode.HALF_EVEN), net, net == null))
        } catch (_: ArithmeticException) { AppResult.Failure(AppError.OUT_OF_RANGE) }
    }
}
internal fun BigInteger.toMoneyExact(currency: CurrencyCode): Money {
    if (this < BigInteger.valueOf(Long.MIN_VALUE) || this > BigInteger.valueOf(Long.MAX_VALUE))
        throw ArithmeticException("Money range")
    return Money(toLong(), currency)
}
