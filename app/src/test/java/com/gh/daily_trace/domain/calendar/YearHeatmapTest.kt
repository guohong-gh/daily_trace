package com.gh.daily_trace.domain.calendar

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** 年热力图口径（D35）：2026-10-19 = 周一 = epochDay 20745。 */
class YearHeatmapTest {

    private fun day(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).toEpochDay()

    private fun item(id: Long, groupId: Long? = null) = CheckItem(
        id = id,
        name = "I$id",
        color = (100 + id).toInt(),
        type = CheckType.COUNT,
        groupId = groupId,
    )

    private fun rec(
        day: Long,
        itemId: Long = 1,
        status: RecordStatus = RecordStatus.DONE,
    ) = CheckRecord(
        itemId = itemId,
        date = day,
        status = status,
        itemNameSnapshot = "S$itemId",
        itemColorSnapshot = 1,
        itemIconSnapshot = "",
        createdAt = day,
    )

    private fun filter(
        itemIds: List<Long> = emptyList(),
        groupIds: List<Long> = emptyList(),
        dayFilter: DayFilter = DayFilter.ALL,
    ) = FilterView(
        name = "F",
        mode = FilterMode.SELECTED,
        itemIds = itemIds,
        groupIds = groupIds,
        logic = FilterLogic.ANY,
        dayFilter = dayFilter,
    )

    private fun build(
        year: Int = 2026,
        records: List<CheckRecord>,
        items: List<CheckItem> = listOf(item(1)),
        filter: FilterView? = null,
    ) = YearHeatmap.build(
        year = year,
        weekStart = DayOfWeek.MONDAY,
        records = records,
        items = items,
        filter = filter,
    )

    private fun cell(grid: YearGrid, date: Long): HeatCell =
        grid.rows.flatMap { it.cells }.first { it.date == date }

    @Test
    fun `强度按完成项数分档且四档封顶`() {
        val jan5 = day(2026, 1, 5)
        val grid = build(
            records = listOf(
                rec(jan5, 1),
                rec(jan5 + 1, 1), rec(jan5 + 1, 2),
                rec(jan5 + 2, 1), rec(jan5 + 2, 2), rec(jan5 + 2, 3),
                rec(jan5 + 3, 1), rec(jan5 + 3, 2), rec(jan5 + 3, 3), rec(jan5 + 3, 4), rec(jan5 + 3, 5),
            ),
            items = (1L..5L).map { item(it) },
        )
        assertEquals(1, cell(grid, jan5).level)
        assertEquals(2, cell(grid, jan5 + 1).level)
        assertEquals(3, cell(grid, jan5 + 2).level)
        assertEquals(4, cell(grid, jan5 + 3).level)
        assertEquals(5, cell(grid, jan5 + 3).doneCount)
        assertEquals(0, cell(grid, jan5 + 4).level)
    }

    @Test
    fun `同项同日多条完成只计一项`() {
        val jan5 = day(2026, 1, 5)
        val grid = build(
            records = listOf(rec(jan5, 1), rec(jan5, 1), rec(jan5, 1), rec(jan5, 2)),
            items = listOf(item(1), item(2)),
        )
        assertEquals(2, cell(grid, jan5).doneCount)
        assertEquals(2, cell(grid, jan5).level)
    }

    @Test
    fun `部分与计划不计入强度`() {
        val jan5 = day(2026, 1, 5)
        val grid = build(
            records = listOf(
                rec(jan5, 1, RecordStatus.PARTIAL),
                rec(jan5, 2, RecordStatus.PARTIAL),
                rec(jan5, 3, RecordStatus.PLANNED),
            ),
            items = listOf(item(1), item(2), item(3)),
        )
        assertEquals(0, cell(grid, jan5).doneCount)
        assertEquals(0, cell(grid, jan5).level)
    }

    @Test
    fun `筛选只计命中选项且忽略日筛选`() {
        val jan5 = day(2026, 1, 5)
        val records = listOf(rec(jan5, 1), rec(jan5, 2), rec(jan5, 3))
        val items = listOf(item(1), item(2, groupId = 7), item(3, groupId = 7))
        // 选项命中：只算 item2 → 1；dayFilter=UNRECORDED 不影响热力图口径
        val byItem = build(
            records = records,
            items = items,
            filter = filter(itemIds = listOf(2), dayFilter = DayFilter.UNRECORDED),
        )
        assertEquals(1, cell(byItem, jan5).doneCount)
        assertEquals(1, byItem.summary.doneCount)
        // 分组命中：group 7 → item2 + item3 → 2
        val byGroup = build(records = records, items = items, filter = filter(groupIds = listOf(7)))
        assertEquals(2, cell(byGroup, jan5).doneCount)
    }

    @Test
    fun `行结构覆盖年首尾周且年外格留空`() {
        val grid = build(records = emptyList())
        assertEquals(53, grid.rows.size)
        assertEquals(day(2025, 12, 29), grid.rows.first().weekStart)
        assertEquals(day(2026, 12, 28), grid.rows.last().weekStart)
        grid.rows.forEachIndexed { index, row ->
            assertEquals(7, row.cells.size)
            assertEquals(grid.rows.first().weekStart + index * 7L, row.weekStart)
            row.cells.forEachIndexed { offset, c -> assertEquals(row.weekStart + offset, c.date) }
        }
        assertEquals(
            listOf(false, false, false, true),
            grid.rows.first().cells.take(4).map { it.inYear },
        )
        assertEquals(
            listOf(true, true, true, true, false, false, false),
            grid.rows.last().cells.map { it.inYear },
        )
    }

    @Test
    fun `五十四行年份`() {
        val grid = build(year = 2012, records = emptyList())
        assertEquals(54, grid.rows.size)
        assertEquals(day(2011, 12, 26), grid.rows.first().weekStart)
        assertEquals(day(2012, 12, 31), grid.rows.last().weekStart)
    }

    @Test
    fun `月份栏恰好覆盖十二个月各一次`() {
        val grid = build(records = emptyList())
        assertEquals((1..12).toList(), grid.rows.mapNotNull { it.monthStart })
    }

    @Test
    fun `汇总只计当年且去重`() {
        val grid = build(
            records = listOf(
                rec(day(2025, 12, 31), 1),
                rec(day(2026, 1, 5), 1),
                rec(day(2026, 1, 5), 1),
                rec(day(2026, 1, 5), 2),
                rec(day(2026, 3, 5), 1),
                rec(day(2027, 1, 2), 1),
            ),
            items = listOf(item(1), item(2)),
        )
        assertEquals(3, grid.summary.doneCount)
        assertEquals(2, grid.summary.activeDays)
        assertFalse(cell(grid, day(2025, 12, 31)).inYear)
        assertFalse(cell(grid, day(2027, 1, 2)).inYear)
        assertEquals(0, cell(grid, day(2025, 12, 31)).level)
    }

    @Test
    fun `空数据全年为零`() {
        val grid = build(records = emptyList())
        assertTrue(
            grid.rows.flatMap { it.cells }.filter { it.inYear }.all { it.level == 0 }
        )
        assertEquals(YearSummary(0, 0), grid.summary)
    }
}
