package com.tegar.finance.domain.market

import com.tegar.finance.domain.model.AppResult
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    val providers: List<ProviderInfo>
    fun observeQuotes(): Flow<AppResult<List<QuoteRecord>>>
    /** Explicit, single attempt. Outages/empty results preserve the last successful quote. */
    suspend fun refresh(key: InstrumentKey): AppResult<QuoteRecord>
}

interface QuoteCache {
    fun observe(): Flow<List<QuoteRecord>>
    suspend fun read(key: InstrumentKey): QuoteRecord?
    suspend fun write(record: QuoteRecord)
}
