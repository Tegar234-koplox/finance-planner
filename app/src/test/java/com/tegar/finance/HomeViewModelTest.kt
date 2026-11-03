package com.tegar.finance

import androidx.lifecycle.ViewModelStore
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import com.tegar.finance.presentation.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2028-02-01T05:00:00Z"), ZoneOffset.UTC)
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun vm(source: Source, time: Clock = clock) = HomeViewModel(source, source, time).also { store.put("home", it) }
    @Test fun sourceChangesUpdateCashAndConsumptionWithoutCachedCounters() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        source.emit(listOf(source.row(1000, TransactionType.INCOME), source.row(200))); advanceUntilIdle()
        assertEquals(800L, vm.state.value.dashboard!!.recordedCash!!.minorUnits)
        assertEquals(200L, vm.state.value.dashboard!!.budget.actual!!.minorUnits)
        source.emit(emptyList()); advanceUntilIdle()
        assertEquals(0L, vm.state.value.dashboard!!.recordedCash!!.minorUnits)
    }
    @Test fun observationFailureHidesOldTotalsAndRetryRecovers() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        source.data.value = AppResult.Failure(AppError.STORAGE); advanceUntilIdle()
        assertEquals(AppError.STORAGE, vm.state.value.error); assertNull(vm.state.value.dashboard)
        source.emit(emptyList()); vm.retry(); advanceUntilIdle()
        assertNull(vm.state.value.error); assertNotNull(vm.state.value.dashboard)
    }
    @Test fun overflowIsVisibleAndNeverLeavesOldMoneyOnScreen() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        source.emit(listOf(source.row(Long.MAX_VALUE, TransactionType.INCOME), source.row(1, TransactionType.INCOME))); advanceUntilIdle()
        assertEquals(AppError.OUT_OF_RANGE, vm.state.value.error); assertNull(vm.state.value.dashboard)
    }
    @Test fun renamedArchivedCategoriesUpdateLabelsWithoutChangingAmounts() = runTest {
        val source = Source(); source.emit(listOf(source.row(100)))
        val vm = vm(source); advanceUntilIdle()
        val oldCash = vm.state.value.dashboard!!.recordedCash
        source.cats.value = AppResult.Success(listOf(Category("food", "Renamed food", TransactionType.EXPENSE, false))); advanceUntilIdle()
        assertEquals("Renamed food", vm.state.value.categories["food"]!!.name)
        assertFalse(vm.state.value.categories["food"]!!.active)
        assertEquals(oldCash, vm.state.value.dashboard!!.recordedCash)
        assertEquals("food", vm.state.value.dashboard!!.topCategories.single().categoryId)
    }
    @Test fun missingLedgerIsLoadingRatherThanFalseZeroOrStaleData() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        source.data.value = AppResult.Success(null); advanceUntilIdle()
        assertTrue(vm.state.value.loading); assertNull(vm.state.value.dashboard)
    }
    @Test fun refreshReevaluatesReportingZoneMidnightAndMonthWithoutNewDatabaseWrites() = runTest {
        val source = Source()
        val march = YearMonth.of(2028, 3)
        val snapshot = source.snapshot.copy(monthly = source.snapshot.monthly + MonthlyBudget("march", march, Money(20000)),
            transactions = listOf(source.row(200, TransactionType.INCOME).copy(date = march.atDay(1))))
        source.data.value = AppResult.Success(snapshot)
        val time = MutableClock(Instant.parse("2028-02-29T16:59:00Z"))
        val vm = vm(source, time); advanceUntilIdle()
        assertEquals(LocalDate.of(2028, 2, 29), vm.state.value.dashboard!!.date)
        assertEquals(0L, vm.state.value.dashboard!!.recordedCash!!.minorUnits)
        time.value = Instant.parse("2028-02-29T17:01:00Z")
        vm.refresh(); advanceUntilIdle()
        val home = vm.state.value.dashboard!!
        assertEquals(march, home.month); assertEquals(200L, home.recordedCash!!.minorUnits)
        assertEquals(20000L, home.budget.allocation!!.minorUnits)
    }
    private class MutableClock(var value: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun instant(): Instant = value
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(value, zone)
    }
    private class Source : BudgetRepository, CategoryRepository {
        val month = YearMonth.of(2028, 2)
        val snapshot = BudgetSnapshot(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), month.atDay(1), Money(0), Instant.EPOCH),
            listOf(MonthlyBudget("overall", month, Money(10000))), emptyList(), emptyList())
        val data = MutableStateFlow<AppResult<BudgetSnapshot?>>(AppResult.Success(snapshot))
        val cats = MutableStateFlow<AppResult<List<Category>>>(AppResult.Success(listOf(Category("food", "Food", TransactionType.EXPENSE, true))))
        fun row(amount: Long, type: TransactionType = TransactionType.EXPENSE) =
            FinanceTransaction("$type-$amount", type, Money(amount), "food", month.atDay(1), "", Instant.EPOCH, null)
        fun emit(records: List<FinanceTransaction>) { data.value = AppResult.Success(snapshot.copy(transactions = records)) }
        override fun observeBudgets() = data
        override fun observeCategories() = cats
        override suspend fun saveMonthly(month: YearMonth, allocation: Money): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun saveCategory(month: YearMonth, categoryId: String, allocation: Money): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun deleteMonthly(month: YearMonth): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun deleteCategory(month: YearMonth, categoryId: String): AppResult<Unit> = AppResult.Success(Unit)
    }
}
