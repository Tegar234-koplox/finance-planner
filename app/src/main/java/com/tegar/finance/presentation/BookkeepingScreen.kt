package com.tegar.finance.presentation

import com.tegar.finance.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*

@Composable internal fun TransactionType.label() = when (this) {
    TransactionType.INCOME -> stringResource(R.string.ui_income_51595d)
    TransactionType.EXPENSE -> stringResource(R.string.ui_expense_a0417b)
    TransactionType.INVESTMENT -> stringResource(R.string.ui_investment_contribution_14704e)
}
@Composable internal fun FinanceTransaction.label() = if (type == TransactionType.INVESTMENT) (investmentAction ?: InvestmentAction.CONTRIBUTION).label() else type.label()
@Composable private fun AppError.message() = when (this) {
    AppError.ALLOCATION_TOTAL -> stringResource(R.string.portfolio_error_targets)
    AppError.INVESTMENT_IN_USE -> stringResource(R.string.portfolio_error_in_use)
    AppError.INVALID_INPUT -> stringResource(R.string.ui_check_the_amount_date_yyyy_mm_dd_date_range_name_1_80_characters_and_n_031125)
    AppError.OUT_OF_RANGE -> stringResource(R.string.ui_this_amount_is_too_large_890084)
    AppError.CURRENCY_MISMATCH -> stringResource(R.string.ui_the_amount_must_use_the_ledger_currency_c869fa)
    AppError.DATE_BEFORE_COVERAGE -> stringResource(R.string.ui_the_transaction_date_must_be_on_or_after_the_ledger_s_tracked_from_dat_f16e84)
    AppError.CATEGORY_MISMATCH -> stringResource(R.string.ui_choose_a_category_matching_the_transaction_type_existing_category_type_c5caea)
    AppError.CATEGORY_INACTIVE -> stringResource(R.string.ui_this_category_is_archived_restore_it_or_choose_an_active_category_acbb4b)
    AppError.CATEGORY_IN_USE -> stringResource(R.string.ui_this_category_has_transactions_or_budgets_archive_it_to_preserve_histo_7f0961)
    AppError.BUDGET_LIMIT_EXCEEDED -> stringResource(R.string.ui_category_allocations_cannot_exceed_the_overall_budget_5ed1a5)
    AppError.DUPLICATE_CATEGORY -> stringResource(R.string.ui_a_category_with_this_name_and_type_already_exists_including_archived_c_3b240b)
    AppError.NOT_FOUND -> stringResource(R.string.ui_this_record_no_longer_exists_reload_the_list_f9cdbf)
    AppError.WEALTH_INCOMPLETE -> stringResource(R.string.wealth_error_incomplete)
    AppError.WEALTH_DATE_CONFLICT -> stringResource(R.string.wealth_error_conflict)
    AppError.SNAPSHOT_EXISTS -> stringResource(R.string.wealth_snapshot_exists)
    AppError.STORAGE -> stringResource(R.string.ui_the_change_could_not_be_saved_on_this_device_try_again_524950)
}

