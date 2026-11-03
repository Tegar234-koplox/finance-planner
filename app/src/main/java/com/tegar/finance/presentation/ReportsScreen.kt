package com.tegar.finance.presentation
import com.tegar.finance.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode
@Composable private fun ReportKind.label() = when (this) { ReportKind.WEEK -> stringResource(R.string.ui_week_e78041); ReportKind.MONTH -> stringResource(R.string.ui_month_310ca5); ReportKind.YEAR -> stringResource(R.string.ui_year_89f683) }
@Composable private fun ReportCoverage.label() = when (this) {
    ReportCoverage.COMPLETE -> stringResource(R.string.ui_complete_period_d1eeff)
    ReportCoverage.IN_PROGRESS -> stringResource(R.string.ui_period_in_progress_037137)
    ReportCoverage.PARTIAL_HISTORY -> stringResource(R.string.ui_partial_tracking_history_08d6c3)
    ReportCoverage.UNTRACKED -> stringResource(R.string.ui_history_unavailable_cf319f)
    ReportCoverage.FUTURE -> stringResource(R.string.ui_future_period_947bb7)
}
@Composable
fun ReportsScreen(state: ReportsUiState, model: ReportsViewModel) {
    LifecycleResumeEffect(Unit) { model.refresh(); onPauseOrDispose { } }
    var dateText by rememberSaveable { mutableStateOf("") }
    var chart by rememberSaveable { mutableStateOf(false) }
    var datesExpanded by rememberSaveable { mutableStateOf(false) }
    val report = state.report
    LaunchedEffect(report?.selection) { report?.selection?.let { dateText = it.date.toString() } }
    val keyboard = LocalSoftwareKeyboardController.current
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("reports_list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_reports_dacca3), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                ScreenHelp("report_help") {
                    Text(stringResource(R.string.ui_percentage_change_is_unavailable_when_the_previous_amount_is_zero_eba789))
                    Text(stringResource(R.string.ui_opening_cash_is_excluded_contributions_are_not_consumption_bars_start_294d55))
                    Text(stringResource(R.string.ui_bar_scale_zero_to_total_recorded_consumption_archived_and_unallocated_479ca9))
                    Text(stringResource(R.string.ui_reports_use_saved_records_only_unknown_or_future_dates_are_not_zeros_h_44f5da))
                    Text(stringResource(R.string.ui_net_cash_flow_income_consumption_contributions_this_is_not_a_cash_bala_88ffcf))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportKind.entries.forEach { kind ->
                    FilterChip(selected = report?.selection?.kind == kind, enabled = report != null,
                        onClick = { keyboard?.hide(); model.selectKind(kind) },
                        label = { Text(kind.label()) }, modifier = Modifier.testTag("report_kind_${kind.name}"))
                }
            }
            Row {
                TextButton(onClick = { keyboard?.hide(); model.move(-1) }, enabled = report != null) { Text(stringResource(R.string.ui_previous_a57b08)) }
                TextButton(onClick = { keyboard?.hide(); model.today() }, enabled = report != null) { Text(stringResource(R.string.ui_today_2b065c)) }
                TextButton(onClick = { keyboard?.hide(); model.move(1) }, enabled = report != null) { Text(stringResource(R.string.ui_next_1ff57a)) }
            }
            Row {
                TextButton(onClick = { datesExpanded = !datesExpanded }) { Text(if (datesExpanded) stringResource(R.string.ui_hide_date_input_7e4417) else stringResource(R.string.ui_choose_date_343b5a)) }
                TextButton(onClick = model::refresh) { Text(stringResource(R.string.ui_refresh_report_e8d598)) }
            }
            if (datesExpanded) {
                OutlinedTextField(dateText, { dateText = it }, label = { Text(stringResource(R.string.ui_date_in_period_yyyy_mm_dd_0ed53d)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("report_date"))
                Button(onClick = {
                    keyboard?.hide(); model.selectDate(dateText)
                    if (ReportSelection.parseDate(dateText) != null) datesExpanded = false
                }) { Text(stringResource(R.string.ui_open_period_1cc585)) }
            }
            if (state.inputError) Text(stringResource(R.string.ui_enter_a_valid_date_from_year_0001_to_9999_969636), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("report_input_error"))
        }
        if (state.loading) item { CircularProgressIndicator() }
        if (state.error != null) item {
            Text(if (state.error == AppError.OUT_OF_RANGE) stringResource(R.string.ui_a_report_total_exceeds_the_supported_amount_range_no_stale_totals_or_c_578d27)
                else stringResource(R.string.ui_reports_could_not_be_loaded_no_stale_totals_or_charts_are_shown_0f15fc), color = MaterialTheme.colorScheme.error)
            Button(onClick = model::retry) { Text(stringResource(R.string.ui_try_again_d8b839)) }
        }
        report?.let { data ->
            item {
                Text(stringResource(R.string.ui_value_through_value_2b3dc7, data.current.period.start, data.current.period.endExclusive.minusDays(1)),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("report_range"))
                Text(stringResource(R.string.ui_as_of_value_ledger_reporting_date_2486ef, data.asOf))
                Text(data.current.coverage.label(), modifier = Modifier.testTag("report_coverage"))
                Text(data.current.observed?.let { stringResource(R.string.ui_recorded_range_value_through_value_value_transaction_s_8937f3, it.start, it.endExclusive.minusDays(1), data.current.count) }
                    ?: stringResource(R.string.ui_no_covered_dates_in_this_period_unknown_history_is_not_zero_spending_455775))
                if (data.current.coverage == ReportCoverage.PARTIAL_HISTORY)
                    Text(stringResource(R.string.ui_tracking_began_after_this_period_started_totals_omit_earlier_activity_6c83f7))
                if (data.futureExcludedCount > 0) Text(stringResource(R.string.ui_value_future_dated_transaction_s_excluded_until_their_date_3d2379, data.futureExcludedCount))
            }
            item { ReportSummary(data.current.totals) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_previous_period_comparison_e4dd6a), style = MaterialTheme.typography.titleLarge)
                        val comparison = data.comparison
                        if (comparison == null) Text(when (data.comparisonUnavailable) {
                            ComparisonUnavailable.PARTIAL_HISTORY -> stringResource(R.string.ui_not_available_the_selected_period_has_incomplete_tracking_history_4d69e6)
                            ComparisonUnavailable.MISSING_PREVIOUS_HISTORY -> stringResource(R.string.ui_not_available_previous_period_history_is_missing_or_incomplete_e4d595)
                            ComparisonUnavailable.PREVIOUS_SHORTER -> stringResource(R.string.ui_not_available_the_previous_period_has_fewer_days_than_the_current_elap_3b7fbb)
                            ComparisonUnavailable.OUT_OF_RANGE -> stringResource(R.string.ui_not_available_previous_totals_or_a_change_exceed_the_supported_range_9a23f2)
                            ComparisonUnavailable.UNSUPPORTED_PREVIOUS -> stringResource(R.string.ui_not_available_the_previous_period_is_outside_supported_years_6b9bc7)
                            else -> stringResource(R.string.ui_not_available_this_period_has_no_covered_history_yet_c3b3c5)
                        }, modifier = Modifier.testTag("report_comparison"))
                        else {
                            val previous = comparison.previous
                            Text(stringResource(R.string.ui_compared_with_value_through_value_7684b6, previous.period.start, previous.period.endExclusive.minusDays(1)),
                                modifier = Modifier.testTag("report_comparison"))
                            Text(if (comparison.alignedDays) stringResource(R.string.ui_equal_elapsed_calendar_days_are_compared_the_previous_full_period_is_n_02b796)
                                else stringResource(R.string.ui_both_calendar_periods_are_complete_their_lengths_may_differ_these_are_7af531))
                            ReportAmount(stringResource(R.string.ui_income_change_77a5b2), comparison.incomeDelta, "report_income_delta")
                            ReportAmount(stringResource(R.string.ui_consumption_change_67d58b), comparison.expenseDelta, "report_expense_delta")
                            ReportAmount(stringResource(R.string.ui_saving_change_f52d12), comparison.savingDelta, "report_saving_delta")
                            Text(stringResource(R.string.ui_income_change_bf7cdc) + percent(comparison.incomeChangePercent))
                            Text(stringResource(R.string.ui_consumption_change_5544f4) + percent(comparison.expenseChangePercent))
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.ui_income_consumption_and_contributions_over_time_546a7c), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { chart = !chart }, modifier = Modifier.testTag("report_chart_toggle")) {
                    Text(if (chart) stringResource(R.string.ui_hide_chart_6b614d) else stringResource(R.string.ui_show_chart_6d64a1))
                }
            }
            if (chart) {
                val maximum = data.buckets.mapNotNull { it.totals }.flatMap {
                    listOf(it.income.minorUnits, it.consumption.minorUnits, it.contributions.minorUnits)
                }.maxOrNull() ?: 0L
                item {
                    Text(when {
                        data.buckets.none { it.totals != null } -> stringResource(R.string.ui_scale_unavailable_no_covered_values_e3c79a)
                        maximum == 0L -> stringResource(R.string.ui_all_covered_amounts_are_zero_zero_width_bars_are_labeled_explicitly_642453)
                        else -> stringResource(R.string.ui_scale_0_to_9a0216) + MoneyText.format(Money(maximum,
                            data.buckets.first { it.totals != null }.totals!!.income.currency)) +
                            stringResource(R.string.ui_exact_labels_remain_readable_without_color_8fb40c)
                    })
                }
                itemsIndexed(data.buckets, key = { _, bucket -> bucket.period.start.toString() }) { index, bucket ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("report_bucket_$index")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (data.selection.kind == ReportKind.YEAR) bucket.period.start.toString().take(7) else bucket.period.start.toString(),
                                style = MaterialTheme.typography.titleMedium)
                            Text(bucket.coverage.label())
                            val flows = bucket.totals
                            if (flows == null) Text(stringResource(R.string.ui_not_available_no_zero_bar_is_drawn_9b77f8))
                            else {
                                AmountBar(stringResource(R.string.ui_income_51595d), flows.income, maximum, MaterialTheme.colorScheme.primary)
                                AmountBar(stringResource(R.string.ui_consumption_36fad7), flows.consumption, maximum, MaterialTheme.colorScheme.secondary)
                                AmountBar(stringResource(R.string.ui_contributions_7219a0), flows.contributions, maximum, MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.ui_consumption_by_category_1edbbd), style = MaterialTheme.typography.titleLarge)
                if (data.categories.isEmpty()) Text(if (data.current.totals == null) stringResource(R.string.ui_category_amounts_are_unavailable_3d5f4a)
                    else stringResource(R.string.ui_no_consumption_expenses_recorded_in_the_covered_range_c1bec0))
            }
            itemsIndexed(data.categories, key = { _, row -> row.categoryId }) { _, row ->
                val category = state.categories[row.categoryId]
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text((category?.name ?: stringResource(R.string.ui_category_unavailable_da9371)) + if (category?.active == false) stringResource(R.string.ui_archived_c089d2) else "")
                        AmountBar(stringResource(R.string.ui_consumption_36fad7), row.amount, data.current.totals!!.consumption.minorUnits,
                            MaterialTheme.colorScheme.secondary)
                        Text(stringResource(R.string.ui_value_of_recorded_consumption_ffb739, row.sharePercent.toPlainString()))
                    }
                }
            }
            item {  }
        }
    }
}
@Composable private fun percent(value: BigDecimal?) = value?.toPlainString()?.plus("%") ?: stringResource(R.string.ui_not_available_67a926)
@Composable private fun ReportSummary(totals: RecordedFlows?) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_recorded_totals_7cfd6c), style = MaterialTheme.typography.titleLarge)
            ReportAmount(stringResource(R.string.ui_income_51595d), totals?.income, "report_income")
            ReportAmount(stringResource(R.string.ui_consumption_36fad7), totals?.consumption, "report_expenses")
            ReportAmount(stringResource(R.string.ui_contributions_7219a0), totals?.contributions, "report_contributions")
            ReportAmount(stringResource(R.string.portfolio_redeemed), totals?.redemptions, "report_redemptions")
            ReportAmount(stringResource(R.string.ui_income_minus_consumption_541067), totals?.saving, "report_saving")
            Text(stringResource(R.string.ui_saving_rate_9db534) + percent(totals?.savingRatePercent), modifier = Modifier.testTag("report_saving_rate"))
            Text(stringResource(R.string.ui_net_cash_flow_87a3a9) + if (totals?.netCashFlowOverflow == true) stringResource(R.string.ui_outside_supported_range_5fe45d)
                else totals?.netCashFlow?.let { MoneyText.format(it) } ?: stringResource(R.string.ui_not_available_67a926))
        }
    }
}
@Composable private fun ReportAmount(label: String, value: Money?, tag: String) {
    Text("$label: " + (value?.let { MoneyText.format(it) } ?: stringResource(R.string.ui_not_available_67a926)), modifier = Modifier.testTag(tag))
}
@Composable private fun AmountBar(label: String, amount: Money, maximum: Long, color: Color) {
    val fraction = if (maximum <= 0) 0f else BigDecimal.valueOf(amount.minorUnits)
        .divide(BigDecimal.valueOf(maximum), 8, RoundingMode.HALF_EVEN).toFloat().coerceIn(0f, 1f)
    Text("$label: ${MoneyText.format(amount)}")
    // Values and series names are text; the decorative native bar is not a second TalkBack announcement.
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { },
        color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
}
