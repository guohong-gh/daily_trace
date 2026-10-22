package com.gh.daily_trace.domain.stats

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M10-2 分组统计：组内期望日加权、配额项排除、未分组 / 孤儿分组归并。 */
class GroupStatsTest {

    private val start = 20_738L
    private val end = 20_740L

    private fun group(id: Long, name: String, sort: Int = 0, color: Int = 1) =
        Group(id = id, name = name, color = color, sort = sort)

    private fun item(
        id: Long,
        groupId: Long? = null,
        enabled: Boolean = true,
        archived: Boolean = false,
        deleted: Boolean = false,
    ) = CheckItem(
        id = id,
        name = "选项$id",
        color = 1,
        type = CheckType.BOOLEAN,
        groupId = groupId,
        enabled = enabled,
        archived = archived,
        deletedAt = if (deleted) 1L else null,
    )

    private fun rec(itemId: Long, day: Long, status: RecordStatus = RecordStatus.DONE) =
        CheckRecord(
            itemId = itemId,
            date = day,
            status = status,
            itemNameSnapshot = "选项$itemId",
            itemColorSnapshot = 1,
            itemIconSnapshot = "",
        )

    private fun daily(vararg itemIds: Long): Map<Long, FrequencyRule> =
        itemIds.associateWith { FrequencyRule(itemId = it, type = FrequencyType.DAILY) }

    private fun groupRates(
        groups: List<Group>,
        items: List<CheckItem>,
        records: List<CheckRecord>,
        rules: Map<Long, FrequencyRule>,
        partial: Boolean = true,
    ) = StatsCalculator.groupRates(
        groups = groups,
        items = items,
        rulesByItem = rules,
        recordsByDay = records.groupBy { it.date },
        startDay = start,
        endDay = end,
        partialCountsAsDone = partial,
    )

    @Test
    fun `分组完成率按期望日加权合并`() {
        val stats = groupRates(
            groups = listOf(group(1, "健康")),
            items = listOf(item(1, groupId = 1), item(2, groupId = 1)),
            records = listOf(
                rec(1, start), rec(1, start + 1), rec(1, start + 2),
                rec(2, start),
            ),
            rules = daily(1, 2),
        ).single()

        assertEquals(4, stats.rate.doneDays)
        assertEquals(6, stats.rate.expectedDays)
        assertEquals(2.0 / 3.0, stats.rate.rate!!, 0.0001)
        assertEquals(2, stats.itemCount)
        assertEquals(0, stats.quotaItemCount)
    }

    @Test
    fun `部分完成是否计入沿用设置`() {
        val groups = listOf(group(1, "健康"))
        val items = listOf(item(1, groupId = 1))
        val records = listOf(
            rec(1, start, RecordStatus.PARTIAL),
            rec(1, start + 1, RecordStatus.PARTIAL),
            rec(1, start + 2, RecordStatus.PARTIAL),
        )
        val rules = daily(1)

        val counted = groupRates(groups, items, records, rules, partial = true).single()
        val ignored = groupRates(groups, items, records, rules, partial = false).single()

        assertEquals(3, counted.rate.doneDays)
        assertEquals(3, counted.rate.expectedDays)
        assertEquals(0, ignored.rate.doneDays)
        assertEquals(3, ignored.rate.expectedDays)
    }

    @Test
    fun `配额项不计入分母并单独标注`() {
        val stats = groupRates(
            groups = listOf(group(1, "健康"), group(2, "学习")),
            items = listOf(
                item(1, groupId = 1),
                item(2, groupId = 1),
                item(3, groupId = 2),
            ),
            records = listOf(rec(1, start), rec(1, start + 1), rec(1, start + 2), rec(2, start)),
            rules = daily(1) + (2L to FrequencyRule(itemId = 2, type = FrequencyType.TIMES_PER_MONTH, timesPerMonth = 5)) +
                (3L to FrequencyRule(itemId = 3, type = FrequencyType.TIMES_PER_MONTH, timesPerMonth = 2)),
        )

        val mixed = stats.first { it.groupId == 1L }
        assertEquals(3, mixed.rate.doneDays)
        assertEquals(3, mixed.rate.expectedDays)
        assertEquals(2, mixed.itemCount)
        assertEquals(1, mixed.quotaItemCount)

        val quotaOnly = stats.first { it.groupId == 2L }
        assertEquals(0, quotaOnly.rate.doneDays)
        assertEquals(0, quotaOnly.rate.expectedDays)
        assertNull(quotaOnly.rate.rate)
        assertEquals(1, quotaOnly.quotaItemCount)
    }

    @Test
    fun `周配额项同样不计入分母并单独标注`() {
        val stats = groupRates(
            groups = listOf(group(1, "学习")),
            items = listOf(item(1, groupId = 1)),
            records = listOf(rec(1, start)),
            rules = mapOf(1L to FrequencyRule(itemId = 1, type = FrequencyType.TIMES_PER_WEEK, timesPerWeek = 3)),
        ).single()

        assertEquals(0, stats.rate.doneDays)
        assertEquals(0, stats.rate.expectedDays)
        assertNull(stats.rate.rate)
        assertEquals(1, stats.quotaItemCount)
    }

    @Test
    fun `分组按管理顺序输出未分组与孤儿分组置末`() {
        val stats = groupRates(
            groups = listOf(
                group(1, "健康", sort = 1),
                group(2, "学习", sort = 0),
                group(3, "空分组", sort = 2),
            ),
            items = listOf(
                item(1, groupId = 1),
                item(2, groupId = 2),
                item(3, groupId = null),
                item(4, groupId = 999),
                item(5, groupId = 1, archived = true),
                item(6, groupId = 2, deleted = true),
            ),
            records = emptyList(),
            rules = daily(1, 2, 3, 4, 5, 6),
        )

        assertEquals(listOf("学习", "健康", "未分组"), stats.map { it.name })
        assertEquals(listOf(2L, 1L, null), stats.map { it.groupId })
        assertEquals(1, stats[0].itemCount)
        assertEquals(1, stats[1].itemCount)
        assertEquals(2, stats[2].itemCount)
        assertNull(stats[2].color)
        assertTrue(stats.none { it.name == "空分组" })
    }

    @Test
    fun `无在用选项时没有分组`() {
        val stats = groupRates(
            groups = listOf(group(1, "健康")),
            items = listOf(item(1, groupId = 1, enabled = false)),
            records = emptyList(),
            rules = daily(1),
        )

        assertTrue(stats.isEmpty())
    }
}
