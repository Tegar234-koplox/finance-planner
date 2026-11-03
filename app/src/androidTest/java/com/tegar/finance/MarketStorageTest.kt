package com.tegar.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.data.market.*
import com.tegar.finance.data.repository.LocalFoundationRepository
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MarketStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-03T01:00:00Z")
    private val key = InstrumentKey("test", "XNAS", "ABC")
    private val quote = MarketQuote(key, BigDecimal("99999999999999999999.123456789012345678"),
        CurrencyCode("USD"), now.minusSeconds(900), now, "Test source")

    @Test fun exactPriceAndFailureMetadataSurviveDatabaseCloseAndReopen() = runBlocking {
        val name = "market-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, MarketDatabase::class.java, name).build()
        val expected = QuoteRecord(key, quote, now.plusSeconds(30), QuoteFailure.RATE_LIMIT, now.plusSeconds(120))
        try {
            RoomQuoteCache(db.quotes()).write(expected); db.close()
            db = Room.databaseBuilder(context, MarketDatabase::class.java, name).build()
            val cache = RoomQuoteCache(db.quotes())
            assertEquals(expected, cache.read(key)); assertEquals(listOf(expected), cache.observe().first())
            assertEquals(1, db.openHelper.readableDatabase.version)
            assertEquals(QuoteFreshness.STALE, QuoteCachePolicy().freshness(cache.read(key)!!.quote, now))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun compositeIdentitiesZeroAndUnknownRemainDistinctAcrossAtomicUpserts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MarketDatabase::class.java).build()
        try {
            val cache = RoomQuoteCache(db.quotes())
            val zero = QuoteRecord(key, quote.copy(price = BigDecimal.ZERO), now)
            val otherMarket = QuoteRecord(key.copy(market = "XNYS"), lastAttemptAt = now, failure = QuoteFailure.NO_DATA)
            val otherProvider = QuoteRecord(key.copy(provider = "other"), lastAttemptAt = now, failure = QuoteFailure.OFFLINE)
            listOf(zero, otherMarket, otherProvider).forEach { cache.write(it) }
            assertEquals(3, cache.observe().first().size)
            cache.write(zero.copy(lastAttemptAt = now.plusSeconds(30), failure = QuoteFailure.TIMEOUT))
            assertEquals(BigDecimal.ZERO, cache.read(key)!!.quote!!.price)
            assertNull(cache.read(otherMarket.key)!!.quote)
            assertEquals(3, cache.observe().first().size)
        } finally { db.close() }
    }
    @Test fun malformedOrPartialStoredQuotesAreStorageErrorsRatherThanFabricatedValues() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MarketDatabase::class.java).build()
        try {
            val repo = CachedQuoteRepository(RoomQuoteCache(db.quotes()), QuoteProviders(), Clock.fixed(now, ZoneOffset.UTC))
            db.quotes().write(QuoteCacheEntity(key.provider, key.market, key.symbol, "100", null, null, null,
                null, now.toEpochMilli(), null, null))
            assertEquals(AppResult.Failure(AppError.STORAGE), repo.observeQuotes().first())
            assertEquals(AppResult.Failure(AppError.STORAGE), repo.refresh(key))
        } finally { db.close() }
    }
    @Test fun marketUpdatesAndFailuresCannotChangeFinancialRowsOrSchema() = runBlocking {
        val finance = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).addCallback(FinanceMigrations.CALLBACK).build()
        val market = Room.inMemoryDatabaseBuilder(context, MarketDatabase::class.java).build()
        try {
            LocalFoundationRepository(finance, Clock.fixed(now, ZoneOffset.UTC)).initialize()
            fun snapshot(): Map<String, List<List<String?>>> {
                val sql = finance.openHelper.readableDatabase
                val names = sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
                return names.associateWith { table -> sql.query("SELECT * FROM `$table` ORDER BY rowid").use { c ->
                    buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
                } }
            }
            val before = snapshot(); val cache = RoomQuoteCache(market.quotes())
            cache.write(QuoteRecord(key, quote, now))
            val repo = CachedQuoteRepository(cache, QuoteProviders(), Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC))
            val result = (repo.refresh(key) as AppResult.Success).value
            assertEquals(QuoteFailure.NOT_CONFIGURED, result.failure); assertEquals(quote, result.quote)
            assertEquals(before, snapshot()); assertEquals(6, finance.openHelper.readableDatabase.version)
            assertTrue(before.getValue("ledger").isNotEmpty()); assertTrue(before.getValue("category").isNotEmpty())
        } finally { market.close(); finance.close() }
    }
}
