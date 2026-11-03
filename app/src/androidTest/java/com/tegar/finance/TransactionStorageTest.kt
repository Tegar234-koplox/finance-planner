package com.tegar.finance

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.core.currency.MoneyText
import com.tegar.finance.data.local.entity.TransactionEntity
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TransactionStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-06T18:00:00Z"), ZoneOffset.UTC)
    private val date = LocalDate.of(2026, 10, 7)
    private fun open(name: String) = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
        .addMigrations(FinanceMigrations.FROM_1_TO_2).addCallback(FinanceMigrations.CALLBACK).build()
    private fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("Expected a database integrity constraint") } catch (_: SQLiteException) { }
    }
    @Test fun indonesianInputPreservesIsoFractionalMinorUnitsOnAndroid() {
        assertEquals("IDR ISO scale must be stable across Android versions", 2, CurrencyCode.IDR.fractionDigits)
        val parsed = MoneyText.parse("25.000,50").ok()
        assertEquals(2500050L, parsed.minorUnits)
        assertTrue(MoneyText.format(parsed).contains("25.000,50"))
    }
    @Test fun transactionCrudFiltersAndCategoryRenameSurviveReopen() = runBlocking {
        val name = "transactions-${UUID.randomUUID()}.db"
        var db = open(name)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            var repo = LocalBookkeepingRepository(db, clock)
            val food = repo.observeCategories().first().ok().first { it.transactionType == TransactionType.EXPENSE }
            val income = repo.observeCategories().first().ok().first { it.transactionType == TransactionType.INCOME }
            val first = repo.saveTransaction(null, TransactionDraft(TransactionType.EXPENSE, Money(2500050), food.id, date, "Lunch")).ok()
            val second = repo.saveTransaction(null, TransactionDraft(TransactionType.INCOME, Money(900000), income.id, date.plusDays(1), "Pay")).ok()
            val third = repo.saveTransaction(null, TransactionDraft(TransactionType.EXPENSE, Money(100), food.id, date.plusDays(2))).ok()
            assertEquals(listOf(third, second, first), repo.observeTransactions().first().ok().map { it.id })
            assertEquals(listOf(second), repo.observeTransactions(TransactionFilter(date.plusDays(1), date.plusDays(1))).first().ok().map { it.id })
            assertEquals(listOf(third, first), repo.observeTransactions(TransactionFilter(type = TransactionType.EXPENSE, categoryId = food.id)).first().ok().map { it.id })
            assertEquals(0, repo.observeTransactions(TransactionFilter(date.plusDays(5))).first().ok().size)
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.observeTransactions(TransactionFilter(date.plusDays(1), date)).first())
            val original = repo.observeTransactions().first().ok().last()
            repo.saveTransaction(first, TransactionDraft(TransactionType.INCOME, Money(Long.MAX_VALUE), income.id, date.plusDays(3), "Updated")).ok()
            val edited = repo.observeTransactions().first().ok().first()
            assertEquals(first, edited.id); assertEquals(original.createdAt, edited.createdAt)
            assertNotNull(edited.updatedAt); assertEquals(Long.MAX_VALUE, edited.amount.minorUnits)
            repo.saveCategory(income.id, "  My   income ", TransactionType.INCOME).ok()
            assertEquals(AppResult.Failure(AppError.CATEGORY_IN_USE), repo.deleteCategory(income.id))
            db.close(); db = open(name); repo = LocalBookkeepingRepository(db, clock)
            LocalFoundationRepository(db, clock).initialize().ok()
            assertEquals(3, repo.observeTransactions().first().ok().size)
            assertEquals("My income", repo.observeCategories().first().ok().first { it.id == income.id }.name)
            repo.deleteTransaction(first).ok(); repo.deleteTransaction(second).ok(); repo.deleteTransaction(third).ok()
            assertTrue(repo.observeTransactions().first().ok().isEmpty())
            repo.deleteCategory(income.id).ok()
            assertEquals(AppResult.Failure(AppError.NOT_FOUND), repo.deleteTransaction(first))
            assertEquals(AppResult.Failure(AppError.NOT_FOUND), repo.saveTransaction(first,
                TransactionDraft(TransactionType.EXPENSE, Money(1), food.id, date)))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun categoryLifecycleAndInvalidWritesPreserveRecords() = runBlocking {
        val name = "categories-${UUID.randomUUID()}.db"; val db = open(name)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            val repo = LocalBookkeepingRepository(db, clock)
            val id = repo.saveCategory(null, "  Daily   food ", TransactionType.EXPENSE).ok()
            assertEquals(AppResult.Failure(AppError.DUPLICATE_CATEGORY), repo.saveCategory(null, "DAILY FOOD", TransactionType.EXPENSE))
            repo.saveCategory(null, "Daily food", TransactionType.INCOME).ok()
            val draft = TransactionDraft(TransactionType.EXPENSE, Money(100), id, date)
            val tx = repo.saveTransaction(null, draft).ok()
            assertEquals(AppResult.Failure(AppError.INVALID_INPUT), repo.saveTransaction(tx, draft.copy(amount = Money(0))))
            assertEquals(AppResult.Failure(AppError.DATE_BEFORE_COVERAGE), repo.saveTransaction(tx, draft.copy(date = date.minusDays(1))))
            assertEquals(AppResult.Failure(AppError.CATEGORY_MISMATCH), repo.saveTransaction(tx, draft.copy(type = TransactionType.INCOME)))
            assertEquals(AppResult.Failure(AppError.CATEGORY_MISMATCH), repo.saveCategory(id, "Daily food", TransactionType.INCOME))
            assertEquals(AppResult.Failure(AppError.CATEGORY_IN_USE), repo.deleteCategory(id))
            repo.setCategoryActive(id, false).ok()
            assertEquals(AppResult.Failure(AppError.CATEGORY_INACTIVE), repo.saveTransaction(null, draft))
            repo.saveTransaction(tx, draft.copy(note = "Archived history")).ok()
            assertEquals("Archived history", repo.observeTransactions().first().ok().single().note)
            repo.setCategoryActive(id, true).ok()
            repo.saveTransaction(null, draft).ok()
            val unused = repo.saveCategory(null, "Unused", TransactionType.EXPENSE).ok()
            repo.setCategoryActive(unused, false).ok(); repo.deleteCategory(unused).ok()
            val writes = coroutineScope { List(6) { async { repo.saveCategory(null, "Concurrent", TransactionType.EXPENSE) } }.awaitAll() }
            assertEquals(1, writes.count { it is AppResult.Success })
            assertEquals(5, writes.count { it == AppResult.Failure(AppError.DUPLICATE_CATEGORY) })
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun databaseRejectsInvalidAmountDateAndCategoryTypeEvenOutsideRepository() = runBlocking {
        val name = "constraints-${UUID.randomUUID()}.db"; val db = open(name)
        try {
            LocalFoundationRepository(db, clock).initialize().ok()
            val repo = LocalBookkeepingRepository(db, clock)
            val cat = repo.observeCategories().first().ok().first { it.transactionType == TransactionType.EXPENSE }
            val row = TransactionEntity("raw", transactionType = "EXPENSE", amountMinor = 100,
                categoryId = cat.id, date = date.toEpochDay(), note = "", createdAt = 1, updatedAt = null)
            reject { db.transactionDao().insert(row.copy(amountMinor = 0)) }
            reject { db.transactionDao().insert(row.copy(date = date.minusDays(1).toEpochDay())) }
            reject { db.transactionDao().insert(row.copy(transactionType = "INCOME")) }
            reject { db.transactionDao().insert(row.copy(categoryId = "missing")) }
            db.transactionDao().insert(row)
            reject { db.transactionDao().update(row.copy(amountMinor = -1)) }
            reject { db.transactionDao().deleteCategory(cat.id) }
            assertEquals(100L, repo.observeTransactions().first().ok().single().amount.minorUnits)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
