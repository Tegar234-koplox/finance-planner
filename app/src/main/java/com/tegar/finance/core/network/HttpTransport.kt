package com.tegar.finance.core.network

import com.tegar.finance.domain.market.QuoteFailure
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

data class HttpRequest(val uri: URI, val headers: Map<String, String> = emptyMap()) {
    init {
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null)
        require(uri.port == -1 || uri.port == 443)
        require(uri.toASCIIString().length <= 4096)
        require(headers.size <= 16 && headers.all { (key, value) ->
            key.matches(Regex("[A-Za-z0-9-]{1,80}")) && key.lowercase() !in setOf("host", "connection", "content-length") &&
                value.length <= 4096 && value.none { it == '\r' || it == '\n' || it == '\u0000' }
        })
    }
}
sealed interface HttpResult {
    data class Success(val status: Int, val body: String) : HttpResult
    /** No URL, headers, provider body or exception text can escape through failures. */
    data class Failure(val reason: QuoteFailure, val retryAt: Instant? = null) : HttpResult
}
fun interface HttpTransport { suspend fun get(request: HttpRequest): HttpResult }

/** One bounded HTTPS GET. Platform certificate/hostname checks stay enabled. No redirect or retry. */
class UrlConnectionTransport(private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val maxBytes: Int = 1_048_576) : HttpTransport {
    init { require(maxBytes in 1..1_048_576) }
    override suspend fun get(request: HttpRequest): HttpResult = withContext(dispatcher) {
        var connection: HttpURLConnection? = null
        try {
            ensureActive()
            val client = connect(request.uri.toURL()).also { connection = it }
            client.requestMethod = "GET"
            client.connectTimeout = 10_000
            client.readTimeout = 10_000
            client.instanceFollowRedirects = false
            client.useCaches = false
            client.setRequestProperty("Accept", "application/json")
            client.setRequestProperty("Accept-Encoding", "identity")
            request.headers.forEach { (key, value) -> client.setRequestProperty(key, value) }
            val status = client.responseCode
            ensureActive()
            if (status !in 200..299) return@withContext HttpResult.Failure(when (status) {
                401, 403 -> QuoteFailure.AUTHENTICATION
                404 -> QuoteFailure.UNSUPPORTED
                429 -> QuoteFailure.RATE_LIMIT
                else -> QuoteFailure.PROVIDER
            }, if (status == 429) retryAt(client.getHeaderField("Retry-After")) else null)
            if (client.contentLengthLong > maxBytes) return@withContext HttpResult.Failure(QuoteFailure.INVALID_RESPONSE)
            val bytes = ByteArrayOutputStream()
            // Socket timeouts alone do not bound a server that continuously dribbles data.
            val deadline = System.nanoTime() + 20_000_000_000L
            client.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    if (System.nanoTime() > deadline) return@withContext HttpResult.Failure(QuoteFailure.TIMEOUT)
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (bytes.size() + read > maxBytes) return@withContext HttpResult.Failure(QuoteFailure.INVALID_RESPONSE)
                    bytes.write(buffer, 0, read)
                }
            }
            ensureActive()
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            HttpResult.Success(status, decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            HttpResult.Failure(when (error) {
                is SocketTimeoutException -> QuoteFailure.TIMEOUT
                is UnknownHostException, is ConnectException -> QuoteFailure.OFFLINE
                is java.nio.charset.CharacterCodingException -> QuoteFailure.INVALID_RESPONSE
                is IOException -> QuoteFailure.PROVIDER
                else -> QuoteFailure.PROVIDER
            })
        } finally { connection?.disconnect() }
    }

    private fun retryAt(raw: String?): Instant? {
        if (raw == null) return null
        val now = clock.instant()
        val value = raw.trim().toLongOrNull()?.takeIf { it in 0..86400 }?.let { now.plusSeconds(it) }
            ?: try { ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
                catch (_: java.time.DateTimeException) { null }
        return value?.takeIf { it > now }?.coerceAtMost(now.plusSeconds(86400))
    }
}
