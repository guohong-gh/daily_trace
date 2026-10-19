package com.gh.daily_trace.domain.calendar

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** 周视图矩阵口径（D34）：2026-10-19 = 周一 = epochDay 20745。 */
class WeekMatrixTest {

    private val monday = 20745L
    private val days = (monday until monday + 7).toList()

    private fun item(
        id: Long,
        name: String = "I$id",
        color: Int = (100 + id).toInt(),
        groupId: Long? = null,
        deletedAt: Long? = null,
    ) = CheckItem(
        id = id,
        name = name,
        color = color,
        type = CheckType.COUNT,
        groupId = groupId,
        deletedAt = deletedAt,
    )

    private fun rec(
        day: Long,
        itemId: Long = 1,
        status: RecordStatus = RecordStatus.DONE,
        createdAt: Long = 0L,
    ) = CheckRecord(
        itemId = itemId,
        date = day,
        status = status,
        itemNameSnapshot = "S$itemId",
        itemColorSnapshot = 1,
        itemIconSnapshot = "",
        createdAt = createdAt,
    )

    private fun rule(
        itemId: Long,
        allowSkip: Boolean = true,
        skipDates: List<Long> = emptyList(),
    ) = FrequencyRule(itemId = itemId, allowSkip = allowSkip, skipDates = skipDates)

    private fun filter(
        mode: FilterMode = FilterMode.SELECTED,
        itemIds: List<Long> = emptyList(),
        groupIds: List<Long> = emptyList(),
        logic: FilterLogic = FilterLogic.ANY,
    ) = FilterView(
        name = "F",
        mode = mode,
        itemIds = itemIds,
        groupIds = groupIds,
        logic = logic,
        dayFilter = DayFilter.ALL,
    )

    private fun statuses(row: WeekRow) = row.cells.map { it.status }

    @Test
    fun `状态优先级完成高于部分与计划`() {
        val rows = WeekMatrix.build(
            days = days,
            records = listOf(
                rec(monday, status = RecordStatus.PARTIAL),
                rec(monday, status = RecordStatus.DONE, createdAt = 1),
                rec(monday + 1, status = RecordStatus.PLANNED),
                rec(monday + 1, status = RecordStatus.PARTIAL),
                rec(monday + 2, status = RecordStatus.PLANNED),
            ),
            rules = emptyList(),
            items = listOf(item(1)),
            filter = null,
        )
        assertEquals(
            listOf(
                WeekCellStatus.DONE,
                WeekCellStatus.PARTIAL,
                WeekCellStatus.PLANNED,
                WeekCellStatus.NONE,
                WeekCellStatus.NONE,
                WeekCellStatus.NONE,
                WeekCellStatus.NONE,
            ),
            statuses(rows.single()),
        )
    }

    @Test
    fun `跳过来自规则且受allowSkip门控`() {
        val rows = WeekMatrix.build(
            days = days,
            records = emptyList(),
            rules = listOf(
                rule(1, allowSkip = true, skipDates = listOf(monday)),
                rule(2, allowSkip = false, skipDates = listOf(monday)),
            ),
            items = listOf(item(1), item(2)),
            filter = null,
        )
        assertEquals(WeekCellStatus.SKIPPED, rows[0].cells.first().status)
        assertEquals(WeekCellStatus.NONE, rows[1].cells.first().status)
    }

    @Test
    fun `记录存在时跳过不覆盖记录状态`() {
        val rows = WeekMatrix.build(
            days = days,
            records = listOf(rec(monday, status = RecordStatus.DONE)),
            rules = listOf(rule(1, skipDates = listOf(monday))),
            items = listOf(item(1)),
            filter = null,
        )
        assertEquals(WeekCellStatus.DONE, rows.single().cells.first().status)
    }

    @Test
    fun `筛选只保留命中选项且忽略日筛选`() {
        val rows = WeekMatrix.build(
            days = days,
            records = emptyList(),
            rules = emptyList(),
            items = listOf(item(1), item(2, groupId = 7), item(3, groupId = 8)),
            filter = filter(itemIds = listOf(5), groupIds = listOf(7)),
        )
        assertEquals(listOf(2L), rows.map { it.itemId })
    }

    @Test
    fun `行按选项顺序且格子日期连续`() {
        val rows = WeekMatrix.build(
            days = days,
            records = emptyList(),
            rules = emptyList(),
            items = listOf(item(3, name = "C"), item(1, name = "A"), item(2, name = "B")),
            filter = null,
        )
        assertEquals(listOf("C", "A", "B"), rows.map { it.name })
        assertEquals(days, rows.first().cells.map { it.date })
    }

    @Test
    fun `回收站选项不出行`() {
        val rows = WeekMatrix.build(
            days = days,
            records = listOf(rec(monday, itemId = 2)),
            rules = emptyList(),
            items = listOf(item(1), item(2, deletedAt = 123L)),
            filter = null,
        )
        assertEquals(listOf(1L), rows.map { it.itemId })
    }

    @Test
    fun `周外记录不落入矩阵`() {
        val rows = WeekMatrix.build(
            days = days,
            records = listOf(
                rec(monday - 1, status = RecordStatus.DONE),
                rec(monday + 7, status = RecordStatus.DONE),
            ),
            rules = emptyList(),
            items = listOf(item(1)),
            filter = null,
        )
        assertEquals(
            List(7) { WeekCellStatus.NONE },
            statuses(rows.single()),
        )
    }
}
