package com.tegar.finance.core.di

import android.content.Context
import androidx.room.Room
import com.tegar.finance.core.database.MarketDatabase
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
        Room.databaseBuilder(context, MarketDatabase::class.java, "market-cache.db").build()
    // Authorized, provider-specific adapters belong to subsequent market phases.
    @Provides @Singleton fun providers() = QuoteProviders()
    @Provides @Singleton fun transport(clock: Clock): HttpTransport = UrlConnectionTransport(clock)
    @Provides @Singleton fun cache(database: MarketDatabase): QuoteCache = RoomQuoteCache(database.quotes())
    @Provides @Singleton fun quotes(cache: QuoteCache, providers: QuoteProviders, clock: Clock): QuoteRepository =
        CachedQuoteRepository(cache, providers, clock)
    @Provides @Singleton fun policy() = QuoteCachePolicy()
}
