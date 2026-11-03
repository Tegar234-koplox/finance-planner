package com.tegar.finance

import com.tegar.finance.core.network.*
import com.tegar.finance.domain.market.QuoteFailure
import java.io.*
import java.net.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpTransportTest {
    private val now = Instant.parse("2026-11-03T01:00:00Z")
    private val request = HttpRequest(URI("https://feed.example/quote?symbol=ABC"))
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private class Client(var status: Int = 200, val bytes: ByteArray = "{\"price\":\"1.12345678\"}".toByteArray()) :
        HttpURLConnection(URL("https://feed.example/quote")) {
        var closed = false; var streamClosed = false; var retry: String? = null; var declared = -1L
        override fun getResponseCode() = status
        override fun getContentLengthLong() = declared
        override fun getHeaderField(name: String) = if (name == "Retry-After") retry else null
        override fun getInputStream(): InputStream = object : ByteArrayInputStream(bytes) {
            override fun close() { streamClosed = true; super.close() }
        }
        override fun connect() { }
        override fun usingProxy() = false
        override fun disconnect() { closed = true }
    }
    private suspend fun get(client: Client, max: Int = 1_048_576) =
        UrlConnectionTransport(clock, UnconfinedTestDispatcher(), { client }, max).get(request)

    @Test fun transportIsBoundedGetAndReleasesTheConnectionAndBody() = runTest {
        val client = Client()
        assertEquals(HttpResult.Success(200, "{\"price\":\"1.12345678\"}"), get(client))
        assertEquals("GET", client.requestMethod); assertEquals(10_000, client.connectTimeout)
        assertEquals(10_000, client.readTimeout); assertFalse(client.instanceFollowRedirects)
        assertFalse(client.useCaches); assertTrue(client.closed); assertTrue(client.streamClosed)
    }
    @Test fun redirectsAndHttpFailuresAreNeverFollowedOrReturnedAsProviderBodies() = runTest {
        for ((status, reason) in mapOf(301 to QuoteFailure.PROVIDER, 302 to QuoteFailure.PROVIDER,
            401 to QuoteFailure.AUTHENTICATION, 403 to QuoteFailure.AUTHENTICATION,
            404 to QuoteFailure.UNSUPPORTED, 429 to QuoteFailure.RATE_LIMIT, 500 to QuoteFailure.PROVIDER)) {
            val client = Client(status, "secret diagnostic".toByteArray())
            assertEquals(HttpResult.Failure(reason), get(client)); assertTrue(client.closed)
        }
    }
    @Test fun bothDeclaredAndStreamingBodyLimitsAreEnforced() = runTest {
        val declared = Client().apply { this.declared = 101 }
        assertEquals(HttpResult.Failure(QuoteFailure.INVALID_RESPONSE), get(declared, 100))
        val streamed = Client(bytes = ByteArray(101))
        assertEquals(HttpResult.Failure(QuoteFailure.INVALID_RESPONSE), get(streamed, 100))
        assertTrue(streamed.streamClosed); assertTrue(streamed.closed)
    }
    @Test fun emptySuccessfulResponseIsAvailableToTheProviderAsEmptyAndNotAFakePrice() = runTest {
        assertEquals(HttpResult.Success(204, ""), get(Client(204, byteArrayOf())))
    }
    @Test fun invalidUtf8FailsInsteadOfReplacingCharactersInAPrice() = runTest {
        assertEquals(HttpResult.Failure(QuoteFailure.INVALID_RESPONSE), get(Client(bytes = byteArrayOf(0xC3.toByte(), 0x28))))
    }
    @Test fun retryAfterSupportsSecondsAndHttpDatesWithoutRetrying() = runTest {
        val seconds = Client(429).apply { retry = "120" }
        assertEquals(HttpResult.Failure(QuoteFailure.RATE_LIMIT, now.plusSeconds(120)), get(seconds))
        val date = Client(429).apply { retry = "Tue, 3 Nov 2026 01:05:00 GMT" }
        assertEquals(HttpResult.Failure(QuoteFailure.RATE_LIMIT, now.plusSeconds(300)), get(date))
    }
    @Test fun malformedOrExpiredRetryAfterDoesNotInventACooldown() = runTest {
        for (value in listOf("garbage", "-1", "9999999999999999999999", "Tue, 3 Nov 2026 00:00:00 GMT"))
            assertEquals(HttpResult.Failure(QuoteFailure.RATE_LIMIT), get(Client(429).apply { retry = value }))
    }
    @Test fun networkExceptionsAreSanitizedAndClassified() = runTest {
        for ((error, reason) in listOf(UnknownHostException("secret") to QuoteFailure.OFFLINE,
            ConnectException("secret") to QuoteFailure.OFFLINE, SocketTimeoutException("secret") to QuoteFailure.TIMEOUT,
            javax.net.ssl.SSLHandshakeException("secret") to QuoteFailure.PROVIDER)) {
            val result = UrlConnectionTransport(clock, UnconfinedTestDispatcher(), { throw error }).get(request)
            assertEquals(HttpResult.Failure(reason), result); assertFalse(result.toString().contains("secret"))
        }
    }
    @Test fun coroutineCancellationIsNotReportedAsAnOutage() = runTest {
        try {
            UrlConnectionTransport(clock, UnconfinedTestDispatcher(), { throw CancellationException("cancel") }).get(request)
            fail("cancellation swallowed")
        } catch (_: CancellationException) { }
    }
    @Test fun insecureEndpointsUserInfoFragmentsAndHeaderInjectionAreRejected() {
        for (uri in listOf("http://feed.example/quote", "file:///tmp/file", "https://name:secret@feed.example/",
            "https://feed.example:444/", "https://feed.example/quote#token"))
            assertThrows(uri, IllegalArgumentException::class.java) { HttpRequest(URI(uri)) }
        assertThrows(IllegalArgumentException::class.java) { HttpRequest(request.uri, mapOf("X-Key" to "secret\r\nHost: evil")) }
        assertThrows(IllegalArgumentException::class.java) { HttpRequest(request.uri, mapOf("Host" to "evil")) }
    }
    @Test fun adapterHeadersAreAppliedOnlyToTheRequestedOrigin() = runTest {
        val client = Client(302)
        UrlConnectionTransport(clock, UnconfinedTestDispatcher(), { client }).get(request.copy(headers = mapOf("X-Key" to "test-key")))
        assertEquals("test-key", client.getRequestProperty("X-Key")); assertFalse(client.instanceFollowRedirects)
    }
}
