package com.tegar.finance

import com.tegar.finance.core.network.*
import com.tegar.finance.data.market.*
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.presentation.KeyEditor
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TwelveDataProviderTest {
    private val key = InstrumentKey("twelvedata", "XNAS", "AAPL")
    private val now = Instant.parse("2026-11-03T01:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    // Synthetic protocol fixtures: these are not evidence of live market coverage.
    private val body = """{"symbol":"AAPL","mic_code":"XNAS","exchange":"NASDAQ","currency":"USD","timestamp":1700000000,"close":"1.123456789012345678"}"""
    private val keys = TestProviderKeys()
    private val gate = TestRequestGate()
    private val requests = mutableListOf<HttpRequest>()
    private var response: HttpResult = HttpResult.Success(200, body)
    private fun provider() = TwelveDataProvider(HttpTransport { request -> requests += request; response }, keys, gate, clock)

    @Test fun absentKeyDoesNotReadTheNetworkOrConsumeTheRequestBudget() = runTest {
        assertEquals(QuoteFetchResult.Failure(QuoteFailure.NOT_CONFIGURED), provider().fetch(key))
        assertEquals(0, gate.reservations); assertTrue(requests.isEmpty())
    }
    @Test fun savedKeyBuildsOnlyTheOfficialHttpsEndpointWithEncodedInputsAndNoFinancialRecords() = runTest {
        keys.save("synthetic_key_1234")
        val result = provider().fetch(key) as QuoteFetchResult.Success
        assertEquals("1.123456789012345678", result.payload.decimalPrice)
        assertEquals(Instant.ofEpochSecond(1700000000), result.payload.observedAt)
        val uri = requests.single().uri
        assertEquals("https", uri.scheme); assertEquals("api.twelvedata.com", uri.host); assertEquals("/quote", uri.path)
        assertTrue(uri.rawQuery.contains("mic_code=XNAS")); assertTrue(uri.rawQuery.contains("country=United+States"))
        assertFalse(requests.single().toString().contains("synthetic_key_1234"))
        assertFalse(requests.single().toString().contains("apikey=")); assertEquals(1, gate.reservations)
    }
    @Test fun wrongProviderOrUnsupportedVenueNeverSendsTheKey() = runTest {
        keys.save("synthetic_key_1234")
        for (value in listOf(key.copy(provider = "other"), key.copy(market = "IDX")))
            assertEquals(QuoteFetchResult.Failure(QuoteFailure.UNSUPPORTED), provider().fetch(value))
        assertTrue(requests.isEmpty()); assertEquals(0, gate.reservations)
    }
    @Test fun indexRequestsAreExplicitUsIndicesInsteadOfEtfProxies() = runTest {
        keys.save("synthetic_key_1234")
        val index = key.copy(market = "US_INDEX", symbol = "SPX")
        response = HttpResult.Success(200, body.replace("AAPL", "SPX").replace("\"mic_code\":\"XNAS\",", "")
            .dropLast(1) + ",\"type\":\"Index\",\"country\":\"United States\"}")
        val source = TwelveDataProvider(HttpTransport { request ->
            requests += request
            if (request.uri.path == "/indices") HttpResult.Success(200, """{"data":[{"symbol":"SPX","country":"United States","exchange":"NASDAQ","type":"Index"}],"status":"ok"}""") else response
        }, keys, gate, clock)
        assertEquals(index, (source.fetch(index) as QuoteFetchResult.Success).payload.key)
        assertTrue(requests.last().uri.rawQuery.contains("type=Index"))
        assertFalse(requests.last().uri.rawQuery.contains("mic_code=")); assertEquals(2, gate.reservations)
    }
    @Test fun jsonErrorEnvelopesAreTypedEvenWithHttp200AndDoNotExposeMessageText() {
        for ((code, reason) in mapOf(400 to QuoteFailure.UNSUPPORTED, 401 to QuoteFailure.AUTHENTICATION,
            403 to QuoteFailure.AUTHENTICATION, 429 to QuoteFailure.RATE_LIMIT, 500 to QuoteFailure.PROVIDER)) {
            val result = TwelveDataJson.decode("""{"status":"error","code":$code,"message":"secret token"}""", key)
            assertEquals(QuoteFetchResult.Failure(reason), result); assertFalse(result.toString().contains("secret"))
        }
    }
    @Test fun priceNumberTokensRemainExactRatherThanPassingThroughDouble() {
        val result = TwelveDataJson.decode(body.replace("\"1.123456789012345678\"", "1.123456789012345678"), key)
        assertEquals("1.123456789012345678", (result as QuoteFetchResult.Success).payload.decimalPrice)
    }
    @Test fun duplicateTrailingMalformedOrWrongIdentityResponsesAreRejected() {
        val invalid = listOf(body.dropLast(1) + ",\"close\":\"2\"}", body + "{}", "[]", "{close:1}",
            body.replace("AAPL", "MSFT"), body.replace("XNAS", "XNYS"), body.replace("USD", "EUR"),
            body.replace("1.123456789012345678", "-1"), body.replace("1.123456789012345678", "1e4"),
            body.replace("1700000000", "1.5"), body.replace("1700000000", "-1"),
            body.dropLast(1) + ",\"country\":\"Germany\"}", body.dropLast(1) + ",\"type\":\"Index\"}")
        for (value in invalid) assertEquals(value, QuoteFetchResult.Failure(QuoteFailure.INVALID_RESPONSE), TwelveDataJson.decode(value, key))
    }
    @Test fun emptyAndZeroTimestampAreUnavailableButAZeroPriceWithSourceTimeIsKnown() {
        for (value in listOf("", "{}", body.replace("1700000000", "0")))
            assertEquals(QuoteFetchResult.Empty, TwelveDataJson.decode(value, key))
        val result = TwelveDataJson.decode(body.replace("1.123456789012345678", "0"), key)
        assertEquals("0", (result as QuoteFetchResult.Success).payload.decimalPrice)
    }
    @Test fun localBudgetRejectsWithoutTransportOrHiddenWait() = runTest {
        keys.save("synthetic_key_1234"); gate.next = now.plusSeconds(30)
        assertEquals(QuoteFetchResult.Failure(QuoteFailure.RATE_LIMIT, gate.next), provider().fetch(key))
        assertTrue(requests.isEmpty())
    }
    @Test fun providerRateLimitsBlockTheSharedBudgetAndUseABoundedFallback() = runTest {
        keys.save("synthetic_key_1234"); response = HttpResult.Failure(QuoteFailure.RATE_LIMIT)
        assertEquals(QuoteFetchResult.Failure(QuoteFailure.RATE_LIMIT, now.plusSeconds(60)), provider().fetch(key))
        assertEquals(now.plusSeconds(60), gate.blocked); assertEquals(1, requests.size)
        assertEquals(QuoteFetchResult.Failure(QuoteFailure.RATE_LIMIT, gate.blocked), provider().fetch(key))
        assertEquals(1, requests.size)
    }
    @Test fun keyReadErrorsFailClosedAndDoNotConsumeRequests() = runTest {
        keys.fail = true
        assertEquals(QuoteFetchResult.Failure(QuoteFailure.AUTHENTICATION), provider().fetch(key))
        assertTrue(requests.isEmpty()); assertEquals(0, gate.reservations)
    }
    @Test fun transportFailuresPropagateWithoutLosingTheirClassification() = runTest {
        keys.save("synthetic_key_1234")
        for (reason in listOf(QuoteFailure.OFFLINE, QuoteFailure.TIMEOUT, QuoteFailure.AUTHENTICATION, QuoteFailure.PROVIDER)) {
            response = HttpResult.Failure(reason)
            assertEquals(QuoteFetchResult.Failure(reason), provider().fetch(key))
        }
    }
    @Test fun cancellationDoesNotBecomeAProviderFailure() = runTest {
        keys.save("synthetic_key_1234")
        val source = TwelveDataProvider(HttpTransport { throw CancellationException("cancelled") }, keys, gate, clock)
        try { source.fetch(key); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }
    @Test fun keyDraftsAndRequestsCannotAccidentallyPrintCredentials() {
        val secret = "synthetic_key_1234"
        assertFalse(KeyEditor(secret).toString().contains(secret))
        assertFalse(HttpRequest(java.net.URI("https://api.twelvedata.com/quote?apikey=$secret"), mapOf("X-Key" to secret)).toString().contains(secret))
        assertFalse(ProviderKeyRules.valid("key\nInjected")); assertFalse(ProviderKeyRules.valid("https://site?key=secret"))
    }
    @Test fun watchlistNormalizationPreservesIdentityAndRejectsMisleadingKindVenueCombinations() {
        val draft = WatchlistRules.normalize(WatchlistDraft(" Apple ", "aapl", "XNAS"))
        assertEquals("Apple", draft.name); assertEquals("AAPL", draft.symbol)
        for (bad in listOf(WatchlistDraft("", "AAPL", "XNAS"), WatchlistDraft("Bad\nname", "AAPL", "XNAS"),
            WatchlistDraft("Bad", "AAPL?secret", "XNAS"), WatchlistDraft("Index", "SPX", "XNAS", InstrumentKind.INDEX),
            WatchlistDraft("Stock", "AAPL", "US_INDEX")))
            assertThrows(IllegalArgumentException::class.java) { WatchlistRules.normalize(bad) }
    }
    @Test fun unverifiedOrWrongExchangeQuotesCannotBeRenderedAsIndexPoints() {
        val index = key.copy(market = "US_INDEX")
        assertEquals(QuoteFetchResult.Failure(QuoteFailure.INVALID_RESPONSE), TwelveDataJson.decode(body, index))
        assertEquals(QuoteFetchResult.Failure(QuoteFailure.INVALID_RESPONSE), TwelveDataJson.decode(body, index, "OTHER"))
    }
    @Test fun indexDirectoryMustIdentifyOneExactUsIndexAndCannotSubstituteAnEtf() {
        val valid = """{"data":[{"symbol":"SPX","country":"United States","exchange":"Index Source","type":"Index"}],"status":"ok"}"""
        assertEquals(IndexReference.Found("Index Source"), TwelveDataIndexJson.decode(valid, "SPX"))
        for (bad in listOf(valid.replace("Index\"", "ETF\""), valid.replace("United States", "Germany"), valid.replace("SPX", "SPY"), """{"data":[],"status":"ok"}"""))
            assertTrue(TwelveDataIndexJson.decode(bad, "SPX") is IndexReference.Failure)
    }
    @Test fun deniedIndexDirectoryAccessDoesNotTryAQuoteOrFallbackToAnEtf() = runTest {
        keys.save("synthetic_key_1234"); response = HttpResult.Success(200, """{"code":403,"status":"error","message":"upgrade secret"}""")
        val result = provider().fetch(key.copy(market = "US_INDEX", symbol = "SPX"))
        assertEquals(QuoteFetchResult.Failure(QuoteFailure.AUTHENTICATION), result)
        assertEquals("/indices", requests.single().uri.path); assertFalse(result.toString().contains("secret"))
    }
}

internal class TestProviderKeys : ProviderKeyStore {
    var value: String? = null
    var fail = false
    var saves = 0
    val status = MutableStateFlow<AppResult<Boolean>>(AppResult.Success(false))
    override fun observeStatus() = status
    override suspend fun read(): AppResult<String?> = if (fail) AppResult.Failure(AppError.STORAGE) else AppResult.Success(value)
    override suspend fun save(key: String): AppResult<Unit> {
        saves++
        if (fail) return AppResult.Failure(AppError.STORAGE)
        value = key; status.value = AppResult.Success(true); return AppResult.Success(Unit)
    }
    override suspend fun clear(): AppResult<Unit> {
        if (fail) return AppResult.Failure(AppError.STORAGE)
        value = null; status.value = AppResult.Success(false); return AppResult.Success(Unit)
    }
}
internal class TestRequestGate : ProviderRequestGate {
    var next: Instant? = null; var blocked: Instant? = null; var reservations = 0
    override suspend fun reserve(now: Instant): Instant? { reservations++; return next }
    override suspend fun block(until: Instant) { blocked = until; next = until }
}
