package com.gh.daily_trace.domain.report

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.domain.stats.DailyRecordIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B18 周报/月报报告式（纯计算）。
 * 2026-10-17 = 周六 = epochDay 20743；本周（周一起始）20738..20744。
 */
class PeriodReportTest {

    private val start = 20738L // 周一
    private val end = 20744L // 周日（窗口可用完整周 20738..20744）

    private fun item(
        id: Long,
        name: String,
        sort: Int = 0,
        deleted: Boolean = false,
        archived: Boolean = false,
        disabled: Boolean = false,
    ) = CheckItem(
        id = id,
        name = name,
        color = 1,
        type = CheckType.BOOLEAN,
        sort = sort,
        enabled = !disabled,
        archived = archived,
        deletedAt = if (deleted) 1L else null,
    )

    private fun rec(
        itemId: Long,
        day: Long,
        status: RecordStatus = RecordStatus.DONE,
    ) = CheckRecord(
        itemId = itemId,
        date = day,
        status = status,
        itemNameSnapshot = "$itemId",
        itemColorSnapshot = 1,
        itemIconSnapshot = "",
    )

    private fun build(
        items: List<CheckItem>,
        records: List<CheckRecord>,
        rules: Map<Long, FrequencyRule> = emptyMap(),
        start: Long = this.start,
        end: Long = this.end,
        partialCountsAsDone: Boolean = false,
    ): PeriodReport = buildPeriodReport(
        items = items,
        rulesByItem = rules,
        index = DailyRecordIndex.from(records),
        startDay = start,
        endDay = end,
        partialCountsAsDone = partialCountsAsDone,
    )

    // ---------- 空数据 ----------

    @Test
    fun `空数据完成率为null且无最佳日`() {
        val report = build(items = listOf(item(1, "A")), records = emptyList())
        // DAILY 期望 7 天、完成 0 → 0% 而非 null（null 仅在上期 / 本期无期望日时出现）
        assertEquals(0.0, report.rate!!, 1e-9)
        assertEquals(0.0, report.previousRate!!, 1e-9)
        assertNull(report.bestDay)
        assertEquals(0, report.completions)
        assertEquals(0, report.recordDays)
        assertEquals(7, report.expectedTotal) // DAILY → 全周 7 天期望
        assertEquals(0, report.topItems.size)
    }

    // ---------- 期望日口径与实际完成口径 ----------

    @Test
    fun `DAILY项完成率按期望日聚合`() {
        val items = listOf(item(1, "A"))
        // 7 天中 3 天完成
        val records = listOf(rec(1, start), rec(1, start + 2), rec(1, start + 5))
        val report = build(items, records)
        assertEquals(3, report.expectedDone)
        assertEquals(7, report.expectedTotal)
        assertEquals(3, report.completions)
        // 打卡天数 = 有 DONE 记录的天数
        assertEquals(3, report.recordDays)
    }

    @Test
    fun `最佳日按完成选项数并列取最早`() {
        val items = listOf(item(1, "A"), item(2, "B"), item(3, "C"))
        val records = listOf(
            rec(1, start), rec(2, start), rec(3, start), // d0：3 项
            rec(1, start + 3), rec(2, start + 3), // d3：2 项
            rec(3, end), rec(1, end), rec(2, end), // 最后一天又 3 项（并列）
        )
        val report = build(items, records)
        // 4 个不同完成日（d0 / d3 / end）
        assertEquals(3, report.recordDays)
        // 并列最多取最早一天
        assertEquals(start, report.bestDay)
        assertEquals(3, report.bestDayCount)
    }

    @Test
    fun `PARTIAL默认不算完成且单独计数`() {
        val items = listOf(item(1, "A"))
        val report = build(items, listOf(rec(1, start, RecordStatus.PARTIAL)))
        assertEquals(1, report.partialCount)
        assertEquals(0, report.completions)
        assertEquals(0, report.recordDays)
        assertEquals(7, report.expectedTotal) // DAILY 全周 7 天均为期望日（MISSED）
        assertEquals(0, report.expectedDone)
        assertEquals(0.0, report.rate!!, 1e-9) // 完成率 0% 而非 null
    }

