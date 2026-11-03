package com.tegar.finance.domain.market

import com.tegar.finance.domain.model.CurrencyCode
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/** Provider identity is part of the key: equal symbols need not be equal instruments. */
data class InstrumentKey(val provider: String, val market: String, val symbol: String) {
    init {
        require(provider.matches(Regex("[a-z][a-z0-9_-]{0,39}")))
        require(market.matches(Regex("[A-Z0-9][A-Z0-9._-]{0,19}")))
        require(symbol.matches(Regex("[A-Za-z0-9^][A-Za-z0-9.^:_/-]{0,63}")))
    }
}

data class ProviderInfo(val id: String, val name: String) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_-]{0,39}")))
        require(name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl))
    }
}

/** Quotes retain their own currency and precision; they are never ledger money or an FX rate. */
data class MarketQuote(val key: InstrumentKey, val price: BigDecimal, val currency: CurrencyCode,
    val observedAt: Instant, val fetchedAt: Instant, val source: String) {
    init {
        require(price.signum() >= 0 && price.scale() in 0..18 && price.precision() <= 38)
        require(observedAt >= Instant.EPOCH && fetchedAt >= Instant.EPOCH)
        require(source.isNotBlank() && source.length <= 80 && source.none(Char::isISOControl))
    }
}

enum class QuoteFailure { OFFLINE, TIMEOUT, AUTHENTICATION, RATE_LIMIT, PROVIDER, INVALID_RESPONSE,
    NO_DATA, UNSUPPORTED, NOT_CONFIGURED }
enum class QuoteFreshness { MISSING, FRESH, STALE }

data class QuoteRecord(val key: InstrumentKey, val quote: MarketQuote? = null,
    val lastAttemptAt: Instant, val failure: QuoteFailure? = null, val retryAt: Instant? = null) {
    init {
        require(quote == null || quote.key == key)
        require(lastAttemptAt >= Instant.EPOCH)
        require(retryAt == null || failure == QuoteFailure.RATE_LIMIT)
    }
    val lastSuccessAt: Instant? get() = quote?.fetchedAt
}

data class QuoteCachePolicy(val maxAge: Duration = Duration.ofMinutes(15)) {
    init { require(!maxAge.isNegative && !maxAge.isZero && maxAge <= Duration.ofDays(7)) }
    fun freshness(quote: MarketQuote?, now: Instant): QuoteFreshness {
        if (quote == null) return QuoteFreshness.MISSING
        // Refetching an old observation does not turn it into a current price.
        val sourceAge = Duration.between(quote.observedAt, now)
        val fetchAge = Duration.between(quote.fetchedAt, now)
        return if (sourceAge.isNegative || fetchAge.isNegative || sourceAge >= maxAge || fetchAge >= maxAge)
            QuoteFreshness.STALE else QuoteFreshness.FRESH
    }
}

/** An adapter maps its documented response to this payload, without exposing credentials/errors. */
data class QuotePayload(val key: InstrumentKey, val decimalPrice: String, val currency: String,
    val observedAt: Instant)
sealed interface QuoteFetchResult {
    data class Success(val payload: QuotePayload) : QuoteFetchResult
    data object Empty : QuoteFetchResult
    data class Failure(val reason: QuoteFailure, val retryAt: Instant? = null) : QuoteFetchResult
}
interface QuoteProvider {
    val info: ProviderInfo
    suspend fun fetch(key: InstrumentKey): QuoteFetchResult
}
class QuoteProviders(providers: List<QuoteProvider> = emptyList()) {
    private val entries = providers.associateBy { it.info.id }
    init { require(entries.size == providers.size) }
    val available: List<ProviderInfo> get() = entries.values.map { it.info }.sortedBy { it.id }
    fun find(id: String): QuoteProvider? = entries[id]
}
