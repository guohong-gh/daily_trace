package com.gh.daily_trace.domain.filter

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMatcherTest {

    private fun item(id: Long, groupId: Long? = null) =
        CheckItem(id = id, name = "I$id", color = 0, type = CheckType.BOOLEAN, groupId = groupId)

    private fun filter(
        mode: FilterMode = FilterMode.SELECTED,
        itemIds: List<Long> = emptyList(),
        groupIds: List<Long> = emptyList(),
        logic: FilterLogic = FilterLogic.ANY,
        dayFilter: DayFilter = DayFilter.ALL,
    ) = FilterView(
        name = "t", mode = mode, itemIds = itemIds, groupIds = groupIds,
        logic = logic, dayFilter = dayFilter,
    )

    @Test
    fun `ALL模式不限制选项与分组`() {
        assertTrue(FilterMatcher.itemMatches(item(1, 9), filter(mode = FilterMode.ALL)))
    }

    @Test
    fun `仅选项时命中itemIds`() {
        val f = filter(itemIds = listOf(1, 2))
        assertTrue(FilterMatcher.itemMatches(item(1), f))
        assertTrue(FilterMatcher.itemMatches(item(2), f))
        assertFalse(FilterMatcher.itemMatches(item(3), f))
    }

    @Test
    fun `仅分组时命中groupIds`() {
        val f = filter(groupIds = listOf(5))
        assertTrue(FilterMatcher.itemMatches(item(1, 5), f))
        assertFalse(FilterMatcher.itemMatches(item(1, 6), f))
        assertFalse(FilterMatcher.itemMatches(item(1, null), f))
    }

    @Test
    fun `ANY取并集`() {
        val f = filter(itemIds = listOf(1), groupIds = listOf(5), logic = FilterLogic.ANY)
        assertTrue(FilterMatcher.itemMatches(item(1, 6), f)) // 仅选项命中
        assertTrue(FilterMatcher.itemMatches(item(2, 5), f)) // 仅分组命中
        assertFalse(FilterMatcher.itemMatches(item(2, 6), f))
    }

    @Test
    fun `ALL取交集`() {
        val f = filter(itemIds = listOf(1), groupIds = listOf(5), logic = FilterLogic.ALL)
        assertTrue(FilterMatcher.itemMatches(item(1, 5), f))
        assertFalse(FilterMatcher.itemMatches(item(1, 6), f))
        assertFalse(FilterMatcher.itemMatches(item(2, 5), f))
    }

    @Test
    fun `dayFilter按有无记录过滤`() {
        val recorded = filter(dayFilter = DayFilter.RECORDED)
        assertTrue(FilterMatcher.matches(item(1), true, recorded))
        assertFalse(FilterMatcher.matches(item(1), false, recorded))

        val unrecorded = filter(dayFilter = DayFilter.UNRECORDED)
        assertTrue(FilterMatcher.matches(item(1), false, unrecorded))
        assertFalse(FilterMatcher.matches(item(1), true, unrecorded))
    }

    @Test
    fun `null筛选视为全部通过`() {
        assertTrue(FilterMatcher.matches(item(1), hasRecords = false, filter = null))
    }

    @Test
    fun `SELECTED但未选任何条件时视为不限制`() {
        assertTrue(FilterMatcher.itemMatches(item(1, 2), filter()))
    }
}
