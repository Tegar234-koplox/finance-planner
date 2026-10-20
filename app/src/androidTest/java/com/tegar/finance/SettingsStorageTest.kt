package com.tegar.finance

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
