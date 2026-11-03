package com.tegar.finance

import androidx.lifecycle.ViewModelStore
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.presentation.*
import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModelTest {
    private val now = Instant.parse("2026-11-03T01:00:00Z")
    private val clock = MutableClock(now)
    private val store = ViewModelStore()
    private val entries = Entries()
    private val quotes = Quotes()
    private val keys = TestProviderKeys()
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun entry(symbol: String) = WatchlistEntry(symbol, InstrumentKey("twelvedata", "XNAS", symbol), symbol, InstrumentKind.STOCK, now)
    private fun model() = MarketViewModel(quotes, clock, QuoteCachePolicy(), entries, keys).also { store.put("market", it) }
    @Test fun unpricedWatchlistIsVisibleWithoutInventingAQuoteOrSendingRequests() = runTest {
        entries.emit(listOf(entry("AAPL")))
        val model = model(); advanceUntilIdle()
        assertTrue(model.state.value.managed); assertEquals("AAPL", model.state.value.rows.single().entry!!.name)
        assertNull(model.state.value.rows.single().record.quote); assertFalse(model.state.value.canRefresh)
        model.refresh(); advanceUntilIdle(); assertTrue(quotes.calls.isEmpty())
    }
    @Test fun savedKeyEnablesExplicitRefreshWithoutAutomaticallyConnectingOrFetching() = runTest {
        entries.emit(listOf(entry("AAPL"))); val model = model(); advanceUntilIdle()
        model.openConnection(); model.editKey("synthetic_key_1234"); model.saveKey(); advanceUntilIdle()
        assertTrue(model.state.value.hasKey); assertTrue(model.state.value.canRefresh)
        assertNull(model.state.value.keyEditor); assertTrue(quotes.calls.isEmpty())
        model.openConnection(); assertEquals("", model.state.value.keyEditor!!.value)
    }
    @Test fun failedKeySaveKeepsTheMaskedDraftAndNeverClaimsAConnection() = runTest {
        val model = model(); advanceUntilIdle(); keys.fail = true
        model.openConnection(); model.editKey("synthetic_key_1234"); model.saveKey(); advanceUntilIdle()
        assertEquals(AppError.STORAGE, model.state.value.formError); assertNotNull(model.state.value.keyEditor)
        assertFalse(model.state.value.hasKey); assertFalse(model.state.value.toString().contains("synthetic_key_1234"))
        model.closeEditor(); assertNull(model.state.value.keyEditor)
    }
    @Test fun duplicateWriteKeepsTheEntryDraftAndSuccessfulRetryClosesIt() = runTest {
        val model = model(); advanceUntilIdle(); entries.error = AppError.DUPLICATE_INSTRUMENT
        model.openEntry(); model.editEntry(WatchlistDraft("Apple", "AAPL", "XNAS")); model.saveEntry(); advanceUntilIdle()
        assertEquals(AppError.DUPLICATE_INSTRUMENT, model.state.value.formError)
        assertEquals("Apple", model.state.value.editor!!.draft.name)
        entries.error = null; model.saveEntry(); advanceUntilIdle(); assertNull(model.state.value.editor)
    }
    @Test fun onlyWatchedIdentitiesAreDisplayedAndChangingAnIdentityCannotReuseAnotherPrice() = runTest {
        val old = entry("OLD"); val new = entry("NEW")
        quotes.data.value = AppResult.Success(listOf(QuoteRecord(old.key,
            MarketQuote(old.key, BigDecimal.ONE, CurrencyCode("USD"), now, now, "Test"), now)))
        entries.emit(listOf(new)); val model = model(); advanceUntilIdle()
        assertEquals("NEW", model.state.value.rows.single().record.key.symbol)
        assertNull(model.state.value.rows.single().record.quote)
        entries.emit(emptyList()); advanceUntilIdle(); assertTrue(model.state.value.rows.isEmpty())
        assertEquals(1, (quotes.data.value as AppResult.Success).value.size)
    }
    @Test fun explicitBatchIsBoundedPrioritizesUnpricedRowsAndGuardsRepeatedClicks() = runTest {
        entries.emit((1..8).map { entry("S$it") }); keys.save("synthetic_key_1234")
        val model = model(); advanceUntilIdle(); model.refresh(); runCurrent(); model.refresh(); advanceUntilIdle()
        assertEquals(6, quotes.calls.size); assertEquals(6, quotes.calls.distinct().size)
        model.refresh(); advanceUntilIdle()
        assertEquals(listOf("S7", "S8"), quotes.calls.drop(6).take(2).map { it.symbol })
    }
    @Test fun authenticationFailureStopsTheRestOfTheBatchAndKeepsTheKeyForCorrection() = runTest {
        entries.emit(listOf(entry("AAPL"), entry("MSFT"))); keys.save("synthetic_key_1234")
        quotes.failure = QuoteFailure.AUTHENTICATION
        val model = model(); advanceUntilIdle(); model.refresh(); advanceUntilIdle()
        assertEquals(1, quotes.calls.size); assertTrue(model.state.value.hasKey); assertFalse(model.state.value.refreshing)
    }
    @Test fun removingTheKeyKeepsWatchlistAndCachedDataButDisablesRefresh() = runTest {
        entries.emit(listOf(entry("AAPL"))); keys.save("synthetic_key_1234")
        val model = model(); advanceUntilIdle(); model.askDisconnect(); model.disconnect(); advanceUntilIdle()
        assertFalse(model.state.value.hasKey); assertFalse(model.state.value.canRefresh)
        assertEquals(1, model.state.value.rows.size); assertTrue(quotes.calls.isEmpty())
    }
    private class Entries : WatchlistRepository {
        val data = MutableStateFlow<AppResult<List<WatchlistEntry>>>(AppResult.Success(emptyList()))
        var error: AppError? = null
        fun emit(rows: List<WatchlistEntry>) { data.value = AppResult.Success(rows) }
        override fun observe() = data
        override suspend fun save(id: String?, draft: WatchlistDraft): AppResult<Unit> = error?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
        override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
    }
    private inner class Quotes : QuoteRepository {
        override val providers = listOf(ProviderInfo("twelvedata", "Twelve Data"))
        val data = MutableStateFlow<AppResult<List<QuoteRecord>>>(AppResult.Success(emptyList()))
        val calls = mutableListOf<InstrumentKey>(); var failure: QuoteFailure? = null
        override fun observeQuotes() = data
        override suspend fun refresh(key: InstrumentKey): AppResult<QuoteRecord> {
            calls += key; delay(100)
            val record = QuoteRecord(key, if (failure == null) MarketQuote(key, BigDecimal.ONE, CurrencyCode("USD"), now, now, "Test") else null, now, failure)
            data.value = AppResult.Success((data.value as AppResult.Success).value.filter { it.key != key } + record)
            return AppResult.Success(record)
        }
    }
}
