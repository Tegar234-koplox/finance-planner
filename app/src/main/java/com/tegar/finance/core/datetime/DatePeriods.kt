package com.tegar.finance.core.datetime

import java.time.*
import java.time.temporal.TemporalAdjusters

data class DatePeriod(val start: LocalDate, val endExclusive: LocalDate) {
    init { require(endExclusive > start) }
    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive
}
object DatePeriods {
    val DefaultZone: ZoneId = ZoneId.of("Asia/Jakarta")
    fun today(clock: Clock, zone: ZoneId = DefaultZone): LocalDate = LocalDate.now(clock.withZone(zone))
    fun month(month: YearMonth): DatePeriod = DatePeriod(month.atDay(1), month.plusMonths(1).atDay(1))
    fun week(date: LocalDate): DatePeriod {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return DatePeriod(monday, monday.plusDays(7))
    }
    fun year(year: Int): DatePeriod = DatePeriod(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))
}
