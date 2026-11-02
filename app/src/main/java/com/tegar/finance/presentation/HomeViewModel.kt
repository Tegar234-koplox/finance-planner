package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(val loading: Boolean = true, val error: AppError? = null,
    val dashboard: HomeDashboard? = null, val categories: Map<String, Category> = emptyMap())

@HiltViewModel
class HomeViewModel @Inject constructor(private val source: BudgetRepository,
    private val categories: CategoryRepository, private val clock: Clock) : ViewModel() {
    private val mutable = MutableStateFlow(HomeUiState())
    val state = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)
    private var observation: Job? = null
    init { retry() }
    fun refresh() { refreshes.update { it + 1 } }
    fun retry() {
        observation?.cancel()
        mutable.value = HomeUiState()
        observation = viewModelScope.launch {
            combine(source.observeBudgets(), categories.observeCategories(), refreshes) { data, cats, _ -> data to cats }
                .collect { (data, cats) ->
                    if (data is AppResult.Failure || cats is AppResult.Failure) {
                        mutable.value = HomeUiState(loading = false,
                            error = (data as? AppResult.Failure)?.error ?: (cats as AppResult.Failure).error)
                        return@collect
                    }
                    val snapshot = (data as AppResult.Success).value
                    if (snapshot == null) { mutable.value = HomeUiState(); return@collect }
                    when (val result = HomeDashboardCalculator.calculate(snapshot, DatePeriods.today(clock, snapshot.ledger.reportingZone))) {
                        is AppResult.Failure -> mutable.value = HomeUiState(loading = false, error = result.error)
                        is AppResult.Success -> mutable.value = HomeUiState(loading = false, dashboard = result.value,
                            categories = (cats as AppResult.Success).value.associateBy { it.id })
                    }
                }
        }
    }
}
