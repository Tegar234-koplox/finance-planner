package com.tegar.finance.presentation

import com.tegar.finance.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*

@Composable
fun ForecastScreen(state: ForecastUiState, model: ForecastViewModel, openGoals: (() -> Unit)? = null) {
    LifecycleResumeEffect(Unit) { model.refresh(); onPauseOrDispose { } }
    var showSamples by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("forecast_list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(R.string.ui_forecasts_075c74), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.ui_a_baseline_from_your_completed_months_for_the_next_1_and_3_calendar_mo_384001))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ForecastMethod.entries.forEach { method ->
                    FilterChip(selected = state.options.method == method, onClick = { model.selectMethod(method) },
                        label = { Text(if (method == ForecastMethod.ROLLING) stringResource(R.string.ui_rolling_mean_ec9573) else stringResource(R.string.ui_weighted_mean_6abad8)) },
                        modifier = Modifier.testTag("forecast_method_${method.name}"))
                }
            }
            Text(stringResource(R.string.ui_history_window_97f1e4))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 6, 12).forEach { months ->
                    FilterChip(selected = state.options.window == months, onClick = { model.selectWindow(months) },
                        label = { Text(stringResource(R.string.ui_value_months_5fdb33, months)) }, modifier = Modifier.testTag("forecast_window_$months"))
                }
            }
            TextButton(onClick = model::refresh) { Text(stringResource(R.string.ui_refresh_forecasts_e69b5d)) }
            if (openGoals != null) OutlinedButton(onClick = openGoals, modifier = Modifier.testTag("open_goals")) {
                Text(stringResource(R.string.goals_title))
            }
        }
        if (state.loading) item { CircularProgressIndicator() }
        if (state.error != null) item {
            Text(if (state.error == AppError.OUT_OF_RANGE) stringResource(R.string.ui_a_historical_monthly_total_exceeds_the_supported_amount_range_forecast_c60154)
                else stringResource(R.string.ui_forecasts_could_not_be_loaded_no_previous_estimates_or_rules_are_shown_fda780),
                color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("forecast_error"))
            Button(onClick = model::retry) { Text(stringResource(R.string.ui_try_again_d8b839)) }
        }
        state.forecast?.let { data ->
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_as_of_value_ledger_reporting_date_2486ef, data.asOf))
                        Text(if (data.windowEnd.year < 1) stringResource(R.string.ui_no_earlier_supported_calendar_months_f22b4c)
                            else stringResource(R.string.ui_lookback_value_through_value_4518ae, data.windowStart, data.windowEnd))
                        Text(stringResource(R.string.ui_value_complete_month_s_used_value_month_s_lack_complete_tracking_3c4e76, data.samples.size, data.omittedMonths),
                            modifier = Modifier.testTag("forecast_samples"))
                        Text(stringResource(R.string.ui_evidence_value_a6f3ef, stringResource(when (data.evidence) {
                            ForecastEvidence.UNAVAILABLE -> R.string.evidence_unavailable
                            ForecastEvidence.LOW -> R.string.evidence_low
                            ForecastEvidence.MEDIUM -> R.string.evidence_medium
                            ForecastEvidence.HIGHER -> R.string.evidence_higher
                        })), modifier = Modifier.testTag("forecast_evidence"),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ui_0_samples_unavailable_1_low_2_5_medium_6_higher_these_describe_evidenc_89cd22))
                        Text(if (data.options.method == ForecastMethod.ROLLING) stringResource(R.string.ui_method_sum_of_completed_monthly_amounts_sample_count_every_month_has_e_32c824)
                            else if (data.samples.isEmpty()) stringResource(R.string.ui_method_weighted_mean_no_complete_months_are_available_to_assign_weight_9013b4)
                            else stringResource(R.string.ui_method_monthly_amounts_weights_1_through_value_oldest_to_newest_divide_ce8c7c, data.samples.size))
                        if (data.samples.size == 1) Text(stringResource(R.string.ui_one_observed_month_cannot_establish_variability_matching_range_endpoin_7fe259))
                        Text(stringResource(R.string.ui_each_horizon_repeats_that_monthly_baseline_income_consumption_and_cont_df1699))
                        Text(stringResource(R.string.ui_current_month_excluded_value_recorded_transaction_s_future_dated_recor_43458b, data.currentMonthRecordsExcluded, data.futureRecordsExcluded))
                        Text(stringResource(R.string.ui_only_fully_tracked_completed_calendar_months_count_covered_months_with_89bb3a))
                    }
                }
            }
            items(data.horizons, key = { it.months }) { horizon -> ForecastHorizonCard(horizon) }
            item {
                Text(stringResource(R.string.ui_rules_and_next_steps_a108bd), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.ui_rule_version_value_forecast_rules_use_the_selected_method_window_and_r_da9e14, ForecastCalculator.RULE_VERSION))
                if (data.rules.isEmpty()) Text(stringResource(R.string.ui_no_rule_triggered_this_does_not_establish_that_your_finances_are_healt_0f50d6))
            }
            items(data.rules, key = { it.kind.name }) { rule ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("forecast_rule_${rule.kind.name}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fun value(index: Int) = MoneyText.format(rule.amounts[index])
                        when (rule.kind) {
                            ForecastRuleKind.MORE_HISTORY -> {
                                Text(stringResource(R.string.ui_build_more_completed_history_65d3c2), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.ui_value_complete_month_s_2_record_at_least_two_complete_months_before_re_53d8bf, data.samples.size))
                            }
                            ForecastRuleKind.HISTORICAL_DEFICIT -> {
                                Text(stringResource(R.string.ui_review_the_income_consumption_gap_70f1a3), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.ui_estimated_consumption_value_income_value_for_next_month_review_recurri_674d30, value(0), value(1)))
                            }
                            ForecastRuleKind.CONTRIBUTION_PRESSURE -> {
                                Text(stringResource(R.string.ui_review_contribution_pressure_on_cash_0c0ed7), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.ui_income_value_consumption_value_contributions_value_0_this_baseline_red_b8aabb, value(0), value(1), value(2)))
                            }
                            ForecastRuleKind.NEXT_BUDGET_MISSING -> {
                                Text(stringResource(R.string.ui_set_next_month_s_overall_budget_724990), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.ui_no_overall_allocation_exists_for_value_category_allocations_alone_cann_e8de4a, data.horizons.first().start.toString().take(7)))
                            }
                            ForecastRuleKind.NEXT_BUDGET_SHORTFALL -> {
                                Text(stringResource(R.string.ui_review_next_month_s_budget_gap_f2e1db), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.ui_estimated_consumption_value_next_month_s_overall_allocation_value_revi_72bf08, value(0), value(1)))
                            }
                            ForecastRuleKind.CONSUMPTION_RISE -> {
                                Text(stringResource(R.string.ui_review_a_rise_in_recorded_consumption_5f68ef), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.ui_value_value_versus_value_value_rise_value_20_both_are_complete_months_9a0471, data.samples.last().month, value(0), data.samples[data.samples.lastIndex - 1].month, value(1), rule.percent!!.toPlainString()))
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showSamples = !showSamples }, modifier = Modifier.testTag("forecast_source_toggle")) {
                    Text(if (showSamples) stringResource(R.string.ui_hide_source_months_455a04) else stringResource(R.string.ui_show_source_months_8d1f45))
                }
            }
            if (showSamples) items(data.samples, key = { it.month.toString() }) { sample ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("forecast_source_${sample.month}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.ui_value_complete_weight_value_95617c, sample.month, sample.weight), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ui_income_value_bd0cd1, MoneyText.format(sample.flows.income)))
                        Text(stringResource(R.string.ui_consumption_value_91a5ee, MoneyText.format(sample.flows.consumption)))
                        Text(stringResource(R.string.ui_contributions_value_5279a1, MoneyText.format(sample.flows.contributions)))
                    }
                }
            }
            item {
                Text(stringResource(R.string.ui_these_are_recorded_history_scenarios_not_guarantees_statistical_confid_5ca0fb))
                Text(stringResource(R.string.ui_goals_and_purchases_are_not_implemented_no_goal_capacity_target_dates_2be60a),
                    modifier = Modifier.testTag("forecast_future_scope"))
            }
        }
    }
}

