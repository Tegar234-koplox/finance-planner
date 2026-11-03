package com.tegar.finance.data.market

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.tegar.finance.domain.market.*
import com.tegar.finance.domain.model.*
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Platform AES-GCM/Android Keystore. Only a versioned nonce+ciphertext enters no-backup storage. */
class KeystoreProviderKeyStore(context: Context, namespace: String = "twelvedata") : ProviderKeyStore {
    init { require(namespace.matches(Regex("[a-z0-9-]{1,60}"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "$namespace-key-v1.bin"))
    private val changes = MutableStateFlow(0)
    private val lock = Mutex()
    private val alias = "finance.$namespace.v1"
    private val aad = "finance-planner:$namespace:v1".toByteArray(Charsets.UTF_8)
    override fun observeStatus() = changes.map { when (val result = read()) {
        is AppResult.Success -> AppResult.Success(result.value != null)
        is AppResult.Failure -> result
    } }
    override suspend fun read(): AppResult<String?> = operation {
        val backup = File(file.baseFile.path + ".bak")
        val candidate = if (backup.exists()) backup else file.baseFile
        if (!candidate.exists()) return@operation null
        require(candidate.length() in 33..157)
        val bytes = file.readFully()
        require(bytes.size in 33..157)
        require(bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, storedKey() ?: throw IOException("Key unavailable"), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(bytes, 13, bytes.size - 13)
        try { plain.toString(Charsets.UTF_8).also { require(ProviderKeyRules.valid(it)) } } finally { plain.fill(0) }
    }
    override suspend fun save(key: String): AppResult<Unit> {
        if (!ProviderKeyRules.valid(key)) return AppResult.Failure(AppError.INVALID_INPUT)
        return operation {
            val secret = storedKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build()); generateKey()
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret); cipher.updateAAD(aad)
            require(cipher.iv.size == 12)
            val plain = key.toByteArray(Charsets.UTF_8)
            val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
            val stream = file.startWrite()
            try { stream.write(byteArrayOf(1) + cipher.iv + encrypted); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
            changes.value++; Unit
        }
    }
    override suspend fun clear(): AppResult<Unit> = operation {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) }
            file.delete(); if (file.baseFile.exists()) throw IOException("Delete failed")
        } finally { changes.value++ }
        Unit
    }
    private fun storedKey() = KeyStore.getInstance("AndroidKeyStore").run { load(null); getKey(alias, null) as? SecretKey }
    private suspend fun <T> operation(work: () -> T): AppResult<T> = withContext(Dispatchers.IO) { lock.withLock {
        try { AppResult.Success(work()) } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error !is IOException && error !is GeneralSecurityException && error !is IllegalArgumentException && error !is java.security.ProviderException) throw error
            AppResult.Failure(AppError.STORAGE)
        }
    } }
}
