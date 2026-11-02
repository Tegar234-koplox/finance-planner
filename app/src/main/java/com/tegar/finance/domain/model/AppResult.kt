package com.tegar.finance.domain.model

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
enum class AppError {
    INVALID_INPUT, OUT_OF_RANGE, CURRENCY_MISMATCH, STORAGE, NOT_FOUND,
    DATE_BEFORE_COVERAGE, CATEGORY_MISMATCH, CATEGORY_INACTIVE, CATEGORY_IN_USE, DUPLICATE_CATEGORY,
    BUDGET_LIMIT_EXCEEDED,
    WEALTH_INCOMPLETE, WEALTH_DATE_CONFLICT, SNAPSHOT_EXISTS,
}
