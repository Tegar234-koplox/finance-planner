package com.tegar.finance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.BudgetRepository
import com.tegar.finance.presentation.ForecastViewModel
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun vm(source: Source, time: Clock = clock, saved: SavedStateHandle = SavedStateHandle()) =
        ForecastViewModel(source, time, saved).also { store.put("forecast", it) }

    @Test fun methodsAndWindowsChangeOnlyReadOnlySelectionAndSaveIt() = runTest {
        val saved = SavedStateHandle(); val model = vm(Source(), saved = saved); advanceUntilIdle()
        assertEquals(100L, model.state.value.forecast!!.horizons.first().totals!!.consumption.minorUnits)
        model.selectMethod(ForecastMethod.WEIGHTED); model.selectWindow(12); advanceUntilIdle()
        assertEquals(133L, model.state.value.forecast!!.horizons.first().totals!!.consumption.minorUnits)
        assertEquals("WEIGHTED", saved.get<String>("forecast_method")); assertEquals(12, saved.get<Int>("forecast_window"))
        assertEquals(ForecastOptions(ForecastMethod.WEIGHTED, 12), model.state.value.options)
    }
    @Test fun restoredSelectionAndInvalidSavedValuesHaveDefinedBehavior() = runTest {
        val restored = vm(Source(), saved = SavedStateHandle(mapOf("forecast_method" to "WEIGHTED", "forecast_window" to 3)))
        advanceUntilIdle(); assertEquals(ForecastOptions(ForecastMethod.WEIGHTED, 3), restored.state.value.options)
        val invalid = vm(Source(), saved = SavedStateHandle(mapOf("forecast_method" to "missing", "forecast_window" to -1)))
        advanceUntilIdle(); assertEquals(ForecastOptions(), invalid.state.value.options)
        invalid.selectWindow(7); advanceUntilIdle(); assertEquals(ForecastOptions(), invalid.state.value.options)
    }
    @Test fun editingDeletingAndBudgetChangesUpdateBothEstimatesAndRules() = runTest {
        val source = Source(); val model = vm(source); advanceUntilIdle()
        source.data.value = AppResult.Success(source.snapshot.copy(transactions = listOf(source.row(400)),
            monthly = listOf(MonthlyBudget("cap", YearMonth.of(2028, 4), Money(150)))))
        advanceUntilIdle()
        assertEquals(200L, model.state.value.forecast!!.horizons.first().totals!!.consumption.minorUnits)
        assertTrue(model.state.value.forecast!!.rules.any { it.kind == ForecastRuleKind.NEXT_BUDGET_SHORTFALL })
        source.data.value = AppResult.Success(source.snapshot.copy(transactions = emptyList())); advanceUntilIdle()
        assertEquals(0L, model.state.value.forecast!!.horizons.first().totals!!.consumption.minorUnits)
        assertFalse(model.state.value.forecast!!.rules.any { it.kind == ForecastRuleKind.NEXT_BUDGET_SHORTFALL })
    }
    @Test fun storageFailureClearsEstimatesAndRulesAndRetryRecoversSelection() = runTest {
        val source = Source(); val model = vm(source); advanceUntilIdle()
        model.selectWindow(3); advanceUntilIdle()
        source.data.value = AppResult.Failure(AppError.STORAGE); advanceUntilIdle()
        assertEquals(AppError.STORAGE, model.state.value.error); assertNull(model.state.value.forecast)
        source.data.value = AppResult.Success(source.snapshot); model.retry(); advanceUntilIdle()
        assertNull(model.state.value.error); assertNotNull(model.state.value.forecast)
        assertEquals(3, model.state.value.options.window)
    }
    @Test fun missingLedgerAndOverflowAreDistinctFromZeroObservedHistory() = runTest {
        val source = Source(); val model = vm(source); advanceUntilIdle()
        source.data.value = AppResult.Success(null); advanceUntilIdle()
        assertTrue(model.state.value.loading); assertNull(model.state.value.forecast)
        source.data.value = AppResult.Success(source.snapshot.copy(transactions = listOf(source.row(Long.MAX_VALUE), source.row(1))))
        advanceUntilIdle(); assertEquals(AppError.OUT_OF_RANGE, model.state.value.error); assertNull(model.state.value.forecast)
        source.data.value = AppResult.Success(source.snapshot.copy(transactions = emptyList())); advanceUntilIdle()
        assertNull(model.state.value.error)
        assertEquals(0L, model.state.value.forecast!!.horizons.first().totals!!.consumption.minorUnits)
    }
    @Test fun refreshUsesLedgerZoneAndNewlyCompletedMonthAtMidnight() = runTest {
        val source = Source(); val time = MutableClock(Instant.parse("2028-03-31T16:59:00Z"))
        val model = vm(source, time); advanceUntilIdle()
        assertEquals(LocalDate.of(2028, 3, 31), model.state.value.forecast!!.asOf)
        assertEquals(2, model.state.value.forecast!!.samples.size)
        time.value = Instant.parse("2028-03-31T17:01:00Z"); model.refresh(); advanceUntilIdle()
        assertEquals(LocalDate.of(2028, 4, 1), model.state.value.forecast!!.asOf)
        assertEquals(3, model.state.value.forecast!!.samples.size)
        assertEquals(LocalDate.of(2028, 5, 1), model.state.value.forecast!!.horizons.first().start)
    }
    private class MutableClock(var value: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun instant(): Instant = value
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(value, zone)
    }
    private class Source : BudgetRepository {
        val snapshot = BudgetSnapshot(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), LocalDate.of(2028, 1, 1), Money(0), Instant.EPOCH),
            emptyList(), emptyList(), listOf(row(200)))
        val data = MutableStateFlow<AppResult<BudgetSnapshot?>>(AppResult.Success(snapshot))
        fun row(amount: Long) = FinanceTransaction("row-$amount", TransactionType.EXPENSE, Money(amount), "food",
            LocalDate.of(2028, 2, 10), "", Instant.EPOCH, null)
        override fun observeBudgets() = data
        override suspend fun saveMonthly(month: YearMonth, allocation: Money): AppResult<Unit> = error("Forecast must not write money")
        override suspend fun saveCategory(month: YearMonth, categoryId: String, allocation: Money): AppResult<Unit> = error("Forecast must not write money")
        override suspend fun deleteMonthly(month: YearMonth): AppResult<Unit> = error("Forecast must not write money")
        override suspend fun deleteCategory(month: YearMonth, categoryId: String): AppResult<Unit> = error("Forecast must not write money")
    }
}
