package com.tegar.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tegar.finance.core.database.*
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ReportsStorageTest {
    @Test fun reportsFollowPersistedEditsDeletionArchivalAndReopenWithoutWritingCounters() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="report-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,FinanceDatabase::class.java,name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2,BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5, InvestmentMigrations.FROM_5_TO_6).addCallback(FinanceMigrations.CALLBACK).build()
        var db=open()
        fun <T> AppResult<T>.ok(): T { assertTrue(toString(),this is AppResult.Success); return (this as AppResult.Success).value }
        val clock=Clock.fixed(Instant.parse("2028-01-01T05:00:00Z"),ZoneOffset.UTC)
        try {
            LocalFoundationRepository(db,clock).initialize().ok()
            var book=LocalBookkeepingRepository(db,clock); var source=LocalBudgetRepository(db,clock)
            val cats=book.observeCategories().first().ok()
            val food=cats.first { it.transactionType==TransactionType.EXPENSE }
            val income=cats.first { it.transactionType==TransactionType.INCOME }
            val investment=cats.first { it.transactionType==TransactionType.INVESTMENT }
            suspend fun save(type: TransactionType,amount: Long,category: String,date: String)=
                book.saveTransaction(null,TransactionDraft(type,Money(amount),category,LocalDate.parse(date))).ok()
            save(TransactionType.INCOME,10000,income.id,"2028-01-10")
            save(TransactionType.EXPENSE,1000,food.id,"2028-01-11")
            save(TransactionType.INCOME,20000,income.id,"2028-02-10")
            val id=save(TransactionType.EXPENSE,4000,food.id,"2028-02-10")
            save(TransactionType.INVESTMENT,5000,investment.id,"2028-02-10")
            val selection=ReportSelection(ReportKind.MONTH,LocalDate.of(2028,2,10))
            suspend fun report()=ReportCalculator.calculate(source.observeBudgets().first().ok()!!,selection,LocalDate.of(2028,3,1)).ok()
            assertEquals(4000L,report().current.totals!!.consumption.minorUnits)
            assertEquals(3000L,report().comparison!!.expenseDelta.minorUnits)
            book.saveTransaction(id,TransactionDraft(TransactionType.EXPENSE,Money(8000),food.id,LocalDate.of(2028,2,10))).ok()
            book.saveCategory(food.id,"Report food",TransactionType.EXPENSE).ok()
            book.setCategoryActive(food.id,false).ok()
            db.close(); db=open(); book=LocalBookkeepingRepository(db,clock); source=LocalBudgetRepository(db,clock)
            val reopened=report()
            assertEquals(8000L,reopened.current.totals!!.consumption.minorUnits)
            assertEquals(7000L,reopened.comparison!!.expenseDelta.minorUnits)
            assertEquals(7000L,reopened.current.totals.netCashFlow!!.minorUnits)
            assertEquals(food.id,reopened.categories.single().categoryId)
            assertEquals(5,source.observeBudgets().first().ok()!!.transactions.size)
            assertEquals(6,db.openHelper.readableDatabase.version)
            book.deleteTransaction(id).ok()
            assertEquals(0L,report().current.totals!!.consumption.minorUnits)
            assertTrue(report().categories.isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
