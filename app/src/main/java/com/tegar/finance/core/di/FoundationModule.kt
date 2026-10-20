package com.tegar.finance.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.repository.*
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.*

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton fun clock(): Clock = Clock.systemUTC()
    @Provides @Singleton fun database(@ApplicationContext context: Context): FinanceDatabase =
        Room.databaseBuilder(context, FinanceDatabase::class.java, "finance.db").build()
    @Provides @Singleton fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("settings") })
}
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun ledger(implementation: LocalFoundationRepository): LedgerRepository
    @Binds abstract fun categories(implementation: LocalFoundationRepository): CategoryRepository
    @Binds abstract fun settings(implementation: DataStoreSettingsRepository): SettingsRepository
}
