package com.tegar.finance

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tegar.finance.core.database.*
import com.tegar.finance.data.local.entity.TransactionEntity
import com.tegar.finance.data.repository.LocalBookkeepingRepository
import com.tegar.finance.domain.model.*
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinanceMigrationTest {
    @Test fun versionOneMigratesWithoutLosingLedgerOrEditedArchivedCategories() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        // Reconstruct the actual exported v1 schema, including its Room identity hash.
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val schema = JSONObject(testContext.assets.open("com.tegar.finance.core.database.FinanceDatabase/1.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index); val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("$" + "{TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO ledger VALUES(1, 'IDR', 'Asia/Jakarta', 20000, -12345, 100, 200)")
            old.execSQL("INSERT INTO category VALUES('kept', 'Renamed food', 'renamed food', 'EXPENSE', 0, 7, 'food', 100, 200)")
            old.version = 1
        }
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5, InvestmentMigrations.FROM_5_TO_6).addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        try {
            val ledger = db.foundationDao().ledger()!!
            assertEquals(-12345L, ledger.openingCashMinor); assertEquals(20000L, ledger.trackedFromDate)
            assertEquals(200L, ledger.updatedAt)
            val category = db.transactionDao().category("kept")!!
            assertEquals("Renamed food", category.name); assertFalse(category.active)
            assertEquals("food", category.seedKey); assertEquals(7, category.sortOrder)
            assertEquals(6, db.openHelper.readableDatabase.version)
            val row = TransactionEntity("migrated-write", transactionType = "EXPENSE", amountMinor = 100,
                categoryId = "kept", date = 20000, note = "After migration", createdAt = 300, updatedAt = null)
            try { db.transactionDao().insert(row); fail("Archived category accepted") } catch (_: SQLiteException) { }
            val repo = LocalBookkeepingRepository(db, Clock.systemUTC())
            assertTrue(repo.setCategoryActive("kept", true) is AppResult.Success)
            db.transactionDao().insert(row)
            try { db.transactionDao().update(row.copy(amountMinor = 0)); fail("Invalid amount accepted") } catch (_: SQLiteException) { }
            db.close(); db = open()
            val records = LocalBookkeepingRepository(db, Clock.systemUTC()).observeTransactions().first() as AppResult.Success
            assertEquals("After migration", records.value.single().note)
            assertEquals(-12345L, db.foundationDao().ledger()!!.openingCashMinor)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
