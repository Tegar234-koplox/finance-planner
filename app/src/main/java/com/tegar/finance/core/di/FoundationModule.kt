package com.tegar.finance.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.tegar.finance.core.database.FinanceDatabase
import com.tegar.finance.core.database.FinanceMigrations
import com.tegar.finance.core.database.BudgetMigrations
import com.tegar.finance.core.database.GoalMigrations
import com.tegar.finance.core.database.WealthMigrations
import com.tegar.finance.core.database.InvestmentMigrations
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
        Room.databaseBuilder(context, FinanceDatabase::class.java, "finance.db")
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5, InvestmentMigrations.FROM_5_TO_6).addCallback(FinanceMigrations.CALLBACK).build()
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
    @Binds abstract fun transactions(implementation: LocalBookkeepingRepository): TransactionRepository
    @Binds abstract fun editableCategories(implementation: LocalBookkeepingRepository): EditableCategoryRepository
    @Binds abstract fun budgets(implementation: LocalBudgetRepository): BudgetRepository
    @Binds abstract fun goals(implementation: LocalGoalRepository): GoalRepository
    @Binds abstract fun investments(implementation: LocalInvestmentRepository): InvestmentRepository
    @Binds abstract fun wealth(implementation: LocalWealthRepository): WealthRepository
}
