package com.tegar.finance.data.repository

import android.database.sqlite.SQLiteException
import com.tegar.finance.domain.model.*
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private fun isStorageError(error: Exception): Boolean =
    error is IOException || error is SQLiteException || error is IllegalArgumentException || error is DateTimeException

internal suspend fun <T> storageOperation(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (error: Exception) {
    if (error is CancellationException || !isStorageError(error)) throw error
    AppResult.Failure(AppError.STORAGE)
}

internal fun <T> Flow<T>.storageResults(): Flow<AppResult<T>> =
    map<T, AppResult<T>> { AppResult.Success(it) }.catch { error ->
        if (error !is Exception || error is CancellationException || !isStorageError(error)) throw error
        emit(AppResult.Failure(AppError.STORAGE))
    }
