package com.tegar.finance.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object GoalMigrations {
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS finance_goal (
                id TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL,
                targetMinor INTEGER NOT NULL, earmarkedMinor INTEGER NOT NULL, asOfDate INTEGER NOT NULL,
                targetDate INTEGER NOT NULL, priority TEXT NOT NULL, state TEXT NOT NULL,
                createdAt INTEGER NOT NULL, updatedAt INTEGER,
                FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_finance_goal_ledgerId ON finance_goal(ledgerId)")
            installGuards(db)
        }
    }
    fun installGuards(db: SupportSQLiteDatabase) {
        for (operation in listOf("INSERT", "UPDATE")) db.execSQL("""CREATE TRIGGER IF NOT EXISTS goal_valid_${operation.lowercase()}
            BEFORE $operation ON finance_goal BEGIN
            SELECT CASE WHEN NEW.ledgerId != 1 OR length(trim(NEW.name)) NOT BETWEEN 1 AND 80
                OR NEW.kind NOT IN ('SAVING','PURCHASE') OR NEW.state NOT IN ('ACTIVE','PAUSED')
                OR NEW.priority NOT IN ('HIGH','NORMAL','LOW')
                OR typeof(NEW.targetMinor) != 'integer' OR NEW.targetMinor <= 0
                OR typeof(NEW.earmarkedMinor) != 'integer' OR NEW.earmarkedMinor < 0
                OR typeof(NEW.asOfDate) != 'integer' OR NEW.asOfDate NOT BETWEEN -719162 AND 2932896
                OR typeof(NEW.targetDate) != 'integer' OR NEW.targetDate NOT BETWEEN -719162 AND 2932896
                OR NEW.asOfDate < (SELECT trackedFromDate FROM ledger WHERE id = NEW.ledgerId)
                THEN RAISE(ABORT, 'Invalid goal') END; END""")
    }
}
