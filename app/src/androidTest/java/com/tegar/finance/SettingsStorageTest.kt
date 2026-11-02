package com.tegar.finance

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.data.repository.DataStoreSettingsRepository
import com.tegar.finance.domain.model.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SettingsStorageTest {
    @Test fun languagePersistsWithoutChangingThemeAndInvalidValueCanBeRepaired() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "language-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            var repository = DataStoreSettingsRepository(data)
            assertEquals(AppLanguage.ENGLISH, (repository.observeSettings().first() as AppResult.Success).value.language)
            assertTrue(repository.setTheme(ThemeMode.DARK) is AppResult.Success)
            assertTrue(repository.setLanguage(AppLanguage.INDONESIAN) is AppResult.Success)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            repository = DataStoreSettingsRepository(data)
            assertEquals(AppSettings(ThemeMode.DARK, AppLanguage.INDONESIAN), (repository.observeSettings().first() as AppResult.Success).value)
            data.edit { it[stringPreferencesKey("language")] = "INVALID" }
            assertEquals(AppResult.Failure(AppError.STORAGE), repository.observeSettings().first())
            assertTrue(repository.setLanguage(AppLanguage.ENGLISH) is AppResult.Success)
            assertEquals(AppSettings(ThemeMode.DARK, AppLanguage.ENGLISH), (repository.observeSettings().first() as AppResult.Success).value)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
    @Test fun themePersistsAcrossStoreRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun open() = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        try {
            val initial = open()
            assertEquals(ThemeMode.SYSTEM, (initial.observeSettings().first() as AppResult.Success).value.theme)
            assertTrue(initial.setTheme(ThemeMode.DARK) is AppResult.Success)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            assertEquals(ThemeMode.DARK, (open().observeSettings().first() as AppResult.Success).value.theme)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
