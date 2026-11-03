package com.tegar.finance

import androidx.lifecycle.ViewModelStore
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.InvestmentRepository
import com.tegar.finance.presentation.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModelTest {
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun model(repo: Source, time: Clock = clock) = PortfolioViewModel(repo, time).also { store.put("portfolio", it) }
    @Test fun initialHoldingValueAndCostParseExactlyWithoutDoubleCounting() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openHolding()
        model.editHolding(model.state.value.holdingEditor!!.copy(name = "Fund", cost = "10.000,50", amount = "9.000,25")); model.saveHolding(); advanceUntilIdle()
        assertEquals(Money(1000050), repo.holdingDraft!!.cost); assertEquals(Money(900025), repo.initial!!.amount)
        assertNull(model.state.value.holdingEditor); assertEquals(1, repo.calls)
    }
    @Test fun invalidDatesAndAmountsNeverReachRepositoryAndKeepDraft() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openHolding()
        model.editHolding(model.state.value.holdingEditor!!.copy(name = "Draft", acquired = "2028-02-30", amount = "1")); model.saveHolding(); advanceUntilIdle()
        assertEquals(AppError.INVALID_INPUT, model.state.value.error); assertEquals(0, repo.calls)
        model.editHolding(model.state.value.holdingEditor!!.copy(acquired = "2028-03-01", amount = "9999999999999999999999999")); model.saveHolding(); advanceUntilIdle()
        assertEquals(AppError.OUT_OF_RANGE, model.state.value.error); assertEquals("Draft", model.state.value.holdingEditor!!.name)
    }
    @Test fun allocationTargetsRejectIncompleteTotalBeforeWritingAndAcceptExactFractions() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openTargets()
        model.editTarget(AllocationBucket.CASH, "99,99"); model.saveTargets(); advanceUntilIdle()
        assertEquals(AppError.ALLOCATION_TOTAL, model.state.value.error); assertEquals(0, repo.calls)
        model.editTarget(AllocationBucket.CASH, "87,50"); model.editTarget(AllocationBucket.FUND, "12,50"); model.saveTargets(); advanceUntilIdle()
        assertEquals(10000, repo.targets!!.sumOf { it.basisPoints }); assertEquals(1250, repo.targets!!.first { it.bucket == AllocationBucket.FUND }.basisPoints)
        assertNull(model.state.value.targetEditor)
    }
    @Test fun linkedRedemptionRequiresExplicitPostFlowBalanceAndPreservesAction() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openMovement()
        model.editMovement(model.state.value.movementEditor!!.copy(action = InvestmentAction.REDEMPTION, amount = "500", holdingId = repo.holding.id))
        model.saveMovement(); advanceUntilIdle(); assertEquals(0, repo.calls); assertNotNull(model.state.value.movementEditor)
        model.editMovement(model.state.value.movementEditor!!.copy(valueAfter = "250")); model.saveMovement(); advanceUntilIdle()
        assertEquals(InvestmentAction.REDEMPTION, repo.movement!!.investmentAction); assertEquals(Money(50000), repo.movement!!.amount)
        assertEquals(Money(25000), repo.after); assertEquals(repo.holding.id, repo.movement!!.holdingId); assertNull(model.state.value.movementEditor)
    }
    @Test fun writeFailureKeepsValueEditorAndRetryClosesIt() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openValue(repo.holding)
        model.editValue(model.state.value.valueEditor!!.copy(amount = "0", notes = "Keep")); repo.failure = AppError.WEALTH_DATE_CONFLICT
        model.saveValue(); advanceUntilIdle(); assertEquals("Keep", model.state.value.valueEditor!!.notes)
        assertEquals(AppError.WEALTH_DATE_CONFLICT, model.state.value.error)
        repo.failure = null; model.saveValue(); advanceUntilIdle(); assertNull(model.state.value.valueEditor)
    }
    @Test fun busyGuardsPreventDuplicateWritesAndClosingAnInFlightForm() = runTest {
        val repo = Source().apply { gate = CompletableDeferred() }; val model = model(repo); advanceUntilIdle(); model.openPlan(); model.editPlan("0")
        model.savePlan(); model.savePlan(); model.closeEditors(); runCurrent()
        assertTrue(model.state.value.busy); assertEquals("0", model.state.value.planEditor); assertEquals(1, repo.calls)
        repo.gate!!.complete(Unit); advanceUntilIdle(); assertNull(model.state.value.planEditor); assertFalse(model.state.value.busy)
    }
    @Test fun readFailureHidesStalePortfolioAndRetryRetainsDraftWithoutUsingOldCurrency() = runTest {
        val repo = Source(); val model = model(repo); advanceUntilIdle(); model.openHolding(); model.editHolding(model.state.value.holdingEditor!!.copy(name = "Draft", amount = "1"))
        repo.data.value = AppResult.Failure(AppError.STORAGE); advanceUntilIdle(); assertNull(model.state.value.source); assertNull(model.state.value.overview)
        model.saveHolding(); advanceUntilIdle(); assertEquals(0, repo.calls); assertEquals(AppError.STORAGE, model.state.value.error)
        repo.data.value = AppResult.Success(repo.snapshot); model.retry(); advanceUntilIdle(); assertNotNull(model.state.value.overview)
        assertEquals("Draft", model.state.value.holdingEditor!!.name)
    }
    @Test fun selectingMonthsAndMidnightRefreshUseTheLedgerTimezone() = runTest {
        val time = MutableClock(Instant.parse("2028-03-31T16:59:00Z")); val repo = Source(); val model = model(repo, time); advanceUntilIdle()
        assertEquals(YearMonth.of(2028, 3), model.state.value.overview!!.progress.month)
        time.value = Instant.parse("2028-03-31T17:01:00Z"); model.refresh(); advanceUntilIdle(); model.openMovement()
        assertEquals("2028-04-01", model.state.value.movementEditor!!.date); assertEquals(YearMonth.of(2028, 4), model.state.value.overview!!.progress.month)
        model.closeEditors(); model.selectMonth("2028-02"); advanceUntilIdle(); assertEquals(YearMonth.of(2028, 2), model.state.value.overview!!.progress.month)
        model.selectMonth("2028-13"); advanceUntilIdle(); assertEquals(AppError.INVALID_INPUT, model.state.value.error); assertEquals(YearMonth.of(2028, 2), model.state.value.overview!!.progress.month)
    }
    @Test fun updateTodayReusesTheObservationWhileExplicitHistoryEntryRemainsSeparate() = runTest {
        val repo = Source()
        val value = HoldingValue("today-value", repo.holding.id, LocalDate.of(2028, 3, 10), Money(12345), "Existing", Instant.EPOCH, null)
        repo.data.value = AppResult.Success(repo.snapshot.copy(values = listOf(value)))
        val model = model(repo); advanceUntilIdle(); model.updateValue(repo.holding)
        assertEquals("today-value", model.state.value.valueEditor!!.id); assertEquals("123,45", model.state.value.valueEditor!!.amount)
        model.closeEditors(); model.openValue(repo.holding)
        assertNull(model.state.value.valueEditor!!.id); assertEquals(0, repo.calls)
    }
    private class MutableClock(var value: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun instant() = value
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(value, zone)
    }
    private class Source : InvestmentRepository {
        val holding = InvestmentHolding("fund", "Fund", "", AllocationBucket.FUND, LocalDate.of(2028, 1, 1), null, "", true, Instant.EPOCH, null)
        val snapshot = PortfolioSource(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), LocalDate.of(2028, 1, 1), Money(1000), Instant.EPOCH), emptyList(), listOf(holding), emptyList(), emptyList(), emptyList(), listOf(Category("cat", "Invest", TransactionType.INVESTMENT, true)))
        val data = MutableStateFlow<AppResult<PortfolioSource?>>(AppResult.Success(snapshot))
        var calls = 0; var failure: AppError? = null; var gate: CompletableDeferred<Unit>? = null
        var holdingDraft: HoldingDraft? = null; var initial: HoldingValueDraft? = null; var movement: TransactionDraft? = null
        var after: Money? = null; var targets: List<AllocationTarget>? = null
        override fun observePortfolio() = data
        private suspend fun result(): AppResult<String> { calls++; gate?.await(); return failure?.let { AppResult.Failure(it) } ?: AppResult.Success("saved") }
        override suspend fun saveHolding(id: String?, draft: HoldingDraft, initial: HoldingValueDraft?): AppResult<String> { holdingDraft = draft; this.initial = initial; return result() }
        override suspend fun saveValue(id: String?, draft: HoldingValueDraft) = result()
        override suspend fun saveMovement(id: String?, draft: TransactionDraft, valueAfter: Money?): AppResult<String> { movement = draft; after = valueAfter; return result() }
        private suspend fun unit(): AppResult<Unit> = when (val r = result()) { is AppResult.Failure -> r; else -> AppResult.Success(Unit) }
        override suspend fun deleteHolding(id: String) = unit()
        override suspend fun deleteValue(id: String) = unit()
        override suspend fun deleteMovement(id: String) = unit()
        override suspend fun saveTargets(targets: List<AllocationTarget>): AppResult<Unit> { this.targets = targets; return unit() }
        override suspend fun savePlan(month: YearMonth, target: Money?) = unit()
    }
}
