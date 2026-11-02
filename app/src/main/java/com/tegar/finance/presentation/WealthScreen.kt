package com.tegar.finance.presentation
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tegar.finance.R
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.domain.model.*
@Composable private fun kindName(kind: WealthKind) = stringResource(if (kind == WealthKind.ASSET) R.string.wealth_asset else R.string.wealth_liability)
@Composable private fun categoryName(category: WealthCategory) = stringResource(when (category) {
    WealthCategory.PROPERTY -> R.string.wealth_property; WealthCategory.VEHICLE -> R.string.wealth_vehicle
    WealthCategory.PERSONAL -> R.string.wealth_personal; WealthCategory.LOAN -> R.string.wealth_loan
    WealthCategory.CARD -> R.string.wealth_card; WealthCategory.OTHER -> R.string.wealth_other
})
@Composable private fun wealthError(error: AppError) = stringResource(when (error) {
    AppError.INVALID_INPUT, AppError.DATE_BEFORE_COVERAGE -> R.string.wealth_error_input
    AppError.CURRENCY_MISMATCH -> R.string.wealth_error_currency
    AppError.OUT_OF_RANGE -> R.string.wealth_error_range
    AppError.NOT_FOUND -> R.string.wealth_error_missing
    AppError.WEALTH_INCOMPLETE -> R.string.wealth_error_incomplete
    AppError.WEALTH_DATE_CONFLICT -> R.string.wealth_error_conflict
    AppError.SNAPSHOT_EXISTS -> R.string.wealth_snapshot_exists
    else -> R.string.wealth_error_storage
})
@Composable private fun amount(money: Money?) = money?.let(MoneyText::format) ?: stringResource(R.string.ui_not_available_67a926)
@Composable private fun Metric(label: Int, money: Money?, tag: String) {
    if (tag == "wealth_total" || tag.startsWith("wealth_saved_total_")) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
        Text(amount(money), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag(tag))
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(amount(money), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(tag))
    }
}
@Composable
fun WealthScreen(state: WealthUiState, model: WealthViewModel, openInvestments: (() -> Unit)? = null) {
    LifecycleResumeEffect(Unit) { model.refresh(); onPauseOrDispose { } }
    var deletion by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf("") }
    var deleteName by rememberSaveable { mutableStateOf("") }
    var captureConfirmation by rememberSaveable { mutableStateOf(false) }
    fun delete(type: String, id: String, name: String = "") { deletion = type; deleteId = id; deleteName = name }
    LazyColumn(Modifier.fillMaxSize().testTag("wealth_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.wealth_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                ScreenHelp("wealth_help") {
                    Text(stringResource(R.string.wealth_intro))
                    Text(stringResource(R.string.wealth_scope))
                    Text(stringResource(R.string.wealth_investment_scope))
                    Text(stringResource(R.string.wealth_history_hint))
                    Text(stringResource(R.string.wealth_archive_hint))
                    Text(stringResource(R.string.wealth_form_hint))
                    Text(stringResource(R.string.wealth_cost_hint))
                }
            }
            Button(onClick = { model.openItem() }, enabled = !state.busy && state.source != null,
                modifier = Modifier.testTag("wealth_add")) { Text(stringResource(R.string.wealth_add)) }
            TextButton(onClick = model::refresh, enabled = !state.busy) { Text(stringResource(R.string.wealth_refresh)) }
        }
        if (state.loading) item { CircularProgressIndicator(Modifier.testTag("wealth_loading")) }
        if (state.readError != null) item {
            Text(wealthError(state.readError), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.wealth_read_error), modifier = Modifier.testTag("wealth_read_error"))
            Button(onClick = model::retry) { Text(stringResource(R.string.ui_try_again_d8b839)) }
        }
        if (state.error != null && state.itemEditor == null && state.valueEditor == null) item {
            Text(wealthError(state.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("wealth_error"))
            TextButton(onClick = model::clearError) { Text(stringResource(R.string.ui_dismiss_48845b)) }
        }
        if (state.captureSaved) item { Text(stringResource(R.string.wealth_capture_saved), modifier = Modifier.testTag("wealth_saved")) }
        state.overview?.let { overview ->
            item {
                Card(Modifier.fillMaxWidth().testTag("wealth_summary")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric(R.string.wealth_total, overview.total, "wealth_total")
                        Metric(R.string.ui_recorded_cash_44197f, overview.cash, "wealth_cash")
                        Metric(R.string.wealth_asset_total, overview.assets, "wealth_assets")
                        Metric(R.string.portfolio_invested, overview.investments, "wealth_investments")
                        if (openInvestments != null) TextButton(onClick = openInvestments, modifier = Modifier.testTag("wealth_investments_open")) { Text(stringResource(R.string.portfolio_open)) }
                        Metric(R.string.wealth_debt_total, overview.liabilities, "wealth_liabilities")
                        if (overview.cashOverflow || overview.assetsOverflow || overview.liabilitiesOverflow || overview.investmentsOverflow || overview.totalOverflow)
                            Text(stringResource(R.string.wealth_error_range), color = MaterialTheme.colorScheme.error)
                        if (overview.missingInvestments > 0) Text(stringResource(R.string.portfolio_unknown_holdings))
                        if (overview.missingAssets + overview.missingLiabilities > 0) Text(stringResource(R.string.wealth_missing,
                            overview.missingAssets.toString(), overview.missingLiabilities.toString()), modifier = Modifier.testTag("wealth_missing"))
                        if (overview.futureValues + overview.futureItems > 0) Text(stringResource(R.string.wealth_future,
                            overview.futureValues.toString(), overview.futureItems.toString()))
                        val exists = overview.history.any { it.date == overview.today }
                        Button(onClick = { captureConfirmation = true }, enabled = !state.busy && overview.total != null && !exists,
                            modifier = Modifier.testTag("wealth_capture")) { Text(stringResource(R.string.wealth_capture)) }
                        if (exists) Text(stringResource(R.string.wealth_snapshot_exists), modifier = Modifier.testTag("wealth_exists"))
                    }
                }
            }
            item { Text(stringResource(R.string.wealth_current_items), style = MaterialTheme.typography.titleLarge) }
            if (overview.items.isEmpty()) item { Text(stringResource(R.string.wealth_empty)) }
            items(overview.items, key = { "item_${it.item.id}" }) { status ->
                WealthItemCard(status, state.source!!.values.filter { it.itemId == status.item.id }, state.busy, model,
                    deleteItem = { delete("item", status.item.id, status.item.name) }, deleteValue = { delete("value", it) })
            }
            item {
                Text(stringResource(R.string.wealth_history_title), style = MaterialTheme.typography.titleLarge)
                if (overview.history.isEmpty()) Text(stringResource(R.string.wealth_history_empty), modifier = Modifier.testTag("wealth_history_empty"))
                if (overview.historyChange != null) Text(stringResource(R.string.wealth_history_change, amount(overview.historyChange)), modifier = Modifier.testTag("wealth_history_change"))
                if (overview.historyScopeChanged) Text(stringResource(R.string.portfolio_wealth_scope_changed), modifier = Modifier.testTag("wealth_scope_changed"))
                if (overview.historyChangeOverflow) Text(stringResource(R.string.wealth_error_range))
            }
            items(overview.history, key = { "capture_${it.id}" }) { capture ->
                CaptureCard(capture, state.busy) { delete("capture", capture.id) }
            }
        }
    }
    state.itemEditor?.let { ItemDialog(it, state.busy, state.error, model) }
    state.valueEditor?.let { ValueDialog(it, state.busy, state.error, model) }
    if (deletion != null) AlertDialog(onDismissRequest = { if (!state.busy) deletion = null },
        title = { Text(stringResource(R.string.wealth_delete_title)) }, text = { Text(when (deletion) {
            "item" -> stringResource(R.string.wealth_delete_item_body, deleteName)
            "value" -> stringResource(R.string.wealth_delete_value_body)
            else -> stringResource(R.string.wealth_delete_capture_body)
        }) }, confirmButton = { TextButton(onClick = {
            when (deletion) { "item" -> model.deleteItem(deleteId); "value" -> model.deleteValue(deleteId); else -> model.deleteCapture(deleteId) }
            deletion = null
        }, enabled = !state.busy, modifier = Modifier.testTag("wealth_confirm_delete")) { Text(stringResource(R.string.ui_delete_e2d0a5)) } },
        dismissButton = { TextButton(onClick = { deletion = null }, enabled = !state.busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
    if (captureConfirmation) AlertDialog(onDismissRequest = { if (!state.busy) captureConfirmation = false },
        title = { Text(stringResource(R.string.wealth_capture_confirm_title)) }, text = { Text(stringResource(R.string.wealth_capture_confirm_body)) },
        confirmButton = { TextButton(onClick = { model.capture(); captureConfirmation = false }, enabled = !state.busy,
            modifier = Modifier.testTag("wealth_confirm_capture")) { Text(stringResource(R.string.wealth_capture)) } },
        dismissButton = { TextButton(onClick = { captureConfirmation = false }, enabled = !state.busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
}
@Composable private fun WealthItemCard(status: WealthItemStatus, values: List<WealthValue>, busy: Boolean,
    model: WealthViewModel, deleteItem: () -> Unit, deleteValue: (String) -> Unit) {
    val item = status.item
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().testTag("wealth_item_${item.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleLarge)
            Text(kindName(item.kind) + " · " + categoryName(item.category))
            if (!item.active) Text(stringResource(R.string.wealth_archived))
            if (status.latest == null) Text(stringResource(R.string.wealth_unknown)) else {
                Text(stringResource(R.string.wealth_latest, amount(status.latest.amount), status.latest.date))
                Text(stringResource(R.string.wealth_age, status.ageDays.toString()))
            }
            TextButton(onClick = { model.openValue(item) }, enabled = !busy, modifier = Modifier.testTag("wealth_add_value_${item.id}")) { Text(stringResource(R.string.wealth_record_value)) }
            Row {
            TextButton(onClick = { model.openItem(item) }, enabled = !busy, modifier = Modifier.testTag("wealth_edit_${item.id}")) { Text(stringResource(R.string.wealth_edit)) }
            TextButton(onClick = { model.toggleActive(item) }, enabled = !busy, modifier = Modifier.testTag("wealth_active_${item.id}")) {
                Text(stringResource(if (item.active) R.string.wealth_archive else R.string.wealth_restore))
            }
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("wealth_values_${item.id}")) {
                Text(stringResource(if (expanded) R.string.wealth_hide_values else R.string.wealth_value_history))
            }
            if (expanded) {
                Text(stringResource(R.string.wealth_acquired_value, item.acquiredOn))
                item.acquisitionCost?.let { Text(stringResource(R.string.wealth_cost_value, amount(it))) }
                if (item.notes.isNotBlank()) Text(item.notes)
                if (values.isEmpty()) Text(stringResource(R.string.wealth_no_values))
                values.sortedByDescending { it.date }.forEach { value ->
                    HorizontalDivider()
                    Text(stringResource(R.string.wealth_value_row, value.date, amount(value.amount)), modifier = Modifier.testTag("wealth_value_${value.id}"))
                    if (value.notes.isNotBlank()) Text(value.notes)
                    TextButton(onClick = { model.openValue(item, value) }, enabled = !busy, modifier = Modifier.testTag("wealth_edit_value_${value.id}")) { Text(stringResource(R.string.wealth_edit_value)) }
                    TextButton(onClick = { deleteValue(value.id) }, enabled = !busy, modifier = Modifier.testTag("wealth_delete_value_${value.id}")) { Text(stringResource(R.string.wealth_delete_value)) }
                }
            }
            TextButton(onClick = deleteItem, enabled = !busy, modifier = Modifier.testTag("wealth_delete_${item.id}")) { Text(stringResource(R.string.wealth_delete_item)) }
        }
    }
}
@Composable private fun CaptureCard(capture: WealthCapture, busy: Boolean, delete: () -> Unit) {
    var expanded by rememberSaveable(capture.id) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().testTag("wealth_history_${capture.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.wealth_snapshot_date, capture.date), style = MaterialTheme.typography.titleLarge)
            Metric(R.string.wealth_total, capture.total, "wealth_saved_total_${capture.id}")
            Metric(R.string.ui_recorded_cash_44197f, capture.cash, "wealth_saved_cash_${capture.id}")
            Metric(R.string.wealth_asset_total, capture.assets, "wealth_saved_assets_${capture.id}")
            Metric(R.string.wealth_debt_total, capture.liabilities, "wealth_saved_liabilities_${capture.id}")
            if (capture.method == WealthRules.WITH_INVESTMENTS) {
                Metric(R.string.portfolio_invested, capture.investments, "wealth_saved_investments_${capture.id}")
                Text(stringResource(R.string.portfolio_snapshot_time, capture.capturedAt))
            } else Text(stringResource(R.string.wealth_snapshot_time, capture.capturedAt))
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("wealth_components_${capture.id}")) {
                Text(stringResource(if (expanded) R.string.wealth_hide_components else R.string.wealth_history_components))
            }
            if (expanded) capture.lines.sortedBy { it.name }.forEach { line ->
                Text(stringResource(R.string.wealth_component, line.name, kindName(line.kind), amount(line.amount), line.valuedOn))
            }
            if (expanded) capture.investmentLines.sortedBy { it.name }.forEach { line ->
                Text(stringResource(R.string.portfolio_snapshot_line, line.name, line.bucket.label(), amount(line.amount), line.valuedOn))
            }
            TextButton(onClick = delete, enabled = !busy, modifier = Modifier.testTag("wealth_delete_capture_${capture.id}")) { Text(stringResource(R.string.wealth_delete_capture)) }
        }
    }
}
@Composable private fun ItemDialog(editor: WealthItemEditor, busy: Boolean, error: AppError?, model: WealthViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest = model::closeEditors, title = { Text(stringResource(if (editor.id == null) R.string.wealth_add else R.string.wealth_edit)) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).testTag("wealth_item_form"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(editor.name, R.string.wealth_name, "wealth_name", busy) { model.editItem(editor.copy(name = it)) }
            Choice(stringResource(R.string.wealth_kind) + ": " + kindName(editor.kind), WealthKind.entries.map { it to kindName(it) }, !busy && editor.id == null, "wealth_kind") {
                model.editItem(editor.copy(kind = it, category = if (it == WealthKind.ASSET) WealthCategory.PERSONAL else WealthCategory.LOAN, cost = ""))
            }
            Choice(stringResource(R.string.wealth_category) + ": " + categoryName(editor.category), WealthCategory.entries.filter { WealthRules.categoryValid(editor.kind, it) }.map { it to categoryName(it) }, !busy, "wealth_category") { model.editItem(editor.copy(category = it)) }
            Field(editor.acquired, R.string.wealth_acquired, "wealth_acquired", busy) { model.editItem(editor.copy(acquired = it)) }
            if (editor.kind == WealthKind.ASSET) Field(editor.cost, R.string.wealth_cost, "wealth_cost", busy, decimal = true) { model.editItem(editor.copy(cost = it)) }
            if (editor.id == null) {
                Field(editor.amount, R.string.wealth_value_amount, "wealth_amount", busy, decimal = true) { model.editItem(editor.copy(amount = it)) }
                Field(editor.valueDate, R.string.wealth_value_date, "wealth_date", busy) { model.editItem(editor.copy(valueDate = it)) }
            }
            Field(editor.notes, R.string.wealth_notes, "wealth_notes", busy, done = true) { model.editItem(editor.copy(notes = it)) }
            if (error != null) Text(wealthError(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("wealth_editor_error"))
        } }, confirmButton = { TextButton(onClick = { keyboard?.hide(); model.saveItem() }, enabled = !busy,
            modifier = Modifier.testTag("wealth_save_item")) { Text(stringResource(R.string.wealth_save_item)) } },
        dismissButton = { TextButton(onClick = model::closeEditors, enabled = !busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
}
@Composable private fun ValueDialog(editor: WealthValueEditor, busy: Boolean, error: AppError?, model: WealthViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest = model::closeEditors, title = { Text(stringResource(if (editor.id == null) R.string.wealth_record_value else R.string.wealth_edit_value)) },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(editor.amount, R.string.wealth_value_amount, "wealth_amount", busy, decimal = true) { model.editValue(editor.copy(amount = it)) }
            Field(editor.date, R.string.wealth_value_date, "wealth_date", busy) { model.editValue(editor.copy(date = it)) }
            Field(editor.notes, R.string.wealth_notes, "wealth_notes", busy, done = true) { model.editValue(editor.copy(notes = it)) }
            if (error != null) Text(wealthError(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("wealth_editor_error"))
        } }, confirmButton = { TextButton(onClick = { keyboard?.hide(); model.saveValue() }, enabled = !busy,
            modifier = Modifier.testTag("wealth_save_value")) { Text(stringResource(R.string.wealth_save_value)) } },
        dismissButton = { TextButton(onClick = model::closeEditors, enabled = !busy) { Text(stringResource(R.string.ui_cancel_19766e)) } })
}
@Composable private fun Field(value: String, label: Int, tag: String, busy: Boolean, decimal: Boolean = false, done: Boolean = false, edit: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(value, edit, label = { Text(stringResource(label)) }, enabled = !busy, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text, imeAction = if (done) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }), modifier = Modifier.fillMaxWidth().testTag(tag))
}
@Composable private fun <T> Choice(selected: String, options: List<Pair<T, String>>, enabled: Boolean, tag: String, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(selected) }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { (value, label) ->
            DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; choose(value) })
        } }
    }
}
