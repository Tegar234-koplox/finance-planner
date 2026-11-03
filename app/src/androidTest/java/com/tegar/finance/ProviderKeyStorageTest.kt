package com.tegar.finance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.data.market.KeystoreProviderKeyStore
import com.tegar.finance.domain.model.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProviderKeyStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val secret = "synthetic_key_123456789"
    private fun namespace() = "test-${UUID.randomUUID()}"
    @Test fun platformKeyStoreCiphertextSurvivesStoreRecreationAndNeverContainsThePlainKey() = runBlocking {
        val ns = namespace(); val store = KeystoreProviderKeyStore(context, ns)
        val file = File(context.noBackupFilesDir, "$ns-key-v1.bin")
        try {
            assertEquals(AppResult.Success(null), store.read()); assertEquals(AppResult.Success(Unit), store.save(secret))
            val first = file.readBytes(); assertFalse(first.toString(Charsets.ISO_8859_1).contains(secret))
            val reopened = KeystoreProviderKeyStore(context, ns)
            assertEquals(AppResult.Success(secret), reopened.read()); assertEquals(AppResult.Success(true), reopened.observeStatus().first())
            assertEquals(AppResult.Success(Unit), reopened.save(secret)); assertFalse(first.contentEquals(file.readBytes()))
            assertEquals(AppResult.Success(Unit), reopened.clear()); assertFalse(file.exists())
            assertEquals(AppResult.Success(false), reopened.observeStatus().first())
        } finally { store.clear() }
    }
    @Test fun tamperingIsAnExplicitReadErrorAndOnlyAnExplicitReplacementRepairsIt() = runBlocking {
        val ns = namespace(); val store = KeystoreProviderKeyStore(context, ns)
        val file = File(context.noBackupFilesDir, "$ns-key-v1.bin")
        try {
            store.save(secret); val data = file.readBytes(); data[data.lastIndex] = (data.last().toInt() xor 1).toByte(); file.writeBytes(data)
            assertEquals(AppResult.Failure(AppError.STORAGE), store.read())
            assertEquals(AppResult.Failure(AppError.STORAGE), store.observeStatus().first())
            assertTrue(data.contentEquals(file.readBytes()))
            assertEquals(AppResult.Success(Unit), store.save(secret)); assertEquals(AppResult.Success(secret), store.read())
        } finally { store.clear() }
    }
    @Test fun interruptedAtomicWriteRecoversThePreviousEncryptedKey() = runBlocking {
        val ns = namespace(); val store = KeystoreProviderKeyStore(context, ns)
        val file = File(context.noBackupFilesDir, "$ns-key-v1.bin"); val backup = File(file.path + ".bak")
        try {
            store.save(secret); assertTrue(file.renameTo(backup)); assertFalse(file.exists())
            assertEquals(AppResult.Success(secret), KeystoreProviderKeyStore(context, ns).read())
            assertTrue(file.exists()); assertFalse(backup.exists())
        } finally { store.clear(); backup.delete() }
    }
}
