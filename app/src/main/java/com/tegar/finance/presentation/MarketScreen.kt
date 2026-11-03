package com.tegar.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tegar.finance.R
import com.tegar.finance.domain.market.*
import java.text.NumberFormat
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MarketScreen(state: MarketUiState, retry: () -> Unit, refresh: () -> Unit,
    recheckAge: () -> Unit, portfolio: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheckAge() }
    LaunchedEffect(Unit) {
        while (isActive) { recheckAge(); delay(60_000) }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("market_screen").padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.market_prices_title), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f))
                if (state.canRefresh || state.refreshing) IconButton(onClick = refresh,
                    enabled = !state.refreshing, modifier = Modifier.testTag("market_refresh")) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.market_refresh))
                }
                ScreenHelp("market_help") { Text(stringResource(R.string.market_help_body)) }
            }
        }
        if (state.loading || state.refreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.storageError) item {
            Card(Modifier.fillMaxWidth().testTag("market_storage_error")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.market_cache_error))
                    TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
                }
            }
        } else if (!state.loading && state.rows.isEmpty()) item {
            Card(Modifier.fillMaxWidth().testTag("market_empty")) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.market_no_prices), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.market_manual_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = portfolio, modifier = Modifier.testTag("market_portfolio")) {
                        Text(stringResource(R.string.market_open_portfolio))
                    }
                }
            }
        }
        items(state.rows, key = { "${it.record.key.provider}|${it.record.key.market}|${it.record.key.symbol}" }) { row ->
            MarketPriceCard(row)
        }
    }
}

@Composable
private fun MarketPriceCard(row: MarketQuoteRow) {
    val record = row.record
    val quote = record.quote
    val locale = LocalConfiguration.current.locales[0]
    val price = quote?.let {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 18 }.format(it.price)
    }
    val time = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC) }
    Card(Modifier.fillMaxWidth().testTag("market_quote_${record.key.symbol}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(record.key.symbol, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(when (row.freshness) {
                    QuoteFreshness.FRESH -> R.string.market_recent
                    QuoteFreshness.STALE -> R.string.market_stale
                    QuoteFreshness.MISSING -> R.string.market_missing
                }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (quote == null) stringResource(R.string.market_price_unavailable) else "${quote.currency.value} $price",
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("market_price_${record.key.symbol}"))
            Text("${record.key.market} · ${quote?.source ?: record.key.provider}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (quote != null) Text(stringResource(R.string.market_observed, time.format(quote.observedAt)),
                style = MaterialTheme.typography.bodySmall)
            record.failure?.let { reason ->
                Text(stringResource(reason.message()), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("market_quote_failure"))
                record.retryAt?.let { retry -> Text(stringResource(R.string.market_retry_after, time.format(retry)),
                    style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun QuoteFailure.message() = when (this) {
    QuoteFailure.OFFLINE -> R.string.market_error_offline
    QuoteFailure.TIMEOUT -> R.string.market_error_timeout
    QuoteFailure.AUTHENTICATION -> R.string.market_error_auth
    QuoteFailure.RATE_LIMIT -> R.string.market_error_limit
    QuoteFailure.PROVIDER -> R.string.market_error_provider
    QuoteFailure.INVALID_RESPONSE -> R.string.market_error_invalid
    QuoteFailure.NO_DATA -> R.string.market_error_empty
    QuoteFailure.UNSUPPORTED -> R.string.market_error_unsupported
    QuoteFailure.NOT_CONFIGURED -> R.string.market_error_unconfigured
}
