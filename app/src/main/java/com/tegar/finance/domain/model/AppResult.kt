package com.tegar.finance.domain.model

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
enum class AppError { INVALID_INPUT, OUT_OF_RANGE, CURRENCY_MISMATCH, STORAGE }