@Composable
fun BookkeepingScreen(state: BookkeepingState, model: BookkeepingViewModel, categoryMode: Boolean) {
    var deletingTransaction by remember { mutableStateOf<FinanceTransaction?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    when {
        state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
        state.loadFailed -> Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.ui_records_could_not_be_loaded_f028b7))
            Button(onClick = model::retry) { Text(stringResource(R.string.ui_try_again_d8b839)) }
        }
        else -> LazyColumn(Modifier.fillMaxSize().imePadding().testTag("records_list"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(if (categoryMode) stringResource(R.string.ui_categories_b8b1d8) else stringResource(R.string.ui_transactions_3e5136), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.ui_stored_only_on_this_device_44053f), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { if (categoryMode) model.openCategory() else model.openTransaction() },
                    enabled = !state.busy, modifier = Modifier.testTag("add_record")) {
                    Text(if (categoryMode) stringResource(R.string.ui_add_category_aafdf4) else stringResource(R.string.ui_add_transaction_6502c0))
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.error != null && state.transactionEditor == null && state.categoryEditor == null) item {
                Text(state.error.message(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("record_error"))
                TextButton(onClick = model::clearError) { Text(stringResource(R.string.ui_dismiss_48845b)) }
            }
            if (categoryMode) {
                item { Text(stringResource(R.string.ui_rename_archive_or_restore_categories_type_is_fixed_after_creation_only_8ea1ac)) }
                items(state.categories, key = { it.id }) { category ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                            Text(category.transactionType.label() + if (category.active) "" else stringResource(R.string.ui_archived_c089d2))
                            Row {
                                TextButton(onClick = { model.openCategory(category) }, enabled = !state.busy) { Text(stringResource(R.string.ui_rename_3064d7)) }
                                TextButton(onClick = { model.setCategoryActive(category) }, enabled = !state.busy) {
                                    Text(if (category.active) stringResource(R.string.ui_archive_66f480) else stringResource(R.string.ui_restore_a76e13))
                                }
                                TextButton(onClick = { deletingCategory = category }, enabled = !state.busy) { Text(stringResource(R.string.ui_delete_e2d0a5)) }
                            }
                        }
                    }
                }
            } else {
                item { Filters(state, model::applyFilter) }
                item {
                    Text(if (state.transactions.isEmpty()) stringResource(R.string.ui_no_transactions_match_these_filters_c5f787) else
                        stringResource(R.string.ui_value_transaction_s_0120d6, state.transactions.size), modifier = Modifier.testTag("history_count"))
                }
                items(state.transactions, key = { it.id }) { transaction ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("transaction_${transaction.id}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(MoneyText.format(transaction.amount), style = MaterialTheme.typography.titleLarge)
                            Text("${transaction.label()} · ${transaction.date}")
                            val category = state.categories.firstOrNull { it.id == transaction.categoryId }
                            Text((category?.name ?: stringResource(R.string.ui_category_unavailable_da9371)) + if (category?.active == false) stringResource(R.string.ui_archived_c089d2) else "")
                            if (transaction.note.isNotEmpty()) Text(transaction.note)
                            Row {
                                TextButton(onClick = { model.openTransaction(transaction) }, enabled = !state.busy,
                                    modifier = Modifier.testTag("edit_${transaction.id}")) { Text(stringResource(R.string.ui_edit_464c4f)) }
                                TextButton(onClick = { deletingTransaction = transaction }, enabled = !state.busy,
                                    modifier = Modifier.testTag("delete_${transaction.id}")) { Text(stringResource(R.string.ui_delete_e2d0a5)) }
                            }
                        }
                    }
                }
            }
        }
    }
    state.transactionEditor?.let { editor -> TransactionForm(editor, state, model) }
    state.categoryEditor?.let { editor ->
        AlertDialog(onDismissRequest = model::closeEditors, title = { Text(if (editor.id == null) stringResource(R.string.ui_add_category_aafdf4) else stringResource(R.string.ui_rename_category_897993)) },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(editor.name, { model.editCategory(editor.copy(name = it)) }, label = { Text(stringResource(R.string.ui_category_name_73ddd9)) },
                    enabled = !state.busy, singleLine = true, modifier = Modifier.testTag("category_name"))
                Choice(stringResource(R.string.ui_type_baaddf), editor.type.label(), if (editor.id == null) TransactionType.entries.map { it.name to it.label() } else emptyList()) {
                    model.editCategory(editor.copy(type = TransactionType.valueOf(it)))
                }
                state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = model::saveCategory, enabled = !state.busy) { Text(stringResource(R.string.ui_save_category_5f2622)) } },
            dismissButton = { TextButton(onClick = model::closeEditors, enabled = !state.busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
    }
    deletingTransaction?.let { row ->
        ConfirmDelete(stringResource(R.string.ui_delete_transaction_a61cf3), stringResource(if (row.type == TransactionType.INVESTMENT) R.string.portfolio_movement_delete_body else R.string.ui_this_permanently_removes_this_transaction_ce239b), {
            model.deleteTransaction(row.id); deletingTransaction = null
        }, { deletingTransaction = null })
    }
    deletingCategory?.let { row ->
        ConfirmDelete(stringResource(R.string.ui_delete_category_2b9e08), stringResource(R.string.ui_delete_value_categories_used_by_transactions_must_be_archived_instead_653c9d, row.name), {
            model.deleteCategory(row.id); deletingCategory = null
        }, { deletingCategory = null })
    }
}

