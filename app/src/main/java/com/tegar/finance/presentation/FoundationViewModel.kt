package com.tegar.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface FoundationStatus {
    data object Loading : FoundationStatus
    data class Ready(val ledger: Ledger) : FoundationStatus
    data object StorageError : FoundationStatus
}
data class AppUiState(val status: FoundationStatus = FoundationStatus.Loading,
    val theme: ThemeMode = ThemeMode.SYSTEM, val themeSaveFailed: Boolean = false)

@HiltViewModel
class FoundationViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    init { retry() }
    fun retry() {
        observation?.cancel()
        mutableState.update { it.copy(status = FoundationStatus.Loading) }
        observation = viewModelScope.launch {
            if (ledger.initialize() is AppResult.Failure) {
                mutableState.update { it.copy(status = FoundationStatus.StorageError) }
                return@launch
            }
            combine(ledger.observeLedger(), categories.observeCategories(), settings.observeSettings()) { account, seeds, prefs ->
                val current = (account as? AppResult.Success)?.value
                if (current == null || seeds is AppResult.Failure || prefs is AppResult.Failure) {
                    AppUiState(status = FoundationStatus.StorageError)
                } else {
                    AppUiState(FoundationStatus.Ready(current), (prefs as AppResult.Success).value.theme)
                }
            }.collect { next -> mutableState.update { next.copy(themeSaveFailed = it.themeSaveFailed) } }
        }
    }
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            val result = settings.setTheme(mode)
            mutableState.update { it.copy(themeSaveFailed = result is AppResult.Failure) }
        }
    }
}
