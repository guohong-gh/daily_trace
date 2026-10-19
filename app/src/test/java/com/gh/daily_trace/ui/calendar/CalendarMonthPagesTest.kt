package com.gh.daily_trace.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** 翻月页索引换算（D32：1970-01 ~ 2100-12 固定页） */
class CalendarMonthPagesTest {

    @Test
    fun `范围边界与总页数`() {
        assertEquals(0, monthToPage(CALENDAR_MIN_MONTH))
        assertEquals(CALENDAR_MONTH_COUNT - 1, monthToPage(CALENDAR_MAX_MONTH))
        // 1970-01 ~ 2100-12 共 131 年 × 12 个月
        assertEquals(131 * 12, CALENDAR_MONTH_COUNT)
        assertEquals(YearMonth.of(1970, 1), pageToMonth(0))
        assertEquals(YearMonth.of(2100, 12), pageToMonth(CALENDAR_MONTH_COUNT - 1))
    }

    @Test
    fun `页与月互为逆映射`() {
        for (ym in listOf(
            YearMonth.of(1970, 1),
            YearMonth.of(1971, 12),
            YearMonth.of(2000, 2),
            YearMonth.of(2026, 10),
            YearMonth.of(2100, 12),
        )) {
            assertEquals(ym, pageToMonth(monthToPage(ym)))
        }
        for (page in listOf(0, 11, 12, 359, 720, CALENDAR_MONTH_COUNT - 1)) {
            assertEquals(page, monthToPage(pageToMonth(page)))
        }
    }

    @Test
    fun `年份跨越换算正确`() {
        // 2026-10 距 1970-01：56 年 × 12 个月 + 9
        assertEquals(56 * 12 + 9, monthToPage(YearMonth.of(2026, 10)))
        assertEquals(YearMonth.of(2026, 12), pageToMonth(monthToPage(YearMonth.of(2026, 10)) + 2))
    }
}
