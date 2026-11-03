package com.tegar.finance

import androidx.lifecycle.ViewModelStore
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.presentation.*
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MarketViewModelTest {
    private val store = ViewModelStore()
    private val now = Instant.parse("2026-11-03T01:00:00Z")
    private val clock = MutableClock(now)
    private val key = InstrumentKey("test", "XNAS", "ABC")
    private val quote = MarketQuote(key, BigDecimal("1.123456789012345678"), CurrencyCode("USD"), now, now, "Test")
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun model(source: Source) = MarketViewModel(source, clock, QuoteCachePolicy()).also { store.put("market", it) }
    private class Source : QuoteRepository {
        override var providers: List<ProviderInfo> = emptyList()
        val data = MutableStateFlow<AppResult<List<QuoteRecord>>>(AppResult.Success(emptyList()))
        var calls = 0; var failWrite = false
        override fun observeQuotes() = data
        override suspend fun refresh(key: InstrumentKey): AppResult<QuoteRecord> {
            calls++; delay(100)
            return if (failWrite) AppResult.Failure(AppError.STORAGE) else
                AppResult.Success((data.value as AppResult.Success).value.first { it.key == key })
        }
    }
    @Test fun unconfiguredEmptyScreenNeverOffersOrStartsANetworkRefresh() = runTest {
        val source = Source(); val model = model(source); advanceUntilIdle()
        assertFalse(model.state.value.loading); assertFalse(model.state.value.canRefresh)
        assertTrue(model.state.value.rows.isEmpty()); model.refresh(); advanceUntilIdle(); assertEquals(0, source.calls)
    }
    @Test fun recencyIsRecomputedAtBoundaryAndAfterClockRollbackWithoutRefetching() = runTest {
        val source = Source(); source.data.value = AppResult.Success(listOf(QuoteRecord(key, quote, now)))
        val model = model(source); advanceUntilIdle()
        assertEquals(QuoteFreshness.FRESH, model.state.value.rows.single().freshness)
        clock.time = now.plusSeconds(900); model.recheckAge(); advanceUntilIdle()
        assertEquals(QuoteFreshness.STALE, model.state.value.rows.single().freshness)
        clock.time = now.minusSeconds(1); model.recheckAge(); advanceUntilIdle()
        assertEquals(QuoteFreshness.STALE, model.state.value.rows.single().freshness); assertEquals(0, source.calls)
    }
    @Test fun readFailureClearsDisplayedPricesAndRetryCanRecover() = runTest {
        val source = Source(); source.data.value = AppResult.Success(listOf(QuoteRecord(key, quote, now)))
        val model = model(source); advanceUntilIdle()
        source.data.value = AppResult.Failure(AppError.STORAGE); advanceUntilIdle()
        assertTrue(model.state.value.storageError); assertTrue(model.state.value.rows.isEmpty())
        source.data.value = AppResult.Success(emptyList()); model.retry(); advanceUntilIdle()
        assertFalse(model.state.value.storageError); assertFalse(model.state.value.loading)
    }
    @Test fun supportedRowsRefreshOnceWhileBusyAndCooldownRowsStayUntouched() = runTest {
        val source = Source(); source.providers = listOf(ProviderInfo("test", "Test"))
        source.data.value = AppResult.Success(listOf(QuoteRecord(key, quote, now),
            QuoteRecord(key.copy(symbol = "OTHER"), lastAttemptAt = now, failure = QuoteFailure.RATE_LIMIT, retryAt = now.plusSeconds(60))))
        val model = model(source); advanceUntilIdle()
        model.refresh(); runCurrent(); assertTrue(model.state.value.refreshing)
        model.refresh(); advanceUntilIdle(); assertEquals(1, source.calls); assertFalse(model.state.value.refreshing)
    }
    @Test fun cooldownExpiresWithoutAutomaticNetworkRequests() = runTest {
        val source = Source(); source.providers = listOf(ProviderInfo("test", "Test"))
        source.data.value = AppResult.Success(listOf(QuoteRecord(key, quote, now, QuoteFailure.RATE_LIMIT, now.plusSeconds(60))))
        val model = model(source); advanceUntilIdle(); assertFalse(model.state.value.canRefresh)
        clock.time = now.plusSeconds(60); model.recheckAge(); advanceUntilIdle()
        assertTrue(model.state.value.canRefresh); assertEquals(0, source.calls)
    }
    @Test fun writeFailureRemainsVisibleThroughAgeChecksUntilExplicitRetry() = runTest {
        val source = Source(); source.providers = listOf(ProviderInfo("test", "Test")); source.failWrite = true
        source.data.value = AppResult.Success(listOf(QuoteRecord(key, quote, now)))
        val model = model(source); advanceUntilIdle(); model.refresh(); advanceUntilIdle()
        model.recheckAge(); advanceUntilIdle()
        assertTrue(model.state.value.storageError); assertTrue(model.state.value.rows.isEmpty())
        assertFalse(model.state.value.canRefresh); model.retry(); advanceUntilIdle()
        assertFalse(model.state.value.storageError); assertEquals(quote, model.state.value.rows.single().record.quote)
    }
}