@Composable private fun ForecastHorizonCard(horizon: ForecastHorizon) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("forecast_horizon_${horizon.months}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_value_month_forecast_total_85a2c5, horizon.months), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_value_through_value_2b3dc7, horizon.start, horizon.endExclusive.minusDays(1)))
            val flows = horizon.totals
            if (flows == null) Text(when (horizon.unavailable) {
                ForecastUnavailable.NO_SAMPLES -> stringResource(R.string.ui_not_available_no_complete_covered_months_e3d9d2)
                ForecastUnavailable.UNSUPPORTED_DATES -> stringResource(R.string.ui_not_available_horizon_exceeds_supported_years_381c92)
                else -> stringResource(R.string.ui_not_available_this_horizon_exceeds_the_supported_amount_range_c58281)
            }, modifier = Modifier.testTag("forecast_${horizon.months}_unavailable"))
            else {
                ForecastAmount(stringResource(R.string.ui_income_51595d), flows.income, "forecast_${horizon.months}_income")
                ForecastAmount(stringResource(R.string.ui_consumption_36fad7), flows.consumption, "forecast_${horizon.months}_consumption")
                ForecastAmount(stringResource(R.string.ui_contributions_7219a0), flows.contributions, "forecast_${horizon.months}_contributions")
                ForecastAmount(stringResource(R.string.ui_income_minus_consumption_541067), flows.saving, "forecast_${horizon.months}_saving")
                Text(stringResource(R.string.ui_saving_rate_9db534) + (flows.savingRatePercent?.toPlainString()?.plus("%") ?: stringResource(R.string.ui_not_available_67a926)))
                Text(stringResource(R.string.ui_net_cash_flow_87a3a9) + if (flows.netCashFlowOverflow) stringResource(R.string.ui_outside_supported_range_5fe45d) else MoneyText.format(flows.netCashFlow!!))
                Text(stringResource(R.string.ui_opening_cash_is_excluded_contributions_reduce_cash_not_consumption_or_2303d4))
                Text(stringResource(R.string.ui_historical_monthly_range_value_not_a_prediction_interval_3461ad, horizon.months), style = MaterialTheme.typography.titleMedium)
                @Composable fun range(label: String, band: ForecastBand?) = "$label: " +
                    (band?.low?.let(MoneyText::format) ?: stringResource(R.string.ui_outside_supported_range_5fe45d)) + stringResource(R.string.ui_to_877216) +
                    (band?.high?.let(MoneyText::format) ?: stringResource(R.string.ui_outside_supported_range_5fe45d))
                Text(range(stringResource(R.string.ui_income_51595d), horizon.incomeBand))
                Text(range(stringResource(R.string.ui_consumption_36fad7), horizon.consumptionBand))
                Text(range(stringResource(R.string.ui_contributions_7219a0), horizon.contributionBand))
            }
        }
    }
}
@Composable private fun ForecastAmount(label: String, value: Money, tag: String) {
    Text("$label: ${MoneyText.format(value)}", modifier = Modifier.testTag(tag))
}
