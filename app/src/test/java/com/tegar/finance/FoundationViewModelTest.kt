package com.tegar.finance

import androidx.lifecycle.ViewModelStore
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import com.tegar.finance.presentation.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class FoundationViewModelTest {
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun viewModel(source: FakeSource): FoundationViewModel = FoundationViewModel(source, source, source).also {
        store.put("foundation", it)
    }
    @Test fun initializeAndObserveReadyState() = runTest {
        val source = FakeSource()
        val vm = viewModel(source)
        advanceUntilIdle()
        assertTrue(vm.state.value.status is FoundationStatus.Ready)
        source.preferences.value = AppResult.Success(AppSettings(ThemeMode.DARK, AppLanguage.INDONESIAN))
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.state.value.theme)
        assertEquals(AppLanguage.INDONESIAN, vm.state.value.language)
    }
    @Test fun initializationFailureIsVisibleAndRetryRecovers() = runTest {
        val source = FakeSource().apply { initializationFails = true }
        val vm = viewModel(source)
        advanceUntilIdle()
        assertEquals(FoundationStatus.StorageError, vm.state.value.status)
        source.initializationFails = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.state.value.status is FoundationStatus.Ready)
    }
    @Test fun failedThemeWriteDoesNotPretendItWasSaved() = runTest {
        val source = FakeSource().apply { preferenceWriteFails = true }
        val vm = viewModel(source)
        advanceUntilIdle()
        vm.setTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertTrue(vm.state.value.themeSaveFailed)
        assertEquals(ThemeMode.SYSTEM, vm.state.value.theme)
    }
    @Test fun failedRepositoryObservationIsVisible() = runTest {
        val source = FakeSource()
        val vm = viewModel(source)
        advanceUntilIdle()
        source.categories.value = AppResult.Failure(AppError.STORAGE)
        advanceUntilIdle()
        assertEquals(FoundationStatus.StorageError, vm.state.value.status)
    }
    @Test fun languageChangesOnlyAfterSuccessfulPreferenceWriteAndKeepsTheme() = runTest {
        val source = FakeSource(); val vm = viewModel(source); advanceUntilIdle()
        vm.setTheme(ThemeMode.DARK); advanceUntilIdle()
        source.preferenceWriteFails = true
        vm.setLanguage(AppLanguage.INDONESIAN); advanceUntilIdle()
        assertTrue(vm.state.value.languageSaveFailed); assertEquals(AppLanguage.ENGLISH, vm.state.value.language)
        source.preferenceWriteFails = false
        vm.setLanguage(AppLanguage.INDONESIAN); advanceUntilIdle()
        assertFalse(vm.state.value.languageSaveFailed); assertEquals(AppLanguage.INDONESIAN, vm.state.value.language)
        assertEquals(ThemeMode.DARK, vm.state.value.theme)
    }

    private class FakeSource : LedgerRepository, CategoryRepository, SettingsRepository {
        var initializationFails = false
        var preferenceWriteFails = false
        val preferences = MutableStateFlow<AppResult<AppSettings>>(AppResult.Success(AppSettings()))
        val categories = MutableStateFlow<AppResult<List<Category>>>(AppResult.Success(emptyList()))
        private val account = Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), LocalDate.of(2026, 10, 6),
            Money(0), Instant.EPOCH)
        override suspend fun initialize(): AppResult<Unit> = if (initializationFails)
            AppResult.Failure(AppError.STORAGE) else AppResult.Success(Unit)
        override fun observeLedger(): Flow<AppResult<Ledger?>> = flowOf(AppResult.Success(account))
        override fun observeCategories() = categories
        override fun observeSettings() = preferences
        override suspend fun setTheme(mode: ThemeMode): AppResult<Unit> {
            if (preferenceWriteFails) return AppResult.Failure(AppError.STORAGE)
            preferences.value = AppResult.Success((preferences.value as AppResult.Success).value.copy(theme = mode))
            return AppResult.Success(Unit)
        }
        override suspend fun setLanguage(language: AppLanguage): AppResult<Unit> {
            if (preferenceWriteFails) return AppResult.Failure(AppError.STORAGE)
            preferences.value = AppResult.Success((preferences.value as AppResult.Success).value.copy(language = language))
            return AppResult.Success(Unit)
        }
    }
}
