package com.tegar.finance.data.market

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.tegar.finance.core.network.*
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.AppResult
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException

class TwelveDataProvider(private val transport: HttpTransport, private val keys: ProviderKeyStore,
    private val gate: ProviderRequestGate, private val clock: Clock) : QuoteProvider {
    override val info = ProviderInfo(WatchlistRules.PROVIDER, "Twelve Data")
    override suspend fun fetch(key: InstrumentKey): QuoteFetchResult {
        if (key.provider != info.id || key.market !in WatchlistRules.STOCK_MARKETS + "US_INDEX")
            return QuoteFetchResult.Failure(QuoteFailure.UNSUPPORTED)
        val saved = keys.read()
        val secret = (saved as? AppResult.Success)?.value
            ?: return QuoteFetchResult.Failure(if (saved is AppResult.Failure) QuoteFailure.AUTHENTICATION else QuoteFailure.NOT_CONFIGURED)
        gate.reserve(clock.instant())?.let { return QuoteFetchResult.Failure(QuoteFailure.RATE_LIMIT, it) }
        var indexExchange: String? = null
        if (key.market == "US_INDEX") {
            val reference = when (val response = transport.get(request("indices", mapOf("symbol" to key.symbol,
                "country" to "United States", "format" to "JSON", "apikey" to secret)))) {
                is HttpResult.Failure -> return finish(QuoteFetchResult.Failure(response.reason, response.retryAt))
                is HttpResult.Success -> TwelveDataIndexJson.decode(response.body, key.symbol)
            }
            when (reference) {
                is IndexReference.Failure -> return finish(QuoteFetchResult.Failure(reference.reason))
                is IndexReference.Found -> indexExchange = reference.exchange
            }
            gate.reserve(clock.instant())?.let { return QuoteFetchResult.Failure(QuoteFailure.RATE_LIMIT, it) }
        }
        val params = linkedMapOf("symbol" to key.symbol, "country" to "United States", "format" to "JSON", "timezone" to "UTC")
        if (key.market == "US_INDEX") { params["type"] = "Index"; params["exchange"] = indexExchange!! } else params["mic_code"] = key.market
        // The official SDK documents query authentication. Requests/errors never log the URI.
        params["apikey"] = secret
        val response = transport.get(request("quote", params))
        val result = when (response) {
            is HttpResult.Failure -> QuoteFetchResult.Failure(response.reason, response.retryAt)
            is HttpResult.Success -> TwelveDataJson.decode(response.body, key, indexExchange)
        }
        return finish(result)
    }
    private fun request(endpoint: String, params: Map<String, String>): HttpRequest {
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return HttpRequest(URI("https://api.twelvedata.com/$endpoint?$query"))
    }
    private suspend fun finish(result: QuoteFetchResult): QuoteFetchResult {
        if (result is QuoteFetchResult.Failure && result.reason == QuoteFailure.RATE_LIMIT) {
            val until = (result.retryAt ?: clock.instant().plusSeconds(60))
                .coerceAtMost(clock.instant().plusSeconds(86400))
            gate.block(until)
            return result.copy(retryAt = until)
        }
        return result
    }
}

