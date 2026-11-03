package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.WealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WealthItemEditor(val id: String? = null, val name: String = "", val kind: WealthKind = WealthKind.ASSET,
    val category: WealthCategory = WealthCategory.PERSONAL, val acquired: String = "", val cost: String = "",
    val notes: String = "", val active: Boolean = true, val amount: String = "", val valueDate: String = "")
data class WealthValueEditor(val id: String? = null, val itemId: String, val amount: String = "", val date: String, val notes: String = "")
data class WealthUiState(val loading: Boolean = true, val readError: AppError? = null, val error: AppError? = null,
    val busy: Boolean = false, val source: WealthSource? = null, val overview: WealthOverview? = null,
    val itemEditor: WealthItemEditor? = null, val valueEditor: WealthValueEditor? = null, val captureSaved: Boolean = false)

@HiltViewModel
class WealthViewModel @Inject constructor(private val repository: WealthRepository, private val clock: Clock) : ViewModel() {
    private val mutable = MutableStateFlow(WealthUiState())
    val state = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)
    private var observation: Job? = null
    init { retry() }
    fun refresh() { refreshes.update { it + 1 } }
    fun retry() {
        observation?.cancel(); mutable.update { it.copy(loading = true, readError = null, source = null, overview = null) }
        observation = viewModelScope.launch {
            combine(repository.observeWealth(), refreshes) { data, _ -> data }.collect { data ->
                if (data is AppResult.Failure) {
                    mutable.update { it.copy(loading = false, readError = data.error, source = null, overview = null) }; return@collect
                }
                val source = (data as AppResult.Success).value
                if (source == null) { mutable.update { it.copy(loading = true, readError = null, source = null, overview = null) }; return@collect }
                when (val result = WealthCalculator.calculate(source, DatePeriods.today(clock, source.ledger.reportingZone))) {
                    is AppResult.Success -> mutable.update { it.copy(loading = false, readError = null, source = source, overview = result.value) }
                    is AppResult.Failure -> mutable.update { it.copy(loading = false, readError = result.error, source = null, overview = null) }
                }
            }
        }
    }
    private fun money(value: Money?) = value?.decimalAmount()?.toPlainString()?.replace('.', ',').orEmpty()
    fun openItem(item: WealthItem? = null) {
        if (state.value.busy) return
        val source = state.value.source ?: return
        val today = DatePeriods.today(clock, source.ledger.reportingZone).toString()
        mutable.update { it.copy(error = null, captureSaved = false, valueEditor = null, itemEditor = WealthItemEditor(item?.id,
            item?.name.orEmpty(), item?.kind ?: WealthKind.ASSET, item?.category ?: WealthCategory.PERSONAL,
            item?.acquiredOn?.toString() ?: today, money(item?.acquisitionCost), item?.notes.orEmpty(), item?.active ?: true, valueDate = today)) }
    }
    fun editItem(editor: WealthItemEditor) { if (!state.value.busy) mutable.update { it.copy(itemEditor = editor, error = null) } }
    fun openValue(item: WealthItem, value: WealthValue? = null) {
        if (state.value.busy) return
        val source = state.value.source ?: return
        mutable.update { it.copy(error = null, captureSaved = false, itemEditor = null, valueEditor = WealthValueEditor(value?.id, item.id,
            money(value?.amount), value?.date?.toString() ?: DatePeriods.today(clock, source.ledger.reportingZone).toString(), value?.notes.orEmpty())) }
    }
    fun editValue(editor: WealthValueEditor) { if (!state.value.busy) mutable.update { it.copy(valueEditor = editor, error = null) } }
    fun closeEditors() { if (!state.value.busy) mutable.update { it.copy(itemEditor = null, valueEditor = null, error = null) } }
    fun clearError() { mutable.update { it.copy(error = null, captureSaved = false) } }
    private fun fail(error: AppError) { mutable.update { it.copy(error = error) } }
    fun saveItem() {
        val editor = state.value.itemEditor ?: return
        val currency = state.value.source?.ledger?.currency ?: run { fail(AppError.STORAGE); return }
        val acquired = ReportSelection.parseDate(editor.acquired) ?: run { fail(AppError.INVALID_INPUT); return }
        val costResult = editor.cost.takeIf { it.isNotBlank() && editor.kind == WealthKind.ASSET }?.let { MoneyText.parse(it, currency) }
        if (costResult is AppResult.Failure) { fail(costResult.error); return }
        var initial: WealthValueDraft? = null
        if (editor.id == null) {
            val value = MoneyText.parse(editor.amount, currency)
            val date = ReportSelection.parseDate(editor.valueDate)
            if (value is AppResult.Failure || date == null) { fail((value as? AppResult.Failure)?.error ?: AppError.INVALID_INPUT); return }
            initial = WealthValueDraft("", date, (value as AppResult.Success).value)
        }
        val draft = WealthItemDraft(editor.name, editor.kind, editor.category, acquired,
            (costResult as? AppResult.Success)?.value, editor.notes, editor.active)
        mutate({ repository.saveItem(editor.id, draft, initial) }) { mutable.update { it.copy(itemEditor = null) } }
    }
    fun saveValue() {
        val editor = state.value.valueEditor ?: return
        val currency = state.value.source?.ledger?.currency ?: run { fail(AppError.STORAGE); return }
        val amount = MoneyText.parse(editor.amount, currency); val date = ReportSelection.parseDate(editor.date)
        if (amount is AppResult.Failure || date == null) { fail((amount as? AppResult.Failure)?.error ?: AppError.INVALID_INPUT); return }
        mutate({ repository.saveValue(editor.id, WealthValueDraft(editor.itemId, date, (amount as AppResult.Success).value, editor.notes)) }) {
            mutable.update { it.copy(valueEditor = null) }
        }
    }
    fun toggleActive(item: WealthItem) = mutate({ repository.saveItem(item.id, WealthItemDraft(item.name, item.kind, item.category,
        item.acquiredOn, item.acquisitionCost, item.notes, !item.active)) })
    fun deleteItem(id: String) = mutate({ repository.deleteItem(id) })
    fun deleteValue(id: String) = mutate({ repository.deleteValue(id) })
    fun deleteCapture(id: String) = mutate({ repository.deleteCapture(id) })
    fun capture() = mutate({ repository.captureToday() }) { mutable.update { it.copy(captureSaved = true) } }
    private fun <T> mutate(action: suspend () -> AppResult<T>, success: () -> Unit = {}) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null, captureSaved = false) }
        viewModelScope.launch {
            val result = action()
            mutable.update { it.copy(busy = false, error = (result as? AppResult.Failure)?.error) }
            if (result is AppResult.Success) success()
        }
    }
}
