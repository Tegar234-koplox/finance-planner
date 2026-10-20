package com.tegar.finance.core.currency

import com.tegar.finance.domain.model.*
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Numeric input uses explicit locale, strict grouping, and no symbols/exponents. */
object MoneyText {
    val Indonesian: Locale = Locale.forLanguageTag("id-ID")
    fun parse(input: String, currency: CurrencyCode = CurrencyCode.IDR, locale: Locale = Indonesian): AppResult<Money> {
        val text = input.trim()
        if (text.isEmpty() || text.length > 64) return AppResult.Failure(AppError.INVALID_INPUT)
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val group = Regex.escape(symbols.groupingSeparator.toString())
        val decimal = Regex.escape(symbols.decimalSeparator.toString())
        val whole = "(?:[0-9]+|[0-9]{1,3}(?:$group[0-9]{3})+)"
        val fraction = if (currency.fractionDigits == 0) "" else "(?:$decimal[0-9]{1,${currency.fractionDigits}})?"
        if (!Regex("-?$whole$fraction").matches(text)) return AppResult.Failure(AppError.INVALID_INPUT)
        return try {
            val exact = BigDecimal(text.replace(symbols.groupingSeparator.toString(), "").replace(symbols.decimalSeparator, '.'))
            AppResult.Success(Money(exact.movePointRight(currency.fractionDigits).longValueExact(), currency))
        } catch (_: ArithmeticException) { AppResult.Failure(AppError.OUT_OF_RANGE) }
        catch (_: NumberFormatException) { AppResult.Failure(AppError.INVALID_INPUT) }
    }
    fun format(money: Money, locale: Locale = Indonesian): String {
        val amount = money.decimalAmount()
        return NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(money.currency.value)
            minimumFractionDigits = if (amount.stripTrailingZeros().scale() <= 0) 0 else money.currency.fractionDigits
            maximumFractionDigits = money.currency.fractionDigits
        }.format(amount)
    }
}
