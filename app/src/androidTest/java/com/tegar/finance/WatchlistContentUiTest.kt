package com.tegar.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tegar.finance.core.ui.AppLocalization
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.presentation.*
import java.math.BigDecimal
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class WatchlistContentUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun indicesShowExactPointsWhileEtfsKeepUsdAndUnknownValuesStayUnavailableInBothLanguages() {
        val now = Instant.parse("2026-11-03T01:00:00Z")
        val key = InstrumentKey("twelvedata", "US_INDEX", "SPX")
        val entry = WatchlistEntry("test-index", key, "Index", InstrumentKind.INDEX, now)
        val quote = MarketQuote(key, BigDecimal("5123.123456789012345678"), CurrencyCode("USD"), now, now, "Test source")
        val language = mutableStateOf(AppLanguage.ENGLISH)
        val state = mutableStateOf(MarketUiState(loading = false, rows = listOf(MarketQuoteRow(QuoteRecord(key, quote, now), QuoteFreshness.FRESH, entry))))
        compose.setContent { AppLocalization(language.value) { MaterialTheme { MarketScreen(state.value, {}, {}, {}, {}) } } }
        compose.onNodeWithTag("market_price_SPX").assertTextEquals("5,123.123456789012345678 pts")
        compose.runOnIdle { language.value = AppLanguage.INDONESIAN }
        compose.onNodeWithTag("market_price_SPX").assertTextEquals("5.123,123456789012345678 poin")
        compose.runOnIdle {
            val etfKey = key.copy(market = "ARCX", symbol = "SPY")
            val etf = entry.copy(key = etfKey, kind = InstrumentKind.ETF)
            state.value = state.value.copy(rows = listOf(MarketQuoteRow(QuoteRecord(etfKey, quote.copy(key = etfKey), now), QuoteFreshness.FRESH, etf)))
        }
        compose.onNodeWithTag("market_price_SPY").assertTextEquals("USD 5.123,123456789012345678")
        compose.runOnIdle { state.value = state.value.copy(rows = listOf(MarketQuoteRow(QuoteRecord(key, lastAttemptAt = now), QuoteFreshness.MISSING, entry))) }
        compose.onNodeWithTag("market_price_SPX").assertTextEquals("Harga tidak tersedia")
    }
}