    @Test
    fun `PARTIAL计入完成由partialCountsAsDone决定`() {
        val items = listOf(item(1, "A"))
        val report = build(
            items,
            listOf(rec(1, start, RecordStatus.PARTIAL)),
            partialCountsAsDone = true,
        )
        assertEquals(1, report.completions)
        assertEquals(1, report.recordDays)
        // partialCount 记录的是 PARTIAL 条数本身，不随 partialCountsAsDone 变化
        assertEquals(1, report.partialCount)
    }

    @Test
    fun `配额项无日级期望但完成记录计入实际口径`() {
        val quota = FrequencyRule(itemId = 1, type = FrequencyType.TIMES_PER_WEEK, timesPerWeek = 3)
        val items = listOf(item(1, "A"))
        val report = build(items, listOf(rec(1, start)), rules = mapOf(1L to quota))
        // 期望日口径：配额项无日级期望，不计入分母
        assertEquals(0, report.expectedDone)
        assertEquals(0, report.expectedTotal)
        assertNull(report.rate)
        // 实际口径：完成记录照常计入（凑满当周配额）
        assertEquals(1, report.completions)
        assertEquals(1, report.recordDays)
        assertEquals(1, report.topItems.size)
    }

    // ---------- 最勤选项 ----------

    @Test
    fun `最勤选项前三并保持管理顺序`() {
        val items = listOf(item(1, "A", sort = 0), item(2, "B", sort = 1), item(3, "C", sort = 2), item(4, "D", sort = 3))
        val records = listOf(
            rec(1, start), rec(1, start + 1), rec(1, start + 2), // A ×3
            rec(2, start), rec(2, start + 1), // B ×2
            rec(3, start), // C ×1
        )
        val report = build(items, records)
        assertEquals(3, report.topItems.size)
        assertEquals(1L, report.topItems[0].itemId)
        assertEquals(3, report.topItems[0].doneDays)
        assertEquals(2L, report.topItems[1].itemId)
        assertEquals(3L, report.topItems[2].itemId)
    }

    // ---------- 上期对比 ----------

    @Test
    fun `上期窗口为紧邻等长时段`() {
        val items = listOf(item(1, "A"))
        val records = listOf(
            rec(1, start), rec(1, start + 6), // 本期 2/7
            rec(1, start - 1), rec(1, start - 3), rec(1, start - 5), // 上期 3/7
        )
        val report = build(items, records)
        assertEquals(2, report.expectedDone)
        assertEquals(7, report.expectedTotal)
        assertEquals(3, report.previousRate?.let { (it * 7).toInt() }) // 3/7
        assertEquals(1.0, report.previousRate!! / (3.0 / 7.0), 1e-9)
    }

    // ---------- 文案纯函数 ----------

    @Test
    fun `完成率文案与差值文案`() {
        assertEquals("—", reportRateText(null))
        assertEquals("86%", reportRateText(6.0 / 7.0))
        assertEquals("0%", reportRateText(0.0))
        assertNull(reportDeltaText(null, null))
        assertNull(reportDeltaText(0.5, null))
        assertNull(reportDeltaText(null, 0.5))
        assertEquals("较上期 +2 pp", reportDeltaText(0.72, 0.70))
        assertEquals("较上期 -3 pp", reportDeltaText(0.67, 0.70))
        assertEquals("较上期持平", reportDeltaText(0.70, 0.70))
    }

    // ---------- 窗口守卫 ----------

    @Test
    fun `单日窗口正常工作`() {
        val items = listOf(item(1, "A"))
        val report = build(items, listOf(rec(1, start)), start = start, end = start)
        assertEquals(1, report.expectedDone)
        assertEquals(1, report.expectedTotal)
        assertEquals(start, report.bestDay)
    }
}
