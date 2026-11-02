package com.tegar.finance

import com.tegar.finance.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class TransactionRulesTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val ledger = Ledger(CurrencyCode.IDR, ZoneId.of("Asia/Jakarta"), start, Money(0), Instant.EPOCH)
    private val category = Category("food", "Food", TransactionType.EXPENSE, true)
    private val draft = TransactionDraft(TransactionType.EXPENSE, Money(1), "food", start)
    private fun validate(value: TransactionDraft, cat: Category? = category, old: FinanceTransaction? = null) =
        TransactionRules.validate(value, ledger, cat, old)
    @Test fun firstCoveredDateAndExactLongMaximumAreValid() {
        assertNull(validate(draft.copy(amount = Money(Long.MAX_VALUE))))
    }
    @Test fun zeroAndNegativeAmountsAreRejected() {
        for (amount in listOf(0L, -1L, Long.MIN_VALUE)) assertEquals(AppError.INVALID_INPUT, validate(draft.copy(amount = Money(amount))))
    }
    @Test fun currencyCannotRelabelTheLedger() {
        assertEquals(AppError.CURRENCY_MISMATCH, validate(draft.copy(amount = Money(100, CurrencyCode("USD")))))
    }
    @Test fun datesBeforeCoverageAreRejected() {
        assertEquals(AppError.DATE_BEFORE_COVERAGE, validate(draft.copy(date = start.minusDays(1))))
    }
    @Test fun categoryMustExistAndMatchTypeAndIdentity() {
        assertEquals(AppError.CATEGORY_MISMATCH, validate(draft, null))
        assertEquals(AppError.CATEGORY_MISMATCH, validate(draft, category.copy(id = "different")))
        assertEquals(AppError.CATEGORY_MISMATCH, validate(draft.copy(type = TransactionType.INCOME)))
    }
    @Test fun archivedCategoriesCannotBeUsedForNewOrRetargetedTransactions() {
        val archived = category.copy(active = false)
        assertEquals(AppError.CATEGORY_INACTIVE, validate(draft, archived))
        val old = FinanceTransaction("old", draft.type, draft.amount, "other", start, "", Instant.EPOCH, null)
        assertEquals(AppError.CATEGORY_INACTIVE, validate(draft, archived, old))
    }
    @Test fun existingArchivedCategoryCanBeKeptWhenEditingHistory() {
        val old = FinanceTransaction("old", draft.type, draft.amount, "food", start, "", Instant.EPOCH, null)
        assertNull(validate(draft.copy(note = "Corrected"), category.copy(active = false), old))
    }
    @Test fun noteLimitIsEnforcedWithoutTruncation() {
        assertNull(validate(draft.copy(note = "x".repeat(500))))
        assertEquals(AppError.INVALID_INPUT, validate(draft.copy(note = "x".repeat(501))))
    }
    @Test fun dateFilterSupportsUnboundedInclusiveAndLeapDates() {
        val leap = LocalDate.of(2028, 2, 29)
        assertTrue(TransactionFilter(leap, leap).valid)
        assertTrue(TransactionFilter(from = leap).valid)
        assertTrue(TransactionFilter(through = leap).valid)
        assertFalse(TransactionFilter(leap.plusDays(1), leap).valid)
    }
    @Test fun categoryNamesAreNormalizedWithoutLocaleDependentIdentity() {
        assertEquals("food / jajan", CategoryNames.normalized("  FOOD   / Jajan  "))
        assertEquals("Income", CategoryNames.clean("  Income  "))
        assertFalse(CategoryNames.valid("  "))
        assertFalse(CategoryNames.valid("x".repeat(81)))
        assertFalse(CategoryNames.valid("Bad\u0000name"))
        assertTrue(CategoryNames.valid("Transportasi"))
    }
}
