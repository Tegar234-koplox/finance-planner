package com.tegar.finance.presentation

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tegar.finance.R
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.AppError

@Composable internal fun marketVenue(market: String): String = when (market) {
    "XNAS" -> "NASDAQ"
    "XNYS" -> "NYSE"
    "ARCX" -> "NYSE Arca"
    else -> stringResource(R.string.market_us_indices)
}
@Composable private fun InstrumentKind.label() = stringResource(when (this) {
    InstrumentKind.STOCK -> R.string.market_stock
    InstrumentKind.ETF -> R.string.market_etf
    InstrumentKind.INDEX -> R.string.market_index
})

@Composable
internal fun MarketDialogs(state: MarketUiState, model: MarketViewModel) {
    state.editor?.let { editor ->
        val draft = editor.draft
        AlertDialog(onDismissRequest = model::closeEditor,
            title = { Text(stringResource(if (editor.id == null) R.string.market_add else R.string.market_edit)) },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.name, { model.editEntry(draft.copy(name = it)) }, singleLine = true, enabled = !state.saving,
                    label = { Text(stringResource(R.string.market_name)) }, modifier = Modifier.testTag("market_name"))
                OutlinedTextField(draft.symbol, { model.editEntry(draft.copy(symbol = it)) }, singleLine = true, enabled = !state.saving,
                    label = { Text(stringResource(R.string.market_symbol)) }, modifier = Modifier.testTag("market_symbol"))
                var types by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { types = true }, enabled = !state.saving, modifier = Modifier.testTag("market_kind")) { Text(draft.kind.label()) }
                    DropdownMenu(types, onDismissRequest = { types = false }) {
                        InstrumentKind.entries.forEach { kind -> DropdownMenuItem(text = { Text(kind.label()) },
                            onClick = { types = false; model.editEntry(draft.copy(kind = kind, market = if (kind == InstrumentKind.INDEX) "US_INDEX" else if (draft.market == "US_INDEX") "XNAS" else draft.market)) },
                            modifier = Modifier.testTag("market_kind_option_${kind.name}")) }
                    }
                }
                if (draft.kind != InstrumentKind.INDEX) {
                    var venues by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { venues = true }, enabled = !state.saving, modifier = Modifier.testTag("market_venue")) { Text(marketVenue(draft.market)) }
                        DropdownMenu(venues, onDismissRequest = { venues = false }) {
                            WatchlistRules.STOCK_MARKETS.forEach { venue -> DropdownMenuItem(text = { Text(marketVenue(venue)) },
                                onClick = { venues = false; model.editEntry(draft.copy(market = venue)) }, modifier = Modifier.testTag("market_venue_$venue")) }
                        }
                    }
                } else Text(stringResource(R.string.market_index_access), style = MaterialTheme.typography.bodySmall)
                MarketFormError(state.formError)
            } },
            confirmButton = { TextButton(onClick = model::saveEntry, enabled = !state.saving, modifier = Modifier.testTag("market_save")) { Text(stringResource(R.string.market_save)) } },
            dismissButton = { TextButton(onClick = model::closeEditor, enabled = !state.saving) { Text(stringResource(R.string.market_cancel)) } })
    }
    state.keyEditor?.let { editor ->
        val handler = LocalUriHandler.current
        val context = LocalContext.current
        val browserError = stringResource(R.string.market_browser_error)
        fun open(url: String) { try { handler.openUri(url) } catch (_: ActivityNotFoundException) { Toast.makeText(context, browserError, Toast.LENGTH_SHORT).show() } }
        AlertDialog(onDismissRequest = model::closeEditor, title = { Text("Twelve Data") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.market_key_explanation), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(editor.value, model::editKey, enabled = !state.saving, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    label = { Text(stringResource(R.string.market_api_key)) }, modifier = Modifier.testTag("market_api_key"))
                Row {
                    TextButton(onClick = { open("https://twelvedata.com/pricing") }) { Text(stringResource(R.string.market_create_account)) }
                    TextButton(onClick = { open("https://twelvedata.com/terms") }) { Text(stringResource(R.string.market_terms)) }
                }
                if (state.hasKey) TextButton(onClick = { model.closeEditor(); model.askDisconnect() }, enabled = !state.saving,
                    modifier = Modifier.testTag("market_disconnect")) { Text(stringResource(R.string.market_remove_key)) }
                MarketFormError(state.formError, key = true)
            } },
            confirmButton = { TextButton(onClick = model::saveKey, enabled = !state.saving, modifier = Modifier.testTag("market_save_key")) { Text(stringResource(R.string.market_save_key)) } },
            dismissButton = { TextButton(onClick = model::closeEditor, enabled = !state.saving) { Text(stringResource(R.string.market_cancel)) } })
    }
    if (state.deleteEntry != null || state.disconnect) AlertDialog(onDismissRequest = model::closeEditor,
        title = { Text(stringResource(if (state.disconnect) R.string.market_remove_key else R.string.market_remove)) },
        text = { Column {
            Text(if (state.disconnect) stringResource(R.string.market_remove_key_confirm) else stringResource(R.string.market_remove_confirm, state.deleteEntry!!.name))
            MarketFormError(state.formError)
        } },
        confirmButton = { TextButton(onClick = { if (state.disconnect) model.disconnect() else model.deleteEntry() }, enabled = !state.saving,
            modifier = Modifier.testTag("market_confirm_remove")) { Text(stringResource(R.string.market_remove)) } },
        dismissButton = { TextButton(onClick = model::closeEditor, enabled = !state.saving) { Text(stringResource(R.string.market_cancel)) } })
}

@Composable private fun MarketFormError(error: AppError?, key: Boolean = false) {
    if (error != null) Text(stringResource(when (error) {
        AppError.DUPLICATE_INSTRUMENT -> R.string.market_duplicate
        AppError.WATCHLIST_LIMIT -> R.string.market_limit
        AppError.INVALID_INPUT -> if (key) R.string.market_invalid_key else R.string.market_invalid_entry
        else -> R.string.market_save_error
    }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("market_form_error"))
}
