package com.gh.daily_trace.ui.calendar

import com.gh.daily_trace.domain.time.DayClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** 周视图页索引换算（D32 同口径：1970-01-01 所在周 ~ 2100-12-31 所在周） */
class CalendarWeekPagesTest {

    private fun day(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun `周一起始边界与总页数`() {
        val bounds = calendarWeekBounds(DayOfWeek.MONDAY)
        // 1970-01-01 为周四 → 所在周一为 1969-12-29；2100-12-31 为周五 → 所在周一为 2100-12-27
        assertEquals(day(1969, 12, 29), bounds.first)
        assertEquals(day(2100, 12, 27), bounds.last)
        assertEquals(6836, weekPageCount(DayOfWeek.MONDAY))
        assertEquals(0, weekToPage(bounds.first, DayOfWeek.MONDAY))
        assertEquals(6835, weekToPage(bounds.last, DayOfWeek.MONDAY))
        assertEquals(bounds.first, pageToWeekStart(0, DayOfWeek.MONDAY))
        assertEquals(bounds.last, pageToWeekStart(6835, DayOfWeek.MONDAY))
    }

    @Test
    fun `周日起始边界与总页数`() {
        val bounds = calendarWeekBounds(DayOfWeek.SUNDAY)
        assertEquals(day(1969, 12, 28), bounds.first)
        assertEquals(day(2100, 12, 26), bounds.last)
        assertEquals(6836, weekPageCount(DayOfWeek.SUNDAY))
    }

    @Test
    fun `页与周互为逆映射`() {
        val samples = listOf(day(2000, 2, 28), day(2026, 10, 15), day(2026, 10, 19), day(2100, 12, 31))
        for (weekStart in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
            val bounds = calendarWeekBounds(weekStart)
            for (sample in samples) {
                val anchor = DayClock.weekStartOf(sample, weekStart)
                assertTrue(anchor in bounds)
                assertTrue(sample - anchor in 0..6)
                assertEquals(anchor, pageToWeekStart(weekToPage(anchor, weekStart), weekStart))
            }
        }
    }
}
