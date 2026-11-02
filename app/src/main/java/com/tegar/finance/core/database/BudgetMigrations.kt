package com.tegar.finance.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object BudgetMigrations {
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS monthly_budget (
                id TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL, month TEXT NOT NULL,
                allocationMinor INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER,
                FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_monthly_budget_ledgerId_month ON monthly_budget(ledgerId, month)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS category_budget (
                id TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL, month TEXT NOT NULL,
                categoryId TEXT NOT NULL, transactionType TEXT NOT NULL, allocationMinor INTEGER NOT NULL,
                createdAt INTEGER NOT NULL, updatedAt INTEGER,
                FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
                FOREIGN KEY(categoryId, transactionType) REFERENCES category(id, transactionType) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_category_budget_ledgerId_categoryId_month ON category_budget(ledgerId, categoryId, month)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_category_budget_categoryId_transactionType ON category_budget(categoryId, transactionType)")
            installGuards(db)
        }
    }
    fun installGuards(db: SupportSQLiteDatabase) {
        for (operation in listOf("INSERT", "UPDATE")) {
            val suffix = operation.lowercase()
            for (table in listOf("monthly_budget", "category_budget")) {
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${table}_valid_$suffix BEFORE $operation ON $table BEGIN
                    SELECT CASE WHEN NEW.ledgerId != 1 OR NEW.allocationMinor < 0 OR typeof(NEW.allocationMinor) != 'integer'
                    OR length(NEW.month) != 7 OR NEW.month NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]'
                    OR substr(NEW.month,1,4) = '0000' OR CAST(substr(NEW.month,6,2) AS INTEGER) NOT BETWEEN 1 AND 12
                    THEN RAISE(ABORT, 'Invalid budget') END; END""")
            }
            // Existing allocations can exceed Long only when no overall cap exists.
            // In that case SQLite SUM overflow aborts adding a cap, which is necessarily too small.
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS monthly_budget_cap_$suffix
                BEFORE $operation ON monthly_budget BEGIN
                SELECT CASE WHEN COALESCE((SELECT SUM(allocationMinor) FROM category_budget
                    WHERE ledgerId = NEW.ledgerId AND month = NEW.month), 0) > NEW.allocationMinor
                    THEN RAISE(ABORT, 'Category allocations exceed overall budget') END; END""")
            val excluded = if (operation == "INSERT") "" else "AND id != OLD.id"
            val archived = if (operation == "INSERT") "1" else "(OLD.categoryId != NEW.categoryId OR OLD.month != NEW.month)"
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS category_budget_cap_$suffix
                BEFORE $operation ON category_budget BEGIN
                SELECT CASE WHEN NEW.transactionType != 'EXPENSE' THEN RAISE(ABORT, 'Expense category required') END;
                SELECT CASE WHEN $archived AND EXISTS(SELECT 1 FROM category WHERE id = NEW.categoryId AND active = 0)
                    THEN RAISE(ABORT, 'Inactive category') END;
                SELECT CASE WHEN EXISTS(SELECT 1 FROM monthly_budget WHERE ledgerId = NEW.ledgerId AND month = NEW.month)
                    AND NEW.allocationMinor > (SELECT allocationMinor FROM monthly_budget WHERE ledgerId = NEW.ledgerId AND month = NEW.month)
                    - COALESCE((SELECT SUM(allocationMinor) FROM category_budget WHERE ledgerId = NEW.ledgerId AND month = NEW.month $excluded), 0)
                    THEN RAISE(ABORT, 'Category allocations exceed overall budget') END; END""")
        }
    }
}