@Composable
private fun Filters(state: BookkeepingState, apply: (String, String, TransactionType?, String?) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf(state.filter.from?.toString().orEmpty()) }
    var through by rememberSaveable { mutableStateOf(state.filter.through?.toString().orEmpty()) }
    var type by rememberSaveable { mutableStateOf(state.filter.type?.name.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(state.filter.categoryId.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) stringResource(R.string.ui_hide_filters_9e6ea4) else stringResource(R.string.ui_show_filters_4594a3)) }
        Text(stringResource(R.string.ui_applied_value_to_value_ac520d, state.filter.from ?: stringResource(R.string.ui_any_date_a4717b), state.filter.through ?: stringResource(R.string.ui_any_date_a4717b)) +
            (state.filter.type?.label() ?: stringResource(R.string.ui_all_types_f10988)) + " · " +
            (state.categories.firstOrNull { it.id == state.filter.categoryId }?.name
                ?: if (state.filter.categoryId == null) stringResource(R.string.ui_all_categories_9d5097) else stringResource(R.string.ui_deleted_category_clear_filters_098da3)))
        if (expanded) {
            OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.ui_from_yyyy_mm_dd_optional_3db29d)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("filter_from"))
            OutlinedTextField(through, { through = it }, label = { Text(stringResource(R.string.ui_through_yyyy_mm_dd_optional_f69da7)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("filter_through"))
            Choice(stringResource(R.string.ui_type_baaddf), TransactionType.entries.firstOrNull { it.name == type }?.label() ?: stringResource(R.string.ui_all_types_f10988),
                listOf("" to stringResource(R.string.ui_all_types_f10988)) + TransactionType.entries.map { it.name to it.label() }) { type = it; category = "" }
            Choice(stringResource(R.string.ui_category_292c06), state.categories.firstOrNull { it.id == category }?.name
                ?: if (category.isEmpty()) stringResource(R.string.ui_all_categories_9d5097) else stringResource(R.string.ui_deleted_category_151951),
                listOf("" to stringResource(R.string.ui_all_categories_9d5097)) + state.categories.filter { type.isEmpty() || it.transactionType.name == type }
                    .map { it.id to (it.name + if (it.active) "" else stringResource(R.string.ui_archived_c089d2)) }) { category = it }
            Row {
                Button(onClick = { keyboard?.hide(); apply(from, through, type.takeIf(String::isNotEmpty)?.let(TransactionType::valueOf), category.takeIf(String::isNotEmpty)) }) { Text(stringResource(R.string.ui_apply_filters_d80ab1)) }
                TextButton(onClick = { keyboard?.hide(); from = ""; through = ""; type = ""; category = ""; apply("", "", null, null) }) { Text(stringResource(R.string.ui_clear_filters_7179ea)) }
            }
        }
    }
}

@Composable
private fun TransactionForm(editor: TransactionEditor, state: BookkeepingState, model: BookkeepingViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    val available = state.categories.filter { it.transactionType == editor.type && (it.active || it.id == editor.categoryId) }
    AlertDialog(onDismissRequest = model::closeEditors, title = { Text(if (editor.id == null) stringResource(R.string.ui_add_transaction_6502c0) else stringResource(R.string.ui_edit_transaction_c71e88)) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice(stringResource(R.string.ui_type_baaddf), editor.type.label(), TransactionType.entries.map { it.name to it.label() }) {
                val type = TransactionType.valueOf(it)
                model.editTransaction(editor.copy(type = type,
                    categoryId = state.categories.firstOrNull { cat -> cat.active && cat.transactionType == type }?.id.orEmpty()))
            }
            if (editor.type == TransactionType.INVESTMENT) Choice(stringResource(R.string.portfolio_activity), editor.investmentAction.label(), InvestmentAction.entries.map { it.name to it.label() }) {
                model.editTransaction(editor.copy(investmentAction = InvestmentAction.valueOf(it)))
            }
            OutlinedTextField(editor.amount, { model.editTransaction(editor.copy(amount = it)) }, enabled = !state.busy,
                label = { Text(stringResource(R.string.ui_amount_idr_28ca9d)) }, supportingText = { Text(stringResource(R.string.ui_example_25_000_50_must_be_positive_c11fff)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true, modifier = Modifier.testTag("transaction_amount"))
            Choice(stringResource(R.string.ui_category_292c06), available.firstOrNull { it.id == editor.categoryId }?.let { it.name + if (it.active) "" else stringResource(R.string.ui_archived_c089d2) } ?: stringResource(R.string.ui_choose_a_category_42d2f2),
                available.map { it.id to it.name }) { model.editTransaction(editor.copy(categoryId = it)) }
            if (available.isEmpty()) Text(stringResource(R.string.ui_create_or_restore_a_category_for_this_type_in_categories_5d96e7))
            OutlinedTextField(editor.date, { model.editTransaction(editor.copy(date = it)) }, enabled = !state.busy,
                label = { Text(stringResource(R.string.ui_date_yyyy_mm_dd_4a4059)) }, singleLine = true,
                supportingText = { Text(stringResource(R.string.ui_tracked_from_value_a8ca54, state.ledger?.trackedFromDate?.toString().orEmpty())) },
                modifier = Modifier.testTag("transaction_date"))
            OutlinedTextField(editor.note, { model.editTransaction(editor.copy(note = it)) }, enabled = !state.busy,
                label = { Text(stringResource(R.string.ui_note_optional_f9b73e)) }, supportingText = { Text("${editor.note.length}/500") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier.testTag("transaction_note"))
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("editor_error")) }
        } },
        confirmButton = { TextButton(onClick = { keyboard?.hide(); model.saveTransaction() }, enabled = !state.busy) { Text(stringResource(R.string.ui_save_transaction_a08199)) } },
        dismissButton = { TextButton(onClick = model::closeEditors, enabled = !state.busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
}

@Composable
private fun Choice(label: String, selected: String, choices: List<Pair<String, String>>, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = choices.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) },
                onClick = { expanded = false; choose(id) }) }
        }
    }
}

@Composable
private fun ConfirmDelete(title: String, body: String, confirm: () -> Unit, cancel: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = confirm) { Text(stringResource(R.string.ui_delete_permanently_280b10)) } },
        dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.ui_cancel_19766e)) } })
}
