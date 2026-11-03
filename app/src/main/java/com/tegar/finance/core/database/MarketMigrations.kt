package com.tegar.finance.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MarketMigrations {
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `market_watchlist` (`id` TEXT NOT NULL, `provider` TEXT NOT NULL, `market` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_market_watchlist_provider_market_symbol` ON `market_watchlist` (`provider`, `market`, `symbol`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `market_request_window` (`provider` TEXT NOT NULL, `windowStart` INTEGER NOT NULL, `requests` INTEGER NOT NULL, `blockedUntil` INTEGER NOT NULL, PRIMARY KEY(`provider`))")
        }
    }
}
