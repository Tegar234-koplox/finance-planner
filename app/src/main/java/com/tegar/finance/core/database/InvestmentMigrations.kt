package com.tegar.finance.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object InvestmentMigrations {
    val FROM_5_TO_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS investment_holding (id TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL,
                name TEXT NOT NULL, symbol TEXT NOT NULL, bucket TEXT NOT NULL, acquiredOn INTEGER NOT NULL, costMinor INTEGER,
                notes TEXT NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER,
                FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_investment_holding_ledgerId ON investment_holding(ledgerId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS investment_value (id TEXT NOT NULL PRIMARY KEY, holdingId TEXT NOT NULL,
                date INTEGER NOT NULL, valueMinor INTEGER NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER,
                FOREIGN KEY(holdingId) REFERENCES investment_holding(id) ON UPDATE RESTRICT ON DELETE CASCADE)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_investment_value_holdingId_date ON investment_value(holdingId,date)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS allocation_target (bucket TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL,
                basisPoints INTEGER NOT NULL, FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_allocation_target_ledgerId ON allocation_target(ledgerId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS contribution_plan (id TEXT NOT NULL PRIMARY KEY, ledgerId INTEGER NOT NULL,
                month TEXT NOT NULL, targetMinor INTEGER NOT NULL,
                FOREIGN KEY(ledgerId) REFERENCES ledger(id) ON UPDATE RESTRICT ON DELETE RESTRICT)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contribution_plan_ledgerId_month ON contribution_plan(ledgerId,month)")
            db.execSQL("ALTER TABLE finance_transaction ADD COLUMN investmentAction TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE finance_transaction ADD COLUMN holdingId TEXT DEFAULT NULL REFERENCES investment_holding(id) ON UPDATE RESTRICT ON DELETE RESTRICT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_finance_transaction_holdingId ON finance_transaction(holdingId)")
            db.execSQL("ALTER TABLE wealth_capture ADD COLUMN investmentsMinor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""CREATE TABLE IF NOT EXISTS wealth_capture_investment (id TEXT NOT NULL PRIMARY KEY, captureId TEXT NOT NULL,
                sourceHoldingId TEXT NOT NULL, name TEXT NOT NULL, bucket TEXT NOT NULL, valueMinor INTEGER NOT NULL, valuedOn INTEGER NOT NULL,
                FOREIGN KEY(captureId) REFERENCES wealth_capture(id) ON UPDATE RESTRICT ON DELETE CASCADE)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wealth_capture_investment_captureId_sourceHoldingId ON wealth_capture_investment(captureId,sourceHoldingId)")
            installGuards(db)
        }
    }
    fun installGuards(db: SupportSQLiteDatabase) {
        val buckets = "('STOCK','BOND','FUND','GOLD','OTHER')"
        for (operation in listOf("INSERT", "UPDATE")) {
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS investment_holding_valid_${operation.lowercase()} BEFORE $operation ON investment_holding BEGIN
                SELECT CASE WHEN NEW.ledgerId!=1 OR length(trim(NEW.name)) NOT BETWEEN 1 AND 80 OR length(NEW.symbol)>30
                    OR NEW.bucket NOT IN $buckets OR typeof(NEW.acquiredOn)!='integer' OR NEW.acquiredOn NOT BETWEEN -719162 AND 2932896
                    OR (NEW.costMinor IS NOT NULL AND (typeof(NEW.costMinor)!='integer' OR NEW.costMinor<0)) OR length(NEW.notes)>500 OR NEW.active NOT IN (0,1)
                    OR EXISTS(SELECT 1 FROM investment_value WHERE holdingId=NEW.id AND date<NEW.acquiredOn)
                    OR EXISTS(SELECT 1 FROM finance_transaction WHERE holdingId=NEW.id AND date<NEW.acquiredOn)
                    THEN RAISE(ABORT,'Invalid investment holding') END; END""")
            val retarget = if (operation == "UPDATE") "OR OLD.holdingId!=NEW.holdingId" else ""
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS investment_value_valid_${operation.lowercase()} BEFORE $operation ON investment_value BEGIN
                SELECT CASE WHEN typeof(NEW.valueMinor)!='integer' OR NEW.valueMinor<0 OR length(NEW.notes)>500
                    OR typeof(NEW.date)!='integer' OR NEW.date NOT BETWEEN -719162 AND 2932896
                    OR NEW.date<(SELECT acquiredOn FROM investment_holding WHERE id=NEW.holdingId) $retarget
                    THEN RAISE(ABORT,'Invalid investment value') END; END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS allocation_target_valid_${operation.lowercase()} BEFORE $operation ON allocation_target BEGIN
                SELECT CASE WHEN NEW.ledgerId!=1 OR NEW.bucket NOT IN ('CASH','STOCK','BOND','FUND','GOLD','OTHER')
                    OR typeof(NEW.basisPoints)!='integer' OR NEW.basisPoints NOT BETWEEN 0 AND 10000
                    OR NEW.basisPoints+COALESCE((SELECT SUM(basisPoints) FROM allocation_target WHERE bucket!=NEW.bucket),0)>10000
                    THEN RAISE(ABORT,'Invalid allocation target') END; END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS contribution_plan_valid_${operation.lowercase()} BEFORE $operation ON contribution_plan BEGIN
                SELECT CASE WHEN NEW.ledgerId!=1 OR typeof(NEW.targetMinor)!='integer' OR NEW.targetMinor<0
                    OR length(NEW.month)!=7 OR NEW.month NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]'
                    OR substr(NEW.month,1,4)='0000' OR substr(NEW.month,6,2) NOT BETWEEN '01' AND '12'
                    THEN RAISE(ABORT,'Invalid contribution plan') END; END""")
            val archived = if (operation == "INSERT") "1" else "(OLD.holdingId IS NOT NEW.holdingId)"
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS investment_transaction_valid_${operation.lowercase()} BEFORE $operation ON finance_transaction BEGIN
                SELECT CASE WHEN (NEW.transactionType!='INVESTMENT' AND (NEW.investmentAction IS NOT NULL OR NEW.holdingId IS NOT NULL))
                    OR (NEW.investmentAction IS NOT NULL AND NEW.investmentAction NOT IN ('CONTRIBUTION','REDEMPTION'))
                    OR (NEW.holdingId IS NOT NULL AND (NEW.date<(SELECT acquiredOn FROM investment_holding WHERE id=NEW.holdingId)
                        OR NEW.ledgerId!=(SELECT ledgerId FROM investment_holding WHERE id=NEW.holdingId)))
                    OR ($archived AND EXISTS(SELECT 1 FROM investment_holding WHERE id=NEW.holdingId AND active=0))
                    THEN RAISE(ABORT,'Invalid investment cash entry') END; END""")
        }
        // Replace the v1-only insertion guard; old captured rows and immutable UPDATE guards stay untouched.
        db.execSQL("DROP TRIGGER IF EXISTS wealth_capture_valid")
        db.execSQL("""CREATE TRIGGER wealth_capture_valid BEFORE INSERT ON wealth_capture BEGIN
            SELECT CASE WHEN NEW.ledgerId!=1 OR typeof(NEW.date)!='integer' OR NEW.date NOT BETWEEN -719162 AND 2932896
                OR NEW.currencyCode!=(SELECT currencyCode FROM ledger WHERE id=NEW.ledgerId)
                OR typeof(NEW.cashMinor)!='integer' OR typeof(NEW.totalMinor)!='integer'
                OR typeof(NEW.assetsMinor)!='integer' OR NEW.assetsMinor<0 OR typeof(NEW.liabilitiesMinor)!='integer' OR NEW.liabilitiesMinor<0
                OR typeof(NEW.investmentsMinor)!='integer' OR NEW.investmentsMinor<0 OR NEW.method NOT IN ('TRACKED_V1','TRACKED_V2')
                OR (NEW.method='TRACKED_V1' AND NEW.investmentsMinor!=0)
                THEN RAISE(ABORT,'Invalid wealth capture') END; END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS wealth_investment_capture_valid BEFORE INSERT ON wealth_capture_investment BEGIN
            SELECT CASE WHEN length(trim(NEW.name)) NOT BETWEEN 1 AND 80 OR NEW.bucket NOT IN $buckets
                OR typeof(NEW.valueMinor)!='integer' OR NEW.valueMinor<0 OR typeof(NEW.valuedOn)!='integer'
                OR NEW.valuedOn NOT BETWEEN -719162 AND 2932896 OR NEW.valuedOn>(SELECT date FROM wealth_capture WHERE id=NEW.captureId)
                OR (SELECT method FROM wealth_capture WHERE id=NEW.captureId)!='TRACKED_V2'
                THEN RAISE(ABORT,'Invalid investment capture') END; END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS wealth_investment_capture_immutable BEFORE UPDATE ON wealth_capture_investment
            BEGIN SELECT RAISE(ABORT,'Captured history is immutable'); END""")
    }
}
