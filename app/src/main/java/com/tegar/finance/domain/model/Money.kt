package com.tegar.finance.domain.model

import java.math.BigDecimal
import java.util.Currency

data class CurrencyCode(val value: String) {
    init {
        require(value.matches(Regex("[A-Z]{3}")))
        require(Currency.getInstance(value).defaultFractionDigits in 0..3)
    }
    val fractionDigits: Int get() = Currency.getInstance(value).defaultFractionDigits
    companion object { val IDR = CurrencyCode("IDR") }
}
data class Money(val minorUnits: Long, val currency: CurrencyCode = CurrencyCode.IDR) {
    fun decimalAmount(): BigDecimal = BigDecimal.valueOf(minorUnits, currency.fractionDigits)
    fun add(other: Money): AppResult<Money> = calculate(other, Math::addExact)
    fun subtract(other: Money): AppResult<Money> = calculate(other, Math::subtractExact)
    private fun calculate(other: Money, operation: (Long, Long) -> Long): AppResult<Money> {
        if (currency != other.currency) return AppResult.Failure(AppError.CURRENCY_MISMATCH)
        return try { AppResult.Success(copy(minorUnits = operation(minorUnits, other.minorUnits))) }
        catch (_: ArithmeticException) { AppResult.Failure(AppError.OUT_OF_RANGE) }
    }
}
