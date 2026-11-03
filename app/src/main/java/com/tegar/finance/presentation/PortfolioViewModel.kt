package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.InvestmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DateTimeException
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HoldingEditor(val id: String? = null, val name: String = "", val symbol: String = "", val bucket: AllocationBucket = AllocationBucket.STOCK,
    val acquired: String, val cost: String = "", val notes: String = "", val active: Boolean = true, val amount: String = "", val valueDate: String)
data class HoldingValueEditor(val id: String? = null, val holdingId: String, val amount: String = "", val date: String, val notes: String = "")
data class InvestmentMovementEditor(val id: String? = null, val action: InvestmentAction = InvestmentAction.CONTRIBUTION,
    val holdingId: String? = null, val amount: String = "", val date: String, val categoryId: String, val note: String = "", val valueAfter: String = "")
data class PortfolioUiState(val loading: Boolean = true, val busy: Boolean = false, val readError: AppError? = null, val error: AppError? = null,
    val source: PortfolioSource? = null, val overview: PortfolioOverview? = null, val holdingEditor: HoldingEditor? = null,
    val valueEditor: HoldingValueEditor? = null, val movementEditor: InvestmentMovementEditor? = null,
    val targetEditor: Map<AllocationBucket, String>? = null, val planEditor: String? = null)

