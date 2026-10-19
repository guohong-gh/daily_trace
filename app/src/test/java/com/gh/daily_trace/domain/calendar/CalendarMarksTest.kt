package com.gh.daily_trace.domain.calendar

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日历标记口径（D29/D31）：
 * 2026-10-19 = 周一 = epochDay 20745。
 */
class CalendarMarksTest {

    private val today = 20745L
    private val yesterday = today - 1

    private fun item(
        id: Long,
        name: String = "I$id",
        color: Int = (100 + id).toInt(),
        groupId: Long? = null,
        type: CheckType = CheckType.COUNT,
    ) = CheckItem(id = id, name = name, color = color, type = type, groupId = groupId)

    private fun rec(
        day: Long,
        itemId: Long = 1,
        color: Int = 1,
        status: RecordStatus = RecordStatus.DONE,
        value: Double? = null,
        unit: String = "",
        createdAt: Long = 0L,
    ) = CheckRecord(
        itemId = itemId,
        date = day,
        status = status,
        value = value,
        unit = unit,
        itemNameSnapshot = "S$itemId",
        itemColorSnapshot = color,
        itemIconSnapshot = "",
        createdAt = createdAt,
    )

    private fun filter(
        mode: FilterMode = FilterMode.SELECTED,
        itemIds: List<Long> = emptyList(),
        groupIds: List<Long> = emptyList(),
        logic: FilterLogic = FilterLogic.ANY,
        dayFilter: DayFilter = DayFilter.ALL,
    ) = FilterView(
        name = "F",
        mode = mode,
        itemIds = itemIds,
        groupIds = groupIds,
        logic = logic,
        dayFilter = dayFilter,
    )

    private fun build(
        records: List<CheckRecord> = emptyList(),
        notes: List<DayNote> = emptyList(),
        rules: List<FrequencyRule> = emptyList(),
        items: List<CheckItem> = listOf(item(1), item(2), item(3)),
        filter: FilterView? = null,
    ) = CalendarMarks.build(records, notes, rules, items, filter)

    // ---------- 圆点 ----------

    @Test
    fun `圆点同项同日合一按管理顺序排列并取最新快照色`() {
        // 管理顺序：item2 在前、item1 在后
        val items = listOf(item(2, color = 22), item(1, color = 11))
        val records = listOf(
            rec(today, itemId = 1, color = 11, createdAt = 10),
            rec(today, itemId = 2, color = 22, createdAt = 20),
            rec(today, itemId = 1, color = 12, createdAt = 30), // 同项第二条：取最新色
        )
        val marks = build(records = records, items = items).marks
        assertEquals(listOf(22, 12), marks[today]!!.dotColors)
        assertEquals(0, marks[today]!!.overflow)
    }

    @Test
    fun `圆点最多四加溢出计数`() {
        val items = (1L..5L).map { item(it) }
        val records = (1L..5L).map { rec(today, itemId = it, color = it.toInt()) }
        val mark = build(records = records, items = items).marks[today]!!
        assertEquals(4, mark.dotColors.size)
        assertEquals(1, mark.overflow)
    }

    @Test
    fun `计划与跳过记录不产生圆点但进入摘要`() {
        val records = listOf(
            rec(today, status = RecordStatus.PLANNED, value = 60.0),
            rec(yesterday, status = RecordStatus.SKIPPED),
        )
        val result = build(records = records)
        assertNull(result.marks[today])
        assertNull(result.marks[yesterday])
        assertEquals(RecordStatus.PLANNED, result.summaries[today]!!.entries.single().status)
        assertEquals(RecordStatus.SKIPPED, result.summaries[yesterday]!!.entries.single().status)
    }

    @Test
    fun `部分完成记录参与圆点`() {
        val mark = build(records = listOf(rec(today, status = RecordStatus.PARTIAL))).marks[today]!!
        assertEquals(1, mark.dotColors.size)
    }

    // ---------- 备注与休息日 ----------

    @Test
    fun `仅有备注的日期有备注标记与摘要`() {
        val result = build(notes = listOf(DayNote(date = today, note = "今天状态不错")))
        val mark = result.marks[today]!!
        assertTrue(mark.dotColors.isEmpty())
        assertTrue(mark.hasNote)
        assertEquals("今天状态不错", result.summaries[today]!!.noteText)
        assertTrue(result.summaries[today]!!.entries.isEmpty())
    }

