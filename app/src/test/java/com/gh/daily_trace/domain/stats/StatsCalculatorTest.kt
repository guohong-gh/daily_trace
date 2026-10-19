package com.gh.daily_trace.domain.stats

import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 统计口径（D16）：
 * 2026-10-17 = 周六 = epochDay 20743；周一 20738；当周（周一起始）20738..20744。
 */
class StatsCalculatorTest {

    private val today = 20743L // 周六
    private val monday = 20738L
    private val daily = FrequencyRule(itemId = 1, type = FrequencyType.DAILY)

    private fun rec(day: Long, status: RecordStatus = RecordStatus.DONE, itemId: Long = 1) =
        CheckRecord(
            itemId = itemId,
            date = day,
            status = status,
            itemNameSnapshot = "X",
            itemColorSnapshot = 1,
            itemIconSnapshot = "",
        )

    private fun byDay(vararg records: CheckRecord) = records.toList().groupBy { it.date }

    // ---------- 期望日与单日状态 ----------

    @Test
    fun `DAILY与无规则每天都是期望日`() {
        assertTrue(StatsCalculator.isExpectedDay(today, null))
        assertTrue(StatsCalculator.isExpectedDay(today, daily))
    }

    @Test
    fun `DAYS_OF_WEEK只在所选周几计期望`() {
        val mondayOnly = FrequencyRule(itemId = 1, type = FrequencyType.DAYS_OF_WEEK, daysOfWeek = listOf(1))
        assertTrue(StatsCalculator.isExpectedDay(monday, mondayOnly))
        assertFalse(StatsCalculator.isExpectedDay(today, mondayOnly)) // 周六
    }

    @Test
    fun `TIMES_PER_MONTH无日级期望`() {
        val quota = FrequencyRule(itemId = 1, type = FrequencyType.TIMES_PER_MONTH, timesPerMonth = 8)
        assertFalse(StatsCalculator.isExpectedDay(today, quota))
        // 即便当天有完成记录，也不是期望日（不计入完成率分母）
        val outcome = StatsCalculator.outcomeOf(1, today, quota, listOf(rec(today)), partialCountsAsDone = true)
        assertEquals(DayOutcome.NOT_EXPECTED, outcome)
    }

    @Test
    fun `CUSTOM按间隔与锚点计期望日`() {
        val custom = FrequencyRule(
            itemId = 1,
            type = FrequencyType.CUSTOM,
            intervalDays = 2,
            anchorEpochDay = today - 4,
        )
        assertTrue(StatsCalculator.isExpectedDay(today - 4, custom)) // 锚点日
        assertFalse(StatsCalculator.isExpectedDay(today - 3, custom))
        assertTrue(StatsCalculator.isExpectedDay(today - 2, custom))
        assertFalse(StatsCalculator.isExpectedDay(today - 1, custom))
        assertTrue(StatsCalculator.isExpectedDay(today, custom))
        assertFalse(StatsCalculator.isExpectedDay(today - 5, custom)) // 锚点之前不算期望
    }

    @Test
    fun `CUSTOM缺参数时退化为每天且完成率按期望日计算`() {
        val broken = FrequencyRule(itemId = 1, type = FrequencyType.CUSTOM)
        assertTrue(StatsCalculator.isExpectedDay(today, broken))
        assertTrue(StatsCalculator.isExpectedDay(today - 10, broken))

        val custom = FrequencyRule(
            itemId = 1,
            type = FrequencyType.CUSTOM,
            intervalDays = 2,
            anchorEpochDay = today - 4,
        )
        val records = byDay(rec(today - 4), rec(today))
        val r = StatsCalculator.rate(1, custom, records, today - 4, today, partialCountsAsDone = true)
        assertEquals(2, r.doneDays)
        assertEquals(3, r.expectedDays) // today-4 / today-2 / today
    }

    @Test
    fun `跳过记录与跳过日都不中断且不计期望`() {
        val rule = FrequencyRule(itemId = 1, type = FrequencyType.DAILY, allowSkip = true, skipDates = listOf(today - 1))
        val records = byDay(rec(today - 2), rec(today - 1, RecordStatus.SKIPPED), rec(today))
        val streaks = StatsCalculator.streaks(1, rule, records, today, partialCountsAsDone = true, sinceDay = today - 2)
        assertEquals(2, streaks.current) // 跳过日桥接不断连，但只计实际打卡日
        val r = StatsCalculator.rate(1, rule, records, today - 2, today, partialCountsAsDone = true)
        assertEquals(2, r.doneDays)
        assertEquals(2, r.expectedDays) // 跳过日不计入期望
    }

