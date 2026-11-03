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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MarketContentUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cachedExactPriceSourceTimeAndFailureRemainVisibleInBothLanguages() {
        val key = InstrumentKey("test", "XNAS", "ABC")
        val now = Instant.parse("2026-11-03T01:00:00Z")
        val quote = MarketQuote(key, BigDecimal("1.123456789012345678"), CurrencyCode("USD"), now, now, "Test source")
        val language = mutableStateOf(AppLanguage.ENGLISH)
        val state = mutableStateOf(MarketUiState(loading = false, rows = listOf(MarketQuoteRow(
            QuoteRecord(key, quote, now, QuoteFailure.OFFLINE), QuoteFreshness.STALE))))
        compose.setContent { AppLocalization(language.value) { MaterialTheme {
            MarketScreen(state.value, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("market_price_ABC").assertTextEquals("USD 1.123456789012345678")
        compose.onNodeWithText("XNAS · Test source").assertExists()
        compose.onNodeWithText("Observed: 2026-11-03 01:00:00 UTC").assertExists()
        compose.onNodeWithText("Outdated").assertExists(); compose.onNodeWithTag("market_quote_failure").assertExists()
        compose.runOnIdle { language.value = AppLanguage.INDONESIAN }
        compose.onNodeWithTag("market_price_ABC").assertTextEquals("USD 1,123456789012345678")
        compose.onNodeWithText("Data lama").assertExists(); compose.onNodeWithText("Luring. Pembaruan tidak tersedia.").assertExists()
        compose.runOnIdle { state.value = state.value.copy(rows = listOf(MarketQuoteRow(
            QuoteRecord(key, lastAttemptAt = now, failure = QuoteFailure.NO_DATA), QuoteFreshness.MISSING))) }
        compose.onNodeWithTag("market_price_ABC").assertTextEquals("Harga tidak tersedia")
        compose.onNodeWithText("Tanpa harga").assertExists()
    }
    @Test fun emptyErrorLoadingAndOptionalHelpDoNotMisrepresentMarketAvailability() {
        val state = mutableStateOf(MarketUiState(loading = false)); var opened = 0; var retried = 0
        compose.setContent { AppLocalization(AppLanguage.ENGLISH) { MaterialTheme {
            MarketScreen(state.value, { retried++ }, {}, {}, { opened++ })
        } } }
        compose.onNodeWithTag("market_empty").assertExists()
        compose.onAllNodesWithTag("market_refresh").assertCountEquals(0)
        compose.onAllNodesWithTag("market_help_content").assertCountEquals(0)
        compose.onNodeWithTag("market_help").performClick(); compose.onNodeWithTag("market_help_content").assertExists()
        compose.onNodeWithTag("market_help_close").performClick()
        compose.onNodeWithTag("market_portfolio").performClick(); compose.runOnIdle { assertEquals(1, opened) }
        compose.runOnIdle { state.value = MarketUiState(loading = false, storageError = true) }
        compose.onNodeWithTag("market_storage_error").assertExists(); compose.onAllNodesWithTag("market_empty").assertCountEquals(0)
        compose.onNodeWithText("Try again").performClick(); compose.runOnIdle { assertEquals(1, retried) }
    }
}
