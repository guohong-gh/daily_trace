package com.gh.daily_trace.domain.stats

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** M10-6：500 选项 × 100 天（5 万条记录）下的索引与统计口径。 */
class DailyRecordIndexTest {

    private val today = 20747L

    private fun record(
        itemId: Long,
        day: Long,
        status: RecordStatus = RecordStatus.DONE,
    ) = CheckRecord(
        itemId = itemId,
        date = day,
        value = 1.0,
        status = status,
        timezone = "Asia/Shanghai",
        createdAt = 0,
        updatedAt = 0,
        itemNameSnapshot = "选项 $itemId",
        itemColorSnapshot = 1,
        itemIconSnapshot = "",
    )

    private fun item(id: Long, groupId: Long? = null) = CheckItem(
        id = id,
        name = "选项 $id",
        color = 1,
        type = CheckType.BOOLEAN,
        groupId = groupId,
    )

    @Test
    fun `五万条记录可按日期与选项直接定位`() {
        val records = ArrayList<CheckRecord>(500 * 100)
        for (itemId in 1L..500L) {
            for (offset in 0 until 100) {
                records.add(record(itemId, today - offset))
            }
        }

        val index = DailyRecordIndex.from(records)
        assertEquals(50_000, records.size)
        assertEquals(500, index.recordsOn(today).size)
        assertEquals(1, index.recordsOn(today, 250L).size)
        assertEquals(today - 99, index.firstDayByItem[1L])
        assertEquals(500, index.firstDayByItem.size)

        val rate = StatsCalculator.rate(1, null, index, today - 99, today, partialCountsAsDone = true)
        assertEquals(100, rate.doneDays)
        assertEquals(100, rate.expectedDays)

        val streak = StatsCalculator.streaks(
            itemId = 1,
            rule = null,
            index = index,
            today = today,
            partialCountsAsDone = true,
            sinceDay = today - 99,
        )
        assertEquals(100, streak.current)
        assertEquals(100, streak.best)
    }

    @Test
    fun `索引路径与按日分组路径的统计结果一致`() {
        val rules = listOf(
            FrequencyRule(
                itemId = 1,
                type = FrequencyType.DAILY,
                allowSkip = true,
                skipDates = listOf(today - 1),
            ),
        )
        val records = listOf(
            record(1, today - 2),
            record(1, today - 1, RecordStatus.SKIPPED),
            record(1, today),
            record(2, today, RecordStatus.PARTIAL),
        )
        val items = listOf(item(1, groupId = 1), item(2, groupId = 1))
        val groups = listOf(Group(id = 1, name = "组", color = 1, sort = 1))
        val rulesByItem = rules.associateBy { it.itemId }
        val rulesForTrend = items.map { ItemRule(it.id, rulesByItem[it.id]) }

        val byDay = records.groupBy { it.date }
        val index = DailyRecordIndex.from(records)

        assertEquals(
            StatsCalculator.rate(1, rules.first(), byDay, today - 2, today, true),
            StatsCalculator.rate(1, rules.first(), index, today - 2, today, true),
        )
        assertEquals(
            StatsCalculator.streaks(1, rules.first(), byDay, today, true, today - 2),
            StatsCalculator.streaks(1, rules.first(), index, today, true, today - 2),
        )
        assertEquals(
            StatsCalculator.groupRates(groups, items, rulesByItem, byDay, today - 2, today, true),
            StatsCalculator.groupRates(groups, items, rulesByItem, index, today - 2, today, true),
        )
        assertEquals(
            StatsCalculator.weeklyTrend(rulesForTrend, byDay, today, 1, 8, true),
            StatsCalculator.weeklyTrend(rulesForTrend, index, today, 1, 8, true),
        )
        assertEquals(
            StatsCalculator.monthlyTrend(rulesForTrend, byDay, today, 6, true),
            StatsCalculator.monthlyTrend(rulesForTrend, index, today, 6, true),
        )
        assertEquals(
            StatsCalculator.monthlyQuotaProgress(1, byDay, today),
            StatsCalculator.monthlyQuotaProgress(1, index, today),
        )
    }
}
