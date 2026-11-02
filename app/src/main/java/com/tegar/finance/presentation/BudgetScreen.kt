package com.tegar.finance.presentation

import com.tegar.finance.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*

@Composable private fun budgetError(error: AppError) = when (error) {
    AppError.BUDGET_LIMIT_EXCEEDED -> stringResource(R.string.ui_category_allocations_cannot_exceed_the_overall_budget_increase_it_or_r_43ae34)
    AppError.CATEGORY_INACTIVE -> stringResource(R.string.ui_this_category_is_archived_restore_it_before_adding_a_new_allocation_d41812)
    AppError.CATEGORY_MISMATCH -> stringResource(R.string.ui_choose_an_expense_category_88cfec)
    AppError.CURRENCY_MISMATCH -> stringResource(R.string.ui_use_the_ledger_currency_4edc7f)
    AppError.OUT_OF_RANGE -> stringResource(R.string.ui_this_amount_or_calculated_total_exceeds_the_supported_range_no_estimat_ff1301)
    AppError.INVALID_INPUT -> stringResource(R.string.ui_enter_a_valid_month_yyyy_mm_and_a_nonnegative_idr_amount_such_as_500_0_d7ddb1)
    AppError.NOT_FOUND -> stringResource(R.string.ui_this_allocation_no_longer_exists_refresh_the_month_13536d)
    else -> stringResource(R.string.ui_budgets_could_not_be_read_or_saved_try_again_38a2f8)
}
@Composable private fun BudgetStatus.label() = when (this) {
    BudgetStatus.NO_BUDGET -> stringResource(R.string.ui_no_allocation_d12537)
    BudgetStatus.NO_DATA -> stringResource(R.string.ui_not_available_67a926)
    BudgetStatus.NEUTRAL -> stringResource(R.string.ui_neutral_1c6ac6)
    BudgetStatus.SAFE -> stringResource(R.string.ui_safe_4f9f0d)
    BudgetStatus.WATCH -> stringResource(R.string.ui_watch_80_7722db)
    BudgetStatus.HIGH -> stringResource(R.string.ui_high_95_be0044)
    BudgetStatus.OVER_BUDGET -> stringResource(R.string.ui_over_budget_82d5e1)
}

@Composable
fun BudgetScreen(state: BudgetUiState, model: BudgetViewModel) {
    LifecycleResumeEffect(Unit) { model.refresh(); onPauseOrDispose { } }
    var monthText by rememberSaveable(state.month) { mutableStateOf(state.month.toString()) }
    // A restored input draft must not override the reporting month requested by Home.
    LaunchedEffect(state.month) { monthText = state.month.toString() }
    var deleting by remember { mutableStateOf<Pair<String?, String>?>(null) }
    val overallBudgetLabel = stringResource(R.string.ui_overall_budget_ece81d)
    val keyboard = LocalSoftwareKeyboardController.current
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("budget_list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(R.string.ui_monthly_budgets_c9c385), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.ui_as_of_value_ledger_reporting_date_2486ef, state.today))
            OutlinedTextField(monthText, { monthText = it }, label = { Text(stringResource(R.string.ui_month_yyyy_mm_70e660)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("budget_month"))
            Row {
                TextButton(onClick = { model.moveMonth(-1) }, enabled = !state.busy) { Text(stringResource(R.string.ui_previous_a57b08)) }
                Button(onClick = { keyboard?.hide(); model.selectMonth(monthText) }, enabled = !state.busy) { Text(stringResource(R.string.ui_open_month_2b285f)) }
                TextButton(onClick = { model.moveMonth(1) }, enabled = !state.busy) { Text(stringResource(R.string.ui_next_1ff57a)) }
            }
            TextButton(onClick = model::refresh) { Text(stringResource(R.string.ui_refresh_0e9161)) }
            Text(stringResource(R.string.ui_income_and_investment_contributions_are_excluded_all_recorded_consumpt_7f969c))
        }
        if (state.loading) item { CircularProgressIndicator() }
        if (state.readError != null) item {
            Text(budgetError(state.readError), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = model::retry) { Text(stringResource(R.string.ui_try_again_d8b839)) }
        }
        if (state.error != null && state.editor == null) item {
            Text(budgetError(state.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("budget_error"))
            TextButton(onClick = model::clearError) { Text(stringResource(R.string.ui_dismiss_48845b)) }
        }
        if (state.snapshot != null) {
            item {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick = model::openOverall, enabled = !state.busy) { Text(stringResource(R.string.ui_set_overall_budget_f19f0e)) }
                OutlinedButton(onClick = { model.openCategory() }, enabled = !state.busy &&
                    state.categories.any { it.active && it.transactionType == TransactionType.EXPENSE }) { Text(stringResource(R.string.ui_add_category_budget_0b7f25)) }
                if (state.snapshot.monthly.none { it.month == state.month } && state.snapshot.categories.none { it.month == state.month })
                    Text(stringResource(R.string.ui_no_budgets_yet_e76acb))
                Text(stringResource(R.string.ui_a_budget_allowance_is_not_available_cash_cash_limits_and_reservations_758a54))
            }
            state.overall?.let { metrics -> item {
                MetricsCard(stringResource(R.string.ui_overall_715b14), metrics, "overall",
                    edit = model::openOverall,
                    delete = if (metrics.allocation == null) null else { { deleting = null to overallBudgetLabel } },
                    enabled = !state.busy)
            } }
            item {
                Text(stringResource(R.string.ui_categories_b8b1d8), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.ui_category_allocations_share_the_overall_cap_when_one_is_set_removing_th_d66d9e))
                if (state.rows.isEmpty() && state.readError == null) Text(stringResource(R.string.ui_no_category_allocations_or_recorded_spending_9e212a))
            }
            items(state.rows, key = { it.category.id }) { row ->
                MetricsCard(row.category.name + if (row.category.active) "" else stringResource(R.string.ui_archived_c089d2), row.metrics, "category_${row.category.id}",
                    edit = { model.openCategory(row.category.id) },
                    delete = if (row.metrics.allocation == null) null else { { deleting = row.category.id to row.category.name } },
                    enabled = !state.busy)
            }
            item { Text(stringResource(R.string.ui_future_dated_transactions_are_excluded_until_their_date_projections_us_31e5f2)) }
        }
    }
    state.editor?.let { editor ->
        val choices = state.categories.filter { it.transactionType == TransactionType.EXPENSE && (it.active ||
            state.snapshot?.categories?.any { budget -> budget.month == editor.month && budget.categoryId == it.id } == true) }
        var expanded by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = model::closeEditor, title = {
            Text(if (editor.categoryId == null) stringResource(R.string.ui_overall_budget_value_8b3547, editor.month) else stringResource(R.string.ui_category_budget_value_bd5e9a, editor.month))
        }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editor.categoryId != null) Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !state.busy, modifier = Modifier.testTag("budget_category")) {
                        Text(choices.firstOrNull { it.id == editor.categoryId }?.name ?: stringResource(R.string.ui_choose_an_expense_category_f0939e))
                    }
                    DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        choices.forEach { category -> DropdownMenuItem(text = { Text(category.name) },
                            onClick = { expanded = false; model.changeCategory(category.id) }) }
                    }
                }
                OutlinedTextField(editor.amount, { model.updateEditor(editor.copy(amount = it)) },
                    label = { Text(stringResource(R.string.ui_allocation_idr_78861e)) }, supportingText = { Text(stringResource(R.string.ui_zero_is_allowed_example_500_000_50_9ea590)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    enabled = !state.busy, modifier = Modifier.testTag("budget_amount"))
                state.error?.let { Text(budgetError(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("budget_editor_error")) }
            }
        }, confirmButton = { TextButton(onClick = { keyboard?.hide(); model.save() }, enabled = !state.busy) { Text(stringResource(R.string.ui_save_budget_6520b6)) } },
            dismissButton = { TextButton(onClick = model::closeEditor, enabled = !state.busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
    }
    deleting?.let { (categoryId, name) ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.ui_remove_allocation_aa4181)) },
            text = { Text(stringResource(R.string.ui_remove_value_for_value_transactions_and_other_allocations_stay_unchang_61bf39, name, state.month)) },
            confirmButton = { TextButton(onClick = { model.delete(categoryId); deleting = null }) { Text(stringResource(R.string.ui_remove_allocation_13e66b)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.ui_cancel_19766e)) } })
    }
}

