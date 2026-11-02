package com.tegar.finance.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tegar.finance.R
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*
import java.math.BigDecimal

@Composable internal fun AllocationBucket.label() = stringResource(when (this) {
    AllocationBucket.CASH -> R.string.portfolio_cash; AllocationBucket.STOCK -> R.string.portfolio_stock
    AllocationBucket.BOND -> R.string.portfolio_bond; AllocationBucket.FUND -> R.string.portfolio_fund
    AllocationBucket.GOLD -> R.string.portfolio_gold; AllocationBucket.OTHER -> R.string.portfolio_other
})
@Composable internal fun InvestmentAction.label() = stringResource(if (this == InvestmentAction.REDEMPTION) R.string.portfolio_redemption else R.string.portfolio_contribution)
@Composable private fun amount(value: Money?) = value?.let(MoneyText::format) ?: stringResource(R.string.ui_not_available_67a926)
@Composable private fun errorText(value: AppError) = stringResource(when (value) {
    AppError.ALLOCATION_TOTAL -> R.string.portfolio_error_targets
    AppError.INVESTMENT_IN_USE -> R.string.portfolio_error_in_use
    AppError.INVALID_INPUT, AppError.DATE_BEFORE_COVERAGE, AppError.CATEGORY_MISMATCH, AppError.CATEGORY_INACTIVE -> R.string.portfolio_error_input
    AppError.OUT_OF_RANGE -> R.string.wealth_error_range
    AppError.CURRENCY_MISMATCH -> R.string.wealth_error_currency
    AppError.NOT_FOUND -> R.string.wealth_error_missing
    AppError.WEALTH_DATE_CONFLICT -> R.string.wealth_error_conflict
    else -> R.string.wealth_error_storage
})
private fun percent(bps: Int?) = bps?.let { BigDecimal.valueOf(it.toLong(), 2).toPlainString() + "%" }
@Composable private fun MoneyRow(label: Int, value: Money?, tag: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(amount(value), modifier = Modifier.testTag(tag))
    }
}

