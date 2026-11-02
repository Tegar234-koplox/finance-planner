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

class InvestmentMigrationTest {
    @Test fun versionFivePreservesEveryOldFieldAndFrozenV1CaptureWhileAddingInvestments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "investment-migrate-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.tegar.finance.core.database.FinanceDatabase/5.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(5) {
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
                    WealthMigrations.FROM_4_TO_5.migrate(db)
                    db.execSQL("INSERT INTO category VALUES('invest','Fund','fund','INVESTMENT',1,8,NULL,100,NULL)")
                    db.execSQL("INSERT INTO finance_transaction VALUES('legacy-investment',1,'INVESTMENT',1000,'invest',20000,'Legacy contribution',100,NULL)")
                    db.execSQL("INSERT INTO wealth_item VALUES('old-asset',1,'Old car','ASSET','VEHICLE',19000,300000,'Keep note',1,100,NULL)")
                    db.execSQL("INSERT INTO wealth_value VALUES('old-value','old-asset',20000,200000,'Old observation',100,NULL)")
                    db.execSQL("INSERT INTO wealth_capture VALUES('old-capture',1,20000,'IDR',974309,200000,0,1174309,'TRACKED_V1',100)")
                    db.execSQL("INSERT INTO wealth_capture_line VALUES('old-line','old-capture','old-asset','Old car','ASSET','VEHICLE',200000,20000)")
                    db.execSQL("UPDATE category SET active=0 WHERE id='food'")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture is v5")
            }).build())
        val old = helper.writableDatabase
        val tables = listOf("ledger", "category", "finance_transaction", "monthly_budget", "category_budget", "finance_goal", "wealth_item", "wealth_value", "wealth_capture", "wealth_capture_line")
        val oldColumns = (0 until schema.getJSONArray("entities").length()).associate { i ->
            val entity = schema.getJSONArray("entities").getJSONObject(i); val fields = entity.getJSONArray("fields")
            entity.getString("tableName") to (0 until fields.length()).joinToString(",") { fields.getJSONObject(it).getString("columnName") }
        }
        fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> = db.query("SELECT ${oldColumns.getValue(table)} FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
        }
        val before = tables.associateWith { rows(old, it) }; helper.close()
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5, InvestmentMigrations.FROM_5_TO_6)
            .addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
        try {
            fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
            var repo = LocalInvestmentRepository(db, clock); var wealth = LocalWealthRepository(db, clock)
            val current = repo.observePortfolio().first().ok()!!
            assertEquals(6, db.openHelper.readableDatabase.version); assertTrue(current.holdings.isEmpty())
            assertTrue(current.targets.isEmpty()); assertTrue(current.plans.isEmpty())
            assertNull(current.transactions.first { it.id == "legacy-investment" }.investmentAction)
            assertEquals(Money(1000), RecordedFlowCalculator.calculate(current.transactions, current.ledger.currency).ok().contributions)
            for (table in tables) { assertFalse(table, before[table]!!.isEmpty()); assertEquals(table, before[table], rows(db.openHelper.readableDatabase, table)) }
            val oldCapture = wealth.observeWealth().first().ok()!!.captures.single()
            assertEquals(WealthRules.METHOD, oldCapture.method); assertEquals(Money(0), oldCapture.investments); assertTrue(oldCapture.investmentLines.isEmpty())
            val date = LocalDate.of(2028, 3, 10)
            val holding = repo.saveHolding(null, HoldingDraft("Fund / Reksa dana", "FUND", AllocationBucket.FUND, date.minusYears(1), Money(20000)),
                HoldingValueDraft("", date.minusDays(2), Money(15000))).ok()
            val saved = wealth.captureToday().ok()
            assertEquals(WealthRules.WITH_INVESTMENTS, wealth.observeWealth().first().ok()!!.captures.first { it.id == saved }.method)
            repo.deleteHolding(holding).ok()
            db.close(); db = open(); repo = LocalInvestmentRepository(db, clock); wealth = LocalWealthRepository(db, clock)
            val reopened = wealth.observeWealth().first().ok()!!
            assertEquals(oldCapture, reopened.captures.first { it.id == "old-capture" })
            assertEquals("Fund / Reksa dana", reopened.captures.first { it.id == saved }.investmentLines.single().name)
            assertTrue(repo.observePortfolio().first().ok()!!.holdings.isEmpty())
            // Only the new capture is additional; compare every original row and original column explicitly.
            wealth.deleteCapture(saved).ok()
            for (table in tables) assertEquals(table, before[table], rows(db.openHelper.readableDatabase, table))
            assertEquals(0, db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { it.count })
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
