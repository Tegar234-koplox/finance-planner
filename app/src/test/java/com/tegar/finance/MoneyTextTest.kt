package com.tegar.finance

import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class MoneyTextTest {
    private fun amount(text: String): Long = (MoneyText.parse(text) as AppResult.Success).value.minorUnits
    @Test fun rupiahIsExact() { assertEquals(50_000_000L, amount("500.000")) }
    @Test fun fractionalRupiahIsPreserved() { assertEquals(50_000_050L, amount("500.000,50")) }
    @Test fun ungroupedInputIsValid() { assertEquals(50_000_000L, amount("500000")) }
    @Test fun minimumMinorUnit() { assertEquals(1L, amount("0,01")) }
    @Test fun negativeCashIsRepresentable() { assertEquals(-100L, amount("-1")) }
    @Test fun zeroIsRepresentable() { assertEquals(0L, amount("0")) }
    @Test fun longMaximumRemainsExact() { assertEquals(Long.MAX_VALUE, amount("92233720368547758,07")) }
    @Test fun longMinimumRemainsExact() { assertEquals(Long.MIN_VALUE, amount("-92233720368547758,08")) }
    @Test fun overflowingInputFails() {
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), MoneyText.parse("92233720368547758,08"))
    }
    @Test fun malformedInputsAreRejected() {
        listOf("", "1e3", "NaN", "Rp100", "1.23", "12.34.567", "1,234", "+100", "1 000", "9".repeat(65)).forEach {
            assertEquals(it, AppResult.Failure(AppError.INVALID_INPUT), MoneyText.parse(it))
        }
    }
    @Test fun usGroupingUsesItsOwnLocale() {
        assertEquals(AppResult.Success(Money(123456, CurrencyCode("USD"))),
            MoneyText.parse("1,234.56", CurrencyCode("USD"), Locale.US))
    }
    @Test fun zeroScaleCurrencyRejectsFraction() {
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), MoneyText.parse("1.1", CurrencyCode("JPY"), Locale.US))
    }
    @Test fun additionDetectsOverflow() {
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), Money(Long.MAX_VALUE).add(Money(1)))
    }
    @Test fun subtractionDetectsOverflow() {
        assertEquals(AppResult.Failure(AppError.OUT_OF_RANGE), Money(Long.MIN_VALUE).subtract(Money(1)))
    }
    @Test fun currenciesCannotBeMixed() {
        assertEquals(AppResult.Failure(AppError.CURRENCY_MISMATCH), Money(100).add(Money(100, CurrencyCode("USD"))))
    }
    @Test fun arithmeticIsExactBeyondDoublePrecision() {
        assertEquals(AppResult.Success(Money(9_007_199_254_740_993L)), Money(9_007_199_254_740_992L).add(Money(1)))
    }
    @Test fun formattingDoesNotDropFractions() {
        assertTrue(MoneyText.format(Money(50_000_050)).contains("500.000,50"))
        assertFalse(MoneyText.format(Money(50_000_000)).contains(",00"))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCurrencyFails() { CurrencyCode("idr") }
}
