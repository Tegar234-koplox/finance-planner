package com.tegar.finance.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.tegar.finance.domain.model.*
import com.tegar.finance.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreSettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : SettingsRepository {
    override fun observeSettings() = store.data.map { preferences ->
        AppSettings(preferences[ThemeKey]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM)
    }.storageResults()
    override suspend fun setTheme(mode: ThemeMode): AppResult<Unit> = storageOperation {
        store.edit { it[ThemeKey] = mode.name }
        Unit
    }
    companion object { private val ThemeKey = stringPreferencesKey("theme") }
}
