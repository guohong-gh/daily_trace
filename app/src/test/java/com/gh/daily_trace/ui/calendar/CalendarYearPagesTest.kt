package com.gh.daily_trace.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

/** 年视图页索引换算（D32 同口径：1970 ~ 2100） */
class CalendarYearPagesTest {

    @Test
    fun `范围与边界页`() {
        assertEquals(1970, CALENDAR_MIN_YEAR)
        assertEquals(2100, CALENDAR_MAX_YEAR)
        assertEquals(131, CALENDAR_YEAR_COUNT)
        assertEquals(0, yearToPage(CALENDAR_MIN_YEAR))
        assertEquals(130, yearToPage(CALENDAR_MAX_YEAR))
        assertEquals(CALENDAR_MIN_YEAR, pageToYear(0))
        assertEquals(CALENDAR_MAX_YEAR, pageToYear(130))
    }

    @Test
    fun `页与年互为逆映射`() {
        for (year in listOf(1970, 1999, 2026, 2049, 2100)) {
            assertEquals(year, pageToYear(yearToPage(year)))
        }
    }
}
