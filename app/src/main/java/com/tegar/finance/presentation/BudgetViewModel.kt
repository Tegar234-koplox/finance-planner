package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BudgetRow(val category: Category, val metrics: BudgetMetrics)
data class BudgetEditor(val month: YearMonth, val categoryId: String? = null, val amount: String = "")
data class BudgetUiState(
    val month: YearMonth, val today: LocalDate, val loading: Boolean = true, val readError: AppError? = null,
    val snapshot: BudgetSnapshot? = null, val categories: List<Category> = emptyList(),
    val overall: BudgetMetrics? = null, val rows: List<BudgetRow> = emptyList(),
    val editor: BudgetEditor? = null, val busy: Boolean = false, val error: AppError? = null,
)

@HiltViewModel
class BudgetViewModel @Inject constructor(private val budgets: BudgetRepository,
    private val categories: CategoryRepository, private val clock: Clock) : ViewModel() {
    private val mutable = MutableStateFlow(BudgetUiState(YearMonth.from(DatePeriods.today(clock)), DatePeriods.today(clock)))
    val state = mutable.asStateFlow()
    private val selected = MutableStateFlow<YearMonth?>(null)
    private val refreshes = MutableStateFlow(0)
    private var observation: Job? = null
    init { retry() }
    fun refresh() { refreshes.update { it + 1 } }
    fun retry() {
        observation?.cancel()
        mutable.update { it.copy(loading = true, readError = null) }
        observation = viewModelScope.launch {
            combine(budgets.observeBudgets(), categories.observeCategories(), selected, refreshes) { data, cats, month, _ ->
                Triple(data, cats, month)
            }.collect { (data, cats, month) ->
                if (data is AppResult.Failure || cats is AppResult.Failure) {
                    mutable.update { it.copy(loading = false, readError = AppError.STORAGE, overall = null, rows = emptyList()) }
                    return@collect
                }
                val snapshot = (data as AppResult.Success).value ?: return@collect
                val allCategories = (cats as AppResult.Success).value
                val today = DatePeriods.today(clock, snapshot.ledger.reportingZone)
                val target = month ?: YearMonth.from(today)
                val allocation = snapshot.monthly.firstOrNull { it.month == target }?.allocation
                fun calculate(amount: Money?, id: String? = null) = BudgetCalculator.calculate(target, today,
                    snapshot.ledger.trackedFromDate, snapshot.ledger.currency, amount, snapshot.transactions, id)
                val overall = calculate(allocation)
                val rows = mutableListOf<BudgetRow>()
                var error = (overall as? AppResult.Failure)?.error
                for (category in allCategories.filter { it.transactionType == TransactionType.EXPENSE }) {
                    val amount = snapshot.categories.firstOrNull { it.month == target && it.categoryId == category.id }?.allocation
                    when (val result = calculate(amount, category.id)) {
                        is AppResult.Failure -> error = result.error
                        is AppResult.Success -> if (amount != null || (result.value.actual?.minorUnits ?: 0) > 0)
                            rows += BudgetRow(category, result.value)
                    }
                }
                mutable.update { it.copy(month = target, today = today, loading = false, readError = error,
                    snapshot = snapshot, categories = allCategories,
                    overall = if (error == null) (overall as AppResult.Success).value else null,
                    rows = if (error == null) rows else emptyList()) }
            }
        }
    }
    fun selectMonth(value: String) {
        if (state.value.busy || state.value.editor != null) return
        val month = BudgetMonths.parse(value)
        if (month == null) { mutable.update { it.copy(error = AppError.INVALID_INPUT) }; return }
        selected.value = month
        mutable.update { it.copy(error = null) }
        refresh()
    }
    fun moveMonth(offset: Long) {
        val next = state.value.month.plusMonths(offset)
        if (BudgetMonths.valid(next)) selectMonth(next.toString())
    }
    fun openOverall() {
        val current = state.value
        val amount = current.snapshot?.monthly?.firstOrNull { it.month == current.month }?.allocation
        mutable.update { it.copy(editor = BudgetEditor(current.month, amount = input(amount)), error = null) }
    }
    fun openCategory(id: String? = null) {
        val current = state.value
        val categoryId = id ?: current.categories.firstOrNull { it.active && it.transactionType == TransactionType.EXPENSE }?.id ?: return
        val amount = current.snapshot?.categories?.firstOrNull { it.month == current.month && it.categoryId == categoryId }?.allocation
        mutable.update { it.copy(editor = BudgetEditor(current.month, categoryId, input(amount)), error = null) }
    }
    fun changeCategory(id: String) {
        val editor = state.value.editor ?: return
        val amount = state.value.snapshot?.categories?.firstOrNull { it.month == editor.month && it.categoryId == id }?.allocation
        updateEditor(editor.copy(categoryId = id, amount = input(amount)))
    }
    fun updateEditor(editor: BudgetEditor) { if (!state.value.busy) mutable.update { it.copy(editor = editor, error = null) } }
    fun closeEditor() { if (!state.value.busy) mutable.update { it.copy(editor = null, error = null) } }
    fun save() {
        val editor = state.value.editor ?: return
        val currency = state.value.snapshot?.ledger?.currency ?: return
        val amount = MoneyText.parse(editor.amount, currency)
        if (amount is AppResult.Failure) { mutable.update { it.copy(error = amount.error) }; return }
        mutate({
            if (editor.categoryId == null) budgets.saveMonthly(editor.month, (amount as AppResult.Success).value)
            else budgets.saveCategory(editor.month, editor.categoryId, (amount as AppResult.Success).value)
        }) { mutable.update { it.copy(editor = null) } }
    }
    fun delete(categoryId: String?) {
        val month = state.value.month
        mutate({ if (categoryId == null) budgets.deleteMonthly(month) else budgets.deleteCategory(month, categoryId) })
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private fun input(amount: Money?) = amount?.decimalAmount()?.toPlainString()?.replace('.', ',').orEmpty()
    private fun mutate(operation: suspend () -> AppResult<Unit>, success: () -> Unit = {}) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = operation()
            mutable.update { it.copy(busy = false, error = (result as? AppResult.Failure)?.error) }
            if (result is AppResult.Success) success()
        }
    }
}
