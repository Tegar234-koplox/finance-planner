package com.tegar.finance.presentation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tegar.finance.R
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*
@Composable private fun goalKind(value: GoalKind) = stringResource(if (value == GoalKind.SAVING) R.string.goals_kind_saving else R.string.goals_kind_purchase)
@Composable private fun goalPriority(value: GoalPriority) = stringResource(when (value) {
    GoalPriority.HIGH -> R.string.goals_priority_high; GoalPriority.NORMAL -> R.string.goals_priority_normal; GoalPriority.LOW -> R.string.goals_priority_low
})
@Composable private fun goalStatus(value: GoalState) = stringResource(if (value == GoalState.ACTIVE) R.string.goals_active else R.string.goals_paused)
@Composable private fun goalAmount(value: Money?) = value?.let { MoneyText.format(it) } ?: stringResource(R.string.ui_not_available_67a926)
@Composable private fun goalError(value: AppError) = stringResource(when (value) {
    AppError.INVALID_INPUT -> R.string.goals_error_input
    AppError.DATE_BEFORE_COVERAGE -> R.string.goals_error_coverage
    AppError.CURRENCY_MISMATCH -> R.string.goals_error_currency
    AppError.OUT_OF_RANGE -> R.string.goals_error_range
    AppError.NOT_FOUND -> R.string.goals_error_missing
    else -> R.string.goals_error_storage
})
@Composable
fun GoalsScreen(state: GoalsUiState, model: GoalsViewModel) {
    LifecycleResumeEffect(Unit) { model.refresh(); onPauseOrDispose { } }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("goals_list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                ScreenHelp("goals_help") {
                    Text(stringResource(R.string.goals_intro))
                    Text(stringResource(R.string.goals_queue))
                    Text(stringResource(R.string.goals_schedule))
                    Text(stringResource(R.string.goals_capacity_formula))
                    Text(stringResource(R.string.goals_footer))
                    Text(stringResource(R.string.goals_purchase_hint))
                }
            }
            Row {
                Button(onClick = { model.openGoal() }, enabled = state.plan != null && !state.busy,
                    modifier = Modifier.testTag("goal_add")) { Text(stringResource(R.string.goals_add)) }
                TextButton(onClick = model::refresh, enabled = !state.busy) { Text(stringResource(R.string.goals_refresh)) }
            }
        }
        if (state.loading) item { CircularProgressIndicator() }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.readError?.let { error -> item {
            Text(stringResource(R.string.goals_read_error), color = MaterialTheme.colorScheme.error)
            Text(goalError(error), modifier = Modifier.testTag("goal_read_error"))
            Button(onClick = model::retry) { Text(stringResource(R.string.ui_try_again_d8b839)) }
        } }
        if (state.error != null && state.editor == null) item {
            Text(goalError(state.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("goal_error"))
            TextButton(onClick = model::clearError) { Text(stringResource(R.string.ui_dismiss_48845b)) }
        }
        state.plan?.let { plan ->
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_as_of_value_ledger_reporting_date_2486ef, plan.today))
                        Text(stringResource(R.string.ui_recorded_cash_44197f) + ": " + goalAmount(plan.recordedCash), modifier = Modifier.testTag("goal_cash"))
                        Text(stringResource(R.string.goals_cash_reserved) + ": " + if (plan.reservationOverflow)
                            stringResource(R.string.ui_outside_supported_range_5fe45d) else goalAmount(plan.reserved), modifier = Modifier.testTag("goal_reserved"))
                        Text(stringResource(R.string.goals_cash_free) + ": " + goalAmount(plan.unreservedCash), modifier = Modifier.testTag("goal_free_cash"))
                        if (plan.reservationsBacked == false) Text(stringResource(R.string.goals_cash_shortfall), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("goal_backing_warning"))
                        if (plan.recordedCash == null) Text(stringResource(R.string.goals_cash_unavailable))
                        Text(stringResource(R.string.goals_capacity), style = MaterialTheme.typography.titleMedium)
                        Text(goalAmount(plan.monthlyCapacity), modifier = Modifier.testTag("goal_capacity"))
                        Text(stringResource(R.string.goals_baseline,
                            stringResource(if (plan.options.method == ForecastMethod.ROLLING) R.string.ui_rolling_mean_ec9573 else R.string.ui_weighted_mean_6abad8),
                            plan.options.window, plan.sampleCount))
                        if (plan.capacityReason != PlanningCapacityReason.AVAILABLE) Text(stringResource(when (plan.capacityReason) {
                            PlanningCapacityReason.NEED_HISTORY -> R.string.goals_need_history
                            PlanningCapacityReason.NO_POSITIVE_FLOW -> R.string.goals_no_capacity
                            else -> R.string.goals_capacity_range
                        }))
                    }
                }
            }
            if (plan.goals.isEmpty()) item { Text(stringResource(R.string.goals_empty), modifier = Modifier.testTag("goals_empty")) }
            items(plan.goals, key = { it.goal.id }) { row ->
                GoalCard(row, enabled = !state.busy, edit = { model.openGoal(row.goal) }, delete = { deleting = row.goal.id })
            }
            item {
            }
        }
    }
    state.editor?.let { editor -> GoalForm(editor, state.busy, state.error, model) }
    deleting?.let { id ->
        val name = state.plan?.goals?.firstOrNull { it.goal.id == id }?.goal?.name.orEmpty()
        AlertDialog(onDismissRequest = { if (!state.busy) deleting = null }, title = { Text(stringResource(R.string.goals_delete_title)) },
            text = { Text(stringResource(R.string.goals_delete_body, name)) }, confirmButton = {
                TextButton(onClick = { model.delete(id); deleting = null }, enabled = !state.busy,
                    modifier = Modifier.testTag("goal_delete_confirm")) { Text(stringResource(R.string.goals_delete)) }
            }, dismissButton = { TextButton(onClick = { deleting = null }, enabled = !state.busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
    }
}
@Composable private fun GoalCard(row: GoalProgress, enabled: Boolean, edit: () -> Unit, delete: () -> Unit) {
    val goal = row.goal
    OutlinedCard(Modifier.fillMaxWidth().testTag("goal_${goal.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(goal.name, style = MaterialTheme.typography.titleLarge)
            Text(goalKind(goal.kind) + " · " + goalPriority(goal.priority) + " · " + goalStatus(goal.state))
            Text(stringResource(R.string.goals_target_value, MoneyText.format(goal.target)))
            Text(stringResource(R.string.goals_earmark_value, MoneyText.format(goal.earmarked), goal.asOf))
            Text(stringResource(R.string.goals_remaining_value, MoneyText.format(row.remaining)), modifier = Modifier.testTag("goal_remaining_${goal.id}"))
            Text(stringResource(R.string.goals_progress, row.percent.toPlainString()), modifier = Modifier.testTag("goal_progress_${goal.id}"))
            LinearProgressIndicator(progress = { row.percent.toFloat().div(100).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { })
            Text(stringResource(when (row.funding) {
                GoalFunding.EMPTY -> R.string.goals_funding_empty; GoalFunding.PARTIAL -> R.string.goals_funding_partial
                GoalFunding.FUNDED -> R.string.goals_funding_full; GoalFunding.OVERFUNDED -> R.string.goals_funding_over
            }))
            Text(stringResource(R.string.goals_date_value, goal.targetDate))
            if (row.overdue) Text(stringResource(R.string.goals_overdue), color = MaterialTheme.colorScheme.error)
            if (row.dueToday) Text(stringResource(R.string.goals_due_today), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.goals_needed_value, goalAmount(row.monthlyNeededAlone)))
            if (row.contributionDatesBeforeDeadline > 0) Text(stringResource(R.string.goals_needed_hint, row.contributionDatesBeforeDeadline))
            else if (row.remaining.minorUnits > 0) Text(stringResource(R.string.goals_no_dates))
            Text(stringResource(R.string.goals_cash_assigned, goalAmount(row.cashAssignedNow)))
            Text(stringResource(R.string.goals_scenario_date, row.availableOn?.toString() ?: stringResource(R.string.ui_not_available_67a926)),
                modifier = Modifier.testTag("goal_ready_${goal.id}"))
            Text(stringResource(when (row.dateReason) {
                GoalDateReason.EARMARKED -> R.string.goals_reason_earmarked
                GoalDateReason.CASH_NOW -> R.string.goals_reason_cash
                GoalDateReason.PROJECTED -> R.string.goals_reason_projected
                GoalDateReason.PAUSED -> R.string.goals_reason_paused
                GoalDateReason.NEED_HISTORY -> R.string.goals_need_history
                GoalDateReason.NO_CAPACITY -> R.string.goals_no_capacity
                GoalDateReason.CASH_UNAVAILABLE -> R.string.goals_cash_unavailable
                GoalDateReason.RESERVATIONS_EXCEED_CASH -> R.string.goals_cash_shortfall
                GoalDateReason.DATE_OUT_OF_RANGE -> R.string.goals_reason_date_range
                GoalDateReason.FORECAST_OUT_OF_RANGE -> R.string.goals_capacity_range
            }))
            row.meetsDeadline?.let { meets -> Text(stringResource(if (meets) R.string.goals_meets_deadline else R.string.goals_misses_deadline)) }
            Row {
                TextButton(onClick = edit, enabled = enabled, modifier = Modifier.testTag("goal_edit_${goal.id}")) { Text(stringResource(R.string.goals_edit)) }
                TextButton(onClick = delete, enabled = enabled, modifier = Modifier.testTag("goal_delete_${goal.id}")) { Text(stringResource(R.string.goals_delete)) }
            }
        }
    }
}
@Composable private fun GoalForm(editor: GoalEditor, busy: Boolean, error: AppError?, model: GoalsViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest = model::closeEditor, title = { Text(stringResource(if (editor.id == null) R.string.goals_add else R.string.goals_edit)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(editor.name, { model.edit(editor.copy(name = it)) }, label = { Text(stringResource(R.string.goals_name)) },
                enabled = !busy, singleLine = true, modifier = Modifier.testTag("goal_name"))
            GoalChoice(goalKind(editor.kind), GoalKind.entries.map { it to goalKind(it) }, !busy, "goal_kind") { model.edit(editor.copy(kind = it)) }
            OutlinedTextField(editor.target, { model.edit(editor.copy(target = it)) }, label = { Text(stringResource(R.string.goals_target)) },
                enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("goal_target"))
            OutlinedTextField(editor.earmarked, { model.edit(editor.copy(earmarked = it)) }, label = { Text(stringResource(R.string.goals_earmarked)) },
                enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("goal_earmarked"))
            Text(stringResource(R.string.goals_amount_hint))
            OutlinedTextField(editor.asOf, { model.edit(editor.copy(asOf = it)) }, label = { Text(stringResource(R.string.goals_asof)) },
                enabled = !busy, singleLine = true, modifier = Modifier.testTag("goal_asof"))
            OutlinedTextField(editor.targetDate, { model.edit(editor.copy(targetDate = it)) }, label = { Text(stringResource(R.string.goals_deadline)) },
                enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }), modifier = Modifier.testTag("goal_target_date"))
            Text(stringResource(R.string.goals_asof_hint))
            GoalChoice(stringResource(R.string.goals_priority) + ": " + goalPriority(editor.priority),
                GoalPriority.entries.map { it to goalPriority(it) }, !busy, "goal_priority") { model.edit(editor.copy(priority = it)) }
            GoalChoice(stringResource(R.string.goals_state) + ": " + goalStatus(editor.status),
                GoalState.entries.map { it to goalStatus(it) }, !busy, "goal_status") { model.edit(editor.copy(status = it)) }
            Text(stringResource(R.string.goals_pause_hint))
            if (error != null) Text(goalError(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("goal_editor_error"))
        } }, confirmButton = { TextButton(onClick = { keyboard?.hide(); model.save() }, enabled = !busy,
            modifier = Modifier.testTag("goal_save")) { Text(stringResource(R.string.goals_save)) } },
        dismissButton = { TextButton(onClick = model::closeEditor, enabled = !busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
}
@Composable private fun <T> GoalChoice(selected: String, choices: List<Pair<T, String>>, enabled: Boolean, tag: String, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(selected) }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { (value, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; choose(value) }) }
        }
    }
}
