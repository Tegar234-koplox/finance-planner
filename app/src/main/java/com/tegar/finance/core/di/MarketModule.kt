package com.tegar.finance.core.di

import android.content.Context
import androidx.room.Room
import com.tegar.finance.core.database.MarketDatabase
import com.tegar.finance.core.database.MarketMigrations
import com.tegar.finance.core.network.HttpTransport
import com.tegar.finance.core.network.UrlConnectionTransport
import com.tegar.finance.data.market.*
import com.tegar.finance.domain.market.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MarketModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): MarketDatabase =
        Room.databaseBuilder(context, MarketDatabase::class.java, "market-cache.db").addMigrations(MarketMigrations.FROM_1_TO_2).build()
    @Provides @Singleton fun keys(@ApplicationContext context: Context): ProviderKeyStore = KeystoreProviderKeyStore(context)
    @Provides @Singleton fun watchlist(db: MarketDatabase, clock: Clock): WatchlistRepository = RoomWatchlistRepository(db, clock)
    @Provides @Singleton fun gate(db: MarketDatabase): ProviderRequestGate = RoomProviderRequestGate(db)
    @Provides @Singleton fun providers(transport: HttpTransport, keys: ProviderKeyStore,
        gate: ProviderRequestGate, clock: Clock) = QuoteProviders(listOf(TwelveDataProvider(transport, keys, gate, clock)))
    @Provides @Singleton fun transport(clock: Clock): HttpTransport = UrlConnectionTransport(clock)
    @Provides @Singleton fun cache(database: MarketDatabase): QuoteCache = RoomQuoteCache(database.quotes())
    @Provides @Singleton fun quotes(cache: QuoteCache, providers: QuoteProviders, clock: Clock): QuoteRepository =
        CachedQuoteRepository(cache, providers, clock)
    @Provides @Singleton fun policy() = QuoteCachePolicy()
}
