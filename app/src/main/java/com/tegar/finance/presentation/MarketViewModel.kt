package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MarketQuoteRow(val record: QuoteRecord, val freshness: QuoteFreshness, val entry: WatchlistEntry? = null)
data class WatchlistEditor(val id: String? = null, val draft: WatchlistDraft = WatchlistDraft("", "", "XNAS"))
data class KeyEditor(val value: String = "") {
    override fun toString() = "KeyEditor([redacted])"
}
data class MarketUiState(val loading: Boolean = true, val storageError: Boolean = false,
    val rows: List<MarketQuoteRow> = emptyList(), val refreshing: Boolean = false,
    val canRefresh: Boolean = false, val checkedAt: Instant = Instant.EPOCH,
    val managed: Boolean = false, val hasKey: Boolean = false, val keyError: Boolean = false,
    val saving: Boolean = false, val editor: WatchlistEditor? = null, val keyEditor: KeyEditor? = null,
    val deleteEntry: WatchlistEntry? = null, val disconnect: Boolean = false, val formError: AppError? = null)

@HiltViewModel
class MarketViewModel @Inject constructor(private val repository: QuoteRepository,
    private val clock: Clock, private val policy: QuoteCachePolicy,
    private val watchlist: WatchlistRepository? = null, private val keys: ProviderKeyStore? = null) : ViewModel() {
    private val mutable = MutableStateFlow(MarketUiState())
    val state = mutable.asStateFlow()
    private val checks = MutableStateFlow(0)
    private var observation: Job? = null
    private var refreshJob: Job? = null
    private var writeFailed = false
    init { retry() }
    fun recheckAge() { checks.update { it + 1 } }
    fun retry() {
        observation?.cancel()
        writeFailed = false
        mutable.update { it.copy(loading = true, storageError = false, rows = emptyList(), canRefresh = false, managed = watchlist != null) }
        observation = viewModelScope.launch {
            val entries = watchlist?.observe() ?: flowOf(AppResult.Success(emptyList<WatchlistEntry>()))
            val status = keys?.observeStatus() ?: flowOf(AppResult.Success(true))
            combine(repository.observeQuotes(), entries, status, checks) { data, list, key, _ -> Triple(data, list, key) }.collect { (result, list, key) ->
                val now = clock.instant()
                val hasKey = (key as? AppResult.Success)?.value == true
                if (result is AppResult.Failure || list is AppResult.Failure || writeFailed) mutable.update {
                    it.copy(loading = false, storageError = true, rows = emptyList(), canRefresh = false, checkedAt = now,
                        hasKey = hasKey, keyError = key is AppResult.Failure)
                } else {
                    val records = (result as AppResult.Success).value
                    val supported = repository.providers.map { it.id }.toSet()
                    val cached = records.associateBy { it.key }
                    val rows = if (watchlist == null) records.sortedWith(compareBy({ it.key.provider }, { it.key.market }, { it.key.symbol }))
                        .map { row -> MarketQuoteRow(row, policy.freshness(row.quote, now)) }
                    else (list as AppResult.Success).value.map { entry ->
                        val row = cached[entry.key] ?: QuoteRecord(entry.key, lastAttemptAt = Instant.EPOCH)
                        MarketQuoteRow(row, policy.freshness(row.quote, now), entry)
                    }
                    val cooldown = watchlist != null && rows.any { it.record.failure == QuoteFailure.RATE_LIMIT && it.record.retryAt?.isAfter(now) == true }
                    mutable.update { it.copy(loading = false, storageError = false,
                        rows = rows, hasKey = hasKey, keyError = key is AppResult.Failure,
                        canRefresh = hasKey && !cooldown && rows.any { row -> row.record.key.provider in supported && row.record.retryAt?.isAfter(now) != true },
                        checkedAt = now) }
                }
            }
        }
    }
    fun refresh() {
        if (busy() || !mutable.value.canRefresh || mutable.value.storageError) return
        var eligible = mutable.value.rows.map { it.record }.filter { row ->
            repository.providers.any { it.id == row.key.provider } && row.retryAt?.isAfter(clock.instant()) != true
        }
        if (watchlist != null) eligible = eligible.sortedBy { it.lastSuccessAt ?: Instant.EPOCH }.take(6)
        val selected = eligible.map { it.key }
        mutable.update { it.copy(refreshing = true) }
        refreshJob = viewModelScope.launch {
            try {
                for (key in selected) {
                    when (val result = repository.refresh(key)) {
                        is AppResult.Failure -> {
                            writeFailed = true
                            mutable.update { it.copy(storageError = true, rows = emptyList(), canRefresh = false) }
                            break
                        }
                        is AppResult.Success -> if (result.value.failure in listOf(QuoteFailure.RATE_LIMIT, QuoteFailure.AUTHENTICATION, QuoteFailure.NOT_CONFIGURED)) break
                    }
                }
            } finally {
                mutable.update { it.copy(refreshing = false) }
                recheckAge()
            }
        }
    }
    fun cancelRefresh() { refreshJob?.cancel() }
    private fun busy() = mutable.value.saving || refreshJob?.isActive == true
    fun openEntry(entry: WatchlistEntry? = null) { if (!busy()) mutable.update {
        it.copy(editor = entry?.let { e -> WatchlistEditor(e.id, WatchlistDraft(e.name, e.key.symbol, e.key.market, e.kind)) } ?: WatchlistEditor(), formError = null)
    } }
    fun editEntry(draft: WatchlistDraft) { if (!busy()) mutable.update { it.copy(editor = it.editor?.copy(draft = draft), formError = null) } }
    fun closeEditor() { if (!busy()) mutable.update { it.copy(editor = null, keyEditor = null, deleteEntry = null, disconnect = false, formError = null) } }
    fun saveEntry() {
        val editor = mutable.value.editor ?: return
        val source = watchlist ?: return
        write { source.save(editor.id, editor.draft) }
    }
    fun askDelete(entry: WatchlistEntry) { if (!busy()) mutable.update { it.copy(deleteEntry = entry, formError = null) } }
    fun deleteEntry() {
        val entry = mutable.value.deleteEntry ?: return
        val source = watchlist ?: return
        write { source.delete(entry.id) }
    }
    fun openConnection() { if (!busy()) mutable.update { it.copy(keyEditor = KeyEditor(), formError = null) } }
    fun editKey(value: String) { if (!busy()) mutable.update { it.copy(keyEditor = KeyEditor(value), formError = null) } }
    fun saveKey() {
        val key = mutable.value.keyEditor?.value?.trim() ?: return
        val source = keys ?: return
        if (!ProviderKeyRules.valid(key)) { mutable.update { it.copy(formError = AppError.INVALID_INPUT) }; return }
        write { source.save(key) }
    }
    fun askDisconnect() { if (!busy()) mutable.update { it.copy(disconnect = true, formError = null) } }
    fun disconnect() { val source = keys ?: return; write { source.clear() } }
    private fun write(work: suspend () -> AppResult<Unit>) {
        if (busy()) return
        mutable.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try {
                when (val result = work()) {
                    is AppResult.Success -> mutable.update { it.copy(editor = null, keyEditor = null, deleteEntry = null, disconnect = false) }
                    is AppResult.Failure -> mutable.update { it.copy(formError = result.error) }
                }
            } finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
