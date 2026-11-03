package com.tegar.finance.presentation
import com.tegar.finance.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*
@Composable
fun HomeScreen(state: HomeUiState, model: HomeViewModel, transactions: () -> Unit, categories: () -> Unit,
    addTransaction: () -> Unit, budget: () -> Unit, reports: () -> Unit, goals: () -> Unit, wealth: () -> Unit = {}, investments: () -> Unit = {}) {
    LifecycleResumeEffect(Unit) { model.refresh(); onPauseOrDispose { } }
    LazyColumn(Modifier.fillMaxSize().testTag("home_list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(R.string.ui_your_finances_428334), style = MaterialTheme.typography.headlineMedium)
            state.dashboard?.let { Text(stringResource(R.string.ui_as_of_value_ledger_reporting_date_2486ef, it.date)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = transactions) { Text(stringResource(R.string.ui_transactions_3e5136)) }
                OutlinedButton(onClick = categories) { Text(stringResource(R.string.ui_categories_b8b1d8)) }
            }
            Row {
                TextButton(onClick = addTransaction, modifier = Modifier.testTag("home_add")) { Text(stringResource(R.string.ui_add_transaction_6502c0)) }
                TextButton(onClick = reports, modifier = Modifier.testTag("home_reports")) { Text(stringResource(R.string.ui_reports_dacca3)) }
                IconButton(onClick = model::refresh) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ui_refresh_dashboard_d61273)) }
            }
        }
        when {
            state.loading -> item { CircularProgressIndicator(Modifier.testTag("home_loading")) }
            state.error != null -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_dashboard_unavailable_9e3edb), style = MaterialTheme.typography.titleLarge)
                        Text(if (state.error == AppError.OUT_OF_RANGE)
                            stringResource(R.string.ui_a_calculated_total_exceeds_the_supported_amount_range_no_totals_or_spe_46e84b)
                            else stringResource(R.string.ui_the_dashboard_could_not_be_read_no_previous_totals_are_shown_32ca8f),
                            modifier = Modifier.testTag("home_error"))
                        Button(onClick = model::retry) { Text(stringResource(R.string.ui_try_again_d8b839)) }
                    }
                }
            }
            else -> state.dashboard?.let { data ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(stringResource(R.string.ui_recorded_cash_44197f), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                ScreenHelp("home_help") {
                                    Text(stringResource(R.string.ui_ledger_opening_cash_saved_income_consumption_investment_contributions_975c89, data.date))
                                    Text(stringResource(R.string.ui_tracking_began_value_this_is_your_recorded_ledger_cash_not_a_bank_bala_d9625c, data.trackedFrom),
                                style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.ui_lower_of_cash_and_remaining_budget_each_clamped_at_zero_divided_by_val_998af5, data.budget.remainingDays))
                                    Text(stringResource(R.string.ui_cash_cap_and_budget_cap_are_applied_together_this_limit_never_exceeds_e50119))
                                    Text(stringResource(R.string.ui_goals_bill_reservations_and_unrecorded_obligations_are_not_included_th_b808ea),
                                style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.ui_contributions_reduce_cash_but_are_not_consumption_saving_excludes_cont_cd4988),
                                style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.ui_projection_follows_the_recorded_daily_pace_it_is_not_a_forecast_of_fut_29cedd),
                                style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(amount(data.recordedCash), style = MaterialTheme.typography.headlineMedium,
                                color = if ((data.recordedCash?.minorUnits ?: 0) < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("home_cash"))
                            if (data.reservationOverflow) Text(stringResource(R.string.goals_reserve_overflow))
                            else HomeValue(stringResource(R.string.goals_cash_reserved), data.reservedForGoals, "home_goal_reserved")
                            HomeValue(stringResource(R.string.goals_cash_free), data.unreservedCash, "home_unreserved_cash")
                            TextButton(onClick = investments, modifier = Modifier.testTag("home_investments")) { Text(stringResource(R.string.portfolio_open)) }
                            TextButton(onClick = wealth, modifier = Modifier.testTag("home_wealth")) { Text(stringResource(R.string.wealth_open)) }
                            TextButton(onClick = goals, modifier = Modifier.testTag("home_goals")) { Text(stringResource(R.string.goals_manage)) }
                            if (data.recordedCount == 0) Text(stringResource(R.string.ui_no_recorded_transactions_through_this_date_add_income_expenses_or_cont_f53ca9))
                            if (data.futureCount > 0) Text(stringResource(R.string.ui_value_future_dated_transaction_s_are_excluded_until_their_date_985afa, data.futureCount))
                            if ((data.recordedCash?.minorUnits ?: 0) < 0)
                                Text(stringResource(R.string.ui_recorded_cash_is_negative_review_missing_or_incorrect_transactions_bef_b8ca8e),
                                    color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.ui_daily_spending_limit_e3d71e), style = MaterialTheme.typography.titleLarge)
                            Text(amount(data.dailyLimit), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("home_daily_limit"))
                            Text(when (data.dailyLimitReason) {
                                DailyLimitReason.AVAILABLE -> stringResource(R.string.ui_the_monthly_budget_is_the_lower_or_equal_limit_9ebc66)
                                DailyLimitReason.CASH_LIMITED -> stringResource(R.string.ui_recorded_cash_is_the_lower_limit_e8de6f)
                                DailyLimitReason.GOAL_RESERVED -> stringResource(R.string.goals_limit_reason)
                                DailyLimitReason.NO_OVERALL_BUDGET -> stringResource(R.string.ui_set_an_overall_monthly_budget_to_calculate_a_limit_category_allocation_6d4cb2)
                                DailyLimitReason.PARTIAL_COVERAGE -> stringResource(R.string.ui_this_month_s_tracking_is_incomplete_a_daily_spending_limit_is_unavaila_d6aa93)
                                DailyLimitReason.NO_COVERAGE -> stringResource(R.string.ui_the_reporting_date_is_before_tracking_began_recorded_cash_and_a_spendi_cca51a)
                            }, modifier = Modifier.testTag("home_limit_reason"))
                            if (data.dailyLimit != null) {
                            }
                            TextButton(onClick = budget) { Text(stringResource(R.string.ui_review_budget_8673c7)) }
                        }
                    }
                }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ui_value_recorded_activity_157b11, data.month), style = MaterialTheme.typography.titleLarge)
                            HomeValue(stringResource(R.string.ui_income_51595d), data.monthIncome, "home_income")
                            HomeValue(stringResource(R.string.ui_consumption_expenses_81a198), data.budget.actual, "home_expenses")
                            HomeValue(stringResource(R.string.ui_investment_contributions_9acd9c), data.monthContributions, "home_contributions")
                            HomeValue(stringResource(R.string.ui_income_minus_consumption_541067), data.monthSaving, "home_saving")
                            Text(stringResource(R.string.ui_saving_rate_9db534) + (data.savingRatePercent?.toPlainString()?.plus("%") ?: stringResource(R.string.ui_not_available_67a926)),
                                modifier = Modifier.testTag("home_saving_rate"))
                            Text(when (data.budget.coverage) {
                                BudgetCoverage.FULL -> stringResource(R.string.ui_tracking_covers_this_month_through_the_reporting_date_edf479)
                                BudgetCoverage.PARTIAL -> stringResource(R.string.ui_partial_month_tracking_began_value_these_totals_may_omit_earlier_activ_be8187, data.trackedFrom)
                                BudgetCoverage.UNTRACKED, BudgetCoverage.FUTURE -> stringResource(R.string.ui_this_month_s_activity_is_not_yet_covered_by_the_ledger_9f19dd)
                            }, modifier = Modifier.testTag("home_coverage"))
                        }
                    }
                }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ui_monthly_budget_c24759), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.ui_overall_allocation_70c43d) + (data.budget.allocation?.let { MoneyText.format(it) } ?: stringResource(R.string.ui_not_set_4895f7)),
                                modifier = Modifier.testTag("home_allocation"))
                            HomeValue(stringResource(R.string.ui_remaining_after_recorded_expenses_9b3407), data.budget.remaining, "home_remaining")
                            Text(stringResource(R.string.ui_utilization_7f21c8) + (data.budget.utilizationPercent?.toPlainString()?.plus("%") ?: stringResource(R.string.ui_not_available_67a926)))
                            Text(stringResource(R.string.ui_recorded_status_7c5bbd) + when (data.budget.status) {
                                BudgetStatus.NO_BUDGET -> stringResource(R.string.ui_no_overall_allocation_e6d29a)
                                BudgetStatus.NO_DATA -> stringResource(R.string.ui_not_available_67a926)
                                BudgetStatus.NEUTRAL -> stringResource(R.string.ui_neutral_1c6ac6)
                                BudgetStatus.SAFE -> stringResource(R.string.ui_below_80_ca9e35)
                                BudgetStatus.WATCH -> stringResource(R.string.ui_watch_80_7722db)
                                BudgetStatus.HIGH -> stringResource(R.string.ui_high_95_be0044)
                                BudgetStatus.OVER_BUDGET -> stringResource(R.string.ui_over_budget_82d5e1)
                            }, modifier = Modifier.testTag("home_budget_status"))
                            Text(stringResource(R.string.ui_month_end_consumption_projection_5b1eea) + if (data.budget.projectionOverflow) stringResource(R.string.ui_outside_supported_range_5fe45d) else amount(data.budget.projection))
                            TextButton(onClick = budget) { Text(stringResource(R.string.ui_open_monthly_budgets_66d4b1)) }
                        }
                    }
                }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ui_top_recorded_spending_categories_65e274), style = MaterialTheme.typography.titleLarge)
                            if (data.topCategories.isEmpty()) Text(if (data.budget.actual == null) stringResource(R.string.ui_spending_is_not_yet_available_22b156) else stringResource(R.string.ui_no_consumption_expenses_recorded_this_month_881a77))
                            data.topCategories.forEach { row ->
                                val category = state.categories[row.categoryId]
                                Text((category?.name ?: stringResource(R.string.ui_category_unavailable_da9371)) + if (category?.active == false) stringResource(R.string.ui_archived_c089d2) else "")
                                Text(MoneyText.format(row.amount), modifier = Modifier.testTag("home_category_${row.categoryId}"))
                            }
                        }
                    }
                }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ui_recent_recorded_transactions_669a18), style = MaterialTheme.typography.titleLarge)
                            if (data.recent.isEmpty()) Text(stringResource(R.string.ui_no_recorded_transactions_through_this_date_b5c2d8))
                            data.recent.forEach { row ->
                                val category = state.categories[row.categoryId]
                                Text("${row.date} · ${row.label()} · ${MoneyText.format(row.amount)}")
                                Text((category?.name ?: stringResource(R.string.ui_category_unavailable_da9371)) + if (category?.active == false) stringResource(R.string.ui_archived_c089d2) else "")
                                if (row.note.isNotEmpty()) Text(row.note)
                                HorizontalDivider()
                            }
                            TextButton(onClick = transactions) { Text(stringResource(R.string.ui_view_transaction_history_700d6a)) }
                        }
                    }
                }

            }
        }
    }
}
@Composable private fun amount(value: Money?) = value?.let { MoneyText.format(it) } ?: stringResource(R.string.ui_not_available_67a926)
@Composable
private fun HomeValue(label: String, value: Money?, tag: String) {
    Text("$label: " + amount(value), modifier = Modifier.testTag(tag))
}
