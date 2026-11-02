package com.tegar.finance

import android.content.Context
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

class WealthMigrationTest {
    @Test fun versionFourPreservesAllSixFinancialTablesAndAddsDurableFrozenWealthHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "wealth-migrate-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.tegar.finance.core.database.FinanceDatabase/4.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j)
                            .getString("createSql").replace("$" + "{TABLE_NAME}", table))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    FinanceMigrations.FROM_1_TO_2.migrate(db)
                    BudgetMigrations.FROM_2_TO_3.migrate(db)
                    db.execSQL("INSERT INTO ledger VALUES(1,'IDR','Asia/Jakarta',20000,987654,100,200)")
                    db.execSQL("INSERT INTO category VALUES('food','Edited archived food','edited archived food','EXPENSE',1,7,'food',100,200)")
                    db.execSQL("INSERT INTO finance_transaction VALUES('retained',1,'EXPENSE',12345,'food',20000,'Private note / catatan',100,200)")
                    db.execSQL("INSERT INTO monthly_budget VALUES('overall',1,'2028-03',50000,100,200)")
                    db.execSQL("INSERT INTO category_budget VALUES('category',1,'2028-03','food','EXPENSE',10000,100,200)")
                    GoalMigrations.FROM_3_TO_4.migrate(db)
                    db.execSQL("INSERT INTO finance_goal VALUES('retained-goal',1,'Laptop / Laptop','PURCHASE',100000,50000,20000,22000,'HIGH','ACTIVE',100,200)")
                    db.execSQL("UPDATE category SET active=0 WHERE id='food'")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture is v4")
            }).build())
        val old = helper.writableDatabase
        val tables = listOf("ledger", "category", "finance_transaction", "monthly_budget", "category_budget", "finance_goal")
        fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> = db.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
        }
        val before = tables.associateWith { rows(old, it) }; helper.close()
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5)
            .addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
        try {
            var repository = LocalWealthRepository(db, clock)
            fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
            assertTrue(repository.observeWealth().first().ok()!!.items.isEmpty())
            assertEquals(5, db.openHelper.readableDatabase.version)
            for (table in tables) { assertFalse(before[table]!!.isEmpty()); assertEquals(table, before[table], rows(db.openHelper.readableDatabase, table)) }
            val today = LocalDate.of(2028, 3, 10)
            val item = repository.saveItem(null, WealthItemDraft("Vehicle / Kendaraan", WealthKind.ASSET,
                WealthCategory.VEHICLE, today.minusYears(2), Money(2000000)), WealthValueDraft("", today.minusDays(2), Money(1500000))).ok()
            val snapshot = repository.captureToday().ok()
            val frozen = repository.observeWealth().first().ok()!!.captures.single()
            repository.deleteItem(item).ok()
            db.close(); db = open(); repository = LocalWealthRepository(db, clock)
            val reopened = repository.observeWealth().first().ok()!!
            assertTrue(reopened.items.isEmpty()); assertTrue(reopened.values.isEmpty())
            assertEquals(snapshot, reopened.captures.single().id); assertEquals(frozen, reopened.captures.single())
            assertEquals("Vehicle / Kendaraan", reopened.captures.single().lines.single().name)
            assertEquals(today.minusDays(2), reopened.captures.single().lines.single().valuedOn)
            for (table in tables) assertEquals(table, before[table], rows(db.openHelper.readableDatabase, table))
            repository.deleteCapture(snapshot).ok(); assertTrue(repository.observeWealth().first().ok()!!.captures.isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
