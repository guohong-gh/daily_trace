package com.gh.daily_trace.domain.report

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.domain.stats.DailyRecordIndex
import com.gh.daily_trace.domain.stats.DayOutcome
import com.gh.daily_trace.domain.stats.ItemRule
import com.gh.daily_trace.domain.stats.StatsCalculator
import kotlin.math.roundToInt

/**
 * 周报 / 月报报告式汇总（B18）。两种口径并列展示，全部为纯计算便于测试：
 * - 期望日口径（与统计页 / 趋势柱一致，D16）：completed / expected 由 item×日 的
 *   DayOutcome 聚合而来，PARTIAL 按设置参与，配额类型（TIMES_PER_WEEK / TIMES_PER_MONTH）
 *   无日级期望、不计入分母；
 * - 实际完成口径（报告专属）：一条 DONE 记录（或按 partialCountsAsDone 计入的 PARTIAL）
 *   即是一次完成，用于完成项次特意统计实际次数，不分期望与否；打卡天数 = 至少一个
 *   在用选项当天有完成记录；最佳日 = 当天完成的不同选项数最多的一天（并列取最早）；
 *   最勤选项 = 期内实际完成天数最多的前三（并列保持管理顺序）。仅统计在用选项
 *   （启用、未归档、未删除）的记录。
 */
data class ReportTopItem(val itemId: Long, val name: String, val doneDays: Int)

data class PeriodReport(
    /** 期望日口径：完成 / 期望（项×日） */
    val expectedDone: Int,
    val expectedTotal: Int,
    /** 实际完成口径 */
    val completions: Int,
    val recordDays: Int,
    /** PARTIAL 记录条数（不计入完成的过程量，供「部分完成」注记） */
    val partialCount: Int,
    /** 最佳日及其完成的选项数（窗口内无完成时为 null） */
    val bestDay: Long?,
    val bestDayCount: Int,
    /** 本期窗口（供展示期标题日期段） */
    val windowStart: Long,
    val windowEnd: Long,
    val topItems: List<ReportTopItem>,
    /** 上一期（紧邻等长时段）完成率；上期无期望日或窗口异常时为 null，不参与对比 */
    val previousRate: Double?,
) {
    val rate: Double? get() = if (expectedTotal == 0) null else expectedDone.toDouble() / expectedTotal
}

/**
 * 构建一期（由调用方给定 [startDay]..[endDay]，最后一期通常截至今天）报告。
 * [items] 传全量管理顺序列表，内部只取在用项；上期 = 紧邻的等长时段
 * （[startDay]-len .. [startDay]-1）。
 */
fun buildPeriodReport(
    items: List<CheckItem>,
    rulesByItem: Map<Long, FrequencyRule>,
    index: DailyRecordIndex,
    startDay: Long,
    endDay: Long,
    partialCountsAsDone: Boolean,
): PeriodReport {
    require(endDay >= startDay) { "endDay($endDay) must be >= startDay($startDay)" }
    val active = items.filter { it.deletedAt == null && it.enabled && !it.archived }
    val activeIds = active.mapTo(HashSet()) { it.id }
    val itemRules = active.map { ItemRule(it.id, rulesByItem[it.id]) }

    var completed = 0
    var partialCount = 0
    var doneCount = 0
    var expectedCount = 0
    val doneItemsPerDay = HashMap<Long, Int>()
    val doneDaysByItem = HashMap<Long, Int>()

    var day = startDay
    while (day <= endDay) {
        val dayRecords = index.recordsOn(day)
        val completedIds = HashSet<Long>()
        for (record in dayRecords) {
            if (record.itemId !in activeIds) continue
            when (record.status) {
                RecordStatus.DONE -> {
                    completed++
                    completedIds.add(record.itemId)
                }
                RecordStatus.PARTIAL -> {
                    partialCount++
                    if (partialCountsAsDone) {
                        completed++
                        completedIds.add(record.itemId)
                    }
                }
                RecordStatus.SKIPPED, RecordStatus.PLANNED -> Unit
            }
        }
        doneItemsPerDay[day] = completedIds.size
        for (itemId in completedIds) doneDaysByItem.merge(itemId, 1, Int::plus)

        for (item in itemRules) {
            val outcome = StatsCalculator.outcomeOf(
                itemId = item.itemId,
                day = day,
                rule = item.rule,
                recordsOfDay = dayRecords,
                partialCountsAsDone = partialCountsAsDone,
            )
            when (outcome) {
                DayOutcome.DONE -> { doneCount++; expectedCount++ }
                DayOutcome.PARTIAL, DayOutcome.MISSED -> expectedCount++
                else -> Unit
            }
        }
        day++
    }

    var bestDay: Long? = null
    var bestCount = 0
    var probe = startDay
    while (probe <= endDay) {
        val count = doneItemsPerDay[probe] ?: 0
        if (count > bestCount) {
            bestCount = count
            bestDay = probe
        }
        probe++
    }

    val topItems = active
        .mapNotNull { item ->
            doneDaysByItem[item.id]?.let { ReportTopItem(item.id, item.name, it) }
        }
        .sortedByDescending { it.doneDays }
        .take(3)

    val windowLen = endDay - startDay + 1
    val prevEnd = startDay - 1
    val prevStart = prevEnd - (windowLen - 1)
    var prevDone = 0
    var prevExpected = 0
    if (prevStart > 0) {
        var d = prevStart
        while (d <= prevEnd) {
            val dayRecords = index.recordsOn(d)
            for (item in itemRules) {
                val outcome = StatsCalculator.outcomeOf(
                    itemId = item.itemId,
                    day = d,
                    rule = item.rule,
                    recordsOfDay = dayRecords,
                    partialCountsAsDone = partialCountsAsDone,
                )
                when (outcome) {
                    DayOutcome.DONE -> { prevDone++; prevExpected++ }
                    DayOutcome.PARTIAL, DayOutcome.MISSED -> prevExpected++
                    else -> Unit
                }
            }
            d++
        }
    }
    val previousRate = if (prevExpected == 0) null else prevDone.toDouble() / prevExpected

    return PeriodReport(
        expectedDone = doneCount,
        expectedTotal = expectedCount,
        completions = completed,
        recordDays = doneItemsPerDay.count { it.value > 0 },
        partialCount = partialCount,
        bestDay = bestDay,
        bestDayCount = bestCount,
        windowStart = startDay,
        windowEnd = endDay,
        topItems = topItems,
        previousRate = previousRate,
    )
}

/** 完成率展示格式（纯函数）：null → "—"，否则四舍五入为整数百分比 */
fun reportRateText(rate: Double?): String =
    if (rate == null) "—" else "${(rate * 100).roundToInt()}%"

/** 与上期的差值文案（纯函数）：任一缺失返回 null（不展示）；否则 ±Npp / 持平 */
fun reportDeltaText(rate: Double?, previousRate: Double?): String? {
    if (rate == null || previousRate == null) return null
    val deltaPoints = ((rate - previousRate) * 100).roundToInt()
    return when {
        deltaPoints > 0 -> "较上期 +$deltaPoints pp"
        deltaPoints < 0 -> "较上期 $deltaPoints pp"
        else -> "较上期持平"
    }
}
