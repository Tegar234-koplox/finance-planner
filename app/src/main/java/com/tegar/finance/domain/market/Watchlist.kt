package com.tegar.finance.domain.market

import com.tegar.finance.domain.model.AppResult
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.flow.Flow

enum class InstrumentKind { STOCK, ETF, INDEX }
data class WatchlistEntry(val id: String, val key: InstrumentKey, val name: String,
    val kind: InstrumentKind, val createdAt: Instant)
data class WatchlistDraft(val name: String, val symbol: String, val market: String,
    val kind: InstrumentKind = InstrumentKind.STOCK)

object WatchlistRules {
    const val PROVIDER = "twelvedata"
    const val LIMIT = 20
    val STOCK_MARKETS = listOf("XNAS", "XNYS", "ARCX")
    fun normalize(draft: WatchlistDraft): WatchlistDraft {
        val name = draft.name.trim()
        val symbol = draft.symbol.trim().uppercase(Locale.ROOT)
        require(name.isNotEmpty() && name.length <= 60 && name.none(Char::isISOControl))
        require(symbol.matches(Regex("[A-Z0-9^][A-Z0-9.^:_/-]{0,19}")))
        require(if (draft.kind == InstrumentKind.INDEX) draft.market == "US_INDEX" else draft.market in STOCK_MARKETS)
        return draft.copy(name = name, symbol = symbol)
    }
}
interface WatchlistRepository {
    fun observe(): Flow<AppResult<List<WatchlistEntry>>>
    suspend fun save(id: String?, draft: WatchlistDraft): AppResult<Unit>
    suspend fun delete(id: String): AppResult<Unit>
}
interface ProviderKeyStore {
    /** Status contains no key material; UI never reads a previously saved secret. */
    fun observeStatus(): Flow<AppResult<Boolean>>
    suspend fun read(): AppResult<String?>
    suspend fun save(key: String): AppResult<Unit>
    suspend fun clear(): AppResult<Unit>
}
object ProviderKeyRules {
    fun valid(key: String) = key.matches(Regex("[A-Za-z0-9_-]{4,128}"))
}
interface ProviderRequestGate {
    /** Null reserves one request; a timestamp rejects it until that instant, without HTTP. */
    suspend fun reserve(now: Instant): Instant?
    suspend fun block(until: Instant)
}
