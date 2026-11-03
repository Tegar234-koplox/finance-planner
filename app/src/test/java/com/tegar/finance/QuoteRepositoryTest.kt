package com.tegar.finance

import com.tegar.finance.data.market.CachedQuoteRepository
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import java.io.IOException
import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuoteRepositoryTest {
    private val key = InstrumentKey("test", "XNAS", "ABC")
    private val now = Instant.parse("2026-11-03T01:00:00Z")
    private val clock = MutableClock(now)
    private val cache = MemoryQuoteCache()
    private val provider = object : QuoteProvider {
        override val info = ProviderInfo("test", "Test feed")
        var result: QuoteFetchResult = QuoteFetchResult.Success(payload())
        var calls = 0
        var work: (suspend () -> Unit)? = null
        override suspend fun fetch(key: InstrumentKey): QuoteFetchResult {
            calls++; work?.invoke(); return result
        }
    }
    private fun repository(registry: QuoteProviders = QuoteProviders(listOf(provider))) =
        CachedQuoteRepository(cache, registry, clock)
    private fun payload(price: String = "123.123456789012345678", currency: String = "USD",
        date: Instant = now, instrument: InstrumentKey = key) = QuotePayload(instrument, price, currency, date)
    private suspend fun read(repo: QuoteRepository = repository()) = (repo.refresh(key) as AppResult.Success).value

    @Test fun exactDecimalCurrencySourceAndObservationAreCachedWithoutLedgerConversion() = runTest {
        val row = read()
        assertEquals(BigDecimal("123.123456789012345678"), row.quote!!.price)
        assertEquals("USD", row.quote.currency.value); assertEquals("Test feed", row.quote.source)
        assertEquals(now, row.quote.observedAt); assertEquals(now, row.lastSuccessAt)
        assertEquals(listOf(row), (repository().observeQuotes().first() as AppResult.Success).value)
    }
    @Test fun emptyProviderResultPreservesTheExactPriorPriceAndSuccessTime() = runTest {
        val old = read().quote; clock.time = now.plusSeconds(60); provider.result = QuoteFetchResult.Empty
        val row = read()
        assertEquals(old, row.quote); assertEquals(QuoteFailure.NO_DATA, row.failure)
        assertEquals(clock.time, row.lastAttemptAt); assertEquals(now, row.lastSuccessAt)
    }
    @Test fun outageReasonsAreDistinctAndNeverEraseCachedData() = runTest {
        val old = read().quote
        for (reason in listOf(QuoteFailure.OFFLINE, QuoteFailure.TIMEOUT, QuoteFailure.AUTHENTICATION,
            QuoteFailure.RATE_LIMIT, QuoteFailure.PROVIDER, QuoteFailure.UNSUPPORTED)) {
            provider.result = QuoteFetchResult.Failure(reason)
            val row = read(); assertEquals(old, row.quote); assertEquals(reason, row.failure)
        }
    }
    @Test fun noProviderMakesNoRequestAndDoesNotInventAnEmptyPrice() = runTest {
        val row = read(repository(QuoteProviders()))
        assertNull(row.quote); assertEquals(QuoteFailure.NOT_CONFIGURED, row.failure); assertEquals(0, provider.calls)
    }
    @Test fun invalidPayloadsDoNotReplacePreviousSuccess() = runTest {
        val original = read().quote
        val invalid = listOf(payload("-1"), payload("NaN"), payload("1e9"), payload(" 1"), payload("1,23"),
            payload("1.1234567890123456789"), payload("123456789012345678901"), payload(currency = "usd"),
            payload(currency = "ZZZ"), payload(date = now.plusMillis(1)), payload(date = Instant.ofEpochMilli(-1)),
            payload(instrument = key.copy(symbol = "OTHER")), payload(instrument = key.copy(provider = "other")))
        for (value in invalid) {
            provider.result = QuoteFetchResult.Success(value)
            val row = read(); assertEquals(value.toString(), original, row.quote)
            assertEquals(value.toString(), QuoteFailure.INVALID_RESPONSE, row.failure)
        }
    }
    @Test fun zeroIsAKnownPriceWhileAnEmptyResponseIsUnknown() = runTest {
        provider.result = QuoteFetchResult.Success(payload("0"))
        assertEquals(BigDecimal.ZERO, read().quote!!.price)
        cache.rows.value = emptyList(); provider.result = QuoteFetchResult.Empty
        assertNull(read().quote)
    }
    @Test fun olderObservationAndUnexpectedCurrencyCannotRegressOrRelabelACache() = runTest {
        val original = read().quote
        provider.result = QuoteFetchResult.Success(payload(date = now.minusSeconds(1)))
        assertEquals(QuoteFailure.INVALID_RESPONSE, read().failure)
        provider.result = QuoteFetchResult.Success(payload(currency = "IDR"))
        assertEquals(QuoteFailure.INVALID_RESPONSE, read().failure); assertEquals(original, cache.read(key)!!.quote)
    }
    @Test fun successfulRetryClearsFailureButCannotMakeAnOldObservationFresh() = runTest {
        read(); provider.result = QuoteFetchResult.Failure(QuoteFailure.OFFLINE); read()
        clock.time = now.plusSeconds(3600); provider.result = QuoteFetchResult.Success(payload())
        val row = read(); assertNull(row.failure); assertEquals(clock.time, row.lastSuccessAt)
        assertEquals(QuoteFreshness.STALE, QuoteCachePolicy().freshness(row.quote, clock.time))
    }
    @Test fun rateLimitCooldownIsPersistentAndDoesNotPerformHiddenRetries() = runTest {
        provider.result = QuoteFetchResult.Failure(QuoteFailure.RATE_LIMIT, now.plusSeconds(120))
        val row = read(); clock.time = now.plusSeconds(60)
        assertEquals(row, read(repository())); assertEquals(1, provider.calls)
        clock.time = now.plusSeconds(120); provider.result = QuoteFetchResult.Success(payload())
        assertNull(read().failure); assertEquals(2, provider.calls)
    }
    @Test fun rateLimitDelayIsBoundedAndUnrelatedErrorsCannotSetACooldown() = runTest {
        provider.result = QuoteFetchResult.Failure(QuoteFailure.RATE_LIMIT, Instant.MAX)
        assertEquals(now.plusSeconds(86400), read().retryAt)
        cache.rows.value = emptyList()
        provider.result = QuoteFetchResult.Failure(QuoteFailure.PROVIDER, now.plusSeconds(100))
        assertNull(read().retryAt)
    }
    @Test fun cancellationPropagatesWithoutReplacingTheQuoteOrLeavingAPersistedBusyFlag() = runTest {
        val old = read().quote
        provider.work = { throw CancellationException("cancelled") }
        try { read(); fail("cancellation swallowed") } catch (_: CancellationException) { }
        assertEquals(old, cache.read(key)!!.quote)
        provider.work = null; assertNull(read().failure)
    }
    @Test fun unexpectedProviderExceptionsBecomeSanitizedFailures() = runTest {
        provider.work = { throw IllegalStateException("secret response/token") }
        val row = read(); assertNull(row.quote); assertEquals(QuoteFailure.PROVIDER, row.failure)
        assertFalse(row.toString().contains("secret"))
    }
    @Test fun cacheStorageFailureIsNotMisreportedAsAProviderOutage() = runTest {
        cache.broken = true
        assertEquals(AppResult.Failure(AppError.STORAGE), repository().refresh(key))
        assertEquals(AppResult.Failure(AppError.STORAGE), repository().observeQuotes().first())
        assertEquals(0, provider.calls)
    }
    @Test fun simultaneousRefreshesSerializeBeforeReadingThePreviousObservation() = runTest {
        var active = 0; var peak = 0
        provider.work = { active++; peak = maxOf(peak, active); delay(100); active-- }
        val repo = repository()
        val results = List(3) { async { repo.refresh(key) } }.awaitAll()
        assertEquals(1, peak); assertEquals(3, provider.calls)
        assertTrue(results.all { it is AppResult.Success })
    }
    @Test fun identicalSymbolsFromDifferentMarketsOrProvidersRemainIndependent() = runTest {
        val q = read().quote!!
        val second = key.copy(market = "XNYS"); val third = key.copy(provider = "other")
        cache.write(QuoteRecord(second, q.copy(key = second), now))
        cache.write(QuoteRecord(third, q.copy(key = third), now))
        assertEquals(3, cache.rows.value.size); assertEquals(q, cache.read(key)!!.quote)
    }
    @Test fun duplicateProvidersAreRejectedBeforeRoutingRequests() {
        assertThrows(IllegalArgumentException::class.java) { QuoteProviders(listOf(provider, provider)) }
    }
    @Test fun freshnessBoundaryAndClockRollbackAreExplicit() {
        val quote = MarketQuote(key, BigDecimal.ZERO, CurrencyCode.IDR, now, now, "Test")
        val policy = QuoteCachePolicy()
        assertEquals(QuoteFreshness.MISSING, policy.freshness(null, now))
        assertEquals(QuoteFreshness.FRESH, policy.freshness(quote, now.plusSeconds(899)))
        assertEquals(QuoteFreshness.STALE, policy.freshness(quote, now.plusSeconds(900)))
        assertEquals(QuoteFreshness.STALE, policy.freshness(quote, now.minusMillis(1)))
    }
    @Test fun freshnessRequiresBothSourceAndRetrievalTimesToBeCurrent() {
        val quote = MarketQuote(key, BigDecimal.ONE, CurrencyCode.IDR, now.minusSeconds(1000), now, "Test")
        assertEquals(QuoteFreshness.STALE, QuoteCachePolicy().freshness(quote, now))
        assertThrows(IllegalArgumentException::class.java) { QuoteCachePolicy(Duration.ZERO) }
    }
}

internal class MutableClock(var time: Instant) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(time, zone)
    override fun instant() = time
}
internal class MemoryQuoteCache : QuoteCache {
    val rows = MutableStateFlow<List<QuoteRecord>>(emptyList())
    var broken = false
    override fun observe() = flow { if (broken) throw IOException("disk"); emitAll(rows) }
    override suspend fun read(key: InstrumentKey): QuoteRecord? {
        if (broken) throw IOException("disk")
        return rows.value.find { it.key == key }
    }
    override suspend fun write(record: QuoteRecord) {
        if (broken) throw IOException("disk")
        rows.value = rows.value.filter { it.key != record.key } + record
    }
}
