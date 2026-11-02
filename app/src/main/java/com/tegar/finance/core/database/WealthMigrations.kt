package com.tegar.finance.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WealthMigrations {
    val FROM_4_TO_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS wealth_item (id TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL,
                name TEXT NOT NULL, kind TEXT NOT NULL, category TEXT NOT NULL, acquiredOn INTEGER NOT NULL,
                costMinor INTEGER, notes TEXT NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER,
                FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_wealth_item_ledgerId ON wealth_item(ledgerId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS wealth_value (id TEXT NOT NULL PRIMARY KEY, itemId TEXT NOT NULL,
                date INTEGER NOT NULL, valueMinor INTEGER NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER,
                FOREIGN KEY(itemId) REFERENCES wealth_item(id) ON UPDATE RESTRICT ON DELETE CASCADE)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wealth_value_itemId_date ON wealth_value(itemId,date)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS wealth_capture (id TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL,
                date INTEGER NOT NULL, currencyCode TEXT NOT NULL, cashMinor INTEGER NOT NULL, assetsMinor INTEGER NOT NULL,
                liabilitiesMinor INTEGER NOT NULL, totalMinor INTEGER NOT NULL, method TEXT NOT NULL, capturedAt INTEGER NOT NULL,
                FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wealth_capture_ledgerId_date ON wealth_capture(ledgerId,date)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS wealth_capture_line (id TEXT NOT NULL PRIMARY KEY, captureId TEXT NOT NULL,
                sourceItemId TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, category TEXT NOT NULL,
                valueMinor INTEGER NOT NULL, valuedOn INTEGER NOT NULL,
                FOREIGN KEY(captureId) REFERENCES wealth_capture(id) ON UPDATE RESTRICT ON DELETE CASCADE)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wealth_capture_line_captureId_sourceItemId ON wealth_capture_line(captureId,sourceItemId)")
            installGuards(db)
        }
    }
    fun installGuards(db: SupportSQLiteDatabase) {
        val category = "((NEW.kind='ASSET' AND NEW.category IN ('PROPERTY','VEHICLE','PERSONAL','OTHER')) OR (NEW.kind='LIABILITY' AND NEW.category IN ('LOAN','CARD','OTHER')))"
        for (operation in listOf("INSERT", "UPDATE")) {
            val immutableKind = if (operation == "UPDATE") "OR NEW.kind != OLD.kind" else ""
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS wealth_item_valid_${operation.lowercase()} BEFORE $operation ON wealth_item BEGIN
                SELECT CASE WHEN NEW.ledgerId != 1 OR length(trim(NEW.name)) NOT BETWEEN 1 AND 80 OR NOT $category
                    OR typeof(NEW.acquiredOn) != 'integer' OR NEW.acquiredOn NOT BETWEEN -719162 AND 2932896
                    OR (NEW.costMinor IS NOT NULL AND (typeof(NEW.costMinor) != 'integer' OR NEW.costMinor < 0))
                    OR (NEW.kind='LIABILITY' AND NEW.costMinor IS NOT NULL) OR length(NEW.notes)>500
                    OR NEW.active NOT IN (0,1) $immutableKind
                    OR EXISTS(SELECT 1 FROM wealth_value WHERE itemId=NEW.id AND date<NEW.acquiredOn)
                    THEN RAISE(ABORT,'Invalid wealth item') END; END""")
            val retarget = if (operation == "UPDATE") "OR OLD.itemId != NEW.itemId" else ""
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS wealth_value_valid_${operation.lowercase()} BEFORE $operation ON wealth_value BEGIN
                SELECT CASE WHEN typeof(NEW.valueMinor)!='integer' OR NEW.valueMinor<0 OR length(NEW.notes)>500
                    OR typeof(NEW.date)!='integer' OR NEW.date NOT BETWEEN -719162 AND 2932896
                    OR NEW.date<(SELECT acquiredOn FROM wealth_item WHERE id=NEW.itemId) $retarget
                    THEN RAISE(ABORT,'Invalid wealth value') END; END""")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS wealth_capture_valid BEFORE INSERT ON wealth_capture BEGIN
            SELECT CASE WHEN NEW.ledgerId!=1 OR typeof(NEW.date)!='integer' OR NEW.date NOT BETWEEN -719162 AND 2932896
                OR NEW.currencyCode!=(SELECT currencyCode FROM ledger WHERE id=NEW.ledgerId)
                OR typeof(NEW.cashMinor)!='integer' OR typeof(NEW.totalMinor)!='integer'
                OR typeof(NEW.assetsMinor)!='integer' OR NEW.assetsMinor<0 OR typeof(NEW.liabilitiesMinor)!='integer' OR NEW.liabilitiesMinor<0
                OR NEW.method!='TRACKED_V1' THEN RAISE(ABORT,'Invalid wealth capture') END; END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS wealth_capture_line_valid BEFORE INSERT ON wealth_capture_line BEGIN
            SELECT CASE WHEN length(trim(NEW.name)) NOT BETWEEN 1 AND 80 OR NOT $category
                OR typeof(NEW.valueMinor)!='integer' OR NEW.valueMinor<0 OR typeof(NEW.valuedOn)!='integer'
                OR NEW.valuedOn NOT BETWEEN -719162 AND 2932896 OR NEW.valuedOn>(SELECT date FROM wealth_capture WHERE id=NEW.captureId)
                THEN RAISE(ABORT,'Invalid wealth capture line') END; END""")
        for (table in listOf("wealth_capture", "wealth_capture_line")) db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${table}_immutable
            BEFORE UPDATE ON $table BEGIN SELECT RAISE(ABORT,'Captured history is immutable'); END""")
    }
}