    @Test
    fun `其他选项当天的记录不计入本选项`() {
        // 当天只有 item1 打卡 → item2 对同一天应判未完成（回归：曾把别项记录算进来）
        val records = byDay(rec(today, itemId = 1))
        val r2 = StatsCalculator.rate(2, daily, records, today, today, partialCountsAsDone = true)
        assertEquals(0, r2.doneDays)
        assertEquals(1, r2.expectedDays)
        val s2 = StatsCalculator.streaks(2, daily, records, today, true, sinceDay = today - 1)
        assertEquals(0, s2.current)
    }

    // ---------- 完成率 ----------

    @Test
    fun `DAILY完成率等于完成天数比区间天数`() {
        val records = byDay(rec(20737), rec(20738), rec(20740), rec(20741), rec(20743))
        val r = StatsCalculator.rate(1, daily, records, 20737, 20743, partialCountsAsDone = true)
        assertEquals(5, r.doneDays)
        assertEquals(7, r.expectedDays)
        assertEquals(5.0 / 7.0, r.rate!!, 1e-9)
    }

    @Test
    fun `DAYS_OF_WEEK完成率只按所选周几分母`() {
        val mondayOnly = FrequencyRule(itemId = 1, type = FrequencyType.DAYS_OF_WEEK, daysOfWeek = listOf(1))
        val records = byDay(rec(monday), rec(today))
        val r = StatsCalculator.rate(1, mondayOnly, records, 20737, today, partialCountsAsDone = true)
        assertEquals(1, r.doneDays)
        assertEquals(1, r.expectedDays)
    }

    @Test
    fun `PARTIAL按设置计入或不计入完成率`() {
        val records = byDay(rec(today, RecordStatus.PARTIAL))
        val counted = StatsCalculator.rate(1, daily, records, today, today, partialCountsAsDone = true)
        assertEquals(1, counted.doneDays)
        val notCounted = StatsCalculator.rate(1, daily, records, today, today, partialCountsAsDone = false)
        assertEquals(0, notCounted.doneDays)
        assertEquals(1, notCounted.expectedDays)
    }

    // ---------- 连续打卡 ----------

    @Test
    fun `今天未打卡宽限不中断`() {
        val records = byDay(rec(today - 2), rec(today - 1))
        val streaks = StatsCalculator.streaks(1, daily, records, today, true, sinceDay = today - 2)
        assertEquals(2, streaks.current)
        assertEquals(2, streaks.best)
    }

    @Test
    fun `更早的漏打卡会中断当前连续`() {
        val records = byDay(rec(today - 3), rec(today - 1), rec(today))
        val streaks = StatsCalculator.streaks(1, daily, records, today, true, sinceDay = today - 3)
        assertEquals(2, streaks.current)
        assertEquals(2, streaks.best)
    }

    @Test
    fun `最佳连续对历史最长连段取最大`() {
        val records = byDay(rec(20730), rec(20731), rec(20732), rec(20733), rec(today))
        val streaks = StatsCalculator.streaks(1, daily, records, today, true, sinceDay = 20730)
        assertEquals(1, streaks.current)
        assertEquals(4, streaks.best)
    }

    @Test
    fun `今天PARTIAL时当前连续宽限从昨天数`() {
        val records = byDay(rec(today - 1), rec(today, RecordStatus.PARTIAL))
        val streaks = StatsCalculator.streaks(1, daily, records, today, partialCountsAsDone = false, sinceDay = today - 1)
        assertEquals(1, streaks.current)
    }

    // ---------- 月度配额 ----------

    @Test
    fun `月度配额统计当月有完成记录的天数`() {
        val records = byDay(
            rec(20741), rec(20742), rec(today), // 2026-10
            rec(20700), // 更早的月份，不计
            rec(20740, RecordStatus.SKIPPED), // 跳过不计
        )
        assertEquals(3, StatsCalculator.monthlyQuotaProgress(1, records, today))
    }

    // ---------- 聚合总量（D28） ----------

    @Test
    fun `总量只计DONE与PARTIAL且按itemId隔离`() {
        val records = listOf(
            rec(today, RecordStatus.DONE).copy(value = 2.0),
            rec(today - 1, RecordStatus.PARTIAL).copy(value = 3.0),
            rec(today - 2, RecordStatus.SKIPPED).copy(value = 9.0),
            rec(today - 3, RecordStatus.PLANNED).copy(value = 9.0),
            rec(today, RecordStatus.DONE, itemId = 2).copy(value = 100.0),
        )
        val totals = StatsCalculator.totals(1, records)
        assertEquals(1, totals.doneCount)
        assertEquals(1, totals.partialCount)
        assertEquals(5.0, totals.valueSum, 1e-9)
        assertFalse(totals.isEmpty)
    }