    @Test
    fun `跳过日不单独标记但摘要有休息日名单且排除未开启允许跳过的项`() {
        val rules = listOf(
            FrequencyRule(itemId = 1, allowSkip = true, skipDates = listOf(today)),
            FrequencyRule(itemId = 2, allowSkip = false, skipDates = listOf(today)),
        )
        val items = listOf(item(1, name = "久坐提醒"), item(2, name = "喝水"))
        val result = build(rules = rules, items = items)
        assertNull(result.marks[today])
        assertEquals(listOf("久坐提醒"), result.summaries[today]!!.skipNames)
    }

    @Test
    fun `无记录无备注无跳过的日期没有标记也没有摘要`() {
        val result = build()
        assertNull(result.marks[today])
        assertNull(result.summaries[today])
    }

    // ---------- 筛选联动（D31：仅选项/分组，忽略 dayFilter） ----------

    @Test
    fun `筛选按选项与分组过滤圆点与摘要并忽略日筛选`() {
        val items = listOf(item(1, groupId = 10), item(2, groupId = 20), item(3, groupId = 20))
        val records = items.map { rec(today, itemId = it.id, color = it.color) }

        // 仅选项：只保留 item1（dayFilter 即便为「未打卡」也不影响标记）
        val byItem = build(records = records, items = items, filter = filter(itemIds = listOf(1), dayFilter = DayFilter.UNRECORDED))
        assertEquals(listOf(101), byItem.marks[today]!!.dotColors)
        assertEquals(listOf(1L), byItem.summaries[today]!!.entries.map { it.itemId })

        // 仅分组：命中 groupId=20 的两项
        val byGroup = build(records = records, items = items, filter = filter(groupIds = listOf(20)))
        assertEquals(listOf(102, 103), byGroup.marks[today]!!.dotColors)

        // 选项 + 分组 + ANY：并集
        val union = build(records = records, items = items, filter = filter(itemIds = listOf(1), groupIds = listOf(20), logic = FilterLogic.ANY))
        assertEquals(3, union.marks[today]!!.dotColors.size)

        // mode=ALL：不按选项过滤
        val all = build(records = records, items = items, filter = filter(mode = FilterMode.ALL, dayFilter = DayFilter.RECORDED))
        assertEquals(3, all.marks[today]!!.dotColors.size)
    }

    @Test
    fun `筛选同时作用于休息日名单`() {
        val rules = listOf(FrequencyRule(itemId = 1, allowSkip = true, skipDates = listOf(today)))
        val items = listOf(item(1, groupId = 10), item(2, groupId = 20))
        val result = build(rules = rules, items = items, filter = filter(groupIds = listOf(20)))
        assertNull(result.summaries[today])
    }

    @Test
    fun `已清除选项的记录无筛选时保留有选项筛选时排除`() {
        val records = listOf(rec(today, itemId = 99, color = 99))
        val unfiltered = build(records = records)
        assertEquals(listOf(99), unfiltered.marks[today]!!.dotColors)
        assertEquals(99L, unfiltered.summaries[today]!!.entries.single().itemId)

        val filtered = build(records = records, filter = filter(itemIds = listOf(1)))
        assertNull(filtered.marks[today])
        assertNull(filtered.summaries[today])
    }

    // ---------- 摘要条目内容 ----------

    @Test
    fun `摘要条目使用快照字段并携带值单位与状态`() {
        val records = listOf(
            rec(today, itemId = 3, value = 42.5, unit = "kg", color = 3, createdAt = 10),
            rec(today, itemId = 1, value = 30.0, unit = "分钟", color = 1, status = RecordStatus.PARTIAL, createdAt = 20),
        )
        val summary = build(records = records, items = listOf(item(1, type = CheckType.DURATION), item(3, type = CheckType.VALUE))).summaries[today]!!
        // 按选项管理顺序：item1 在前
        assertEquals(listOf(1L, 3L), summary.entries.map { it.itemId })
        val first = summary.entries[0]
        assertEquals("S1", first.name)
        assertEquals(1, first.color)
        assertEquals(CheckType.DURATION, first.type)
        assertEquals(30.0, first.value!!, 1e-9)
        assertEquals("分钟", first.unit)
        assertEquals(RecordStatus.PARTIAL, first.status)
        assertFalse(summary.isEmpty)
    }
}
