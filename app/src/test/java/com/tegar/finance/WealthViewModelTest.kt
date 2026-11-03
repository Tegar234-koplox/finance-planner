package com.tegar.finance

import androidx.lifecycle.ViewModelStore
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.WealthRepository
import com.tegar.finance.presentation.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WealthViewModelTest {
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun model(repo: Source, time: Clock = clock) = WealthViewModel(repo, time).also { store.put("wealth", it) }
    @Test fun newItemParsesExactCurrencyAmountsAndInitialObservationAtomically() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openItem()
        model.editItem(model.state.value.itemEditor!!.copy(name = "House", cost = "10.000,50", amount = "9.000,25"))
        model.saveItem(); advanceUntilIdle()
        assertEquals(Money(1000050), repo.itemDraft!!.acquisitionCost); assertEquals(Money(900025), repo.initial!!.amount)
        assertEquals(LocalDate.of(2028, 3, 10), repo.initial!!.date); assertNull(model.state.value.itemEditor)
        assertNull(repo.itemId); assertEquals(1, repo.calls)
    }
    @Test fun editingMetadataPreservesIdentityAndDoesNotCreateAnotherValue() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openItem(repo.item)
        model.editItem(model.state.value.itemEditor!!.copy(name = "Renamed")); model.saveItem(); advanceUntilIdle()
        assertEquals("asset", repo.itemId); assertEquals("Renamed", repo.itemDraft!!.name); assertNull(repo.initial)
        model.toggleActive(repo.item); advanceUntilIdle()
        assertFalse(repo.itemDraft!!.active); assertNull(repo.initial); assertEquals(2, repo.calls)
    }
    @Test fun invalidDateAndOutOfRangeAmountKeepDraftAndNeverInvokeRepository() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openItem()
        model.editItem(model.state.value.itemEditor!!.copy(name = "Draft", acquired = "2028-02-30", amount = "10")); model.saveItem(); advanceUntilIdle()
        assertEquals(AppError.INVALID_INPUT, model.state.value.error); assertEquals(0, repo.calls)
        model.editItem(model.state.value.itemEditor!!.copy(acquired = "2028-03-01", amount = "99999999999999999999999999")); model.saveItem(); advanceUntilIdle()
        assertEquals(AppError.OUT_OF_RANGE, model.state.value.error); assertEquals("Draft", model.state.value.itemEditor!!.name); assertEquals(0, repo.calls)
    }
    @Test fun failedValueSaveKeepsDateAndAmountThenSuccessfulRetryClosesEditor() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openValue(repo.item)
        model.editValue(model.state.value.valueEditor!!.copy(amount = "1.234,50", notes = "Keep draft"))
        repo.failure = AppError.WEALTH_DATE_CONFLICT; model.saveValue(); advanceUntilIdle()
        assertEquals(AppError.WEALTH_DATE_CONFLICT, model.state.value.error); assertEquals("Keep draft", model.state.value.valueEditor!!.notes)
        repo.failure = null; model.saveValue(); advanceUntilIdle()
        assertEquals(Money(123450), repo.valueDraft!!.amount); assertNull(model.state.value.valueEditor)
    }
    @Test fun busyPreventsDoubleSaveClosingAndOtherWrites() = runTest {
        val repo = Source().apply { gate = CompletableDeferred() }; val model = model(repo); advanceUntilIdle()
        model.openValue(repo.item); model.editValue(model.state.value.valueEditor!!.copy(amount = "0"))
        model.saveValue(); model.saveValue(); model.deleteItem("asset"); model.closeEditors(); runCurrent()
        assertEquals(1, repo.calls); assertTrue(model.state.value.busy); assertNotNull(model.state.value.valueEditor)
        repo.gate!!.complete(Unit); advanceUntilIdle(); assertFalse(model.state.value.busy); assertNull(model.state.value.valueEditor)
    }
    @Test fun readFailureHidesStaleTotalsRetainsDraftAndRetryRestoresSource() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openItem()
        model.editItem(model.state.value.itemEditor!!.copy(name = "Private draft", amount = "1"))
        repo.data.value = AppResult.Failure(AppError.STORAGE); advanceUntilIdle()
        assertNull(model.state.value.overview); assertNull(model.state.value.source); model.saveItem(); advanceUntilIdle()
        assertEquals(0, repo.calls); assertEquals(AppError.STORAGE, model.state.value.error)
        repo.data.value = AppResult.Success(repo.snapshot); model.retry(); advanceUntilIdle()
        assertNotNull(model.state.value.overview); assertEquals("Private draft", model.state.value.itemEditor!!.name)
        repo.data.value = AppResult.Success(null); advanceUntilIdle(); assertTrue(model.state.value.loading); assertNull(model.state.value.overview)
    }
    @Test fun captureFailureCannotClaimSavedAndExplicitDeleteCanBeRetried() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle()
        repo.failure = AppError.SNAPSHOT_EXISTS; model.capture(); advanceUntilIdle()
        assertFalse(model.state.value.captureSaved); assertEquals(AppError.SNAPSHOT_EXISTS, model.state.value.error)
        repo.failure = null; model.deleteCapture("old"); advanceUntilIdle(); assertFalse(model.state.value.captureSaved)
        model.capture(); advanceUntilIdle(); assertTrue(model.state.value.captureSaved)
        model.clearError(); assertFalse(model.state.value.captureSaved); assertNull(model.state.value.error)
    }
    @Test fun refreshAndNewObservationUseLedgerTimezoneAtMidnight() = runTest {
        val time = MutableClock(Instant.parse("2028-03-31T16:59:00Z")); val repo = Source(); val model = model(repo, time); advanceUntilIdle()
        assertEquals(LocalDate.of(2028, 3, 31), model.state.value.overview!!.today)
        time.value = Instant.parse("2028-03-31T17:01:00Z"); model.refresh(); advanceUntilIdle(); model.openValue(repo.item)
        assertEquals(LocalDate.of(2028, 4, 1), model.state.value.overview!!.today); assertEquals("2028-04-01", model.state.value.valueEditor!!.date)
    }
    private class MutableClock(var value: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun instant() = value
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(value, zone)
    }
    private class Source : WealthRepository {
        val item = WealthItem("asset", "House", WealthKind.ASSET, WealthCategory.PROPERTY, LocalDate.of(2028, 1, 1), null, "", true, Instant.EPOCH, null)
        val snapshot = WealthSource(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), LocalDate.of(2028, 1, 1), Money(1000), Instant.EPOCH), emptyList(), listOf(item), emptyList(), emptyList())
        val data = MutableStateFlow<AppResult<WealthSource?>>(AppResult.Success(snapshot))
        var calls = 0; var itemId: String? = null; var itemDraft: WealthItemDraft? = null; var initial: WealthValueDraft? = null
        var valueDraft: WealthValueDraft? = null; var gate: CompletableDeferred<Unit>? = null; var failure: AppError? = null
        override fun observeWealth() = data
        private suspend fun result(): AppResult<String> { calls++; gate?.await(); return failure?.let { AppResult.Failure(it) } ?: AppResult.Success("saved") }
        override suspend fun saveItem(id: String?, draft: WealthItemDraft, initial: WealthValueDraft?): AppResult<String> {
            itemId = id; itemDraft = draft; this.initial = initial; return result()
        }
        override suspend fun saveValue(id: String?, draft: WealthValueDraft): AppResult<String> { valueDraft = draft; return result() }
        override suspend fun captureToday() = result()
        override suspend fun deleteItem(id: String): AppResult<Unit> = when (val r = result()) { is AppResult.Failure -> r; else -> AppResult.Success(Unit) }
        override suspend fun deleteValue(id: String) = deleteItem(id)
        override suspend fun deleteCapture(id: String) = deleteItem(id)
    }
}
