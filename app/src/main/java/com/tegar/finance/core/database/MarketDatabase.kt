package com.tegar.finance.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Rebuildable market information is isolated from authoritative personal records. */
@Entity(tableName = "quote_cache", primaryKeys = ["provider", "market", "symbol"])
data class QuoteCacheEntity(val provider: String, val market: String, val symbol: String,
    val price: String?, val currency: String?, val observedAt: Long?, val fetchedAt: Long?,
    val source: String?, val lastAttemptAt: Long, val failure: String?, val retryAt: Long?)

@Dao
interface QuoteCacheDao {
    @Query("SELECT * FROM quote_cache ORDER BY provider, market, symbol")
    fun observe(): Flow<List<QuoteCacheEntity>>
    @Query("SELECT * FROM quote_cache WHERE provider=:provider AND market=:market AND symbol=:symbol")
    suspend fun read(provider: String, market: String, symbol: String): QuoteCacheEntity?
    @Upsert suspend fun write(row: QuoteCacheEntity)
}

@Database(entities = [QuoteCacheEntity::class], version = 1, exportSchema = true)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun quotes(): QuoteCacheDao
}
