package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GoalEditor(val id: String? = null, val name: String = "", val kind: GoalKind = GoalKind.SAVING,
    val target: String = "", val earmarked: String = "0", val asOf: String = "", val targetDate: String = "",
    val priority: GoalPriority = GoalPriority.NORMAL, val status: GoalState = GoalState.ACTIVE)
data class GoalsUiState(val loading: Boolean = true, val readError: AppError? = null, val error: AppError? = null,
    val busy: Boolean = false, val ledger: Ledger? = null, val plan: GoalPlan? = null, val editor: GoalEditor? = null)

@HiltViewModel
class GoalsViewModel @Inject constructor(private val repository: GoalRepository, private val clock: Clock) : ViewModel() {
    private val mutable = MutableStateFlow(GoalsUiState())
    val state = mutable.asStateFlow()
    private val options = MutableStateFlow(ForecastOptions())
    private val refreshes = MutableStateFlow(0)
    private var observation: Job? = null
    init { retry() }
    fun useForecastOptions(value: ForecastOptions) { if (value.valid) options.value = value }
    fun refresh() { refreshes.update { it + 1 } }
    fun retry() {
        observation?.cancel()
        mutable.update { it.copy(loading = true, readError = null, plan = null) }
        observation = viewModelScope.launch {
            combine(repository.observeGoals(), options, refreshes) { source, chosen, _ -> source to chosen }
                .collect { (source, chosen) ->
                    if (source is AppResult.Failure) {
                        mutable.update { it.copy(loading = false, readError = source.error, plan = null, ledger = null) }
                        return@collect
                    }
                    val snapshot = (source as AppResult.Success).value
                    if (snapshot == null) {
                        mutable.update { it.copy(loading = true, readError = null, plan = null, ledger = null) }
                        return@collect
                    }
                    when (val result = GoalPlanner.calculate(snapshot, DatePeriods.today(clock, snapshot.ledger.reportingZone), chosen)) {
                        is AppResult.Success -> mutable.update { it.copy(loading = false, readError = null, ledger = snapshot.ledger, plan = result.value) }
                        is AppResult.Failure -> mutable.update { it.copy(loading = false, readError = result.error, plan = null, ledger = snapshot.ledger) }
                    }
                }
        }
    }
    fun openGoal(goal: FinancialGoal? = null) {
        if (state.value.busy) return
        val ledger = state.value.ledger ?: return
        val today = DatePeriods.today(clock, ledger.reportingZone)
        val proposed = YearMonth.from(today).plusMonths(3).atEndOfMonth().takeIf { it.year <= 9999 } ?: LocalDate.of(9999, 12, 31)
        fun amount(value: Money?) = value?.decimalAmount()?.toPlainString()?.replace('.', ',')
        mutable.update { it.copy(error = null, editor = GoalEditor(goal?.id, goal?.name.orEmpty(), goal?.kind ?: GoalKind.SAVING,
            amount(goal?.target).orEmpty(), amount(goal?.earmarked) ?: "0", (goal?.asOf ?: today).toString(),
            (goal?.targetDate ?: proposed).toString(), goal?.priority ?: GoalPriority.NORMAL, goal?.state ?: GoalState.ACTIVE)) }
    }
    fun edit(value: GoalEditor) { if (!state.value.busy) mutable.update { it.copy(editor = value, error = null) } }
    fun closeEditor() { if (!state.value.busy) mutable.update { it.copy(editor = null, error = null) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun save() {
        val editor = state.value.editor ?: return
        val ledger = state.value.ledger ?: run { mutable.update { it.copy(error = AppError.STORAGE) }; return }
        val target = MoneyText.parse(editor.target, ledger.currency)
        val earmarked = MoneyText.parse(editor.earmarked, ledger.currency)
        val asOf = ReportSelection.parseDate(editor.asOf)
        val deadline = ReportSelection.parseDate(editor.targetDate)
        val parseError = (target as? AppResult.Failure)?.error ?: (earmarked as? AppResult.Failure)?.error
        if (parseError != null || asOf == null || deadline == null) {
            mutable.update { it.copy(error = parseError ?: AppError.INVALID_INPUT) }; return
        }
        mutate({ repository.saveGoal(editor.id, GoalDraft(editor.name, editor.kind, (target as AppResult.Success).value,
            (earmarked as AppResult.Success).value, asOf, deadline, editor.priority, editor.status)) }) {
            mutable.update { it.copy(editor = null) }
        }
    }
    fun delete(id: String) = mutate({ repository.deleteGoal(id) })
    private fun <T> mutate(action: suspend () -> AppResult<T>, success: () -> Unit = {}) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = action()
            mutable.update { it.copy(busy = false, error = (result as? AppResult.Failure)?.error) }
            if (result is AppResult.Success) success()
        }
    }
}
