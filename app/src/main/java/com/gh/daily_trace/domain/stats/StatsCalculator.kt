package com.gh.daily_trace.domain.stats

import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.RecordStatus
import java.time.LocalDate
import java.time.YearMonth

/** 单日完成状态（统计口径） */
enum class DayOutcome {
    DONE,
    /** 部分完成且设置未将其计入完成 */
    PARTIAL,
    SKIPPED,
    MISSED,
    NOT_EXPECTED,
}

data class RateResult(val doneDays: Int, val expectedDays: Int) {
    /** null = 范围内无期望日 */
    val rate: Double? get() = if (expectedDays == 0) null else doneDays.toDouble() / expectedDays
}

data class StreakResult(val current: Int, val best: Int)

data class PeriodStat(
    val label: String,
    val startDay: Long,
    val endDay: Long,
    val doneDays: Int,
    val expectedDays: Int,
) {
    val rate: Double get() = if (expectedDays == 0) 0.0 else doneDays.toDouble() / expectedDays
}

/** 参与聚合统计的选项与规则（TIMES_PER_MONTH 无日级期望，被期望日模型自然排除） */
data class ItemRule(val itemId: Long, val rule: FrequencyRule?)

/**
 * 统计口径（D16/D24，见 docs/status-and-plan.md）：
 * - 期望日：DAILY / 无规则 = 每天；DAYS_OF_WEEK = 所选周几；TIMES_PER_MONTH = 无日级期望（用月度配额）；
 *   CUSTOM = 每隔 N 天（自锚点日起算，锚点之前不计期望）。
 * - 跳过：记录 status=SKIPPED，或 rule.allowSkip 且当天在 skipDates 中；跳过日不计入期望日，也不中断连续。
 * - 记录按 itemId 过滤（传入全局按日分组的记录也能正确处理各选项互不污染）。
 * - PARTIAL 是否算完成由 partialCountsAsDone 决定。
 * - 连续打卡只遍历期望日；「今天」尚未打卡不视为中断（宽限当天）。
 * - 完成率 = 范围内已完成期望日 / 期望日；范围内无期望日时 rate = 0.0（UI 可显示 —）。
 */
object StatsCalculator {

    fun isExpectedDay(epochDay: Long, rule: FrequencyRule?): Boolean {
        val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
        return when (rule?.type) {
            null, FrequencyType.DAILY -> true
            FrequencyType.DAYS_OF_WEEK -> dow in rule.daysOfWeek
            FrequencyType.TIMES_PER_MONTH -> false
            FrequencyType.CUSTOM -> {
                // 每隔 N 天（自锚点日起算）；缺参数（旧数据 / 异常导入）时退化为每天
                val n = rule.intervalDays
                val anchor = rule.anchorEpochDay
                if (n == null || n <= 0 || anchor == null) true
                else epochDay >= anchor && (epochDay - anchor) % n == 0L
            }
        }
    }

    fun outcomeOf(
        itemId: Long,
        day: Long,
        rule: FrequencyRule?,
        recordsOfDay: List<CheckRecord>,
        partialCountsAsDone: Boolean,
    ): DayOutcome {
        val mine = recordsOfDay.filter { it.itemId == itemId }
        val skipped = mine.any { it.status == RecordStatus.SKIPPED } ||
            (rule?.allowSkip == true && day in rule.skipDates)
        if (skipped) return DayOutcome.SKIPPED
        if (!isExpectedDay(day, rule)) return DayOutcome.NOT_EXPECTED
        return when {
            mine.any { it.status == RecordStatus.DONE } -> DayOutcome.DONE
            mine.any { it.status == RecordStatus.PARTIAL } ->
                if (partialCountsAsDone) DayOutcome.DONE else DayOutcome.PARTIAL
            else -> DayOutcome.MISSED
        }
    }

    fun rate(
        itemId: Long,
        rule: FrequencyRule?,
        recordsByDay: Map<Long, List<CheckRecord>>,
        startDay: Long,
        endDay: Long,
        partialCountsAsDone: Boolean,
    ): RateResult {
        var done = 0
        var expected = 0
        var d = startDay
        while (d <= endDay) {
            when (outcomeOf(itemId, d, rule, recordsByDay[d].orEmpty(), partialCountsAsDone)) {
                DayOutcome.DONE -> { done++; expected++ }
                DayOutcome.PARTIAL, DayOutcome.MISSED -> expected++
                else -> Unit
            }
            d++
        }
        return RateResult(done, expected)
    }

    private data class DayEntry(val day: Long, val outcome: DayOutcome)

