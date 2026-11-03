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
class BookkeepingViewModelTest {
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-06T18:00:00Z"), ZoneOffset.UTC)
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun vm(source: Source) = BookkeepingViewModel(source, source, source, clock).also { store.put("records", it) }
    @Test fun failedWriteKeepsEditorAndRetryClosesOnlyAfterCommit() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        vm.openTransaction()
        assertEquals("2026-10-07", vm.state.value.transactionEditor!!.date)
        vm.editTransaction(vm.state.value.transactionEditor!!.copy(amount = "25.000,50", note = "Lunch"))
        source.failure = AppError.STORAGE; vm.saveTransaction(); advanceUntilIdle()
        assertEquals(AppError.STORAGE, vm.state.value.error)
        assertEquals("Lunch", vm.state.value.transactionEditor!!.note)
        source.failure = null; vm.saveTransaction(); advanceUntilIdle()
        assertNull(vm.state.value.transactionEditor)
        assertEquals(2500050L, source.lastDraft!!.amount.minorUnits)
    }
    @Test fun malformedAmountAndDateNeverReachStorage() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        vm.openTransaction()
        vm.editTransaction(vm.state.value.transactionEditor!!.copy(amount = "1e9"))
        vm.saveTransaction(); advanceUntilIdle()
        assertEquals(AppError.INVALID_INPUT, vm.state.value.error); assertEquals(0, source.writes)
        vm.editTransaction(vm.state.value.transactionEditor!!.copy(amount = "1", date = "2026-02-30"))
        vm.saveTransaction(); advanceUntilIdle()
        assertEquals(AppError.INVALID_INPUT, vm.state.value.error); assertEquals(0, source.writes)
    }
    @Test fun badFiltersPreserveLastAppliedFilterAndClearRestoresAll() = runTest {
        val vm = vm(Source()); advanceUntilIdle()
        vm.applyFilter("2026-10-07", "2026-10-09", TransactionType.INCOME, null)
        val valid = vm.state.value.filter
        vm.applyFilter("2026-10-10", "2026-10-09", null, null)
        assertEquals(valid, vm.state.value.filter); assertEquals(AppError.INVALID_INPUT, vm.state.value.error)
        vm.applyFilter("2026-02-30", "", null, null)
        assertEquals(valid, vm.state.value.filter)
        vm.applyFilter("", "", null, null)
        assertEquals(TransactionFilter(), vm.state.value.filter); assertNull(vm.state.value.error)
    }
    @Test fun repeatedSaveWhileWritingDoesNotCreateDuplicateTransactions() = runTest {
        val source = Source(); val gate = CompletableDeferred<Unit>(); source.gate = gate
        val vm = vm(source); advanceUntilIdle(); vm.openTransaction()
        vm.editTransaction(vm.state.value.transactionEditor!!.copy(amount = "100"))
        vm.saveTransaction(); runCurrent(); vm.saveTransaction(); runCurrent()
        assertTrue(vm.state.value.busy); assertEquals(1, source.writes)
        vm.closeEditors(); assertNotNull(vm.state.value.transactionEditor)
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(vm.state.value.busy); assertNull(vm.state.value.transactionEditor)
    }
    @Test fun observationFailureIsVisibleAndRetryRecovers() = runTest {
        val source = Source(); val vm = vm(source); advanceUntilIdle()
        source.cats.value = AppResult.Failure(AppError.STORAGE); advanceUntilIdle()
        assertTrue(vm.state.value.loadFailed)
        source.cats.value = AppResult.Success(listOf(source.category)); vm.retry(); advanceUntilIdle()
        assertFalse(vm.state.value.loadFailed); assertFalse(vm.state.value.loading)
    }
    private class Source : TransactionRepository, EditableCategoryRepository, LedgerRepository {
        val category = Category("food", "Food", TransactionType.EXPENSE, true)
        val cats = MutableStateFlow<AppResult<List<Category>>>(AppResult.Success(listOf(category)))
        var failure: AppError? = null
        var gate: CompletableDeferred<Unit>? = null
        var writes = 0
        var lastDraft: TransactionDraft? = null
        override fun observeCategories() = cats
        override fun observeTransactions(filter: TransactionFilter): Flow<AppResult<List<FinanceTransaction>>> =
            flowOf(AppResult.Success(emptyList()))
        override fun observeLedger(): Flow<AppResult<Ledger?>> = flowOf(AppResult.Success(Ledger(CurrencyCode.IDR,
            ZoneId.of("Asia/Jakarta"), LocalDate.of(2026, 10, 7), Money(0), Instant.EPOCH)))
        override suspend fun initialize(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun saveTransaction(id: String?, draft: TransactionDraft): AppResult<String> {
            writes++; lastDraft = draft; gate?.await()
            return failure?.let { AppResult.Failure(it) } ?: AppResult.Success(id ?: "saved")
        }
        override suspend fun deleteTransaction(id: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun saveCategory(id: String?, name: String, type: TransactionType): AppResult<String> = AppResult.Success(id ?: "new")
        override suspend fun setCategoryActive(id: String, active: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun deleteCategory(id: String): AppResult<Unit> = AppResult.Success(Unit)
    }
}
