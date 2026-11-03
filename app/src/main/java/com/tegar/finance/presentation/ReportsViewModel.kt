package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DateTimeException
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReportsUiState(val loading: Boolean = true, val error: AppError? = null,
    val inputError: Boolean = false, val report: FinanceReport? = null,
    val categories: Map<String, Category> = emptyMap())

@HiltViewModel
class ReportsViewModel @Inject constructor(private val source: BudgetRepository,
    private val categories: CategoryRepository, private val clock: Clock) : ViewModel() {
    private val mutable = MutableStateFlow(ReportsUiState())
    val state = mutable.asStateFlow()
    private val selection = MutableStateFlow<ReportSelection?>(null)
    private val refreshes = MutableStateFlow(0)
    private var observation: Job? = null
    private var ledgerZone: ZoneId? = null
    init { retry() }
    fun retry() {
        observation?.cancel()
        mutable.update { it.copy(loading = true, error = null, report = null) }
        observation = viewModelScope.launch {
            combine(source.observeBudgets(), categories.observeCategories(), selection, refreshes) { data, cats, chosen, _ ->
                Triple(data, cats, chosen)
            }.collect { (data, cats, chosen) ->
                if (data is AppResult.Failure || cats is AppResult.Failure) {
                    mutable.update { it.copy(loading = false, error = AppError.STORAGE, report = null) }
                    return@collect
                }
                val snapshot = (data as AppResult.Success).value
                if (snapshot == null) { mutable.update { it.copy(loading = true, error = null, report = null) }; return@collect }
                val today = DatePeriods.today(clock, snapshot.ledger.reportingZone)
                ledgerZone = snapshot.ledger.reportingZone
                val current = chosen ?: ReportSelection(ReportKind.MONTH, today)
                when (val result = ReportCalculator.calculate(snapshot, current, today)) {
                    is AppResult.Failure -> mutable.update { it.copy(loading = false, error = result.error, report = null) }
                    is AppResult.Success -> mutable.update { it.copy(loading = false, error = null, report = result.value,
                        categories = (cats as AppResult.Success).value.associateBy { it.id }) }
                }
            }
        }
    }
    fun refresh() { refreshes.update { it + 1 } }
    fun selectDate(text: String) {
        val date = ReportSelection.parseDate(text)
        if (date == null) { mutable.update { it.copy(inputError = true) }; return }
        choose(ReportSelection(selection.value?.kind ?: state.value.report?.selection?.kind ?: ReportKind.MONTH, date))
    }
    fun selectKind(kind: ReportKind) {
        val current = selection.value ?: state.value.report?.selection ?: return
        choose(current.copy(kind = kind))
    }
    fun move(offset: Long) {
        val current = selection.value ?: state.value.report?.selection ?: return
        val moved = try { current.move(offset) } catch (_: DateTimeException) { null }
            catch (_: ArithmeticException) { null }
        if (moved == null || !moved.valid) { mutable.update { it.copy(inputError = true) }; return }
        choose(moved)
    }
    fun today() {
        val report = state.value.report ?: return
        val today = DatePeriods.today(clock, ledgerZone ?: return)
        choose(report.selection.copy(date = today))
        refresh()
    }
    private fun choose(value: ReportSelection) {
        selection.value = value
        mutable.update { it.copy(inputError = false) }
        refresh()
    }
}
