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
class ReportsViewModelTest {
    private val store=ViewModelStore()
    private val clock=Clock.fixed(Instant.parse("2028-02-10T05:00:00Z"),ZoneOffset.UTC)
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun vm(source: Source,time: Clock=clock)=ReportsViewModel(source,source,time).also { store.put("reports",it) }
    @Test fun kindDateAndNavigationChangeOnlyReadOnlyReportSelection() = runTest {
        val source=Source(); val vm=vm(source); advanceUntilIdle()
        vm.selectKind(ReportKind.WEEK); advanceUntilIdle()
        assertEquals(ReportKind.WEEK,vm.state.value.report!!.selection.kind)
        vm.selectDate("2028-02-29"); advanceUntilIdle()
        assertEquals(LocalDate.of(2028,2,29),vm.state.value.report!!.selection.date)
        vm.move(1); advanceUntilIdle()
        assertEquals(LocalDate.of(2028,3,6),vm.state.value.report!!.selection.date)
    }
    @Test fun invalidInputPreservesAppliedPeriodAndCorrectionRecovers() = runTest {
        val vm=vm(Source()); advanceUntilIdle()
        val selected=vm.state.value.report!!.selection
        vm.selectDate("2028-02-30"); advanceUntilIdle()
        assertTrue(vm.state.value.inputError); assertEquals(selected,vm.state.value.report!!.selection)
        vm.selectDate("2028-01-01"); advanceUntilIdle()
        assertFalse(vm.state.value.inputError); assertEquals(LocalDate.of(2028,1,1),vm.state.value.report!!.selection.date)
    }
    @Test fun recordAndCategoryChangesUpdateTotalsAndNames() = runTest {
        val source=Source(); val vm=vm(source); advanceUntilIdle()
        source.data.value=AppResult.Success(source.snapshot.copy(transactions=listOf(source.row(100))))
        source.cats.value=AppResult.Success(listOf(Category("food","Archived food",TransactionType.EXPENSE,false))); advanceUntilIdle()
        assertEquals(100L,vm.state.value.report!!.current.totals!!.consumption.minorUnits)
        assertEquals("Archived food",vm.state.value.categories["food"]!!.name)
        source.data.value=AppResult.Success(source.snapshot); advanceUntilIdle()
        assertEquals(0L,vm.state.value.report!!.current.totals!!.consumption.minorUnits)
    }
    @Test fun readFailureAndRetryDoNotExposeOldChartsOrTotals() = runTest {
        val source=Source(); val vm=vm(source); advanceUntilIdle()
        source.data.value=AppResult.Failure(AppError.STORAGE); advanceUntilIdle()
        assertEquals(AppError.STORAGE,vm.state.value.error); assertNull(vm.state.value.report)
        source.data.value=AppResult.Success(source.snapshot); vm.retry(); advanceUntilIdle()
        assertNull(vm.state.value.error); assertNotNull(vm.state.value.report)
    }
    @Test fun missingLedgerAndOverflowRemainDistinctFromZeroReports() = runTest {
        val source=Source(); val vm=vm(source); advanceUntilIdle()
        source.data.value=AppResult.Success(null); advanceUntilIdle()
        assertTrue(vm.state.value.loading); assertNull(vm.state.value.report)
        source.data.value=AppResult.Success(source.snapshot.copy(transactions=listOf(source.row(Long.MAX_VALUE),source.row(1))))
        advanceUntilIdle()
        assertEquals(AppError.OUT_OF_RANGE,vm.state.value.error); assertNull(vm.state.value.report)
    }
    @Test fun todayReadsFreshLedgerZoneDateAcrossMidnightWithoutPriorRefresh() = runTest {
        val source=Source()
        val clock=MutableClock(Instant.parse("2028-02-29T16:59:00Z"))
        val vm=vm(source,clock); advanceUntilIdle()
        vm.selectDate("2020-01-01"); advanceUntilIdle()
        clock.value=Instant.parse("2028-02-29T17:01:00Z")
        vm.today(); advanceUntilIdle()
        assertEquals(LocalDate.of(2028,3,1),vm.state.value.report!!.selection.date)
        assertEquals(LocalDate.of(2028,3,1),vm.state.value.report!!.asOf)
    }
    private class MutableClock(var value: Instant): Clock() {
        override fun getZone(): ZoneId=ZoneOffset.UTC
        override fun instant(): Instant=value
        override fun withZone(zone: ZoneId): Clock=Clock.fixed(value,zone)
    }
    private class Source: BudgetRepository,CategoryRepository {
        val snapshot=BudgetSnapshot(Ledger(CurrencyCode.IDR,ZoneId.of("Asia/Jakarta"),LocalDate.of(2027,1,1),Money(0),Instant.EPOCH),
            emptyList(),emptyList(),emptyList())
        val data=MutableStateFlow<AppResult<BudgetSnapshot?>>(AppResult.Success(snapshot))
        val cats=MutableStateFlow<AppResult<List<Category>>>(AppResult.Success(listOf(Category("food","Food",TransactionType.EXPENSE,true))))
        fun row(amount: Long)=FinanceTransaction("row-$amount",TransactionType.EXPENSE,Money(amount),"food",LocalDate.of(2028,2,10),"",Instant.EPOCH,null)
        override fun observeBudgets()=data
        override fun observeCategories()=cats
        override suspend fun saveMonthly(month: YearMonth,allocation: Money): AppResult<Unit> = error("Reports must be read-only")
        override suspend fun saveCategory(month: YearMonth,categoryId: String,allocation: Money): AppResult<Unit> = error("Reports must be read-only")
        override suspend fun deleteMonthly(month: YearMonth): AppResult<Unit> = error("Reports must be read-only")
        override suspend fun deleteCategory(month: YearMonth,categoryId: String): AppResult<Unit> = error("Reports must be read-only")
    }
}
