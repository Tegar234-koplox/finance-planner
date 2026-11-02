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

class GoalMigrationTest {
    @Test fun versionThreePreservesEveryFinancialTableAndAddsDurableGoals() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "goal-migrate-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.tegar.finance.core.database.FinanceDatabase/3.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(3) {
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
                    db.execSQL("UPDATE category SET active=0 WHERE id='food'")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture is v3")
            }).build())
        val old = helper.writableDatabase
        val tables = listOf("ledger", "category", "finance_transaction", "monthly_budget", "category_budget")
        fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> = db.query("SELECT " + (if (table == "finance_transaction") "id,ledgerId,transactionType,amountMinor,categoryId,date,note,createdAt,updatedAt" else "*") + " FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
        }
        val before = tables.associateWith { rows(old, it) }; helper.close()
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceMigrations.FROM_1_TO_2, BudgetMigrations.FROM_2_TO_3, GoalMigrations.FROM_3_TO_4, WealthMigrations.FROM_4_TO_5, InvestmentMigrations.FROM_5_TO_6)
            .addCallback(FinanceMigrations.CALLBACK).build()
        var db = open()
        val clock = Clock.fixed(Instant.parse("2028-03-10T05:00:00Z"), ZoneOffset.UTC)
        try {
            var source = LocalBudgetRepository(db, clock)
            val snapshot = (source.observeBudgets().first() as AppResult.Success).value!!
            assertEquals(6, db.openHelper.readableDatabase.version); assertTrue(snapshot.goals.isEmpty())
            for (table in tables) assertEquals(table, before[table], rows(db.openHelper.readableDatabase, table))
            var goals = LocalGoalRepository(db, source, clock)
            val id = (goals.saveGoal(null, GoalDraft("Phone / Ponsel", GoalKind.PURCHASE, Money(100000), Money(50000),
                LocalDate.of(2028, 3, 10), LocalDate.of(2028, 12, 31), GoalPriority.HIGH, GoalState.ACTIVE)) as AppResult.Success).value
            db.close(); db = open(); source = LocalBudgetRepository(db, clock); goals = LocalGoalRepository(db, source, clock)
            val reopened = (goals.observeGoals().first() as AppResult.Success).value!!
            assertEquals(id, reopened.goals.single().id); assertEquals("Phone / Ponsel", reopened.goals.single().name)
            assertEquals(Money(50000), reopened.goals.single().earmarked)
            for (table in tables) assertEquals(table, before[table], rows(db.openHelper.readableDatabase, table))
            assertTrue(goals.deleteGoal(id) is AppResult.Success)
            assertTrue((goals.observeGoals().first() as AppResult.Success).value!!.goals.isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
