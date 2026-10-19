package com.gh.daily_trace.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 时间线窗口口径（D36）：含今天共 30 天、不早于 1970-01-01 */
class CalendarTimelineWindowTest {

    @Test
    fun `窗口起点为含今天共三十天`() {
        assertEquals(30, TIMELINE_PAGE_DAYS)
        assertEquals(
            LocalDate.of(2026, 9, 20).toEpochDay(),
            timelineWindowStart(LocalDate.of(2026, 10, 19).toEpochDay()),
        )
    }

    @Test
    fun `窗口起点不早于下限`() {
        assertEquals(TIMELINE_FLOOR, timelineWindowStart(TIMELINE_FLOOR + 10))
    }
}
