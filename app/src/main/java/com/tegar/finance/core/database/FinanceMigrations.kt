package com.tegar.finance.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object FinanceMigrations {
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS finance_transaction (
                id TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL, transactionType TEXT NOT NULL,
                amountMinor INTEGER NOT NULL, categoryId TEXT NOT NULL, date INTEGER NOT NULL, note TEXT NOT NULL,
                createdAt INTEGER NOT NULL, updatedAt INTEGER,
                FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
                FOREIGN KEY(categoryId, transactionType) REFERENCES category(id, transactionType) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_finance_transaction_ledgerId ON finance_transaction(ledgerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_finance_transaction_categoryId_transactionType ON finance_transaction(categoryId, transactionType)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_finance_transaction_date_createdAt ON finance_transaction(date, createdAt)")
            installGuards(db)
        }
    }
    val CALLBACK = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            installGuards(db)
            BudgetMigrations.installGuards(db)
            GoalMigrations.installGuards(db)
            WealthMigrations.installGuards(db)
        }
    }
    /** Also protect invariants for writes outside the repository, on both fresh and migrated databases. */
    private fun installGuards(db: SupportSQLiteDatabase) {
        for (operation in listOf("INSERT", "UPDATE")) {
            val archived = if (operation == "INSERT") "1" else
                "(OLD.categoryId != NEW.categoryId OR OLD.transactionType != NEW.transactionType)"
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS transaction_guard_${operation.lowercase()}
                BEFORE $operation ON finance_transaction BEGIN
                SELECT CASE WHEN NEW.amountMinor <= 0 OR typeof(NEW.amountMinor) != 'integer'
                    OR length(NEW.note) > 500 OR NEW.ledgerId != 1
                    THEN RAISE(ABORT, 'Invalid transaction') END;
                SELECT CASE WHEN NEW.date < (SELECT trackedFromDate FROM ledger WHERE id = NEW.ledgerId)
                    THEN RAISE(ABORT, 'Transaction before coverage') END;
                SELECT CASE WHEN $archived AND EXISTS(SELECT 1 FROM category WHERE id = NEW.categoryId AND active = 0)
                    THEN RAISE(ABORT, 'Inactive category') END;
                END""")
        }
    }
}
