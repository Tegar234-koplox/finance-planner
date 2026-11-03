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

data class MarketQuoteRow(val record: QuoteRecord, val freshness: QuoteFreshness)
data class MarketUiState(val loading: Boolean = true, val storageError: Boolean = false,
    val rows: List<MarketQuoteRow> = emptyList(), val refreshing: Boolean = false,
    val canRefresh: Boolean = false, val checkedAt: Instant = Instant.EPOCH)

@HiltViewModel
class MarketViewModel @Inject constructor(private val repository: QuoteRepository,
    private val clock: Clock, private val policy: QuoteCachePolicy) : ViewModel() {
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
        mutable.update { it.copy(loading = true, storageError = false, rows = emptyList(), canRefresh = false) }
        observation = viewModelScope.launch {
            combine(repository.observeQuotes(), checks) { data, _ -> data }.collect { result ->
                val now = clock.instant()
                if (result is AppResult.Failure || writeFailed) mutable.update {
                    it.copy(loading = false, storageError = true, rows = emptyList(), canRefresh = false, checkedAt = now)
                } else {
                    val records = (result as AppResult.Success).value
                    val supported = repository.providers.map { it.id }.toSet()
                    mutable.update { it.copy(loading = false, storageError = false,
                        rows = records.sortedWith(compareBy({ it.key.provider }, { it.key.market }, { it.key.symbol }))
                            .map { row -> MarketQuoteRow(row, policy.freshness(row.quote, now)) },
                        canRefresh = records.any { row -> row.key.provider in supported && row.retryAt?.isAfter(now) != true },
                        checkedAt = now) }
                }
            }
        }
    }
    fun refresh() {
        if (refreshJob?.isActive == true || !mutable.value.canRefresh || mutable.value.storageError) return
        val keys = mutable.value.rows.map { it.record }.filter { row ->
            repository.providers.any { it.id == row.key.provider } && row.retryAt?.isAfter(clock.instant()) != true
        }.map { it.key }
        mutable.update { it.copy(refreshing = true) }
        refreshJob = viewModelScope.launch {
            try {
                for (key in keys) if (repository.refresh(key) is AppResult.Failure) {
                    writeFailed = true
                    mutable.update { it.copy(storageError = true, rows = emptyList(), canRefresh = false) }
                    break
                }
            } finally {
                mutable.update { it.copy(refreshing = false) }
                recheckAge()
            }
        }
    }
}
