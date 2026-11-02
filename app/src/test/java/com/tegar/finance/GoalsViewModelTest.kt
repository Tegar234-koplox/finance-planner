package com.tegar.finance

import androidx.lifecycle.ViewModelStore
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.GoalRepository
import com.tegar.finance.presentation.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun model(source: Source, time: Clock = clock) = GoalsViewModel(source, time).also { store.put("goals", it) }

    @Test fun createEditPauseAndDeleteUseExactAmountsAndPreserveIdentity() = runTest {
        val source = Source(); val model = model(source); advanceUntilIdle()
        model.openGoal(); model.edit(model.state.value.editor!!.copy(name = "Phone", kind = GoalKind.PURCHASE,
            target = "10.000,50", earmarked = "5.000,25", priority = GoalPriority.HIGH))
        model.save(); advanceUntilIdle()
        assertNull(model.state.value.editor); assertEquals(1, source.saveCalls)
        val created = model.state.value.plan!!.goals.single().goal
        assertEquals(Money(1000050), created.target); assertEquals(Money(500025), created.earmarked)
        assertEquals(GoalKind.PURCHASE, created.kind); assertEquals(GoalPriority.HIGH, created.priority)
        model.openGoal(created); model.edit(model.state.value.editor!!.copy(name = "Updated phone", status = GoalState.PAUSED))
        model.save(); advanceUntilIdle()
        val edited = model.state.value.plan!!.goals.single().goal
        assertEquals(created.id, edited.id); assertEquals(created.createdAt, edited.createdAt)
        assertEquals(GoalState.PAUSED, edited.state)
        model.delete(edited.id); advanceUntilIdle()
        assertTrue(model.state.value.plan!!.goals.isEmpty()); assertEquals(1, source.deleteCalls)
        assertEquals(source.snapshot.transactions, (source.data.value as AppResult.Success).value!!.transactions)
    }
    @Test fun invalidDateOrAmountNeverInvokesRepositoryAndKeepsDraft() = runTest {
        val source = Source(); val model = model(source); advanceUntilIdle(); model.openGoal()
        model.edit(model.state.value.editor!!.copy(name = "Draft", target = "100", targetDate = "2028-02-30"))
        model.save(); advanceUntilIdle()
        assertEquals(AppError.INVALID_INPUT, model.state.value.error); assertEquals(0, source.saveCalls)
        assertEquals("Draft", model.state.value.editor!!.name)
        model.edit(model.state.value.editor!!.copy(targetDate = "2028-06-30", target = "999999999999999999999999"))
        model.save(); advanceUntilIdle()
        assertEquals(AppError.OUT_OF_RANGE, model.state.value.error); assertEquals(0, source.saveCalls)
    }
    @Test fun repositoryRejectionPreservesEditorAndRetryCanSaveCorrectedDraft() = runTest {
        val source = Source(); val model = model(source); advanceUntilIdle(); model.openGoal()
        model.edit(model.state.value.editor!!.copy(name = "Trip", target = "0")); model.save(); advanceUntilIdle()
        assertEquals(AppError.INVALID_INPUT, model.state.value.error); assertNotNull(model.state.value.editor)
        model.edit(model.state.value.editor!!.copy(target = "100")); model.save(); advanceUntilIdle()
        assertNull(model.state.value.error); assertNull(model.state.value.editor)
        assertEquals(Money(10000), model.state.value.plan!!.goals.single().goal.target)
    }
    @Test fun busyGuardsPreventDoubleSaveAndClosingAnInFlightEditor() = runTest {
        val source = Source().apply { gate = CompletableDeferred() }; val model = model(source)
        advanceUntilIdle(); model.openGoal(); model.edit(model.state.value.editor!!.copy(name = "One", target = "10"))
        model.save(); model.save(); model.closeEditor(); runCurrent()
        assertTrue(model.state.value.busy); assertNotNull(model.state.value.editor); assertEquals(1, source.saveCalls)
        source.gate!!.complete(Unit); advanceUntilIdle()
        assertFalse(model.state.value.busy); assertEquals(1, source.saveCalls)
        assertEquals(1, model.state.value.plan!!.goals.size)
    }
    @Test fun readFailureClearsDatesAndRetryRetainsDraftWithoutWritingStaleData() = runTest {
        val source = Source(); val model = model(source); advanceUntilIdle(); model.openGoal()
        model.edit(model.state.value.editor!!.copy(name = "Private draft", target = "50"))
        source.data.value = AppResult.Failure(AppError.STORAGE); advanceUntilIdle()
        assertNull(model.state.value.plan); assertEquals(AppError.STORAGE, model.state.value.readError)
        model.save(); advanceUntilIdle(); assertEquals(0, source.saveCalls)
        assertEquals(AppError.STORAGE, model.state.value.error)
        assertEquals("Private draft", model.state.value.editor!!.name)
        source.data.value = AppResult.Success(source.snapshot); model.retry(); advanceUntilIdle()
        assertNull(model.state.value.readError); assertNotNull(model.state.value.plan)
        assertEquals("Private draft", model.state.value.editor!!.name)
    }
    @Test fun missingLedgerIsLoadingAndNotAZeroMoneyPlan() = runTest {
        val source = Source(); val model = model(source); advanceUntilIdle()
        source.data.value = AppResult.Success(null); advanceUntilIdle()
        assertTrue(model.state.value.loading); assertNull(model.state.value.plan)
        model.openGoal(); assertNull(model.state.value.editor)
    }
    @Test fun forecastOptionsAreAppliedToTheWholePlanAndInvalidWindowsIgnored() = runTest {
        val source = Source(); val model = model(source); advanceUntilIdle()
        model.useForecastOptions(ForecastOptions(ForecastMethod.WEIGHTED, 12)); advanceUntilIdle()
        assertEquals(ForecastOptions(ForecastMethod.WEIGHTED, 12), model.state.value.plan!!.options)
        model.useForecastOptions(ForecastOptions(window = 5)); advanceUntilIdle()
        assertEquals(ForecastOptions(ForecastMethod.WEIGHTED, 12), model.state.value.plan!!.options)
        assertEquals(0, source.saveCalls); assertEquals(0, source.deleteCalls)
    }
    @Test fun refreshAndNewGoalUseFreshLedgerZoneDateAcrossMonthEnd() = runTest {
        val time = MutableClock(Instant.parse("2028-03-31T16:59:00Z"))
        val source = Source(); val model = model(source, time); advanceUntilIdle()
        assertEquals(LocalDate.of(2028, 3, 31), model.state.value.plan!!.today)
        assertEquals(2, model.state.value.plan!!.sampleCount)
        time.value = Instant.parse("2028-03-31T17:01:00Z"); model.refresh(); advanceUntilIdle(); model.openGoal()
        assertEquals(LocalDate.of(2028, 4, 1), model.state.value.plan!!.today)
        assertEquals(3, model.state.value.plan!!.sampleCount); assertEquals("2028-04-01", model.state.value.editor!!.asOf)
        assertEquals("2028-07-31", model.state.value.editor!!.targetDate)
    }
    private class MutableClock(var value: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun instant(): Instant = value
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(value, zone)
    }
    private class Source : GoalRepository {
        val snapshot = BudgetSnapshot(Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), LocalDate.of(2028, 1, 1), Money(1000), Instant.EPOCH),
            emptyList(), emptyList(), emptyList())
        val data = MutableStateFlow<AppResult<BudgetSnapshot?>>(AppResult.Success(snapshot))
        var saveCalls = 0; var deleteCalls = 0; var gate: CompletableDeferred<Unit>? = null
        override fun observeGoals() = data
        override suspend fun saveGoal(id: String?, draft: GoalDraft): AppResult<String> {
            saveCalls++; gate?.await()
            val oldSource = (data.value as AppResult.Success).value!!
            GoalRules.validate(draft, oldSource.ledger, LocalDate.of(2028, 3, 10))?.let { return AppResult.Failure(it) }
            val old = oldSource.goals.firstOrNull { it.id == id }; val key = id ?: "saved-$saveCalls"
            val goal = FinancialGoal(key, GoalRules.normalizeName(draft.name), draft.kind, draft.target, draft.earmarked,
                draft.asOf, draft.targetDate, draft.priority, draft.state, old?.createdAt ?: Instant.EPOCH, old?.let { Instant.EPOCH.plusSeconds(1) })
            data.value = AppResult.Success(oldSource.copy(goals = oldSource.goals.filter { it.id != key } + goal))
            return AppResult.Success(key)
        }
        override suspend fun deleteGoal(id: String): AppResult<Unit> {
            deleteCalls++; val old = (data.value as AppResult.Success).value!!
            data.value = AppResult.Success(old.copy(goals = old.goals.filter { it.id != id })); return AppResult.Success(Unit)
        }
    }
}