/** Streaming JSON keeps price tokens exact and rejects duplicate/trailing/ambiguous data. */
object TwelveDataJson {
    fun decode(body: String, key: InstrumentKey, indexExchange: String? = null): QuoteFetchResult { return try {
        if (body.isBlank()) return QuoteFetchResult.Empty
        require(body.length <= 1_048_576)
        val fields = mutableMapOf<String, String?>()
        val known = setOf("symbol", "mic_code", "exchange", "currency", "timestamp", "close", "type", "country", "status", "code")
        JsonReader(StringReader(body)).use { reader ->
            reader.strictness = Strictness.STRICT
            reader.beginObject()
            val seen = mutableSetOf<String>()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(seen.add(name) && seen.size <= 128)
                if (name !in known) reader.skipValue() else fields[name] = when (reader.peek()) {
                    JsonToken.NULL -> { reader.nextNull(); null }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString().also { require(it.length <= 160) }
                    else -> throw IllegalArgumentException("Invalid scalar")
                }
            }
            reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        if (fields["status"] == "error") return QuoteFetchResult.Failure(when (fields["code"]?.toIntOrNull()) {
            400, 404 -> QuoteFailure.UNSUPPORTED
            401, 403 -> QuoteFailure.AUTHENTICATION
            429 -> QuoteFailure.RATE_LIMIT
            in 500..599 -> QuoteFailure.PROVIDER
            else -> QuoteFailure.INVALID_RESPONSE
        })
        require(fields["status"] == null || fields["status"] == "ok")
        if (fields.isEmpty() || (fields["close"] == null && fields["timestamp"] == null)) return QuoteFetchResult.Empty
        require(fields["symbol"] == key.symbol && fields["currency"] == "USD")
        if (key.market != "US_INDEX") require(fields["mic_code"] == key.market)
        else require(!indexExchange.isNullOrBlank() && fields["exchange"] == indexExchange)
        fields["country"]?.let { require(it == "United States") }
        fields["type"]?.let { type -> require(if (key.market == "US_INDEX") type.equals("Index", true) else !type.equals("Index", true)) }
        val timestamp = fields["timestamp"] ?: throw IllegalArgumentException("Missing source time")
        require(timestamp.matches(Regex("[0-9]{1,12}")))
        if (timestamp.toLong() == 0L) return QuoteFetchResult.Empty
        val price = fields["close"] ?: throw IllegalArgumentException("Missing price")
        require(price.matches(Regex("[0-9]{1,20}(\\.[0-9]{1,18})?")))
        QuoteFetchResult.Success(QuotePayload(key, price, "USD", Instant.ofEpochSecond(timestamp.toLong())))
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        QuoteFetchResult.Failure(QuoteFailure.INVALID_RESPONSE)
    }
    }
}

sealed interface IndexReference {
    data class Found(val exchange: String) : IndexReference
    data class Failure(val reason: QuoteFailure) : IndexReference
}

/** Only the provider's index directory can establish index identity; never infer it from an ETF. */
object TwelveDataIndexJson {
    fun decode(body: String, symbol: String): IndexReference = try {
        require(body.length <= 1_048_576)
        val matches = mutableListOf<String>()
        var status: String? = null; var code: Int? = null
        JsonReader(StringReader(body)).use { reader ->
            reader.strictness = Strictness.STRICT; reader.beginObject()
            val seen = mutableSetOf<String>()
            while (reader.hasNext()) {
                val name = reader.nextName(); require(seen.add(name) && seen.size <= 32)
                when (name) {
                    "status" -> status = reader.nextString()
                    "code" -> code = reader.nextInt()
                    "data" -> {
                        reader.beginArray(); var count = 0
                        while (reader.hasNext()) {
                            require(++count <= 100)
                            val fields = mutableMapOf<String, String?>(); reader.beginObject()
                            while (reader.hasNext()) {
                                val field = reader.nextName(); require(field !in fields && fields.size <= 64)
                                fields[field] = if (field !in setOf("symbol", "country", "exchange", "currency", "type")) { reader.skipValue(); null }
                                else when (reader.peek()) {
                                    JsonToken.NULL -> { reader.nextNull(); null }
                                    JsonToken.STRING -> reader.nextString().also { require(it.length <= 160) }
                                    else -> throw IllegalArgumentException("Invalid reference")
                                }
                            }
                            reader.endObject()
                            if (fields["symbol"] == symbol && fields["country"] == "United States" &&
                                (fields["currency"] == null || fields["currency"] == "USD") &&
                                (fields["type"] == null || fields["type"].equals("Index", true))) {
                                val exchange = fields["exchange"]
                                if (!exchange.isNullOrBlank() && exchange.length <= 80) matches += exchange
                            }
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        when {
            status == "error" -> IndexReference.Failure(when (code) {
                401, 403 -> QuoteFailure.AUTHENTICATION
                429 -> QuoteFailure.RATE_LIMIT
                400, 404 -> QuoteFailure.UNSUPPORTED
                else -> QuoteFailure.PROVIDER
            })
            status != null && status != "ok" -> IndexReference.Failure(QuoteFailure.INVALID_RESPONSE)
            matches.size == 1 -> IndexReference.Found(matches.single())
            else -> IndexReference.Failure(QuoteFailure.UNSUPPORTED)
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        IndexReference.Failure(QuoteFailure.INVALID_RESPONSE)
    }
}
