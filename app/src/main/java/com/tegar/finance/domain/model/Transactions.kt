package com.tegar.finance.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.Locale

data class FinanceTransaction(
    val id: String, val type: TransactionType, val amount: Money, val categoryId: String,
    val date: LocalDate, val note: String, val createdAt: Instant, val updatedAt: Instant?,
)
data class TransactionDraft(
    val type: TransactionType, val amount: Money, val categoryId: String,
    val date: LocalDate, val note: String = "",
)
/** Inclusive calendar dates in the ledger's reporting zone. Null means unrestricted. */
data class TransactionFilter(
    val from: LocalDate? = null, val through: LocalDate? = null,
    val type: TransactionType? = null, val categoryId: String? = null,
) {
    val valid: Boolean get() = from == null || through == null || !from.isAfter(through)
}
object TransactionRules {
    fun validate(draft: TransactionDraft, ledger: Ledger, category: Category?, previous: FinanceTransaction? = null): AppError? = when {
        draft.amount.currency != ledger.currency -> AppError.CURRENCY_MISMATCH
        draft.amount.minorUnits <= 0 || draft.note.length > 500 -> AppError.INVALID_INPUT
        draft.date < ledger.trackedFromDate -> AppError.DATE_BEFORE_COVERAGE
        category == null || category.id != draft.categoryId || category.transactionType != draft.type -> AppError.CATEGORY_MISMATCH
        !category.active && (previous?.categoryId != category.id || previous.type != draft.type) -> AppError.CATEGORY_INACTIVE
        else -> null
    }
}
object CategoryNames {
    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")
    fun normalized(name: String): String = clean(name).lowercase(Locale.ROOT)
    fun valid(name: String): Boolean = clean(name).let { it.isNotEmpty() && it.length <= 80 && it.none(Char::isISOControl) }
}