    @Test
    fun `总量对布尔型空值记录计次数不计量值`() {
        val records = listOf(
            rec(today, RecordStatus.DONE), // 布尔型 value 为空
            rec(today - 1, RecordStatus.DONE),
        )
        val totals = StatsCalculator.totals(1, records)
        assertEquals(2, totals.doneCount)
        assertEquals(0, totals.partialCount)
        assertEquals(0.0, totals.valueSum, 1e-9)
        assertFalse(totals.isEmpty)
    }

    @Test
    fun `无记录时总量为空`() {
        assertTrue(StatsCalculator.totals(1, emptyList()).isEmpty)
        // 其他选项的记录不算本项总量
        assertTrue(StatsCalculator.totals(1, listOf(rec(today, itemId = 2))).isEmpty)
    }

    // ---------- 周/月趋势 ----------

    @Test
    fun `周趋势按周一对齐且最后一周截至今天`() {
        val doneDays = (20731..20743).filter { it != 20740 } // 10-05 起全勤，仅 10-15 漏卡
        val records = byDay(*doneDays.map { rec(it.toLong()) }.toTypedArray())
        val trend = StatsCalculator.weeklyTrend(
            items = listOf(ItemRule(1, daily)),
            recordsByDay = records,
            today = today,
            weekStart = 1,
            weeks = 2,
            partialCountsAsDone = true,
        )
        assertEquals(2, trend.size)
        // 上一周 10-05..10-11 全勤
        assertEquals(20731L, trend[0].startDay)
        assertEquals(7, trend[0].doneDays)
        assertEquals(7, trend[0].expectedDays)
        // 本周 10-12..10-17（截至今天 6 天），缺 10-15
        assertEquals(20738L, trend[1].startDay)
        assertEquals(5, trend[1].doneDays)
        assertEquals(6, trend[1].expectedDays)
        assertEquals("10/12", trend[1].label)
    }

    @Test
    fun `周起始为周日时对齐到上周日`() {
        val trend = StatsCalculator.weeklyTrend(
            items = listOf(ItemRule(1, daily)),
            recordsByDay = emptyMap(),
            today = today,
            weekStart = 7,
            weeks = 1,
            partialCountsAsDone = true,
        )
        // 周日 2026-10-11 起
        assertEquals(20737L, trend[0].startDay)
    }

    @Test
    fun `月趋势按自然月聚合`() {
        val doneDays = (20697..20743) // 9 月 1 日到 10-17 全勤
        val records = byDay(*doneDays.map { rec(it.toLong()) }.toTypedArray())
        val trend = StatsCalculator.monthlyTrend(
            items = listOf(ItemRule(1, daily)),
            recordsByDay = records,
            today = today,
            months = 2,
            partialCountsAsDone = true,
        )
        assertEquals(2, trend.size)
        assertEquals("9月", trend[0].label)
        assertEquals(30, trend[0].doneDays)
        assertEquals(30, trend[0].expectedDays)
        assertEquals("10月", trend[1].label)
        assertEquals(17, trend[1].expectedDays) // 截至 10-17
    }

    @Test
    fun `多选项聚合为合计完成率`() {
        val rule2 = FrequencyRule(itemId = 2, type = FrequencyType.DAYS_OF_WEEK, daysOfWeek = listOf(6, 7))
        val records = byDay(
            rec(20737, itemId = 2), // 周日：仅 item2 期望（有完成）
            rec(monday, itemId = 1), // 周一：仅 item1 期望（有完成）
        )
        val trend = StatsCalculator.weeklyTrend(
            items = listOf(ItemRule(1, daily), ItemRule(2, rule2)),
            recordsByDay = records,
            today = today,
            weekStart = 1,
            weeks = 1,
            partialCountsAsDone = true,
        )
        // 本周 20738..20743：item1 期望 6 天完成 1；item2 期望周六(20743) 1 天完成 0
        assertEquals(1, trend[0].doneDays)
        assertEquals(7, trend[0].expectedDays)
    }

    @Test
    fun `epochDay与星期换算自检`() {
        assertEquals(6, LocalDate.ofEpochDay(today).dayOfWeek.value) // 周六
        assertEquals(1, LocalDate.ofEpochDay(monday).dayOfWeek.value) // 周一
    }
}
