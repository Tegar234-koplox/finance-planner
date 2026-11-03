package com.tegar.finance

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tegar.finance.core.database.*
import com.tegar.finance.core.network.*
import com.tegar.finance.data.market.*
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WatchlistStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-03T01:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
    @Test fun genuineV1QuoteCacheMigrationPreservesAllOriginalColumnsAndStartsWithNoWatchlist() = runBlocking {
        val name = "market-migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.tegar.finance.core.database.MarketDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val table = schema.getJSONArray("entities").getJSONObject(0)
            old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", "quote_cache"))
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES (42,?)", arrayOf(schema.getString("identityHash")))
            old.execSQL("INSERT INTO quote_cache VALUES (?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("twelvedata", "XNAS", "AAPL", "1.123456789012345678", "USD", now.minusSeconds(1000).toEpochMilli(), now.toEpochMilli(), "Original source", now.toEpochMilli(), "RATE_LIMIT", now.plusSeconds(60).toEpochMilli()))
            old.version = 1
        } finally { old.close() }
        val db = Room.databaseBuilder(context, MarketDatabase::class.java, name).addMigrations(MarketMigrations.FROM_1_TO_2).build()
        try {
            val row = RoomQuoteCache(db.quotes()).read(InstrumentKey("twelvedata", "XNAS", "AAPL"))!!
            assertEquals(BigDecimal("1.123456789012345678"), row.quote!!.price)
            assertEquals("Original source", row.quote.source); assertEquals(now.minusSeconds(1000), row.quote.observedAt)
            assertEquals(now, row.quote.fetchedAt); assertEquals(now, row.lastAttemptAt)
            assertEquals(QuoteFailure.RATE_LIMIT, row.failure); assertEquals(now.plusSeconds(60), row.retryAt)
            assertTrue(db.watchlist().rows().isEmpty()); assertNull(db.watchlist().window("twelvedata"))
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun normalizedCrudDuplicateAndCapacityRulesAreAtomicAndDoNotDeleteCachedQuotes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MarketDatabase::class.java).build()
        try {
            val repo = RoomWatchlistRepository(db, clock)
            repo.save(null, WatchlistDraft(" Apple ", "aapl", "XNAS")).ok()
            val first = repo.observe().first().ok().single(); assertEquals("Apple", first.name); assertEquals("AAPL", first.key.symbol)
            assertEquals(AppResult.Failure(AppError.DUPLICATE_INSTRUMENT), repo.save(null, WatchlistDraft("Again", "AaPl", "XNAS")))
            repo.save(null, WatchlistDraft("Other venue", "AAPL", "XNYS")).ok()
            repo.save(first.id, WatchlistDraft("Renamed", "MSFT", "XNAS")).ok()
            val changed = repo.observe().first().ok().first { it.id == first.id }
            assertEquals(first.createdAt, changed.createdAt); assertEquals("MSFT", changed.key.symbol)
            repeat(18) { repo.save(null, WatchlistDraft("Stock $it", "S$it", "XNAS")).ok() }
            assertEquals(AppResult.Failure(AppError.WATCHLIST_LIMIT), repo.save(null, WatchlistDraft("Excess", "EXCESS", "XNAS")))
            val cache = RoomQuoteCache(db.quotes())
            val quote = MarketQuote(changed.key, BigDecimal.ZERO, CurrencyCode("USD"), now, now, "Test")
            cache.write(QuoteRecord(changed.key, quote, now))
            repo.delete(first.id).ok(); assertEquals(19, repo.observe().first().ok().size)
            assertEquals(quote, cache.read(changed.key)!!.quote)
            assertEquals(AppResult.Failure(AppError.NOT_FOUND), repo.delete(first.id))
        } finally { db.close() }
    }
    @Test fun requestBudgetAndProviderCooldownPersistAcrossReopenAndClockRollback() = runBlocking {
        val name = "market-budget-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, MarketDatabase::class.java, name).build()
        try {
            var gate = RoomProviderRequestGate(db)
            repeat(6) { assertNull(gate.reserve(now)) }
            assertEquals(now.plusSeconds(60), gate.reserve(now))
            db.close(); db = Room.databaseBuilder(context, MarketDatabase::class.java, name).build(); gate = RoomProviderRequestGate(db)
            assertEquals(now.plusSeconds(60), gate.reserve(now.minusSeconds(1)))
            gate.block(now.plusSeconds(90)); assertEquals(now.plusSeconds(90), gate.reserve(now.plusSeconds(61)))
            assertNull(gate.reserve(now.plusSeconds(90)))
            assertEquals(1, db.watchlist().window("twelvedata")!!.requests)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun realProviderParserAndRoomCacheKeepTheLastPriceAfterAnOutageWithoutChangingWatchlistIdentity() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MarketDatabase::class.java).build()
        try {
            val list = RoomWatchlistRepository(db, clock); list.save(null, WatchlistDraft("Apple", "AAPL", "XNAS")).ok()
            val entry = list.observe().first().ok().single()
            val keys = object : ProviderKeyStore {
                override fun observeStatus() = flowOf(AppResult.Success(true))
                override suspend fun read() = AppResult.Success("synthetic_key_1234")
                override suspend fun save(key: String) = AppResult.Success(Unit)
                override suspend fun clear() = AppResult.Success(Unit)
            }
            var offline = false; var calls = 0
            val source = TwelveDataProvider(HttpTransport {
                calls++
                if (offline) HttpResult.Failure(QuoteFailure.OFFLINE) else HttpResult.Success(200,
                    """{"symbol":"AAPL","mic_code":"XNAS","currency":"USD","timestamp":${now.epochSecond},"close":"100.123456789012345678"}""")
            }, keys, RoomProviderRequestGate(db), clock)
            val quotes = CachedQuoteRepository(RoomQuoteCache(db.quotes()), QuoteProviders(listOf(source)), clock)
            val original = quotes.refresh(entry.key).ok().quote!!; offline = true
            val failed = quotes.refresh(entry.key).ok(); assertEquals(original, failed.quote); assertEquals(QuoteFailure.OFFLINE, failed.failure)
            assertEquals(2, calls); assertEquals(entry, list.observe().first().ok().single())
            assertEquals(BigDecimal("100.123456789012345678"), RoomQuoteCache(db.quotes()).read(entry.key)!!.quote!!.price)
        } finally { db.close() }
    }
}
