package com.tegar.finance.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Rebuildable market information is isolated from authoritative personal records. */
@Entity(tableName = "quote_cache", primaryKeys = ["provider", "market", "symbol"])
data class QuoteCacheEntity(val provider: String, val market: String, val symbol: String,
    val price: String?, val currency: String?, val observedAt: Long?, val fetchedAt: Long?,
    val source: String?, val lastAttemptAt: Long, val failure: String?, val retryAt: Long?)

@Entity(tableName = "market_watchlist", indices = [Index(value = ["provider", "market", "symbol"], unique = true)])
data class WatchlistEntity(@PrimaryKey val id: String, val provider: String, val market: String,
    val symbol: String, val name: String, val kind: String, val createdAt: Long)

@Entity(tableName = "market_request_window")
data class RequestWindowEntity(@PrimaryKey val provider: String, val windowStart: Long,
    val requests: Int, val blockedUntil: Long)

@Dao
interface QuoteCacheDao {
    @Query("SELECT * FROM quote_cache ORDER BY provider, market, symbol")
    fun observe(): Flow<List<QuoteCacheEntity>>
    @Query("SELECT * FROM quote_cache WHERE provider=:provider AND market=:market AND symbol=:symbol")
    suspend fun read(provider: String, market: String, symbol: String): QuoteCacheEntity?
    @Upsert suspend fun write(row: QuoteCacheEntity)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM market_watchlist ORDER BY createdAt, id") fun observe(): Flow<List<WatchlistEntity>>
    @Query("SELECT * FROM market_watchlist ORDER BY createdAt, id") suspend fun rows(): List<WatchlistEntity>
    @Upsert suspend fun write(row: WatchlistEntity)
    @Query("DELETE FROM market_watchlist WHERE id=:id") suspend fun delete(id: String): Int
    @Query("SELECT * FROM market_request_window WHERE provider=:provider") suspend fun window(provider: String): RequestWindowEntity?
    @Upsert suspend fun writeWindow(row: RequestWindowEntity)
}

@Database(entities = [QuoteCacheEntity::class, WatchlistEntity::class, RequestWindowEntity::class], version = 2, exportSchema = true)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun quotes(): QuoteCacheDao
    abstract fun watchlist(): WatchlistDao
}
