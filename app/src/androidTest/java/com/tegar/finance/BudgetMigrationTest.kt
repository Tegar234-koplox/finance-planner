package com.tegar.finance

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tegar.finance.core.database.*
import com.tegar.finance.data.repository.*
import com.tegar.finance.domain.model.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BudgetMigrationTest {
    @Test fun versionTwoTransactionsSurviveMigrationAndBudgetWritesAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "budget-migrate-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.tegar.finance.core.database.FinanceDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) for (j in 0 until indices.length())
                            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("$" + "{TABLE_NAME}", table))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    FinanceMigrations.FROM_1_TO_2.migrate(db) // Install the original v2 transaction guards.
                    db.execSQL("INSERT INTO ledger VALUES(1, 'IDR', 'Asia/Jakarta', 20000, -123, 100, 200)")
                    db.execSQL("INSERT INTO category VALUES('food', 'Edited food', 'edited food', 'EXPENSE', 1, 7, 'food', 100, 200)")
                    db.execSQL("INSERT INTO finance_transaction VALUES('retained', 1, 'EXPENSE', 12345, 'food', 20000, 'Keep my note', 100, 200)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture must be v2")
            }).build())
        helper.writableDatabase; helper.close()
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5, InvestmentMigrations.FROM_5_TO_6)
            .addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        try {
            val clock = Clock.systemUTC(); var repo = LocalBudgetRepository(db, clock)
            val before = (repo.observeBudgets().first() as AppResult.Success).value!!
            assertEquals(6, db.openHelper.readableDatabase.version)
            assertEquals(-123L, before.ledger.openingCash.minorUnits)
            assertEquals("retained", before.transactions.single().id)
            assertEquals("Keep my note", before.transactions.single().note)
            assertEquals(12345L, before.transactions.single().amount.minorUnits)
            assertEquals(Instant.ofEpochMilli(100), before.transactions.single().createdAt)
            assertEquals(Instant.ofEpochMilli(200), before.transactions.single().updatedAt)
            assertTrue(repo.saveMonthly(YearMonth.of(2028, 2), Money(50000)) is AppResult.Success)
            assertTrue(repo.saveCategory(YearMonth.of(2028, 2), "food", Money(20000)) is AppResult.Success)
            try { db.openHelper.writableDatabase.execSQL("UPDATE finance_transaction SET amountMinor = 0 WHERE id = 'retained'")
                fail("Original transaction guard must remain") } catch (_: SQLiteException) { }
            db.close(); db = open(); repo = LocalBudgetRepository(db, clock)
            val after = (repo.observeBudgets().first() as AppResult.Success).value!!
            assertEquals(before.transactions, after.transactions)
            assertEquals(before.ledger, after.ledger)
            assertEquals(50000L, after.monthly.single().allocation.minorUnits)
            assertEquals("Edited food", db.transactionDao().category("food")!!.name)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
