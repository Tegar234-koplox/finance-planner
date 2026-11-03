package com.tegar.finance.data.market

import androidx.room.withTransaction
import com.tegar.finance.core.database.*
import com.tegar.finance.data.repository.storageOperation
import com.tegar.finance.data.repository.storageResults
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.map

class RoomWatchlistRepository(private val db: MarketDatabase, private val clock: Clock) : WatchlistRepository {
    override fun observe() = db.watchlist().observe().map { rows -> rows.map { row ->
        val draft = WatchlistRules.normalize(WatchlistDraft(row.name, row.symbol, row.market, InstrumentKind.valueOf(row.kind)))
        require(row.provider == WatchlistRules.PROVIDER && row.name == draft.name && row.symbol == draft.symbol)
        WatchlistEntry(row.id, InstrumentKey(row.provider, row.market, row.symbol), row.name, draft.kind, Instant.ofEpochMilli(row.createdAt))
    } }.storageResults()
    override suspend fun save(id: String?, draft: WatchlistDraft): AppResult<Unit> {
        val normalized = try { WatchlistRules.normalize(draft) } catch (_: IllegalArgumentException) {
            return AppResult.Failure(AppError.INVALID_INPUT)
        }
        return when (val result = storageOperation { db.withTransaction {
            val rows = db.watchlist().rows()
            val old = rows.find { it.id == id }
            when {
                id != null && old == null -> AppResult.Failure(AppError.NOT_FOUND)
                rows.any { it.id != id && it.provider == WatchlistRules.PROVIDER && it.market == normalized.market && it.symbol == normalized.symbol } ->
                    AppResult.Failure(AppError.DUPLICATE_INSTRUMENT)
                old == null && rows.size >= WatchlistRules.LIMIT -> AppResult.Failure(AppError.WATCHLIST_LIMIT)
                else -> {
                    db.watchlist().write(WatchlistEntity(id ?: UUID.randomUUID().toString(), WatchlistRules.PROVIDER,
                        normalized.market, normalized.symbol, normalized.name, normalized.kind.name, old?.createdAt ?: clock.millis()))
                    AppResult.Success(Unit)
                }
            }
        } }) { is AppResult.Failure -> result; is AppResult.Success -> result.value }
    }
    override suspend fun delete(id: String): AppResult<Unit> = when (val result = storageOperation { db.watchlist().delete(id) }) {
        is AppResult.Failure -> result
        is AppResult.Success -> if (result.value == 1) AppResult.Success(Unit) else AppResult.Failure(AppError.NOT_FOUND)
    }
}

/** Conservative local budget; it never promises the account's actual entitlement or quota. */
class RoomProviderRequestGate(private val db: MarketDatabase) : ProviderRequestGate {
    override suspend fun reserve(now: Instant): Instant? = db.withTransaction {
        val millis = now.toEpochMilli()
        val old = db.watchlist().window(WatchlistRules.PROVIDER)
        if (old != null) require(old.windowStart in 0..253402300799999 && old.requests in 0..6 && old.blockedUntil in 0..253402300799999)
        if (old != null && old.blockedUntil > millis) return@withTransaction Instant.ofEpochMilli(old.blockedUntil)
        if (old != null && millis < old.windowStart) return@withTransaction Instant.ofEpochMilli(old.windowStart + 60_000)
        val row = if (old == null || millis - old.windowStart >= 60_000) RequestWindowEntity(WatchlistRules.PROVIDER, millis, 0, 0) else old
        if (row.requests >= 6) return@withTransaction Instant.ofEpochMilli(row.windowStart + 60_000)
        db.watchlist().writeWindow(row.copy(requests = row.requests + 1))
        null
    }
    override suspend fun block(until: Instant) = db.withTransaction {
        val old = db.watchlist().window(WatchlistRules.PROVIDER) ?: RequestWindowEntity(WatchlistRules.PROVIDER, 0, 0, 0)
        db.watchlist().writeWindow(old.copy(blockedUntil = maxOf(old.blockedUntil, until.toEpochMilli())))
    }
}
