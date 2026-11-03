package com.tegar.finance.data.market

import com.tegar.finance.core.database.*
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.CurrencyCode
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.map

class RoomQuoteCache(private val dao: QuoteCacheDao) : QuoteCache {
    override fun observe() = dao.observe().map { rows -> rows.map { it.domain() } }
    override suspend fun read(key: InstrumentKey) = dao.read(key.provider, key.market, key.symbol)?.domain()
    override suspend fun write(record: QuoteRecord) {
        val q = record.quote
        dao.write(QuoteCacheEntity(record.key.provider, record.key.market, record.key.symbol,
            q?.price?.toPlainString(), q?.currency?.value, q?.observedAt?.toEpochMilli(), q?.fetchedAt?.toEpochMilli(),
            q?.source, record.lastAttemptAt.toEpochMilli(), record.failure?.name, record.retryAt?.toEpochMilli()))
    }
    private fun QuoteCacheEntity.domain(): QuoteRecord {
        val key = InstrumentKey(provider, market, symbol)
        val quoteFields = listOf(price, currency, observedAt, fetchedAt, source)
        require(quoteFields.all { it == null } || quoteFields.none { it == null })
        val quote = if (price == null) null else MarketQuote(key, BigDecimal(price), CurrencyCode(currency!!),
            Instant.ofEpochMilli(observedAt!!), Instant.ofEpochMilli(fetchedAt!!), source!!)
        return QuoteRecord(key, quote, Instant.ofEpochMilli(lastAttemptAt), failure?.let(QuoteFailure::valueOf),
            retryAt?.let(Instant::ofEpochMilli))
    }
}
