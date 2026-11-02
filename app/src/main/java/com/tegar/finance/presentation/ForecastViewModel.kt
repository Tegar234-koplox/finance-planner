package com.tegar.finance.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.core.datetime.DatePeriods
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ForecastUiState(val loading: Boolean = true, val error: AppError? = null,
    val options: ForecastOptions = ForecastOptions(), val forecast: FinancialForecast? = null)

@HiltViewModel
class ForecastViewModel @Inject constructor(private val source: BudgetRepository, private val clock: Clock,
    private val saved: SavedStateHandle) : ViewModel() {
    private val options = MutableStateFlow(ForecastOptions(
        ForecastMethod.entries.firstOrNull { it.name == saved.get<String>("forecast_method") } ?: ForecastMethod.ROLLING,
        saved.get<Int>("forecast_window")?.takeIf { it in listOf(3, 6, 12) } ?: 6))
    private val mutable = MutableStateFlow(ForecastUiState(options = options.value))
    val state = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)
    private var observation: Job? = null
    init { retry() }
    fun selectMethod(method: ForecastMethod) = choose(options.value.copy(method = method))
    fun selectWindow(window: Int) { if (window in listOf(3, 6, 12)) choose(options.value.copy(window = window)) }
    private fun choose(value: ForecastOptions) {
        saved["forecast_method"] = value.method.name; saved["forecast_window"] = value.window
        options.value = value
    }
    fun refresh() { refreshes.update { it + 1 } }
    fun retry() {
        observation?.cancel()
        mutable.value = ForecastUiState(options = options.value)
        observation = viewModelScope.launch {
            combine(source.observeBudgets(), options, refreshes) { data, chosen, _ -> data to chosen }
                .collect { (data, chosen) ->
                    if (data is AppResult.Failure) {
                        mutable.value = ForecastUiState(loading = false, error = data.error, options = chosen)
                        return@collect
                    }
                    val snapshot = (data as AppResult.Success).value
                    if (snapshot == null) { mutable.value = ForecastUiState(options = chosen); return@collect }
                    when (val result = ForecastCalculator.calculate(snapshot, DatePeriods.today(clock, snapshot.ledger.reportingZone), chosen)) {
                        is AppResult.Success -> mutable.value = ForecastUiState(loading = false, options = chosen, forecast = result.value)
                        is AppResult.Failure -> mutable.value = ForecastUiState(loading = false, error = result.error, options = chosen)
                    }
                }
        }
    }
}
