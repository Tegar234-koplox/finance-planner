package com.tegar.finance.data.repository

import com.tegar.finance.domain.model.*
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StorageResultsTest {
    @Test fun ioFailureBecomesVisibleStorageResult() = runBlocking {
        assertEquals(AppResult.Failure(AppError.STORAGE), flow<Int> { throw IOException("disk failure") }.storageResults().first())
    }
    @Test fun invalidStoredDateBecomesVisibleStorageResult() = runBlocking {
        assertEquals(AppResult.Failure(AppError.STORAGE), flow<Int> { throw DateTimeException("invalid date") }.storageResults().first())
    }
    @Test fun cancellationIsPropagated() {
        assertThrows(CancellationException::class.java) {
            runBlocking { flow<Int> { throw CancellationException() }.storageResults().first() }
        }
    }
    @Test fun programmingErrorIsNotSwallowed() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { flow<Int> { throw IllegalStateException("unexpected") }.storageResults().first() }
        }
    }
}