@Composable
private fun MetricsCard(title: String, metrics: BudgetMetrics, tag: String, edit: () -> Unit, delete: (() -> Unit)?, enabled: Boolean) {
    @Composable fun amount(value: Money?) = value?.let { MoneyText.format(it) } ?: stringResource(R.string.ui_not_available_67a926)
    OutlinedCard(Modifier.fillMaxWidth().testTag("budget_$tag")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_recorded_status_7c5bbd) + metrics.status.label(), color = if (metrics.status == BudgetStatus.OVER_BUDGET) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.ui_allocation_5a6531) + (metrics.allocation?.let { MoneyText.format(it) } ?: stringResource(R.string.ui_not_set_4895f7)))
            Text(stringResource(R.string.ui_recorded_expenses_e7ccac) + amount(metrics.actual), modifier = Modifier.testTag("${tag}_actual"))
            Text(stringResource(R.string.ui_remaining_after_recorded_expenses_585ccd) + amount(metrics.remaining), modifier = Modifier.testTag("${tag}_remaining"))
            Text(stringResource(R.string.ui_utilization_7f21c8) + (metrics.utilizationPercent?.toPlainString()?.plus("%") ?: stringResource(R.string.ui_not_available_67a926)))
            Text(stringResource(R.string.ui_daily_average_b220f1) + amount(metrics.dailyAverage))
            Text(stringResource(R.string.ui_month_end_projection_2325fb) + if (metrics.projectionOverflow) stringResource(R.string.ui_outside_supported_range_5fe45d) else amount(metrics.projection))
            Text(stringResource(R.string.ui_budget_per_day_01e5ef) + amount(metrics.dailyAllowance), modifier = Modifier.testTag("${tag}_daily"))
            Text(stringResource(R.string.ui_value_recorded_day_s_value_calendar_day_s_left_2c50dc, metrics.elapsedDays, metrics.remainingDays))
            Text(when (metrics.coverage) {
                BudgetCoverage.FULL -> stringResource(R.string.ui_tracking_covers_this_month_through_the_reporting_date_edf479)
                BudgetCoverage.PARTIAL -> stringResource(R.string.ui_tracking_began_after_this_month_started_totals_may_omit_earlier_spendi_f24344)
                BudgetCoverage.UNTRACKED -> stringResource(R.string.ui_this_month_is_before_the_ledger_s_tracked_from_date_spending_is_unknow_a2ee65)
                BudgetCoverage.FUTURE -> stringResource(R.string.ui_this_month_has_not_started_spending_is_not_yet_available_db4e0d)
            }, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = edit, enabled = enabled) { Text(stringResource(R.string.ui_edit_allocation_a92fcc)) }
                if (delete != null) TextButton(onClick = delete, enabled = enabled) { Text(stringResource(R.string.ui_remove_c3812f)) }
            }
        }
    }
}
