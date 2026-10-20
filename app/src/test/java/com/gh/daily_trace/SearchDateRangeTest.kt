package com.gh.daily_trace

import com.gh.daily_trace.domain.search.SearchDatePreset
import com.gh.daily_trace.domain.search.SearchDateRange
import com.gh.daily_trace.domain.search.normalizeSearchRange
import com.gh.daily_trace.domain.search.resolveSearchRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M9-1 搜索日期范围纯函数口径：预设按逻辑日含端点，自定义起止自动交换。 */
class SearchDateRangeTest {

    private val today = 20746L // 2026-10-20

    @Test
    fun allIsUnlimited() {
        val range = resolveSearchRange(SearchDatePreset.ALL, today)
        assertEquals(SearchDateRange(null, null), range)
        assertFalse(range.active)
    }

    @Test
    fun last7CoversTodayAndPreviousSixDays() {
        val range = resolveSearchRange(SearchDatePreset.LAST_7, today)
        assertEquals(SearchDateRange(today - 6, today), range)
        assertTrue(range.active)
    }

    @Test
    fun last30CoversTodayAndPrevious29Days() {
        val range = resolveSearchRange(SearchDatePreset.LAST_30, today)
        assertEquals(SearchDateRange(today - 29, today), range)
    }

    @Test
    fun customKeepsOrderedBounds() {
        val range = resolveSearchRange(SearchDatePreset.CUSTOM, today, today - 5, today - 1)
        assertEquals(SearchDateRange(today - 5, today - 1), range)
    }

    @Test
    fun customSwapsReversedBounds() {
        val range = resolveSearchRange(SearchDatePreset.CUSTOM, today, today, today - 5)
        assertEquals(SearchDateRange(today - 5, today), range)
    }

    @Test
    fun customAllowsSingleSide() {
        assertEquals(SearchDateRange(today - 3, null), normalizeSearchRange(today - 3, null))
        assertEquals(SearchDateRange(null, today - 3), normalizeSearchRange(null, today - 3))
    }

    @Test
    fun emptyCustomIsUnlimited() {
        assertEquals(SearchDateRange(null, null), normalizeSearchRange(null, null))
        assertFalse(resolveSearchRange(SearchDatePreset.CUSTOM, today).active)
    }
}
