package com.tegar.finance.data.market

import com.tegar.finance.data.repository.storageOperation
import com.tegar.finance.data.repository.storageResults
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachedQuoteRepository(private val cache: QuoteCache, private val registry: QuoteProviders,
    private val clock: Clock) : QuoteRepository {
    private val refreshLock = Mutex()
    override val providers get() = registry.available
    override fun observeQuotes() = cache.observe().storageResults()

    override suspend fun refresh(key: InstrumentKey): AppResult<QuoteRecord> = refreshLock.withLock {
        storageOperation {
            val now = Instant.ofEpochMilli(clock.millis())
            val old = cache.read(key)
            if (old?.retryAt?.isAfter(now) == true) return@storageOperation old
            val attempted = (old ?: QuoteRecord(key, lastAttemptAt = now)).copy(lastAttemptAt = now)
            cache.write(attempted)
            val provider = registry.find(key.provider)
            val result = if (provider == null) QuoteFetchResult.Failure(QuoteFailure.NOT_CONFIGURED) else try {
                provider.fetch(key)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                QuoteFetchResult.Failure(QuoteFailure.PROVIDER)
            }
            val completed = Instant.ofEpochMilli(clock.millis())
            val record = when (result) {
                is QuoteFetchResult.Success -> {
                    val quote = parse(result.payload, key, provider!!, completed)
                    if (quote == null || (old?.quote != null &&
                        (quote.observedAt < old.quote.observedAt || quote.currency != old.quote.currency)))
                        attempted.copy(failure = QuoteFailure.INVALID_RESPONSE, retryAt = null)
                    else attempted.copy(quote = quote, failure = null, retryAt = null)
                }
                QuoteFetchResult.Empty -> attempted.copy(failure = QuoteFailure.NO_DATA, retryAt = null)
                is QuoteFetchResult.Failure -> attempted.copy(failure = result.reason,
                    retryAt = result.retryAt?.takeIf { result.reason == QuoteFailure.RATE_LIMIT && it > completed }
                        ?.coerceAtMost(completed.plus(Duration.ofDays(1))))
            }
            cache.write(record)
            record
        }
    }

    private fun parse(payload: QuotePayload, expected: InstrumentKey, provider: QuoteProvider, fetchedAt: Instant): MarketQuote? = try {
        require(payload.key == expected && payload.key.provider == provider.info.id)
        require(payload.decimalPrice.matches(Regex("[0-9]{1,20}(\\.[0-9]{1,18})?")))
        require(payload.observedAt >= Instant.EPOCH && payload.observedAt <= fetchedAt)
        val price = BigDecimal(payload.decimalPrice)
        MarketQuote(payload.key, price, CurrencyCode(payload.currency),
            Instant.ofEpochMilli(payload.observedAt.toEpochMilli()), fetchedAt, provider.info.name)
    } catch (_: IllegalArgumentException) { null }
}