    fun streaks(
        itemId: Long,
        rule: FrequencyRule?,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
        partialCountsAsDone: Boolean,
        sinceDay: Long,
    ): StreakResult {
        val entries = ArrayList<DayEntry>()
        var d = sinceDay
        while (d <= today) {
            val outcome = outcomeOf(itemId, d, rule, recordsByDay[d].orEmpty(), partialCountsAsDone)
            if (outcome == DayOutcome.DONE || outcome == DayOutcome.PARTIAL || outcome == DayOutcome.MISSED) {
                entries.add(DayEntry(d, outcome))
            }
            d++
        }

        // 当前连续：从今天回溯；今天未完成时宽限（跳过今天继续数）
        var current = 0
        var i = entries.size - 1
        if (i >= 0 && entries[i].day == today && entries[i].outcome != DayOutcome.DONE) {
            i--
        }
        while (i >= 0 && entries[i].outcome == DayOutcome.DONE) {
            current++
            i--
        }

        // 最佳连续：全序列扫描 DONE 最长连段
        var best = 0
        var run = 0
        for (e in entries) {
            if (e.outcome == DayOutcome.DONE) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return StreakResult(current, maxOf(best, current))
    }

    /** 月度配额进度：当月有完成（DONE/PARTIAL）记录的天数，供 TIMES_PER_MONTH 展示「本月 x/N 次」 */
    fun monthlyQuotaProgress(
        itemId: Long,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
    ): Int {
        val ym = YearMonth.from(LocalDate.ofEpochDay(today))
        val start = ym.atDay(1).toEpochDay()
        val end = ym.atEndOfMonth().toEpochDay()
        var days = 0
        var d = start
        while (d <= end) {
            val hasMark = recordsByDay[d].orEmpty()
                .any { it.itemId == itemId && (it.status == RecordStatus.DONE || it.status == RecordStatus.PARTIAL) }
            if (hasMark) days++
            d++
        }
        return days
    }

    private fun aggregateDay(
        items: List<ItemRule>,
        recordsByDay: Map<Long, List<CheckRecord>>,
        day: Long,
        partialCountsAsDone: Boolean,
        acc: IntArray, // [done, expected]
    ) {
        for (item in items) {
            when (outcomeOf(item.itemId, day, item.rule, recordsByDay[day].orEmpty(), partialCountsAsDone)) {
                DayOutcome.DONE -> { acc[0]++; acc[1]++ }
                DayOutcome.PARTIAL, DayOutcome.MISSED -> acc[1]++
                else -> Unit
            }
        }
    }

    /** 周趋势：最近 [weeks] 个自然周（按 weekStart 对齐；最后一周截至今天） */
    fun weeklyTrend(
        items: List<ItemRule>,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
        weekStart: Int,
        weeks: Int,
        partialCountsAsDone: Boolean,
    ): List<PeriodStat> {
        val ws = weekStart.coerceIn(1, 7)
        val dow = LocalDate.ofEpochDay(today).dayOfWeek.value
        val currentWeekStart = today - ((dow - ws + 7) % 7)
        return (weeks - 1 downTo 0).map { back ->
            val start = currentWeekStart - back * 7L
            val end = minOf(start + 6, today)
            val acc = IntArray(2)
            var d = start
            while (d <= end) {
                aggregateDay(items, recordsByDay, d, partialCountsAsDone, acc)
                d++
            }
            PeriodStat(
                label = monthDayLabel(start),
                startDay = start,
                endDay = end,
                doneDays = acc[0],
                expectedDays = acc[1],
            )
        }
    }

    /** 月趋势：最近 [months] 个自然月（最后一个月截至今天） */
    fun monthlyTrend(
        items: List<ItemRule>,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
        months: Int,
        partialCountsAsDone: Boolean,
    ): List<PeriodStat> {
        val ymToday = YearMonth.from(LocalDate.ofEpochDay(today))
        return (months - 1 downTo 0).map { back ->
            val ym = ymToday.minusMonths(back.toLong())
            val start = ym.atDay(1).toEpochDay()
            val end = minOf(ym.atEndOfMonth().toEpochDay(), today)
            val acc = IntArray(2)
            var d = start
            while (d <= end) {
                aggregateDay(items, recordsByDay, d, partialCountsAsDone, acc)
                d++
            }
            PeriodStat(
                label = if (ym.year == ymToday.year) "${ym.monthValue}月" else "${ym.year}年${ym.monthValue}月",
                startDay = start,
                endDay = end,
                doneDays = acc[0],
                expectedDays = acc[1],
            )
        }
    }

    private fun monthDayLabel(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return "${date.monthValue}/${date.dayOfMonth}"
    }
}