@Composable
fun PortfolioScreen(state: PortfolioUiState, model: PortfolioViewModel) {
    LifecycleResumeEffect(Unit) { model.refresh(); onPauseOrDispose { } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var deletingKind by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf("") }
    var monthText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.overview?.progress?.month) { monthText = state.overview?.progress?.month?.toString().orEmpty() }
    fun delete(kind: String, id: String) { deletingKind = kind; deletingId = id }
    LazyColumn(Modifier.fillMaxSize().testTag("portfolio_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.portfolio_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                ScreenHelp("portfolio_help") {
                    Text(stringResource(R.string.portfolio_help)); Text(stringResource(R.string.portfolio_flow_help)); Text(stringResource(R.string.portfolio_plan_hint))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.portfolio_holdings, R.string.portfolio_allocation, R.string.portfolio_activity).forEachIndexed { index, label ->
                    FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(stringResource(label)) }, modifier = Modifier.testTag("portfolio_tab_$index"))
                }
            }
        }
        if (state.loading) item { CircularProgressIndicator() }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.readError?.let { error -> item {
            Text(errorText(error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = model::retry) { Text(stringResource(R.string.ui_try_again_d8b839)) }
        } }
        val editing = state.holdingEditor != null || state.valueEditor != null || state.movementEditor != null || state.targetEditor != null || state.planEditor != null
        if (state.error != null && !editing) item {
            Text(errorText(state.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("portfolio_error"))
            TextButton(onClick = model::clearError) { Text(stringResource(R.string.ui_dismiss_48845b)) }
        }
        state.overview?.let { data ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.portfolio_total), style = MaterialTheme.typography.titleMedium)
                        Text(amount(data.total), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("portfolio_total"))
                        MoneyRow(R.string.portfolio_invested, data.valuation.total, "portfolio_invested")
                        MoneyRow(R.string.portfolio_cash, data.cash, "portfolio_cash")
                        if (data.valuation.missing > 0) Text(stringResource(R.string.portfolio_valuation_missing, data.valuation.missing.toString()))
                        if (data.totalOverflow) Text(stringResource(R.string.wealth_error_range), color = MaterialTheme.colorScheme.error)
                        if (data.valuation.futureCount > 0) Text(stringResource(R.string.wealth_future, data.valuation.futureCount.toString(), "0"))
                    }
                }
            }
            when (tab) {
                0 -> {
                    item { Button(onClick = { model.openHolding() }, enabled = !state.busy, modifier = Modifier.testTag("holding_add")) { Text(stringResource(R.string.portfolio_add)) } }
                    if (data.valuation.statuses.isEmpty()) item { Text(stringResource(R.string.portfolio_empty)) }
                    items(data.valuation.statuses, key = { "holding_${it.holding.id}" }) { row ->
                        HoldingCard(row, state.source!!.values.filter { it.holdingId == row.holding.id }, !state.busy, model,
                            deleteHolding = { delete("holding", row.holding.id) }, deleteValue = { delete("value", it) })
                    }
                }
                1 -> {
                    item {
                        OutlinedButton(onClick = model::openTargets, enabled = !state.busy, modifier = Modifier.testTag("allocation_edit")) { Text(stringResource(R.string.portfolio_set_targets)) }
                        if (state.source!!.targets.isNotEmpty()) TextButton(onClick = model::clearTargets, enabled = !state.busy) { Text(stringResource(R.string.portfolio_reset_targets)) }
                        else Text(stringResource(R.string.portfolio_no_targets))
                        if (!data.allocationAvailable) Text(stringResource(R.string.portfolio_allocation_unavailable), modifier = Modifier.testTag("allocation_unavailable"))
                    }
                    items(data.allocations, key = { "allocation_${it.bucket}" }) { row ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(row.bucket.label(), style = MaterialTheme.typography.titleMedium)
                                Text(amount(row.value))
                                Text(stringResource(R.string.portfolio_actual) + ": " + (percent(row.actualBasisPoints) ?: stringResource(R.string.ui_not_available_67a926)),
                                    modifier = Modifier.testTag("allocation_actual_${row.bucket}"))
                                if (row.actualBasisPoints != null) LinearProgressIndicator(progress = { row.actualBasisPoints / 10000f }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { })
                                Text(stringResource(R.string.portfolio_target) + ": " + (percent(row.targetBasisPoints) ?: stringResource(R.string.ui_not_set_4895f7)),
                                    modifier = Modifier.testTag("allocation_target_${row.bucket}"))
                                if (row.gap != null) Text(stringResource(R.string.portfolio_gap, amount(row.gap)), modifier = Modifier.testTag("allocation_gap_${row.bucket}"))
                            }
                        }
                    }
                }
                else -> {
                    item {
                        OutlinedTextField(monthText, { monthText = it }, label = { Text(stringResource(R.string.portfolio_month)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("contribution_month"))
                        Row {
                            TextButton(onClick = { model.moveMonth(-1) }, enabled = !state.busy) { Text(stringResource(R.string.ui_previous_a57b08)) }
                            TextButton(onClick = { model.selectMonth(monthText) }, enabled = !state.busy) { Text(stringResource(R.string.ui_open_month_2b285f)) }
                            TextButton(onClick = { model.moveMonth(1) }, enabled = !state.busy) { Text(stringResource(R.string.ui_next_1ff57a)) }
                        }
                    }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MoneyRow(R.string.portfolio_plan_target, data.progress.target, "contribution_target")
                                MoneyRow(R.string.portfolio_gross, data.progress.contributed, "contribution_gross")
                                MoneyRow(R.string.portfolio_redeemed, data.progress.redeemed, "contribution_redeemed")
                                MoneyRow(R.string.portfolio_net, data.progress.net, "contribution_net")
                                data.progress.percent?.let { Text(stringResource(R.string.portfolio_progress, it.toPlainString()), modifier = Modifier.testTag("contribution_progress")) }
                                if (data.progress.coverage == BudgetCoverage.PARTIAL) Text(stringResource(R.string.portfolio_partial))
                                if (data.progress.coverage in listOf(BudgetCoverage.UNTRACKED, BudgetCoverage.FUTURE)) Text(stringResource(R.string.portfolio_untracked))
                                if (data.progress.target == null) Text(stringResource(R.string.portfolio_no_plan))
                                TextButton(onClick = model::openPlan, enabled = !state.busy, modifier = Modifier.testTag("contribution_plan_edit")) { Text(stringResource(R.string.portfolio_plan)) }
                                if (data.progress.target != null) TextButton(onClick = model::clearPlan, enabled = !state.busy, modifier = Modifier.testTag("contribution_plan_remove")) { Text(stringResource(R.string.portfolio_plan_remove)) }
                            }
                        }
                        Button(onClick = { model.openMovement() }, enabled = !state.busy, modifier = Modifier.testTag("investment_movement_add")) { Text(stringResource(R.string.portfolio_record)) }
                    }
                    if (data.movements.isEmpty()) item { Text(stringResource(R.string.portfolio_no_rows)) }
                    items(data.movements, key = { "movement_${it.id}" }) { row ->
                        OutlinedCard(Modifier.fillMaxWidth().testTag("movement_${row.id}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text((row.investmentAction ?: InvestmentAction.CONTRIBUTION).label() + " · " + row.date)
                                Text((if (row.isRedemption) "+" else "−") + amount(row.amount), style = MaterialTheme.typography.titleLarge)
                                Text(state.source!!.holdings.firstOrNull { it.id == row.holdingId }?.name ?: stringResource(R.string.portfolio_unassigned))
                                if (row.note.isNotBlank()) Text(row.note)
                                Row {
                                    TextButton(onClick = { model.openMovement(row) }, enabled = !state.busy, modifier = Modifier.testTag("movement_edit_${row.id}")) { Text(stringResource(R.string.ui_edit_464c4f)) }
                                    TextButton(onClick = { delete("movement", row.id) }, enabled = !state.busy, modifier = Modifier.testTag("movement_delete_${row.id}")) { Text(stringResource(R.string.ui_delete_e2d0a5)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.holdingEditor?.let { HoldingForm(it, state, model) }
    state.valueEditor?.let { ValueForm(it, state, model) }
    state.movementEditor?.let { MovementForm(it, state, model) }
    state.targetEditor?.let { editor ->
        EditorDialog(R.string.portfolio_set_targets, state, model::closeEditors, model::saveTargets, "allocation_save") {
            val sum = editor.values.map { (InvestmentRules.percent(it) as? AppResult.Success)?.value }
            Text(stringResource(R.string.portfolio_target_total, if (sum.all { it != null }) BigDecimal.valueOf(sum.sumOf { it!!.toLong() }, 2).toPlainString() else "—"))
            AllocationBucket.entries.forEach { bucket ->
                FormField(editor[bucket].orEmpty(), bucket.label() + " (%)", "allocation_input_$bucket", state.busy, decimal = true, done = bucket == AllocationBucket.OTHER) { model.editTarget(bucket, it) }
            }
        }
    }
    state.planEditor?.let { text ->
        EditorDialog(R.string.portfolio_plan_target, state, model::closeEditors, model::savePlan, "contribution_plan_save") {
            FormField(text, stringResource(R.string.portfolio_amount), "contribution_plan_amount", state.busy, decimal = true, done = true, edit = model::editPlan)
            Text(stringResource(R.string.portfolio_plan_hint))
        }
    }
    if (deletingKind != null) AlertDialog(onDismissRequest = { if (!state.busy) deletingKind = null }, title = { Text(stringResource(R.string.wealth_delete_title)) },
        text = { Text(stringResource(when (deletingKind) { "holding" -> R.string.portfolio_holding_delete_body; "value" -> R.string.portfolio_value_delete_body; else -> R.string.portfolio_movement_delete_body })) },
        confirmButton = { TextButton(onClick = {
            when (deletingKind) { "holding" -> model.deleteHolding(deletingId); "value" -> model.deleteValue(deletingId); else -> model.deleteMovement(deletingId) }
            deletingKind = null
        }, enabled = !state.busy, modifier = Modifier.testTag("portfolio_delete_confirm")) { Text(stringResource(R.string.ui_delete_e2d0a5)) } },
        dismissButton = { TextButton(onClick = { deletingKind = null }, enabled = !state.busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
}

@Composable private fun HoldingCard(row: HoldingStatus, values: List<HoldingValue>, enabled: Boolean, model: PortfolioViewModel,
    deleteHolding: () -> Unit, deleteValue: (String) -> Unit) {
    val holding = row.holding
    var menu by remember { mutableStateOf(false) }
    var details by rememberSaveable(holding.id) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().testTag("holding_${holding.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(holding.name, style = MaterialTheme.typography.titleLarge)
                    Text(holding.bucket.label() + if (holding.symbol.isBlank()) "" else " · ${holding.symbol}")
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.testTag("holding_more_${holding.id}")) { Icon(Icons.Default.MoreVert, stringResource(R.string.portfolio_edit)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.portfolio_edit)) }, onClick = { menu = false; model.openHolding(holding) }, modifier = Modifier.testTag("holding_edit_${holding.id}"))
                        DropdownMenuItem(text = { Text(stringResource(if (holding.active) R.string.wealth_archive else R.string.wealth_restore)) }, onClick = { menu = false; model.toggle(holding) }, modifier = Modifier.testTag("holding_toggle_${holding.id}"))
                        DropdownMenuItem(text = { Text(stringResource(R.string.wealth_delete_item)) }, onClick = { menu = false; deleteHolding() }, modifier = Modifier.testTag("holding_delete_${holding.id}"))
                    }
                }
            }
            Text(amount(row.latest?.amount), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("holding_amount_${holding.id}"))
            if (!holding.active) Text(stringResource(R.string.wealth_archived))
            if (row.latest == null) Text(stringResource(R.string.wealth_unknown)) else Text(stringResource(R.string.portfolio_observation, row.latest.date) + " · " + stringResource(R.string.wealth_age, row.ageDays.toString()))
            Row {
                TextButton(onClick = { model.openValue(holding) }, enabled = enabled, modifier = Modifier.testTag("holding_update_${holding.id}")) { Text(stringResource(R.string.portfolio_value)) }
                TextButton(onClick = { details = !details }, modifier = Modifier.testTag("holding_history_${holding.id}")) { Text(stringResource(if (details) R.string.wealth_hide_values else R.string.wealth_value_history)) }
            }
            if (details) {
                Text(stringResource(R.string.wealth_acquired_value, holding.acquiredOn))
                holding.cost?.let { Text(stringResource(R.string.portfolio_cost_note, amount(it))) }
                if (holding.notes.isNotBlank()) Text(holding.notes)
                if (values.isEmpty()) Text(stringResource(R.string.wealth_no_values))
                values.sortedByDescending { it.date }.forEach { value ->
                    HorizontalDivider()
                    Text(stringResource(R.string.wealth_value_row, value.date, amount(value.amount)), modifier = Modifier.testTag("holding_value_${value.id}"))
                    if (value.notes.isNotBlank()) Text(value.notes)
                    Row {
                        TextButton(onClick = { model.openValue(holding, value) }, enabled = enabled, modifier = Modifier.testTag("holding_value_edit_${value.id}")) { Text(stringResource(R.string.ui_edit_464c4f)) }
                        TextButton(onClick = { deleteValue(value.id) }, enabled = enabled, modifier = Modifier.testTag("holding_value_delete_${value.id}")) { Text(stringResource(R.string.ui_delete_e2d0a5)) }
                    }
                }
            }
        }
    }
}
@Composable private fun HoldingForm(editor: HoldingEditor, state: PortfolioUiState, model: PortfolioViewModel) {
    var optional by rememberSaveable(editor.id) { mutableStateOf(editor.cost.isNotBlank() || editor.symbol.isNotBlank() || editor.notes.isNotBlank()) }
    EditorDialog(if (editor.id == null) R.string.portfolio_add else R.string.portfolio_edit, state, model::closeEditors, model::saveHolding, "holding_save") {
        FormField(editor.name, stringResource(R.string.wealth_name), "holding_name", state.busy) { model.editHolding(editor.copy(name = it)) }
        MenuChoice(stringResource(R.string.portfolio_bucket) + ": " + editor.bucket.label(), AllocationBucket.entries.filter { it != AllocationBucket.CASH }.map { it to it.label() }, !state.busy, "holding_bucket") { model.editHolding(editor.copy(bucket = it)) }
        FormField(editor.acquired, stringResource(R.string.wealth_acquired), "holding_acquired", state.busy) { model.editHolding(editor.copy(acquired = it)) }
        if (editor.id == null) {
            FormField(editor.amount, stringResource(R.string.wealth_value_amount), "holding_amount", state.busy, decimal = true) { model.editHolding(editor.copy(amount = it)) }
            FormField(editor.valueDate, stringResource(R.string.portfolio_date), "holding_date", state.busy, done = true) { model.editHolding(editor.copy(valueDate = it)) }
        }
        TextButton(onClick = { optional = !optional }, modifier = Modifier.testTag("holding_optional")) { Text(stringResource(R.string.portfolio_edit)) }
        if (optional) {
            FormField(editor.symbol, stringResource(R.string.portfolio_symbol), "holding_symbol", state.busy) { model.editHolding(editor.copy(symbol = it)) }
            FormField(editor.cost, stringResource(R.string.portfolio_cost), "holding_cost", state.busy, decimal = true) { model.editHolding(editor.copy(cost = it)) }
            FormField(editor.notes, stringResource(R.string.wealth_notes), "holding_notes", state.busy, done = true) { model.editHolding(editor.copy(notes = it)) }
        }
        Text(stringResource(R.string.portfolio_format_hint))
    }
}
@Composable private fun ValueForm(editor: HoldingValueEditor, state: PortfolioUiState, model: PortfolioViewModel) {
    EditorDialog(R.string.portfolio_value, state, model::closeEditors, model::saveValue, "holding_value_save") {
        FormField(editor.amount, stringResource(R.string.wealth_value_amount), "holding_value_amount", state.busy, decimal = true) { model.editValue(editor.copy(amount = it)) }
        FormField(editor.date, stringResource(R.string.portfolio_date), "holding_value_date", state.busy) { model.editValue(editor.copy(date = it)) }
        FormField(editor.notes, stringResource(R.string.wealth_notes), "holding_value_notes", state.busy, done = true) { model.editValue(editor.copy(notes = it)) }
    }
}
@Composable private fun MovementForm(editor: InvestmentMovementEditor, state: PortfolioUiState, model: PortfolioViewModel) {
    EditorDialog(R.string.portfolio_record, state, model::closeEditors, model::saveMovement, "investment_movement_save") {
        MenuChoice(editor.action.label(), InvestmentAction.entries.map { it to it.label() }, !state.busy, "investment_action") { model.editMovement(editor.copy(action = it)) }
        val holdings = state.source?.holdings.orEmpty().filter { it.active || it.id == editor.holdingId }
        MenuChoice(holdings.firstOrNull { it.id == editor.holdingId }?.name ?: stringResource(R.string.portfolio_unassigned),
            listOf<String?>(null).map { it to stringResource(R.string.portfolio_unassigned) } + holdings.map { it.id to it.name }, !state.busy, "investment_holding") {
            model.editMovement(editor.copy(holdingId = it, valueAfter = ""))
        }
        FormField(editor.amount, stringResource(R.string.portfolio_amount), "investment_amount", state.busy, decimal = true) { model.editMovement(editor.copy(amount = it)) }
        val categories = state.source?.categories.orEmpty().filter { it.transactionType == TransactionType.INVESTMENT && (it.active || it.id == editor.categoryId) }
        MenuChoice(categories.firstOrNull { it.id == editor.categoryId }?.name ?: stringResource(R.string.ui_choose_a_category_42d2f2), categories.map { it.id to it.name }, !state.busy, "investment_category") { model.editMovement(editor.copy(categoryId = it)) }
        FormField(editor.date, stringResource(R.string.portfolio_date), "investment_date", state.busy) { model.editMovement(editor.copy(date = it)) }
        if (editor.holdingId != null) {
            FormField(editor.valueAfter, stringResource(R.string.portfolio_after), "investment_value_after", state.busy, decimal = true) { model.editMovement(editor.copy(valueAfter = it)) }
            Text(stringResource(R.string.portfolio_flow_after_hint))
        }
        FormField(editor.note, stringResource(R.string.wealth_notes), "investment_note", state.busy, done = true) { model.editMovement(editor.copy(note = it)) }
    }
}
@Composable private fun EditorDialog(title: Int, state: PortfolioUiState, close: () -> Unit, save: () -> Unit, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(title)) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("portfolio_editor_error")) }
        }
    }, confirmButton = { TextButton(onClick = { keyboard?.hide(); save() }, enabled = !state.busy, modifier = Modifier.testTag(tag)) { Text(stringResource(R.string.portfolio_save)) } },
        dismissButton = { TextButton(onClick = close, enabled = !state.busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
}
@Composable private fun FormField(text: String, label: String, tag: String, busy: Boolean, decimal: Boolean = false, done: Boolean = false, edit: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(text, edit, label = { Text(label) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag(tag),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text, imeAction = if (done) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
}
@Composable private fun <T> MenuChoice(selected: String, choices: List<Pair<T, String>>, enabled: Boolean, tag: String, choose: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled && choices.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(selected) }
        DropdownMenu(open, { open = false }) { choices.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; choose(value) }) } }
    }
}