@HiltViewModel
class PortfolioViewModel @Inject constructor(private val repository: InvestmentRepository, private val clock: Clock) : ViewModel() {
    private val mutable = MutableStateFlow(PortfolioUiState())
    val state = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)
    private val month = MutableStateFlow<YearMonth?>(null)
    private var observation: Job? = null
    init { retry() }
    fun refresh() { refreshes.update { it + 1 } }
    fun retry() {
        observation?.cancel(); mutable.update { it.copy(loading = true, readError = null, source = null, overview = null) }
        observation = viewModelScope.launch {
            combine(repository.observePortfolio(), refreshes, month) { data, _, selected -> data to selected }.collect { (data, selected) ->
                if (data is AppResult.Failure) {
                    mutable.update { it.copy(loading = false, readError = data.error, source = null, overview = null) }; return@collect
                }
                val source = (data as AppResult.Success).value
                if (source == null) { mutable.update { it.copy(loading = true, source = null, overview = null) }; return@collect }
                val today = DatePeriods.today(clock, source.ledger.reportingZone)
                when (val result = PortfolioCalculator.calculate(source, today, selected ?: YearMonth.from(today))) {
                    is AppResult.Success -> mutable.update { it.copy(loading = false, readError = null, source = source, overview = result.value) }
                    is AppResult.Failure -> mutable.update { it.copy(loading = false, readError = result.error, source = null, overview = null) }
                }
            }
        }
    }
    private fun money(value: Money?) = value?.decimalAmount()?.toPlainString()?.replace('.', ',').orEmpty()
    private fun fail(error: AppError) { mutable.update { it.copy(error = error) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun selectMonth(text: String) {
        val parsed = BudgetMonths.parse(text) ?: run { fail(AppError.INVALID_INPUT); return }
        if (!state.value.busy) { month.value = parsed; clearError() }
    }
    fun moveMonth(delta: Long) {
        val current = state.value.overview?.progress?.month ?: return
        try { selectMonth(current.plusMonths(delta).toString()) } catch (_: DateTimeException) { fail(AppError.INVALID_INPUT) }
    }
    fun closeEditors() {
        if (!state.value.busy) mutable.update { it.copy(holdingEditor = null, valueEditor = null, movementEditor = null, targetEditor = null, planEditor = null, error = null) }
    }
    fun openHolding(holding: InvestmentHolding? = null) {
        if (state.value.busy) return
        val today = state.value.overview?.today?.toString() ?: return
        closeEditors()
        mutable.update { it.copy(holdingEditor = HoldingEditor(holding?.id, holding?.name.orEmpty(), holding?.symbol.orEmpty(), holding?.bucket ?: AllocationBucket.STOCK,
            holding?.acquiredOn?.toString() ?: today, money(holding?.cost), holding?.notes.orEmpty(), holding?.active ?: true, valueDate = today)) }
    }
    fun editHolding(value: HoldingEditor) { if (!state.value.busy) mutable.update { it.copy(holdingEditor = value, error = null) } }
    fun saveHolding() {
        val editor = state.value.holdingEditor ?: return
        val currency = state.value.source?.ledger?.currency ?: run { fail(AppError.STORAGE); return }
        val acquired = ReportSelection.parseDate(editor.acquired) ?: run { fail(AppError.INVALID_INPUT); return }
        val cost = editor.cost.takeIf(String::isNotBlank)?.let { MoneyText.parse(it, currency) }
        if (cost is AppResult.Failure) { fail(cost.error); return }
        var initial: HoldingValueDraft? = null
        if (editor.id == null) {
            val amount = MoneyText.parse(editor.amount, currency); val date = ReportSelection.parseDate(editor.valueDate)
            if (amount is AppResult.Failure || date == null) { fail((amount as? AppResult.Failure)?.error ?: AppError.INVALID_INPUT); return }
            initial = HoldingValueDraft("", date, (amount as AppResult.Success).value)
        }
        mutate({ repository.saveHolding(editor.id, HoldingDraft(editor.name, editor.symbol, editor.bucket, acquired,
            (cost as? AppResult.Success)?.value, editor.notes, editor.active), initial) }) { closeEditors() }
    }
    fun toggle(holding: InvestmentHolding) = mutate({ repository.saveHolding(holding.id, HoldingDraft(holding.name, holding.symbol, holding.bucket,
        holding.acquiredOn, holding.cost, holding.notes, !holding.active)) })
    fun deleteHolding(id: String) = mutate({ repository.deleteHolding(id) })
    fun openValue(holding: InvestmentHolding, value: HoldingValue? = null) {
        if (state.value.busy) return
        val today = state.value.overview?.today?.toString() ?: return
        closeEditors()
        mutable.update { it.copy(valueEditor = HoldingValueEditor(value?.id, holding.id, money(value?.amount), value?.date?.toString() ?: today, value?.notes.orEmpty())) }
    }
    fun updateValue(holding: InvestmentHolding) {
        val today = state.value.overview?.today ?: return
        openValue(holding, state.value.source?.values?.firstOrNull { it.holdingId == holding.id && it.date == today })
    }
    fun editValue(value: HoldingValueEditor) { if (!state.value.busy) mutable.update { it.copy(valueEditor = value, error = null) } }
    fun saveValue() {
        val editor = state.value.valueEditor ?: return
        val currency = state.value.source?.ledger?.currency ?: run { fail(AppError.STORAGE); return }
        val amount = MoneyText.parse(editor.amount, currency); val date = ReportSelection.parseDate(editor.date)
        if (amount is AppResult.Failure || date == null) { fail((amount as? AppResult.Failure)?.error ?: AppError.INVALID_INPUT); return }
        mutate({ repository.saveValue(editor.id, HoldingValueDraft(editor.holdingId, date, (amount as AppResult.Success).value, editor.notes)) }) { closeEditors() }
    }
    fun deleteValue(id: String) = mutate({ repository.deleteValue(id) })
    fun openTargets() {
        if (state.value.busy || state.value.source == null) return
        closeEditors(); val targets = state.value.source!!.targets.associate { it.bucket to it.basisPoints }
        mutable.update { it.copy(targetEditor = AllocationBucket.entries.associateWith { bucket ->
            java.math.BigDecimal.valueOf((targets[bucket] ?: 0).toLong(), 2).stripTrailingZeros().toPlainString().replace('.', ',')
        }) }
    }
    fun editTarget(bucket: AllocationBucket, text: String) { if (!state.value.busy) mutable.update { it.copy(targetEditor = it.targetEditor?.plus(bucket to text), error = null) } }
    fun saveTargets() {
        val editor = state.value.targetEditor ?: return
        val rows = mutableListOf<AllocationTarget>()
        for (bucket in AllocationBucket.entries) {
            val result = InvestmentRules.percent(editor[bucket].orEmpty())
            if (result is AppResult.Failure) { fail(result.error); return }
            rows += AllocationTarget(bucket, (result as AppResult.Success).value)
        }
        InvestmentRules.validateTargets(rows)?.let { fail(it); return }
        mutate({ repository.saveTargets(rows) }) { closeEditors() }
    }
    fun clearTargets() = mutate({ repository.saveTargets(emptyList()) })
    fun openPlan() {
        if (state.value.busy) return
        val progress = state.value.overview?.progress ?: return
        closeEditors(); mutable.update { it.copy(planEditor = money(progress.target)) }
    }
    fun editPlan(text: String) { if (!state.value.busy) mutable.update { it.copy(planEditor = text, error = null) } }
    fun savePlan() {
        val text = state.value.planEditor ?: return
        val source = state.value.source ?: run { fail(AppError.STORAGE); return }
        val current = state.value.overview?.progress?.month ?: return
        val amount = MoneyText.parse(text, source.ledger.currency)
        if (amount is AppResult.Failure) { fail(amount.error); return }
        mutate({ repository.savePlan(current, (amount as AppResult.Success).value) }) { closeEditors() }
    }
    fun clearPlan() { val current = state.value.overview?.progress?.month ?: return; mutate({ repository.savePlan(current, null) }) }
    fun openMovement(row: FinanceTransaction? = null) {
        if (state.value.busy) return
        val source = state.value.source ?: return
        val today = DatePeriods.today(clock, source.ledger.reportingZone).toString()
        closeEditors()
        mutable.update { it.copy(movementEditor = InvestmentMovementEditor(row?.id, row?.investmentAction ?: InvestmentAction.CONTRIBUTION,
            row?.holdingId, money(row?.amount), row?.date?.toString() ?: today,
            row?.categoryId ?: source.categories.firstOrNull { cat -> cat.active && cat.transactionType == TransactionType.INVESTMENT }?.id.orEmpty(), row?.note.orEmpty(),
            money(source.values.firstOrNull { value -> value.holdingId == row?.holdingId && value.date == row?.date }?.amount))) }
    }
    fun editMovement(value: InvestmentMovementEditor) { if (!state.value.busy) mutable.update { it.copy(movementEditor = value, error = null) } }
    fun saveMovement() {
        val editor = state.value.movementEditor ?: return
        val currency = state.value.source?.ledger?.currency ?: run { fail(AppError.STORAGE); return }
        val amount = MoneyText.parse(editor.amount, currency); val date = ReportSelection.parseDate(editor.date)
        if (amount is AppResult.Failure || date == null) { fail((amount as? AppResult.Failure)?.error ?: AppError.INVALID_INPUT); return }
        val after = editor.holdingId?.let { MoneyText.parse(editor.valueAfter, currency) }
        if (after is AppResult.Failure) { fail(after.error); return }
        mutate({ repository.saveMovement(editor.id, TransactionDraft(TransactionType.INVESTMENT, (amount as AppResult.Success).value, editor.categoryId,
            date, editor.note, editor.action, editor.holdingId), (after as? AppResult.Success)?.value) }) { closeEditors() }
    }
    fun deleteMovement(id: String) = mutate({ repository.deleteMovement(id) })
    private fun <T> mutate(action: suspend () -> AppResult<T>, success: () -> Unit = {}) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = action(); mutable.update { it.copy(busy = false, error = (result as? AppResult.Failure)?.error) }
            if (result is AppResult.Success) success()
        }
    }
}
