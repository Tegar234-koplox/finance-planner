package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionEditor(
    val id: String? = null, val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "", val categoryId: String = "", val date: String = "", val note: String = "",
    val investmentAction: InvestmentAction = InvestmentAction.CONTRIBUTION, val holdingId: String? = null,
)
data class CategoryEditor(val id: String? = null, val name: String = "", val type: TransactionType = TransactionType.EXPENSE)
data class BookkeepingState(
    val loading: Boolean = true, val loadFailed: Boolean = false, val busy: Boolean = false,
    val ledger: Ledger? = null, val transactions: List<FinanceTransaction> = emptyList(),
    val categories: List<Category> = emptyList(), val filter: TransactionFilter = TransactionFilter(),
    val transactionEditor: TransactionEditor? = null, val categoryEditor: CategoryEditor? = null,
    val error: AppError? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookkeepingViewModel @Inject constructor(
    private val transactions: TransactionRepository, private val categories: EditableCategoryRepository,
    private val ledger: LedgerRepository, private val clock: Clock,
) : ViewModel() {
    private val mutable = MutableStateFlow(BookkeepingState())
    val state = mutable.asStateFlow()
    private val filter = MutableStateFlow(TransactionFilter())
    private var observation: Job? = null
    init { retry() }
    fun retry() {
        observation?.cancel()
        mutable.update { it.copy(loading = true, loadFailed = false) }
        observation = viewModelScope.launch {
            combine(ledger.observeLedger(), categories.observeCategories(),
                filter.flatMapLatest { transactions.observeTransactions(it) }) { account, cats, records ->
                Triple(account, cats, records)
            }.collect { (account, cats, records) ->
                val current = (account as? AppResult.Success)?.value
                val failed = account is AppResult.Failure || cats is AppResult.Failure || records is AppResult.Failure
                mutable.update { it.copy(ledger = current, loading = current == null && !failed, loadFailed = failed,
                    categories = (cats as? AppResult.Success)?.value ?: it.categories,
                    transactions = (records as? AppResult.Success)?.value ?: it.transactions) }
            }
        }
    }
    fun applyFilter(from: String, through: String, type: TransactionType?, categoryId: String?) {
        val next = try { TransactionFilter(from.takeIf(String::isNotBlank)?.let(LocalDate::parse),
            through.takeIf(String::isNotBlank)?.let(LocalDate::parse), type, categoryId) }
        catch (_: DateTimeException) { mutable.update { it.copy(error = AppError.INVALID_INPUT) }; return }
        if (!next.valid) { mutable.update { it.copy(error = AppError.INVALID_INPUT) }; return }
        mutable.update { it.copy(filter = next, error = null) }
        filter.value = next
    }
    fun openTransaction(row: FinanceTransaction? = null) {
        val current = state.value
        val type = row?.type ?: TransactionType.EXPENSE
        mutable.update { it.copy(error = null, transactionEditor = TransactionEditor(
            row?.id, type, row?.amount?.decimalAmount()?.toPlainString()?.replace('.', ',') ?: "",
            row?.categoryId ?: current.categories.firstOrNull { cat -> cat.active && cat.transactionType == type }?.id.orEmpty(),
            (row?.date ?: LocalDate.now(clock.withZone(current.ledger?.reportingZone ?: ZoneId.of("Asia/Jakarta")))).toString(),
            row?.note.orEmpty(), row?.investmentAction ?: InvestmentAction.CONTRIBUTION, row?.holdingId)) }
    }
    fun editTransaction(value: TransactionEditor) { if (!state.value.busy) mutable.update { it.copy(transactionEditor = value, error = null) } }
    fun openCategory(row: Category? = null) { mutable.update { it.copy(error = null,
        categoryEditor = CategoryEditor(row?.id, row?.name.orEmpty(), row?.transactionType ?: TransactionType.EXPENSE)) } }
    fun editCategory(value: CategoryEditor) { if (!state.value.busy) mutable.update { it.copy(categoryEditor = value, error = null) } }
    fun closeEditors() { if (!state.value.busy) mutable.update { it.copy(transactionEditor = null, categoryEditor = null, error = null) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun saveTransaction() {
        val editor = state.value.transactionEditor ?: return
        val account = state.value.ledger ?: return
        val amount = MoneyText.parse(editor.amount, account.currency)
        if (amount is AppResult.Failure) { mutable.update { it.copy(error = amount.error) }; return }
        val date = try { LocalDate.parse(editor.date) }
        catch (_: DateTimeException) { mutable.update { it.copy(error = AppError.INVALID_INPUT) }; return }
        mutate({ transactions.saveTransaction(editor.id, TransactionDraft(editor.type,
            (amount as AppResult.Success).value, editor.categoryId, date, editor.note,
            editor.investmentAction.takeIf { editor.type == TransactionType.INVESTMENT && it == InvestmentAction.REDEMPTION },
            editor.holdingId.takeIf { editor.type == TransactionType.INVESTMENT })) }) {
            mutable.update { it.copy(transactionEditor = null) }
        }
    }
    fun saveCategory() {
        val editor = state.value.categoryEditor ?: return
        mutate({ categories.saveCategory(editor.id, editor.name, editor.type) }) { mutable.update { it.copy(categoryEditor = null) } }
    }
    fun deleteTransaction(id: String) = mutate({ transactions.deleteTransaction(id) })
    fun deleteCategory(id: String) = mutate({ categories.deleteCategory(id) })
    fun setCategoryActive(row: Category) = mutate({ categories.setCategoryActive(row.id, !row.active) })
    private fun <T> mutate(operation: suspend () -> AppResult<T>, success: () -> Unit = {}) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = operation()
            mutable.update { it.copy(busy = false, error = (result as? AppResult.Failure)?.error) }
            if (result is AppResult.Success) success()
        }
    }
}
