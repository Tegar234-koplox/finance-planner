package com.tegar.finance

import com.tegar.finance.core.datetime.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DatePeriodsTest {
    @Test fun reportingZoneDeterminesToday() {
        val clock = Clock.fixed(Instant.parse("2026-12-31T18:00:00Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.of(2027, 1, 1), DatePeriods.today(clock))
        assertEquals(LocalDate.of(2026, 12, 31), DatePeriods.today(clock, ZoneOffset.UTC))
    }
    @Test fun leapMonthIncludesFebruary29() {
        val range = DatePeriods.month(YearMonth.of(2028, 2))
        assertTrue(LocalDate.of(2028, 2, 29) in range)
        assertFalse(LocalDate.of(2028, 3, 1) in range)
    }
    @Test fun nonLeapMonthEndsAtMarch() {
        assertEquals(LocalDate.of(2027, 3, 1), DatePeriods.month(YearMonth.of(2027, 2)).endExclusive)
    }
    @Test fun decemberCrossesYear() {
        assertEquals(LocalDate.of(2027, 1, 1), DatePeriods.month(YearMonth.of(2026, 12)).endExclusive)
    }
    @Test fun sundayBelongsToPreviousMonday() {
        assertEquals(LocalDate.of(2026, 10, 5), DatePeriods.week(LocalDate.of(2026, 10, 11)).start)
    }
    @Test fun mondayBeginsNewWeek() {
        assertEquals(LocalDate.of(2026, 10, 12), DatePeriods.week(LocalDate.of(2026, 10, 12)).start)
    }
    @Test fun yearUsesExclusiveEnd() {
        assertFalse(LocalDate.of(2027, 1, 1) in DatePeriods.year(2026))
        assertTrue(LocalDate.of(2026, 12, 31) in DatePeriods.year(2026))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyRangeIsInvalid() {
        DatePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
    }
}
