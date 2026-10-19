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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 时间线日流口径（D36）：内容日倒序、空日不出现、窗口外剔除、筛选与摘要口径复用 D30。 */
class TimelineFeedTest {

    private fun day(month: Int, dayOfMonth: Int): Long =
        LocalDate.of(2026, month, dayOfMonth).toEpochDay()

    private fun item(id: Long, groupId: Long? = null) = CheckItem(
        id = id,
        name = "I$id",
        color = (100 + id).toInt(),
        type = CheckType.COUNT,
        groupId = groupId,
    )

    private fun rec(
        date: Long,
        itemId: Long = 1,
        status: RecordStatus = RecordStatus.DONE,
    ) = CheckRecord(
        itemId = itemId,
        date = date,
        status = status,
        itemNameSnapshot = "S$itemId",
        itemColorSnapshot = 1,
        itemIconSnapshot = "",
        createdAt = date,
    )

    private fun note(date: Long, text: String = "想法") = DayNote(date = date, note = text)

    private fun skipRule(itemId: Long = 1, vararg skipDates: Long) = FrequencyRule(
        itemId = itemId,
        allowSkip = true,
        skipDates = skipDates.toList(),
    )

    private fun filter(itemIds: List<Long> = emptyList(), groupIds: List<Long> = emptyList()) = FilterView(
        name = "F",
        mode = FilterMode.SELECTED,
        itemIds = itemIds,
        groupIds = groupIds,
        logic = FilterLogic.ANY,
        dayFilter = DayFilter.ALL,
    )

    private fun feed(
        oldest: Long,
        newest: Long,
        records: List<CheckRecord> = emptyList(),
        notes: List<DayNote> = emptyList(),
        rules: List<FrequencyRule> = emptyList(),
        items: List<CheckItem> = listOf(item(1)),
        filter: FilterView? = null,
    ) = TimelineFeed.build(
        oldest = oldest,
        newest = newest,
        records = records,
        notes = notes,
        rules = rules,
        items = items,
        filter = filter,
    )

    @Test
    fun `窗口内内容日倒序且空日不出现`() {
        val d16 = day(10, 16)
        val d17 = day(10, 17)
        val d19 = day(10, 19)
        val result = feed(
            oldest = day(10, 1),
            newest = d19,
            records = listOf(rec(d16), rec(d19)),
            notes = listOf(note(d17)),
        )
        assertEquals(listOf(d19, d17, d16), result.map { it.date })
    }

    @Test
    fun `窗口外内容被剔除`() {
        val result = feed(
            oldest = day(10, 1),
            newest = day(10, 19),
            records = listOf(rec(day(9, 30)), rec(day(10, 20))),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `筛选只保留命中项且备注日仍出现`() {
        val d16 = day(10, 16)
        val items = listOf(item(1), item(2))
        val byItem = feed(
            oldest = day(10, 1),
            newest = day(10, 19),
            records = listOf(rec(d16, 1), rec(d16, 2)),
            items = items,
            filter = filter(itemIds = listOf(2)),
        )
        assertEquals(1, byItem.size)
        assertEquals(listOf("S2"), byItem[0].summary.entries.map { it.name })

        // 筛选不改变备注：全部条目被排除时，备注日仍展示（无条目）
        val noteOnly = feed(
            oldest = day(10, 1),
            newest = day(10, 19),
            records = listOf(rec(d16, 1)),
            notes = listOf(note(d16)),
            items = items,
            filter = filter(itemIds = listOf(99)),
        )
        assertEquals(1, noteOnly.size)
        assertTrue(noteOnly[0].summary.entries.isEmpty())
        assertEquals("想法", noteOnly[0].summary.noteText)
    }

    @Test
    fun `纯备注日与休息日登记日出现`() {
        val d14 = day(10, 14)
        val d15 = day(10, 15)
        val result = feed(
            oldest = day(10, 1),
            newest = day(10, 19),
            notes = listOf(note(d15)),
            rules = listOf(skipRule(1, d14)),
        )
        assertEquals(listOf(d15, d14), result.map { it.date })
        assertTrue(result[0].summary.entries.isEmpty())
        assertEquals("想法", result[0].summary.noteText)
        assertEquals(listOf("I1"), result[1].summary.skipNames)
    }

    @Test
    fun `休息日登记受筛选联动`() {
        val d14 = day(10, 14)
        val items = listOf(item(1), item(2))
        val miss = feed(
            oldest = day(10, 1),
            newest = day(10, 19),
            rules = listOf(skipRule(1, d14)),
            items = items,
            filter = filter(itemIds = listOf(2)),
        )
        assertTrue(miss.isEmpty())
        val hit = feed(
            oldest = day(10, 1),
            newest = day(10, 19),
            rules = listOf(skipRule(2, d14)),
            items = items,
            filter = filter(itemIds = listOf(2)),
        )
        assertEquals(listOf(d14), hit.map { it.date })
        assertEquals(listOf("I2"), hit[0].summary.skipNames)
    }

    @Test
    fun `空窗口与空数据返回空列表`() {
        assertTrue(feed(oldest = day(10, 10), newest = day(10, 1)).isEmpty())
        assertTrue(feed(oldest = day(10, 1), newest = day(10, 19)).isEmpty())
    }
}
