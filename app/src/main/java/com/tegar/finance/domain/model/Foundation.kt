package com.tegar.finance.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Ledger(val currency: CurrencyCode, val reportingZone: ZoneId,
    val trackedFromDate: LocalDate, val openingCash: Money, val createdAt: Instant)
enum class TransactionType { INCOME, EXPENSE, INVESTMENT }
data class Category(val id: String, val name: String, val transactionType: TransactionType, val active: Boolean)
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val tag: String) { ENGLISH("en"), INDONESIAN("id") }
data class AppSettings(val theme: ThemeMode = ThemeMode.SYSTEM, val language: AppLanguage = AppLanguage.ENGLISH)
