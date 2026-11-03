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
class BudgetViewModelTest {
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2028-02-01T05:00:00Z"), ZoneOffset.UTC)
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun vm(source: Source) = BudgetViewModel(source, source, clock).also { store.put("budget", it) }
    @Test fun actualsFollowSourceChangesAndExcludeIncome() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        source.emit(listOf(source.row(100), source.row(999, TransactionType.INCOME))); advanceUntilIdle()
        assertEquals(100L, vm.state.value.overall!!.actual!!.minorUnits)
        assertEquals(9900L, vm.state.value.overall!!.remaining!!.minorUnits)
        source.emit(emptyList()); advanceUntilIdle()
        assertEquals(0L, vm.state.value.overall!!.actual!!.minorUnits)
    }
    @Test fun failedSaveKeepsEditorAndSuccessfulRetryClosesIt() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        vm.openOverall(); vm.updateEditor(vm.state.value.editor!!.copy(amount = "25.000,50"))
        source.failure = AppError.BUDGET_LIMIT_EXCEEDED; vm.save(); advanceUntilIdle()
        assertEquals(AppError.BUDGET_LIMIT_EXCEEDED, vm.state.value.error); assertNotNull(vm.state.value.editor)
        source.failure = null; vm.save(); advanceUntilIdle()
        assertNull(vm.state.value.editor); assertEquals(2500050L, source.lastAmount!!.minorUnits)
    }
    @Test fun invalidMonthAndAmountNeverReplaceSelectionOrReachStorage() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        vm.selectMonth("2028-13"); advanceUntilIdle()
        assertEquals(YearMonth.of(2028, 2), vm.state.value.month)
        assertEquals(AppError.INVALID_INPUT, vm.state.value.error)
        vm.openOverall(); vm.updateEditor(vm.state.value.editor!!.copy(amount = "1e9")); vm.save(); advanceUntilIdle()
        assertEquals(0, source.writes); assertNotNull(vm.state.value.editor)
    }
    @Test fun duplicateSubmitAndMonthChangesAreBlockedWhileWriting() = runTest {
        val source = Source(); val gate = CompletableDeferred<Unit>(); source.gate = gate
        val vm = vm(source); advanceUntilIdle(); vm.openOverall()
        vm.save(); runCurrent(); vm.save(); runCurrent(); vm.closeEditor(); vm.selectMonth("2028-03")
        assertEquals(1, source.writes); assertTrue(vm.state.value.busy); assertNotNull(vm.state.value.editor)
        assertEquals(YearMonth.of(2028, 2), vm.state.value.month)
        gate.complete(Unit); advanceUntilIdle(); assertFalse(vm.state.value.busy); assertNull(vm.state.value.editor)
    }
    @Test fun storageFailureHidesStaleTotalsAndRetryRecovers() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        source.data.value = AppResult.Failure(AppError.STORAGE); advanceUntilIdle()
        assertEquals(AppError.STORAGE, vm.state.value.readError); assertNull(vm.state.value.overall)
        source.emit(emptyList()); vm.retry(); advanceUntilIdle()
        assertNull(vm.state.value.readError); assertEquals(0L, vm.state.value.overall!!.actual!!.minorUnits)
    }
    private class Source : BudgetRepository, CategoryRepository {
        private val month = YearMonth.of(2028, 2)
        private val ledger = Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), month.atDay(1), Money(0), Instant.EPOCH)
        private val snapshot = BudgetSnapshot(ledger, listOf(MonthlyBudget("overall", month, Money(10000))), emptyList(), emptyList())
        val data = MutableStateFlow<AppResult<BudgetSnapshot?>>(AppResult.Success(snapshot))
        var failure: AppError? = null; var gate: CompletableDeferred<Unit>? = null
        var writes = 0; var lastAmount: Money? = null
        fun row(amount: Long, type: TransactionType = TransactionType.EXPENSE) =
            FinanceTransaction("tx-$type", type, Money(amount), "food", month.atDay(1), "", Instant.EPOCH, null)
        fun emit(records: List<FinanceTransaction>) { data.value = AppResult.Success(snapshot.copy(transactions = records)) }
        override fun observeBudgets() = data
        override fun observeCategories(): Flow<AppResult<List<Category>>> =
            flowOf(AppResult.Success(listOf(Category("food", "Food", TransactionType.EXPENSE, true))))
        override suspend fun saveMonthly(month: YearMonth, allocation: Money): AppResult<Unit> {
            writes++; lastAmount = allocation; gate?.await()
            return failure?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
        }
        override suspend fun saveCategory(month: YearMonth, categoryId: String, allocation: Money): AppResult<Unit> = saveMonthly(month, allocation)
        override suspend fun deleteMonthly(month: YearMonth): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun deleteCategory(month: YearMonth, categoryId: String): AppResult<Unit> = AppResult.Success(Unit)
    }
}
